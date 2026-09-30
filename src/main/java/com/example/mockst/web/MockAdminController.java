package com.example.mockst.web;

import com.example.mockst.model.MockDevice;
import com.example.mockst.oauth.TokenStore;
import com.example.mockst.store.DeviceStore;
import com.example.mockst.store.FaultStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 목 전용 조작 API. 실제 SmartThings 에는 없는 경로이며 토큰이 필요 없다.
 * 실기기에서는 재현하기 어려운 상황(원격제어 꺼짐, 권한 오류, 오프라인, 토큰 만료)을 강제로 만든다.
 */
@RestController
@RequestMapping("/mock")
public class MockAdminController {

    private final DeviceStore store;
    private final FaultStore faults;
    private final TokenStore tokens;

    public MockAdminController(DeviceStore store, FaultStore faults, TokenStore tokens) {
        this.store = store;
        this.faults = faults;
        this.tokens = tokens;
    }

    /** 기기 라벨과 deviceId 목록. 어떤 기기를 조작할지 고를 때 쓴다. */
    @GetMapping("/devices")
    public List<Map<String, Object>> devices() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (MockDevice d : store.all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("label", d.label);
            m.put("deviceId", d.deviceId);
            m.put("machineState", d.machineState);
            m.put("jobState", d.jobState);
            m.put("switch", d.switchOn ? "on" : "off");
            m.put("cycle", d.cycle);
            m.put("remainingTime", d.remainingTime);
            m.put("remoteControlEnabled", d.remoteControlEnabled);
            m.put("online", d.online);
            list.add(m);
        }
        return list;
    }

    /** 모든 기기를 초기 상태로 되돌린다. */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        store.resetAll();
        faults.clear();
        return Map.of("reset", true, "devices", store.all().size());
    }

    /**
     * 다음 {@code count} 번의 기기 API 호출을 지정한 상태코드로 실패시킨다.
     * 403 은 관리 프로그램의 SmartThingsPermissionException 경로를 탄다.
     */
    @PostMapping("/fail")
    public Map<String, Object> fail(@RequestParam int status, @RequestParam(defaultValue = "1") int count) {
        faults.queue(status, count);
        return Map.of("status", status, "remaining", faults.remaining());
    }

    @DeleteMapping("/fail")
    public Map<String, Object> clearFail() {
        faults.clear();
        return Map.of("cleared", true);
    }

    /** 원격 제어(Smart Control) 스위치. 끄면 전원 외의 제어 명령이 무시된다. */
    @PostMapping("/devices/{id}/remote-control")
    public ResponseEntity<?> remoteControl(@PathVariable String id, @RequestParam boolean enabled) {
        MockDevice d = store.get(id);
        if (d == null) {
            return ResponseEntity.notFound().build();
        }
        d.setRemoteControlEnabled(enabled);
        return ResponseEntity.ok(Map.of("label", d.label, "remoteControlEnabled", d.remoteControlEnabled));
    }

    /** 기기 온·오프라인. /v1/devices/{id}/health 응답에 반영된다. */
    @PostMapping("/devices/{id}/online")
    public ResponseEntity<?> online(@PathVariable String id, @RequestParam boolean value) {
        MockDevice d = store.get(id);
        if (d == null) {
            return ResponseEntity.notFound().build();
        }
        d.online = value;
        return ResponseEntity.ok(Map.of("label", d.label, "online", d.online));
    }

    /** 발급된 access token 을 전부 만료시킨다. strict 모드에서 갱신 스케줄러를 테스트할 때 쓴다. */
    @PostMapping("/tokens/expire")
    public Map<String, Object> expireTokens() {
        return Map.of("expired", tokens.expireAll(), "issued", tokens.issuedCount());
    }
}
