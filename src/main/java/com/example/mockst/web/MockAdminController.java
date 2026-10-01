package com.example.mockst.web;

import com.example.mockst.model.MockDevice;
import com.example.mockst.oauth.TokenStore;
import com.example.mockst.store.DelayStore;
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
@io.swagger.v3.oas.annotations.tags.Tag(name = "Mock 조작")
@io.swagger.v3.oas.annotations.security.SecurityRequirements
public class MockAdminController {

    private final DeviceStore store;
    private final FaultStore faults;
    private final DelayStore delays;
    private final TokenStore tokens;

    public MockAdminController(DeviceStore store, FaultStore faults, DelayStore delays, TokenStore tokens) {
        this.store = store;
        this.faults = faults;
        this.delays = delays;
        this.tokens = tokens;
    }

    /** 기기 라벨과 deviceId 목록. 어떤 기기를 조작할지 고를 때 쓴다. */
    @io.swagger.v3.oas.annotations.Operation(summary = "라벨·deviceId·현재 상태 한눈에 보기")
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
            m.put("completionTime", d.completionTime.toString());
            m.put("pendingCommands", d.pending.size());
            list.add(m);
        }
        return list;
    }

    /** 모든 기기를 초기 상태로 되돌린다. */
    @io.swagger.v3.oas.annotations.Operation(summary = "모든 기기를 초기 상태로 되돌린다")
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        store.resetAll();
        faults.clear();
        delays.clear();
        return Map.of("reset", true, "devices", store.all().size());
    }

    /**
     * 다음 {@code count} 번의 기기 API 호출을 지정한 상태코드로 실패시킨다.
     * 403 은 관리 프로그램의 SmartThingsPermissionException 경로를 탄다.
     */
    @io.swagger.v3.oas.annotations.Operation(summary = "다음 N번의 기기 API 호출을 지정한 상태코드로 실패시킨다 (403=권한오류 경로, 500=재시도 경로)")
    @PostMapping("/fail")
    public Map<String, Object> fail(@RequestParam int status, @RequestParam(defaultValue = "1") int count) {
        faults.queue(status, count);
        return Map.of("status", status, "remaining", faults.remaining());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "장애 주입 해제")
    @DeleteMapping("/fail")
    public Map<String, Object> clearFail() {
        faults.clear();
        return Map.of("cleared", true);
    }

    /**
     * 다음 {@code count} 번의 기기 API 응답을 {@code ms} 밀리초만큼 늦춘다.
     *
     * <p>
     * 관리 프로그램은 5xx 를 재시도하지 않는다 — Feign 의 Retryer 는 ErrorDecoder 가
     * RetryableException 을 낼 때만 도는데, 그쪽 디코더는 400 이상을 전부 일반 예외로 바꾼다.
     * 따라서 <b>재시도 경로를 실제로 타보려면 상태코드가 아니라 이 지연을 써야 한다.</b>
     * 읽기 타임아웃이 5초이므로 6000ms 정도면 타임아웃 → 재시도(총 2회 시도)가 일어난다.
     */
    @io.swagger.v3.oas.annotations.Operation(
            summary = "다음 N번의 기기 API 응답을 지정한 밀리초만큼 늦춘다 (5xx 는 재시도되지 않으므로 재시도 테스트는 이걸로)",
            description = "클라이언트 읽기 타임아웃이 5초라 `ms=6000` 이면 타임아웃 → 재시도가 일어난다. 상한은 60000ms.")
    @PostMapping("/delay")
    public Map<String, Object> delay(@RequestParam long ms, @RequestParam(defaultValue = "1") int count) {
        delays.queue(ms, count);
        return Map.of("ms", delays.millis(), "remaining", delays.remaining());
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "지연 주입 해제")
    @DeleteMapping("/delay")
    public Map<String, Object> clearDelay() {
        delays.clear();
        return Map.of("cleared", true);
    }

    /** 원격 제어(Smart Control) 스위치. 끄면 전원 외의 제어 명령이 무시된다. */
    @io.swagger.v3.oas.annotations.Operation(summary = "원격 제어(Smart Control) 스위치. 끄면 전원 외의 명령이 무시된다")
    @PostMapping("/devices/{id}/remote-control")
    public ResponseEntity<?> remoteControl(@PathVariable String id, @RequestParam boolean enabled) {
        MockDevice d = store.get(id);
        if (d == null) {
            return ResponseEntity.notFound().build();
        }
        d.setRemoteControlEnabled(enabled);
        return ResponseEntity.ok(Map.of("label", d.label, "remoteControlEnabled", d.remoteControlEnabled));
    }

    /**
     * 기기 온·오프라인. 오프라인이면 명령 전송이 409 ConflictError 로 실패한다.
     * 상태 조회는 실기기와 같이 마지막으로 알려진 값을 계속 돌려준다.
     */
    @io.swagger.v3.oas.annotations.Operation(summary = "기기 온·오프라인 전환 (오프라인이면 명령이 409 로 실패한다)")
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
    @io.swagger.v3.oas.annotations.Operation(summary = "발급된 access token 을 전부 즉시 만료시킨다")
    @PostMapping("/tokens/expire")
    public Map<String, Object> expireTokens() {
        return Map.of("expired", tokens.expireAll(), "issued", tokens.issuedCount());
    }
}
