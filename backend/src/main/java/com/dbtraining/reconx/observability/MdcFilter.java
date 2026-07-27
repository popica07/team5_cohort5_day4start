package com.dbtraining.reconx.observability;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(1)
public class MdcFilter implements Filter {

    static final String HDR_CORRELATION = "X-Correlation-Id";
    static final String HDR_TRADE_REF   = "X-Trade-Ref";

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) req;
        String correlationId = header(http, HDR_CORRELATION, UUID.randomUUID().toString());
        String tradeRef      = header(http, HDR_TRADE_REF, null);
        try {
            MDC.put("correlationId", correlationId);
            if (tradeRef != null) MDC.put("tradeRef", tradeRef);
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }

    private static String header(HttpServletRequest r, String name, String fallback) {
        String v = r.getHeader(name);
        return (v == null || v.isBlank()) ? fallback : v;
    }
}
