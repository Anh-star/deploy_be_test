package com.cmcu.itstudy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebhookRawBodyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(WebhookRawBodyFilter.class);

    private static final String WEBHOOK_PATH = "/api/payments/webhook";
    private static final int MAX_LOGGED_BODY_BYTES = 64 * 1024;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !WEBHOOK_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        byte[] body = request.getInputStream().readAllBytes();
        logRawJson(body);

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, body);
        filterChain.doFilter(wrapped, response);
    }

    private void logRawJson(byte[] body) {
        if (body.length == 0) {
            log.warn("WebhookRawBodyFilter raw body unavailable: empty body (contentLength={} bytes)", 0);
            return;
        }
        int length = Math.min(body.length, MAX_LOGGED_BODY_BYTES);
        String rawJson = new String(body, 0, length, StandardCharsets.UTF_8);
        if (body.length > MAX_LOGGED_BODY_BYTES) {
            log.info("WebhookRawBodyFilter raw JSON (truncated to {} of {} bytes): {}", length, body.length, rawJson);
        } else {
            log.info("WebhookRawBodyFilter raw JSON ({} bytes): {}", body.length, rawJson);
        }
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        CachedBodyHttpServletRequest(HttpServletRequest request, byte[] cachedBody) {
            super(request);
            this.cachedBody = cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.cachedBody);
            return new CachedBodyServletInputStream(byteArrayInputStream);
        }

        @Override
        public BufferedReader getReader() {
            Charset charset = getCharacterEncoding() != null
                    ? Charset.forName(getCharacterEncoding())
                    : StandardCharsets.UTF_8;
            InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(this.cachedBody), charset);
            return new BufferedReader(reader);
        }
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        CachedBodyServletInputStream(ByteArrayInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isFinished() {
            return this.delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("ReadListener not supported in CachedBodyServletInputStream");
        }

        @Override
        public int read() {
            return this.delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return this.delegate.read(b, off, len);
        }
    }
}