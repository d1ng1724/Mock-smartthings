package com.example.mockst.web;

import com.example.mockst.model.MockDevice;
import com.example.mockst.store.DeviceStore;
import com.example.mockst.web.dto.CommandDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * SmartThings REST API 중 세탁기/건조기 테스트에 필요한 엔드포인트만 흉내 낸다.
 * base URL 접두어는 /v1/devices 와 /devices 둘 다 받는다
 * (클라이언트가 base를 api.smartthings.com 으로 잡든 .../v1 로 잡든 대응).
 */
@RestController
@RequestMapping({"/v1/devices", "/devices"})
public class DeviceController {

    private final DeviceStore store;
    private final ObjectMapper mapper;

    public DeviceController(DeviceStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    /** GET /v1/devices — 기기 목록 */
    @GetMapping("")
    public Map<String, Object> list() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (MockDevice d : store.all()) {
            items.add(store.deviceInfo(d));
        }
        return Map.of("items", items, "_links", Map.of());
    }

    /** GET /v1/devices/{id} — 기기 상세 */
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        MockDevice d = store.get(id);
        return d == null ? notFound() : ResponseEntity.ok(store.deviceInfo(d));
    }

    /** GET /v1/devices/{id}/status — 전체 컴포넌트/기능 상태 */
    @GetMapping("/{id}/status")
    public ResponseEntity<?> status(@PathVariable String id) {
        MockDevice d = store.get(id);
        return d == null ? notFound() : ResponseEntity.ok(store.status(d));
    }

    /** GET /v1/devices/{id}/health — ONLINE/OFFLINE */
    @GetMapping("/{id}/health")
    public ResponseEntity<?> health(@PathVariable String id) {
        MockDevice d = store.get(id);
        return d == null ? notFound() : ResponseEntity.ok(store.health(d));
    }

    /** POST /v1/devices/{id}/commands — 명령 실행 */
    @PostMapping("/{id}/commands")
    public ResponseEntity<?> commands(@PathVariable String id, @RequestBody JsonNode body) {
        MockDevice d = store.get(id);
        if (d == null) return notFound();

        // { "commands": [...] } 형태와 그냥 [...] 배열 형태 둘 다 허용
        JsonNode arr = body.has("commands") ? body.get("commands") : body;
        List<Map<String, Object>> results = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode node : arr) {
                CommandDto c = mapper.convertValue(node, CommandDto.class);
                store.applyCommand(d, c.capability(), c.command(), c.arguments());
                results.add(Map.of("id", UUID.randomUUID().toString(), "status", "ACCEPTED"));
            }
        }
        return ResponseEntity.ok(Map.of("results", results));
    }

    private ResponseEntity<Map<String, Object>> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                Map.of("requestId", "mock",
                        "error", Map.of("code", "NotFoundError", "message", "Device not found")));
    }
}
