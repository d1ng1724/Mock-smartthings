package com.example.mockst.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 실제 SmartThings처럼 Authorization 헤더가 없으면 401을 준다.
 * 단, 토큰 "값"은 검증하지 않는다 — 아무 Bearer 값이나 통과 (mock이므로).
 */
@Component
public class AuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String path = req.getRequestURI();
        boolean guarded = path.startsWith("/v1/") || path.startsWith("/devices");

        if (guarded) {
            String auth = req.getHeader("Authorization");
            if (auth == null || auth.isBlank()) {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json");
                res.getWriter().write(
                        "{\"requestId\":\"mock\",\"error\":{\"code\":\"UnauthorizedError\","
                                + "\"message\":\"Authorization header is required\"}}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
