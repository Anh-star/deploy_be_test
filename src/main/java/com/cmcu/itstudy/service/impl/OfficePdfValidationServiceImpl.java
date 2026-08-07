package com.cmcu.itstudy.service.impl;

import com.cmcu.itstudy.config.OfficePreviewProperties;
import com.cmcu.itstudy.handle.OfficeConversionInvalidOutputException;
import com.cmcu.itstudy.handle.OfficeConversionOutputTooLargeException;
import com.cmcu.itstudy.service.contract.OfficePdfValidationService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * PDFBox-based validator for the LibreOffice output PDF. Mirrors the
 * existing {@code LockedPageRenderer} convention of always closing
 * the {@link PDDocument} before returning.
 *
 * <p>Failure mapping:</p>
 * <ul>
 *   <li>missing file or non-regular file → {@code MISSING_OUTPUT}</li>
 *   <li>empty file → {@code EMPTY_OUTPUT}</li>
 *   <li>oversized → {@code OUTPUT_TOO_LARGE}</li>
 *   <li>bad magic bytes → {@code INVALID_PDF_SIGNATURE}</li>
 *   <li>PDFBox load failure → {@code PDFBOX_LOAD_FAIL}</li>
 *   <li>zero pages → {@code ZERO_PAGES}</li>
 * </ul>
 */
@Service
public class OfficePdfValidationServiceImpl implements OfficePdfValidationService {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};

    private final OfficePreviewProperties properties;

    public OfficePdfValidationServiceImpl(OfficePreviewProperties properties) {
        this.properties = properties;
    }

    @Override
    public int validateAndCountPages(Path pdfPath) {
        if (pdfPath == null) {
            throw new OfficeConversionInvalidOutputException("MISSING_OUTPUT",
                    "PDF path is null");
        }
        if (!Files.exists(pdfPath) || !Files.isRegularFile(pdfPath)) {
            throw new OfficeConversionInvalidOutputException("MISSING_OUTPUT",
                    "PDF output is missing");
        }

        long size;
        try {
            size = Files.size(pdfPath);
        } catch (IOException ioe) {
            throw new OfficeConversionInvalidOutputException("MISSING_OUTPUT",
                    "Cannot stat PDF output", ioe);
        }
        if (size == 0) {
            throw new OfficeConversionInvalidOutputException("EMPTY_OUTPUT",
                    "PDF output is empty");
        }
        if (size > properties.getMaxOutputBytes()) {
            throw new OfficeConversionOutputTooLargeException(
                    "PDF output size " + size + " exceeds maximum "
                            + properties.getMaxOutputBytes());
        }

        byte[] head;
        try {
            head = readHead(pdfPath, PDF_MAGIC.length);
        } catch (IOException ioe) {
            throw new OfficeConversionInvalidOutputException("MISSING_OUTPUT",
                    "Cannot read PDF output head", ioe);
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (head[i] != PDF_MAGIC[i]) {
                throw new OfficeConversionInvalidOutputException(
                        "INVALID_PDF_SIGNATURE",
                        "PDF output does not start with the %PDF- signature");
            }
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(pdfPath);
        } catch (IOException ioe) {
            throw new OfficeConversionInvalidOutputException("MISSING_OUTPUT",
                    "Cannot read PDF output bytes", ioe);
        }
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount <= 0) {
                throw new OfficeConversionInvalidOutputException("ZERO_PAGES",
                        "PDF output has zero pages");
            }
            return pageCount;
        } catch (IOException ioe) {
            throw new OfficeConversionInvalidOutputException("PDFBOX_LOAD_FAIL",
                    "PDFBox cannot load the output PDF", ioe);
        }
    }

    private static byte[] readHead(Path file, int len) throws IOException {
        byte[] buf = new byte[len];
        try (var in = Files.newInputStream(file)) {
            int read = 0;
            while (read < len) {
                int n = in.read(buf, read, len - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            if (read < len) {
                byte[] shrunk = new byte[read];
                System.arraycopy(buf, 0, shrunk, 0, read);
                return shrunk;
            }
            return buf;
        }
    }
}
