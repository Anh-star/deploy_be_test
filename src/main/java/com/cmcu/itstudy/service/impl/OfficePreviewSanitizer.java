package com.cmcu.itstudy.service.impl;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts plain text from legacy binary {@code .doc} files via
 * Apache POI HWPF and returns a minimal, fully sanitised HTML
 * document. The HTML is intentionally restrictive: paragraph blocks
 * only, with every special character HTML-escaped and a strict CSP
 * that disallows scripts, inline handlers, and remote resources.
 *
 * <p>The frontend renders the returned HTML inside a namespaced
 * container that further disables form controls, navigation links,
 * and external resource loading.</p>
 */
final class OfficePreviewSanitizer {

    private static final Logger log = LoggerFactory.getLogger(OfficePreviewSanitizer.class);

    static final String CONTENT_TYPE = "text/html; charset=UTF-8";

    /** Maximum length of sanitised HTML body. Beyond this the surplus
     * is truncated; we never blow the response buffer on a giant
     * malformed DOC. */
    static final int MAX_BODY_CHARS = 200_000;

    private static final String HEAD = "<!DOCTYPE html><html lang=\"vi\"><head><meta charset=\"UTF-8\">"
            + "<meta http-equiv=\"Content-Security-Policy\""
            + " content=\"default-src 'none'; style-src 'unsafe-inline'; img-src data:; font-src data:\">"
            + "<title>preview</title></head><body>";

    private static final String FOOT = "</body></html>";

    private OfficePreviewSanitizer() {
    }

    /**
     * Convert a {@code .doc} byte array to a minimal sanitised HTML
     * document. Returns {@code null} on a malformed input so the
     * controller can map the failure to a safe locked response and
     * keep the download button intact.
     */
    static String renderDocHtml(byte[] original) {
        StringBuilder body = new StringBuilder();
        appendSafe(body, "Mở khóa xem trước tài liệu DOC đã được chuyển sang văn bản");
        try (HWPFDocument document = new HWPFDocument(new java.io.ByteArrayInputStream(original));
             WordExtractor extractor = new WordExtractor(document)) {
            String[] paragraphs = extractor.getParagraphText();
            if (paragraphs != null) {
                for (String paragraph : paragraphs) {
                    if (paragraph == null) {
                        continue;
                    }
                    String trimmed = paragraph.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    appendSafe(body, trimmed);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract DOC text; emitting locked body instead");
            return null;
        }
        if (body.length() == 0) {
            return null;
        }
        if (body.length() > MAX_BODY_CHARS) {
            body.setLength(MAX_BODY_CHARS);
            appendSafe(body, "… (nội dung đã được rút gọn)");
        }
        return HEAD + body + FOOT;
    }

    /** Escape every character that has HTML semantics and wrap each
     * paragraph in {@code <p>}. The strict allow-list in the document
     * header is the primary defence; this escaping is the secondary
     * defence and it never trusts the source text. */
    private static void appendSafe(StringBuilder sink, String text) {
        sink.append("<p>");
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sink.append("&amp;");
                case '<' -> sink.append("&lt;");
                case '>' -> sink.append("&gt;");
                case '"' -> sink.append("&quot;");
                case '\'' -> sink.append("&#39;");
                case '/' -> sink.append("&#x2F;");
                default -> sink.append(c);
            }
        }
        sink.append("</p>");
    }
}