package com.example.mockst.web;

import com.example.mockst.oauth.TokenStore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SmartThings OAuth 2.0 Authorization Code 흐름을 흉내 낸다.
 *
 * <p>
 * 관리 프로그램 설정에서 아래 두 값을 목으로 돌리면 로그인 화면 없이 바로 콜백까지 진행된다.
 *
 * <pre>
 * SMARTTHINGS_AUTHORIZE_URL=http://localhost:8080/oauth/authorize
 * SMARTTHINGS_OAUTH_URL=http://localhost:8080/oauth/token
 * </pre>
 */
@RestController
@RequestMapping("/oauth")
@io.swagger.v3.oas.annotations.tags.Tag(name = "OAuth")
@io.swagger.v3.oas.annotations.security.SecurityRequirements
public class OAuthController {

    private static final String GRANT_AUTHORIZATION_CODE = "authorization_code";
    private static final String GRANT_REFRESH_TOKEN = "refresh_token";

    private final TokenStore tokenStore;

    public OAuthController(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    /**
     * 실제 SmartThings 는 여기서 삼성 계정 로그인과 동의 화면을 띄운다. 목은 곧바로
     * {@code redirect_uri?code=...&state=...} 로 302 리다이렉트한다.
     */
    @io.swagger.v3.oas.annotations.Operation(summary = "인가 코드 발급 (302 리다이렉트)",
            description = "로그인·동의 화면 없이 곧바로 `redirect_uri?code=...&state=...` 로 302 를 돌려준다. "
                    + "Swagger UI 에서는 리다이렉트를 따라가므로 브라우저 주소창에서 직접 열어보는 편이 낫다.")
    @GetMapping("/authorize")
    public ResponseEntity<?> authorize(@RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(required = false) String state,
            @RequestParam(name = "client_id", required = false) String clientId) {

        if (redirectUri == null || redirectUri.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid_request", "error_description", "redirect_uri is required"));
        }

        var builder = UriComponentsBuilder.fromUriString(redirectUri).queryParam("code", tokenStore.issueCode());
        if (state != null && !state.isBlank()) {
            builder.queryParam("state", state);
        }

        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(builder.toUriString())).build();
    }

    /**
     * 토큰 발급·갱신. 실제 API 와 동일하게 form-urlencoded 를 받고 Basic 인증으로 클라이언트를 확인한다.
     */
    @io.swagger.v3.oas.annotations.Operation(summary = "토큰 발급·갱신",
            description = """
                    `grant_type=authorization_code` 는 아무 code 나 받아준다.
                    `grant_type=refresh_token` 은 **한 번 쓴 refresh token 을 거절**하고(400 invalid_grant),
                    성공하면 access/refresh 를 함께 교체한다.

                    클라이언트 인증은 `Authorization: Basic <base64(client_id:client_secret)>` 헤더로 하며,
                    값은 검증하지 않고 존재 여부만 본다.
                    """)
    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> token(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(required = false) String code,
            @RequestParam(name = "refresh_token", required = false) String refreshToken) {

        if (authorization == null || !authorization.startsWith("Basic ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "invalid_client",
                            "error_description", "Basic authentication with client credentials is required"));
        }

        TokenStore.Token token;
        if (GRANT_AUTHORIZATION_CODE.equals(grantType)) {
            token = tokenStore.exchange(code);
        } else if (GRANT_REFRESH_TOKEN.equals(grantType)) {
            token = tokenStore.refresh(refreshToken);
            if (token == null) {
                // 실제 OAuth 서버와 동일하게, 모르는(또는 이미 회전된) refresh token 은 거절한다.
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "invalid_grant", "error_description", "refresh token is not valid"));
            }
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "unsupported_grant_type", "error_description", String.valueOf(grantType)));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", token.accessToken());
        body.put("token_type", "Bearer");
        body.put("refresh_token", token.refreshToken());
        body.put("expires_in", token.expiresIn());
        body.put("scope", token.scope());
        return ResponseEntity.ok(body);
    }
}
