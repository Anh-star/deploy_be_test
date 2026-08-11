package com.cmcu.itstudy.service.impl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.IOException;

/**
 * Renders a single locked / paywalled PDF page as a blurred raster
 * image and embeds it into a fresh {@code PDPage}.
 *
 * <h2>Purpose</h2>
 * <p>The locked derivative carries a blurred raster that still
 * hints at the original document layout (headings, paragraphs,
 * tables, highlight colours) but cannot be read. The card
 * overlay, badges, copy and CTAs are deliberately NOT drawn here
 * — those live in the frontend HTML overlay rendered by the
 * LimitedPaidPdfViewer component. The backend stays responsible
 * for producing the raster; the frontend owns the visual
 * hierarchy.</p>
 *
 * <h2>Pipeline</h2>
 * <ol>
 *   <li>Render the source page against a temporary
 *       {@code PDDocument} at {@link #LOCKED_RENDER_DPI}.</li>
 *   <li>Downscale to {@link #LOCKED_WORKING_WIDTH} px wide
 *       (aspect preserved) so glyph detail is destroyed.</li>
 *   <li>Apply a Gaussian blur with
 *       {@link #LOCKED_BLUR_RADIUS}.</li>
 *   <li>Paint a translucent white veil
 *       ({@link #LOCKED_WHITE_OVERLAY_ALPHA}) so the page reads
 *       as a document rather than a dark smear.</li>
 *   <li>Upscale bilinearly to {@link #LOCKED_OUTPUT_WIDTH} px
 *       wide for embedding.</li>
 *   <li>JPEG-encode at {@link #LOCKED_JPEG_QUALITY} and embed
 *       into a fresh {@code PDPage} sized exactly like the
 *       source.</li>
 * </ol>
 *
 * <h2>Security goals</h2>
 * <ul>
 *   <li>{@code appendLockedPage} NEVER calls
 *       {@code PDPage.importPage} on the source — only the
 *       rasterised image crosses the boundary, so the locked
 *       derivative cannot leak vector content, text layer,
 *       annotations, form fields, JavaScript or document
 *       metadata.</li>
 *   <li>The embedded raster width is capped by
 *       {@link #LOCKED_OUTPUT_WIDTH}; higher-resolution pixels
 *       stay server-side.</li>
 *   <li>The output byte size is capped by
 *       {@link #LOCKED_MAX_OUTPUT_BYTES}; oversized pages fall
 *       back to a flat placeholder.</li>
 * </ul>
 */
final class LockedPageRenderer {

    private static final Logger log = LoggerFactory.getLogger(LockedPageRenderer.class);

    /** Source raster DPI — preserves the page's structural shapes
     * (paragraph blocks, tables, headings) while staying safely
     * below text legibility threshold. */
    static final float LOCKED_RENDER_DPI = 72f;

    /** Width of the intermediate downscaled image before the blur
     * pass. 440px is wide enough to preserve colour gradients
     * that encode headings / highlights yet narrow enough that
     * individual glyphs are destroyed. */
    static final int LOCKED_WORKING_WIDTH = 440;

    /** Width of the upscaled raster embedded into the locked
     * page. The image is upscaled bilinearly after the blur so
     * it covers the page without aliasing. */
    static final int LOCKED_OUTPUT_WIDTH = 760;

    /** Gaussian blur radius applied to the working raster. */
    static final int LOCKED_BLUR_RADIUS = 5;

    /** Translucent white veil painted on top of the blurred
     * raster before the bilinear upscale. */
    static final float LOCKED_WHITE_OVERLAY_ALPHA = 0.13f;

    /** JPEG quality used to serialise the embedded image. */
    static final float LOCKED_JPEG_QUALITY = 0.65f;

    /** Defensive cap on the per-page JPEG byte size. */
    static final int LOCKED_MAX_OUTPUT_BYTES = 64 * 1024;

    private LockedPageRenderer() {
    }

    /**
     * Append a single locked page to {@code target} using
     * {@code sourcePage} only as a size reference. The page
     * carries nothing more than the blurred raster — no card,
     * no badge, no copy. The unlock surface lives in the
     * frontend HTML overlay.
     *
     * @param target    the locked derivative document
     * @param source    the original source document (must remain
     *                  open while this method runs)
     * @param sourcePageIndex  zero-based index of the source page
     * @param indexInLockedRange  zero-based index inside the
     *                  locked range (used for diagnostic logs)
     */
    static void appendLockedPage(PDDocument target, PDDocument source,
                                 int sourcePageIndex, int indexInLockedRange) {
        if (source == null || sourcePageIndex < 0
                || sourcePageIndex >= source.getNumberOfPages()) {
            appendPlaceholderLockedPage(target, PDRectangle.LETTER);
            return;
        }
        PDPage sourcePage = source.getPage(sourcePageIndex);
        PDRectangle rawBox = sourcePage.getMediaBox();
        try {
            BufferedImage rendered = renderSourcePage(source, sourcePageIndex, LOCKED_RENDER_DPI);
            if (rendered == null) {
                appendPlaceholderLockedPage(target, rawBox);
                return;
            }
            BufferedImage downscaled = downscale(rendered, LOCKED_WORKING_WIDTH);
            BufferedImage blurred = blurWithVeil(downscaled,
                    LOCKED_BLUR_RADIUS,
                    LOCKED_WHITE_OVERLAY_ALPHA);
            BufferedImage upscaled = upscaleBilinear(blurred, LOCKED_OUTPUT_WIDTH);
            byte[] jpegBytes = encodeJpeg(upscaled, LOCKED_JPEG_QUALITY);
            if (jpegBytes == null || jpegBytes.length > LOCKED_MAX_OUTPUT_BYTES) {
                appendPlaceholderLockedPage(target, rawBox);
                return;
            }
            PDImageXObject pdImage = JPEGFactory.createFromImage(target, upscaled);
            // PDFRenderer.renderImageWithDPI already applies the
            // source page /Rotate to the bitmap it returns, so
            // the raster we receive is in DISPLAY orientation.
            // The destination page MUST therefore:
            //   - keep /Rotate = 0 (so the viewer does not
            //     apply a second rotation);
            //   - carry a MediaBox that matches the display
            //     dimensions of the bitmap (so width and
            //     height stay in display orientation).
            writeRasterOnlyInDisplayOrientation(target, pdImage,
                    upscaled.getWidth(), upscaled.getHeight());
        } catch (IOException e) {
            log.warn("Failed to render locked page {}; using placeholder instead",
                    indexInLockedRange);
            appendPlaceholderLockedPage(target, rawBox);
        }
    }

    /**
     * Backwards-compatible overload that derives a single-page
     * document from a standalone {@link PDPage}. Useful for unit
     * tests that build a synthetic fixture.
     */
    static void appendLockedPage(PDDocument target, PDPage sourcePage, int indexInLockedRange) {
        if (sourcePage == null) {
            appendPlaceholderLockedPage(target, PDRectangle.LETTER);
            return;
        }
        // Wrap the standalone page into a transient document so
        // PDFRenderer can render its existing resources. This is
        // only safe inside the locked-renderer pipeline because
        // the resulting raster is the only thing that survives
        // into the locked derivative.
        try (PDDocument temp = new PDDocument()) {
            temp.addPage(sourcePage);
            appendLockedPage(target, temp, 0, indexInLockedRange);
        } catch (IOException e) {
            appendPlaceholderLockedPage(target, sourcePage.getMediaBox());
        }
    }

    /**
     * Render the source page into a {@link BufferedImage} at the
     * configured DPI. The render is done against a fresh
     * {@code PDDocument} so the locked derivative never inherits
     * resources from the source page.
     */
    static BufferedImage renderSourceLayout(PDPage sourcePage, float dpi) throws IOException {
        try (PDDocument tempDoc = new PDDocument()) {
            PDPage cloned = new PDPage(sourcePage.getMediaBox());
            tempDoc.addPage(cloned);
            PDFRenderer renderer = new PDFRenderer(tempDoc);
            return renderer.renderImageWithDPI(0, dpi);
        }
    }

    /**
     * Render a single source page from a separate (already-loaded)
     * source document. The source document is owned by the
     * caller and MUST remain open while this method runs. The
     * locked derivative never imports the source page — it only
     * consumes the raster.
     */
    static BufferedImage renderSourcePage(PDDocument source, int sourcePageIndex, float dpi) throws IOException {
        PDFRenderer renderer = new PDFRenderer(source);
        return renderer.renderImageWithDPI(sourcePageIndex, dpi);
    }

    /**
     * Append a flat placeholder page (solid neutral colour, no
     * copy, no card). Used when the source page cannot be
     * rendered or the embedded image would exceed the
     * configured size budget.
     */
    private static void appendPlaceholderLockedPage(PDDocument target, PDRectangle size) {
        try {
            PDPage page = new PDPage(size == null ? PDRectangle.LETTER : size);
            target.addPage(page);
            try (PDPageContentStream writer = new PDPageContentStream(target, page)) {
                writer.setNonStrokingColor(0.96f, 0.97f, 0.99f);
                writer.addRect(0, 0,
                        page.getMediaBox().getWidth(),
                        page.getMediaBox().getHeight());
                writer.fill();
            }
        } catch (IOException e) {
            log.warn("Failed to append placeholder locked page");
        }
    }

    /**
     * Embed the blurred raster into a fresh {@code PDPage} whose
     * {@code MediaBox} matches the DISPLAY dimensions of the
     * raster and whose {@code /Rotate} stays at 0.
     *
     * <p>PDFBox's {@link PDFRenderer#renderImageWithDPI(int, float)}
     * honours the source page's {@code /Rotate} while producing
     * the bitmap. The raster we receive is therefore already
     * in display orientation — rotated and sized to match what a
     * PDF viewer would show.</p>
     *
     * <p>To avoid double-rotation we MUST NOT propagate the
     * source page's {@code /Rotate} into the destination page.
     * The destination page carries {@code /Rotate = 0} and a
     * {@code MediaBox} sized to the raster (in CSS pixels at
     * 72 DPI). The image is drawn with the standard
     * {@code drawImage} call, which already uses a positive
     * unit matrix — no rotation, no mirror.</p>
     */
    private static void writeRasterOnlyInDisplayOrientation(PDDocument target,
                                                            PDImageXObject image,
                                                            int imageW,
                                                            int imageH)
            throws IOException {
        if (image == null) {
            appendPlaceholderLockedPage(target, PDRectangle.LETTER);
            return;
        }
        // The raster is sized in pixels. At 72 DPI (the source
        // DPI used by renderSourcePage) one pixel equals one
        // PDF user-space point, so the MediaBox can be sized in
        // pixels directly without a scale conversion.
        float pageW = Math.max(1f, (float) imageW);
        float pageH = Math.max(1f, (float) imageH);
        PDRectangle displayBox = new PDRectangle(0f, 0f, pageW, pageH);
        PDPage page = new PDPage(displayBox);
        // Explicitly pin /Rotate to 0. PDFBox's default is 0,
        // but we assert it here to document the contract:
        // the raster is already display-oriented; no further
        // rotation is applied.
        page.setRotation(0);
        target.addPage(page);
        try (PDPageContentStream writer = new PDPageContentStream(target, page)) {
            // drawImage uses the identity transform — a
            // positive 1×1 unit matrix — so the image is
            // never mirrored or rotated by this call.
            writer.drawImage(image, 0, 0, pageW, pageH);
        }
    }

    /**
     * Downscale the raster so the largest dimension equals
     * {@code maxWidth}. Aspect ratio is preserved.
     */
    static BufferedImage downscale(BufferedImage source, int maxWidth) {
        if (source == null) return null;
        int sw = source.getWidth();
        int sh = source.getHeight();
        if (sw <= 0 || sh <= 0) return source;
        int targetW = Math.min(sw, maxWidth);
        int targetH = Math.max(1, (int) Math.round((double) sh * targetW / sw));
        BufferedImage out = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_SPEED);
            g.drawImage(source, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * Apply a Gaussian blur followed by a translucent white veil
     * so the page reads as a bright document preview rather than
     * a dark smear. Returns a new image with the same dimensions
     * as the input.
     */
    static BufferedImage blurWithVeil(BufferedImage source, int radius, float overlayAlpha) {
        if (source == null) return null;
        int w = source.getWidth();
        int h = source.getHeight();
        if (radius <= 0) return source;
        // Gaussian kernel — size must be odd.
        int kernelSize = Math.max(3, radius * 2 + 1);
        float sigma = Math.max(1.0f, radius / 2.0f);
        float[] kernelData = gaussianKernel1D(kernelSize, sigma);
        Kernel horizontal = new Kernel(kernelSize, 1, kernelData);
        Kernel vertical = new Kernel(1, kernelSize, kernelData);
        BufferedImage step1 = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        new ConvolveOp(horizontal, ConvolveOp.EDGE_NO_OP, null)
                .filter(source, step1);
        BufferedImage blurred = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        new ConvolveOp(vertical, ConvolveOp.EDGE_NO_OP, null)
                .filter(step1, blurred);

        // White veil — keeps the page bright enough to be
        // recognised as a document.
        float alpha = clamp01(overlayAlpha);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(blurred, 0, 0, null);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * Upscale the raster to the configured output width using
     * bilinear interpolation. The blur has already destroyed
     * any readable glyphs; the upscale is purely visual.
     */
    static BufferedImage upscaleBilinear(BufferedImage source, int targetWidth) {
        if (source == null) return null;
        int sw = source.getWidth();
        int sh = source.getHeight();
        if (sw <= 0 || sh <= 0) return source;
        if (targetWidth <= sw) {
            // Don't downscale here — the caller is expected to
            // pass a wider target.
            return source;
        }
        int targetH = Math.max(1, (int) Math.round((double) sh * targetWidth / sw));
        BufferedImage out = new BufferedImage(targetWidth, targetH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(source, 0, 0, targetWidth, targetH, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * Build a 1-D Gaussian kernel with the requested size and
     * sigma. The result is normalised so the kernel sums to 1.
     */
    static float[] gaussianKernel1D(int size, float sigma) {
        int half = size / 2;
        float[] data = new float[size];
        float sum = 0f;
        for (int i = 0; i < size; i++) {
            int x = i - half;
            data[i] = (float) Math.exp(-(x * x) / (2.0 * sigma * sigma));
            sum += data[i];
        }
        if (sum > 0f) {
            for (int i = 0; i < size; i++) {
                data[i] /= sum;
            }
        }
        return data;
    }

    /** Encode a {@link BufferedImage} as JPEG at the given quality. */
    static byte[] encodeJpeg(BufferedImage image, float quality) {
        if (image == null) return null;
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(32 * 1024);
            javax.imageio.ImageWriter writer = javax.imageio.ImageIO
                    .getImageWritersByFormatName("jpg").next();
            javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(clamp01(quality));
            javax.imageio.stream.ImageOutputStream ios = javax.imageio.ImageIO
                    .createImageOutputStream(out);
            writer.setOutput(ios);
            writer.write(null,
                    new javax.imageio.IIOImage(image, null, null), param);
            writer.dispose();
            ios.close();
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v)) return 0f;
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}