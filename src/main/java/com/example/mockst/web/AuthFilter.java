package com.example.mockst.web;

import com.example.mockst.oauth.TokenStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 실제 SmartThings 처럼 Authorization 헤더가 없으면 401 을 준다.
 *
 * <p>
 * {@code mock.token-validation} 으로 검증 강도를 고른다.
 * <ul>
 * <li>{@code lenient}(기본) — 헤더만 있으면 통과. curl 로 아무 토큰이나 넣고 찔러볼 때 편하다.</li>
 * <li>{@code strict} — 목이 /oauth/token 으로 발급한 토큰만 통과하고, 만료되면 401.
 * 관리 프로그램의 토큰 갱신 흐름을 실제로 돌려볼 때 쓴다.</li>
 * </ul>
 */
@Component
@Order(1)
public class AuthFilter extends OncePerRequestFilter {

    private final TokenStore tokenStore;

    @Value("${mock.token-validation:lenient}")
    private String tokenValidation;

    public AuthFilter(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if (!ApiErrors.isDeviceApi(req.getRequestURI())) {
            chain.doFilter(req, res);
            return;
        }

        String auth = req.getHeader("Authorization");
        if (auth == null || auth.isBlank()) {
            ApiErrors.write(res, HttpServletResponse.SC_UNAUTHORIZED, "UnauthorizedError",
                    "Authentication is required and has failed or has not yet been provided.");
            return;
        }

        if ("strict".equalsIgnoreCase(tokenValidation)) {
            String accessToken = auth.regionMatches(true, 0, "Bearer ", 0, 7) ? auth.substring(7).trim() : auth.trim();
            TokenStore.Validity validity = tokenStore.validate(accessToken);
            if (validity != TokenStore.Validity.VALID) {
                ApiErrors.write(res, HttpServletResponse.SC_UNAUTHORIZED, "UnauthorizedError",
                        validity == TokenStore.Validity.EXPIRED
                                ? "The access token is expired."
                                : "The access token is invalid.");
                return;
            }
        }

        chain.doFilter(req, res);
    }
}
