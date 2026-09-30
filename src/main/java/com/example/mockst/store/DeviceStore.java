package com.example.mockst.store;

import com.example.mockst.model.DeviceType;
import com.example.mockst.model.MockDevice;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 가상 기기 저장소 + 상태머신 + 상태 JSON 빌더.
 * 기기를 더 추가하려면 seed()에 add(...) 한 줄만 넣으면 된다.
 */
@Component
public class DeviceStore {

    @Value("${mock.minutes-per-tick:1}")
    private int minutesPerTick;

    private final Map<String, MockDevice> devices = new ConcurrentHashMap<>();

    // 모드별 사이클 시간(분). 없는 모드는 60분 기본.
    private static final Map<String, Integer> CYCLE_MINUTES = Map.of(
            "quick", 30, "rinseSpin", 20, "spinOnly", 15, "airFluff", 20,
            "delicates", 45, "normal", 60, "heavy", 90, "timeDry", 70, "cottons", 80
    );

    @PostConstruct
    void seed() {
        add("11111111-1111-1111-1111-111111111111", DeviceType.WASHER, "Test Washer");
        add("22222222-2222-2222-2222-222222222222", DeviceType.DRYER, "Test Dryer");
        // 대수를 늘리려면 여기에 add(...) 를 더 추가하면 된다.
    }

    private void add(String id, DeviceType type, String label) {
        devices.put(id, new MockDevice(id, type, label));
    }

    public Collection<MockDevice> all() {
        return devices.values();
    }

    public MockDevice get(String id) {
        return devices.get(id);
    }

    // ---------------------------------------------------------------------
    // 시간 시뮬레이션: run 중인 기기의 남은시간을 tick마다 줄인다.
    // ---------------------------------------------------------------------
    @Scheduled(fixedDelayString = "${mock.tick-ms:1000}")
    void tick() {
        for (MockDevice d : devices.values()) {
            if (!d.switchOn || !"run".equals(d.machineState)) continue;
            d.remainingTime -= minutesPerTick;
            if (d.remainingTime <= 0) {
                d.remainingTime = 0;
                d.machineState = "stop";
                d.finished = true;
                d.jobState = d.type.doneJob;
                d.completionTime = Instant.now();
            } else {
                d.completionTime = Instant.now().plusSeconds(60L * d.remainingTime);
            }
        }
    }

    // ---------------------------------------------------------------------
    // 명령 처리. 지원하지 않는 명령이면 false 반환(그래도 ACCEPTED 로 응답).
    // ---------------------------------------------------------------------
    public void applyCommand(MockDevice d, String capability, String command, List<Object> args) {
        String arg0 = (args != null && !args.isEmpty() && args.get(0) != null) ? String.valueOf(args.get(0)) : null;

        if ("switch".equals(capability)) {
            if ("on".equals(command)) {
                d.switchOn = true;
            } else if ("off".equals(command)) {
                d.switchOn = false;
                stop(d);
            }
            return;
        }

        if (d.type.operatingCap.equals(capability) && "setMachineState".equals(command)) {
            switch (arg0 == null ? "" : arg0) {
                case "run" -> run(d);
                case "pause" -> d.machineState = "pause";
                case "stop" -> stop(d);
            }
            return;
        }

        if (d.type.modeCap.equals(capability) && d.type.setModeCmd.equals(command) && arg0 != null) {
            d.mode = arg0;
            d.totalTime = CYCLE_MINUTES.getOrDefault(arg0, 60);
        }
    }

    private void run(MockDevice d) {
        d.switchOn = true;
        d.finished = false;
        d.machineState = "run";
        d.jobState = d.type.runJob;
        if (d.remainingTime <= 0) {
            d.totalTime = CYCLE_MINUTES.getOrDefault(d.mode, 60);
            d.remainingTime = d.totalTime;
        }
        d.completionTime = Instant.now().plusSeconds(60L * d.remainingTime);
    }

    private void stop(MockDevice d) {
        d.machineState = "stop";
        d.jobState = "none";
        d.remainingTime = 0;
        d.finished = false;
        d.completionTime = Instant.now();
    }

    // ---------------------------------------------------------------------
    // JSON 빌더 (SmartThings 응답 형태에 맞춤)
    // ---------------------------------------------------------------------
    public Map<String, Object> deviceInfo(MockDevice d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceId", d.deviceId);
        m.put("name", d.type.deviceTypeName);
        m.put("label", d.label);
        m.put("manufacturerName", "Samsung Electronics");
        m.put("deviceTypeName", d.type.deviceTypeName);
        m.put("type", "OCF");

        List<Map<String, Object>> caps = new ArrayList<>();
        caps.add(cap("switch"));
        caps.add(cap(d.type.operatingCap));
        caps.add(cap(d.type.samsungCap));
        caps.add(cap(d.type.modeCap));
        caps.add(cap("remoteControlStatus"));
        caps.add(cap("healthCheck"));

        Map<String, Object> main = new LinkedHashMap<>();
        main.put("id", "main");
        main.put("label", "main");
        main.put("capabilities", caps);
        main.put("categories", List.of(Map.of("name", d.type.category, "categoryType", "manufacturer")));

        m.put("components", List.of(main));
        return m;
    }

    private Map<String, Object> cap(String id) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", id);
        c.put("version", 1);
        return c;
    }

    public Map<String, Object> status(MockDevice d) {
        Map<String, Object> main = new LinkedHashMap<>();

        // switch
        main.put("switch", Map.of("switch", attr(d.switchOn ? "on" : "off")));

        // 표준 operatingState (washerOperatingState / dryerOperatingState)
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("machineState", attr(d.machineState));
        op.put(d.type.jobAttr, attr(d.jobState));
        op.put("completionTime", attr(d.completionTime.toString()));
        op.put("supportedMachineStates", attr(List.of("pause", "run", "stop")));
        main.put(d.type.operatingCap, op);

        // 삼성 확장 operatingState (samsungce.*)
        Map<String, Object> sop = new LinkedHashMap<>();
        sop.put("operatingState", attr(d.operatingState()));
        sop.put(d.type.jobAttr, attr(d.jobState));
        sop.put("remainingTime", attr(d.remainingTime, "min"));
        sop.put("progress", attr(d.progress(), "%"));
        main.put(d.type.samsungCap, sop);

        // mode
        Map<String, Object> mode = new LinkedHashMap<>();
        mode.put(d.type.modeCap, attr(d.mode));
        mode.put(d.type.supportedModesAttr, attr(d.type.supportedModes));
        main.put(d.type.modeCap, mode);

        // remoteControlStatus
        main.put("remoteControlStatus",
                Map.of("remoteControlEnabled", attr(String.valueOf(d.remoteControlEnabled))));

        // healthCheck
        Map<String, Object> hc = new LinkedHashMap<>();
        hc.put("healthStatus", attr(""));
        hc.put("DeviceWatch-Enroll", attr(""));
        main.put("healthCheck", hc);

        return Map.of("components", Map.of("main", main));
    }

    public Map<String, Object> health(MockDevice d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceId", d.deviceId);
        m.put("state", "ONLINE");
        m.put("lastUpdatedDate", Instant.now().toString());
        return m;
    }

    // attribute 한 칸: { "value": ..., "timestamp": ... } (+ 선택적 unit)
    private Map<String, Object> attr(Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("timestamp", Instant.now().toString());
        return m;
    }

    private Map<String, Object> attr(Object value, String unit) {
        Map<String, Object> m = attr(value);
        m.put("unit", unit);
        return m;
    }
}
