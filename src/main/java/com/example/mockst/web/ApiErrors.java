package com.example.mockst.web;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SmartThings 의 에러 응답 형태를 만든다.
 *
 * <pre>
 * {"requestId":"...","error":{"code":"UnauthorizedError","message":"...","details":[]}}
 * </pre>
 */
final class ApiErrors {

    private ApiErrors() {
    }

    /** 토큰이 필요한 기기 API 경로인지. /oauth/**, /mock/**, / 는 제외된다. */
    static boolean isDeviceApi(String path) {
        return path.startsWith("/v1/devices") || path.startsWith("/devices");
    }

    static Map<String, Object> body(String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("details", List.of());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", UUID.randomUUID().toString().replace("-", ""));
        body.put("error", error);
        return body;
    }

    static void write(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("""
                {"requestId":"%s","error":{"code":"%s","message":"%s","details":[]}}"""
                .formatted(UUID.randomUUID().toString().replace("-", ""), code, message));
    }
}
