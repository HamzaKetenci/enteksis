package com.enteksis.ratelimit;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS_PER_MINUTE = 5;
    private static final long WINDOW_MILLIS = 60_000L;

    // Bellek içi saklama: Çoklu sunucu/instance senaryolarında yetersizdir (README'de bilinen eksik olarak belirtilmiştir)
    private static final ConcurrentHashMap<String, WindowCounter> requestCounts = new ConcurrentHashMap<>();

    // Testlerde sayaçları sıfırlamak için metot
    public static void reset() {
        requestCounts.clear();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Rate limit YALNIZCA POST /api/requests için geçerlidir; statik dosyalar ve /health sayılmaz
        if ("POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().startsWith("/api/requests")) {
            String clientIp = request.getRemoteAddr();
            long now = System.currentTimeMillis();

            WindowCounter counter = requestCounts.compute(clientIp, (ip, current) -> {
                if (current == null || now - current.windowStart > WINDOW_MILLIS) {
                    return new WindowCounter(now, new AtomicInteger(1));
                }
                current.counter.incrementAndGet();
                return current;
            });

            if (counter.counter.get() > MAX_REQUESTS_PER_MINUTE) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"error\": \"Çok fazla istek gönderildi. Lütfen bir dakika bekleyiniz.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static class WindowCounter {
        final long windowStart;
        final AtomicInteger counter;

        WindowCounter(long windowStart, AtomicInteger counter) {
            this.windowStart = windowStart;
            this.counter = counter;
        }
    }
}
