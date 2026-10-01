package com.example.mockst.web;

import com.example.mockst.model.MockDevice;
import com.example.mockst.store.DeviceStore;
import com.example.mockst.web.dto.CommandDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "SmartThings Devices")
public class DeviceController {

    private final DeviceStore store;
    private final ObjectMapper mapper;

    public DeviceController(DeviceStore store, ObjectMapper mapper) {
        this.store = store;
        this.mapper = mapper;
    }

    /** GET /v1/devices — 기기 목록 */
    @Operation(summary = "기기 목록",
            description = "등록된 가상 기기 전체를 SmartThings 형식으로 돌려준다. "
                    + "관리 프로그램은 여기서 label 을 읽어 Machine 을 만든다.")
    @GetMapping("")
    public Map<String, Object> list() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (MockDevice d : store.all()) {
            items.add(store.deviceInfo(d));
        }
        return Map.of("items", items, "_links", Map.of());
    }

    /** GET /v1/devices/{id} — 기기 상세 */
    @Operation(summary = "기기 상세", description = "deviceId 는 `GET /mock/devices` 에서 라벨과 함께 확인할 수 있다.")
    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        MockDevice d = store.get(id);
        return d == null ? notFound() : ResponseEntity.ok(store.deviceInfo(d));
    }

    /** GET /v1/devices/{id}/status — 전체 컴포넌트/기능 상태 */
    @Operation(summary = "기기 상태",
            description = """
                    `components.main.<capability>.<attribute>.{value,timestamp,unit}` 형태.

                    관리 프로그램이 읽는 것은 `switch`, `washerOperatingState`/`dryerOperatingState`,
                    `remoteControlStatus` 세 가지다. `timestamp` 는 조회 시각이 아니라
                    **값이 바뀐 시각**이므로 상태 변화 감지에 그대로 쓸 수 있다.
                    """)
    @GetMapping("/{id}/status")
    public ResponseEntity<?> status(@PathVariable String id) {
        MockDevice d = store.get(id);
        return d == null ? notFound() : ResponseEntity.ok(store.status(d));
    }

    /** GET /v1/devices/{id}/health — ONLINE/OFFLINE */
    @Operation(summary = "기기 온·오프라인", description = "`POST /mock/devices/{id}/online` 으로 값을 바꿀 수 있다.")
    @GetMapping("/{id}/health")
    public ResponseEntity<?> health(@PathVariable String id) {
        MockDevice d = store.get(id);
        return d == null ? notFound() : ResponseEntity.ok(store.health(d));
    }

    /** POST /v1/devices/{id}/commands — 명령 실행 */
    @Operation(summary = "명령 실행",
            description = """
                    실제 API 와 마찬가지로 **기기가 받아들이지 못하는 명령도 `ACCEPTED`** 로 응답하고
                    상태만 바뀌지 않는다. 원격 제어가 꺼진 기기는 전원 외의 명령을 무시한다.

                    오프라인 기기(`POST /mock/devices/{id}/online?value=false`)에는 명령을 보낼 수 없어
                    **409 ConflictError** 가 돌아온다. 상태 조회는 마지막으로 알려진 값을 계속 돌려준다.

                    `mock.command-delay-ms` 를 0 보다 크게 두면 응답은 바로 오지만 상태 반영은
                    그만큼 늦어진다(실기기와 동일).
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "가동 (세탁기)", value = """
                                    {"commands":[{"component":"main","capability":"washerOperatingState",\
                                    "command":"setMachineState","arguments":["run"]}]}"""),
                            @ExampleObject(name = "정지 (세탁기)", value = """
                                    {"commands":[{"component":"main","capability":"washerOperatingState",\
                                    "command":"setMachineState","arguments":["stop"]}]}"""),
                            @ExampleObject(name = "무세제 통세척 (코스 지정 후 가동)", value = """
                                    {"commands":[\
                                    {"component":"main","capability":"samsungce.washerCycle",\
                                    "command":"setWasherCycle","arguments":["6C"]},\
                                    {"component":"main","capability":"washerOperatingState",\
                                    "command":"setMachineState","arguments":["run"]}]}"""),
                            @ExampleObject(name = "전원 끄기", value = """
                                    {"commands":[{"component":"main","capability":"switch",\
                                    "command":"off","arguments":[]}]}"""),
                            @ExampleObject(name = "가동 (건조기)", value = """
                                    {"commands":[{"component":"main","capability":"dryerOperatingState",\
                                    "command":"setMachineState","arguments":["run"]}]}""")})))
    @PostMapping("/{id}/commands")
    public ResponseEntity<?> commands(@PathVariable String id, @RequestBody JsonNode body) {
        MockDevice d = store.get(id);
        if (d == null) return notFound();
        if (!d.online) {
            // 실기기가 연결이 끊긴 상태. 관리 프로그램은 /health 를 조회하지 않으므로
            // 오프라인은 명령 실패로만 드러난다.
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiErrors.body("ConflictError", "Device is offline"));
        }

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
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrors.body("NotFoundError", "Device not found"));
    }
}
