package com.example.mockst.web;

import com.example.mockst.store.DeviceStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 서버가 떴는지 확인하는 용도 (토큰 없이 접근 가능). */
@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Health")
@io.swagger.v3.oas.annotations.security.SecurityRequirements
public class RootController {

    private final DeviceStore store;

    public RootController(DeviceStore store) {
        this.store = store;
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "서버 기동 확인")
    @GetMapping("/")
    public Map<String, Object> index() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", "mock-smartthings");
        out.put("status", "up");
        out.put("deviceCount", store.all().size());
        out.put("endpoints", Map.of(
                "devices", "/v1/devices",
                "oauthAuthorize", "/oauth/authorize",
                "oauthToken", "/oauth/token",
                "mockConsole", "/mock/devices"));
        return out;
    }
}
