package com.enteksis.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MaxPayloadSizeFilter extends OncePerRequestFilter {

    public static final int MAX_BYTES = 16384; // 16 KB

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Yalnızca POST /api/requests için kontrol yapıyoruz
        if ("POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().startsWith("/api/requests")) {
            long contentLength = request.getContentLengthLong();

            if (contentLength > MAX_BYTES) {
                sendPayloadTooLargeResponse(response);
                return;
            }

            // Chunked veya Content-Length belirtilmemiş istekler için okuma esnasında sayma
            CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request, MAX_BYTES);
            if (wrappedRequest.isLimitExceeded()) {
                sendPayloadTooLargeResponse(response);
                return;
            }

            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void sendPayloadTooLargeResponse(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\": \"İstek gövdesi boyutu izin verilen sınırı (16KB) aşıyor.\"}");
    }

    private static class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {
        private final byte[] cachedBody;
        private final boolean limitExceeded;

        public CachedBodyHttpServletRequest(HttpServletRequest request, int maxBytes) throws IOException {
            super(request);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            InputStream is = request.getInputStream();
            byte[] chunk = new byte[1024];
            int read;
            int total = 0;
            boolean exceeded = false;

            while ((read = is.read(chunk)) != -1) {
                total += read;
                if (total > maxBytes) {
                    exceeded = true;
                    break;
                }
                buffer.write(chunk, 0, read);
            }

            this.limitExceeded = exceeded;
            this.cachedBody = buffer.toByteArray();
        }

        public boolean isLimitExceeded() {
            return limitExceeded;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new DelegatingServletInputStream(cachedBody);
        }
    }

    private static class DelegatingServletInputStream extends ServletInputStream {
        private final byte[] source;
        private int index = 0;

        public DelegatingServletInputStream(byte[] source) {
            this.source = source;
        }

        @Override
        public boolean isFinished() {
            return index >= source.length;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {}

        @Override
        public int read() {
            if (index >= source.length) {
                return -1;
            }
            return source[index++] & 0xFF;
        }
    }
}
