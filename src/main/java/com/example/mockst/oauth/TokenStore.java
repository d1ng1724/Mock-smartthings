package com.example.mockst.oauth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 목 OAuth 토큰 저장소.
 *
 * <p>
 * 실제 SmartThings 처럼 refresh 할 때마다 access/refresh 토큰을 함께 교체(rotate)한다.
 * 관리 프로그램의 토큰 갱신 스케줄러가 새 refresh 토큰을 제대로 저장하는지 확인할 수 있다.
 */
@Component
public class TokenStore {

    public static final String DEFAULT_SCOPE = "r:devices:* w:devices:* x:devices:*";

    /** 발급 토큰의 유효 시간(초). 짧게 주면 갱신 스케줄러를 빨리 돌려볼 수 있다. */
    @Value("${mock.oauth.expires-in-seconds:3600}")
    private int expiresInSeconds;

    public record Token(String accessToken, String refreshToken, Instant expiresAt, String scope) {
        public int expiresIn() {
            long seconds = Instant.now().until(expiresAt, java.time.temporal.ChronoUnit.SECONDS);
            return (int) Math.max(0, seconds);
        }

        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public enum Validity {
        VALID, EXPIRED, UNKNOWN
    }

    private final Set<String> pendingCodes = ConcurrentHashMap.newKeySet();
    private final Map<String, Token> byAccessToken = new ConcurrentHashMap<>();
    private final Map<String, Token> byRefreshToken = new ConcurrentHashMap<>();

    public int expiresInSeconds() {
        return expiresInSeconds;
    }

    /** /oauth/authorize 가 발급하는 일회용 authorization code. */
    public String issueCode() {
        String code = "mock-code-" + UUID.randomUUID();
        pendingCodes.add(code);
        return code;
    }

    /**
     * authorization_code 교환. 목이므로 모르는 code 도 받아준다(목 재시작 후에도 붙여볼 수 있도록).
     */
    public Token exchange(String code) {
        if (code != null) {
            pendingCodes.remove(code);
        }
        return issueToken();
    }

    /**
     * refresh_token 교환. 모르는 토큰이면 null 을 돌려주고, 호출 측이 400 invalid_grant 로 응답한다.
     */
    public Token refresh(String refreshToken) {
        if (refreshToken == null) {
            return null;
        }
        Token old = byRefreshToken.remove(refreshToken);
        if (old == null) {
            return null;
        }
        byAccessToken.remove(old.accessToken());
        return issueToken();
    }

    public Validity validate(String accessToken) {
        Token token = byAccessToken.get(accessToken);
        if (token == null) {
            return Validity.UNKNOWN;
        }
        return token.isExpired() ? Validity.EXPIRED : Validity.VALID;
    }

    /** 발급된 모든 access token 을 즉시 만료시킨다(갱신 흐름 테스트용). */
    public int expireAll() {
        int count = 0;
        for (Map.Entry<String, Token> e : byAccessToken.entrySet()) {
            Token t = e.getValue();
            Token expired = new Token(t.accessToken(), t.refreshToken(), Instant.now().minusSeconds(1), t.scope());
            e.setValue(expired);
            byRefreshToken.put(t.refreshToken(), expired);
            count++;
        }
        return count;
    }

    public int issuedCount() {
        return byAccessToken.size();
    }

    private Token issueToken() {
        Token token = new Token(
                "mock-access-" + UUID.randomUUID(),
                "mock-refresh-" + UUID.randomUUID(),
                Instant.now().plusSeconds(expiresInSeconds),
                DEFAULT_SCOPE);
        byAccessToken.put(token.accessToken(), token);
        byRefreshToken.put(token.refreshToken(), token);
        return token;
    }
}
