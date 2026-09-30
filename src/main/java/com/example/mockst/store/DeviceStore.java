package com.example.mockst.store;

import com.example.mockst.model.DeviceType;
import com.example.mockst.model.MockDevice;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 가상 기기 저장소 + 상태머신 + 상태 JSON 빌더.
 *
 * <p>
 * 노출하는 capability 는 관리 프로그램(Washer-Backend-v2)이 실제로 읽고 쓰는 것에 맞췄다.
 * switch / washerOperatingState / dryerOperatingState / remoteControlStatus /
 * samsungce.*OperatingState / samsungce.*Cycle.
 */
@Component
public class DeviceStore {

    /** 기기가 설치된 층. 3층·4층 각각 세탁기 6대 + 건조기 6대. */
    private static final int[] FLOORS = {3, 4};
    private static final String[] POSITIONS = {"L1", "L2", "L3", "R1", "R2", "R3"};

    private static final String LOCATION_ID = "0c1e6a5f-2b4d-4e7a-9f31-5c8d2a7b6e40";

    @Value("${mock.minutes-per-tick:1}")
    private int minutesPerTick;

    /** 완료 상태(finish/finished)를 유지하는 시간(초). 이후 jobState 가 none 으로 돌아간다. */
    @Value("${mock.finish-hold-seconds:30}")
    private int finishHoldSeconds;

    /** label -> device. 라벨 순서를 유지하려고 LinkedHashMap 을 쓴다. */
    private final Map<String, MockDevice> byId = Collections.synchronizedMap(new LinkedHashMap<>());

    @PostConstruct
    void seed() {
        byId.clear();
        for (int floor : FLOORS) {
            for (DeviceType type : DeviceType.values()) {
                for (String position : POSITIONS) {
                    String label = "%s-%dF-%s".formatted(type.category, floor, position);
                    MockDevice d = new MockDevice(type, label, floor);
                    byId.put(d.deviceId, d);
                }
            }
        }
    }

    /** 모든 기기를 초기 상태로 되돌린다. */
    public void resetAll() {
        seed();
    }

    public Collection<MockDevice> all() {
        synchronized (byId) {
            return List.copyOf(byId.values());
        }
    }

    public MockDevice get(String id) {
        return byId.get(id);
    }

    // ---------------------------------------------------------------------
    // 시간 시뮬레이션
    // ---------------------------------------------------------------------

    @Scheduled(fixedDelayString = "${mock.tick-ms:1000}")
    void tick() {
        Instant now = Instant.now();
        for (MockDevice d : all()) {
            synchronized (d) {
                advance(d, now);
            }
        }
    }

    private void advance(MockDevice d, Instant now) {
        // 완료 상태를 일정 시간 유지한 뒤 none 으로 리셋한다(실기기와 같은 동작).
        if (d.finishedAt != null) {
            if (Duration.between(d.finishedAt, now).getSeconds() >= finishHoldSeconds) {
                d.setJobState(MockDevice.IDLE_JOB);
                d.finishedAt = null;
            }
            return;
        }

        if (!d.switchOn || !"run".equals(d.machineState)) {
            return;
        }

        d.setRemainingTime(Math.max(0, d.remainingTime - minutesPerTick));

        if (d.remainingTime == 0) {
            d.setMachineState("stop");
            d.setJobState(d.type.doneJob);
            d.completionTime = now.truncatedTo(ChronoUnit.SECONDS);
            d.finishedAt = now;
        } else {
            d.setJobState(d.type.phaseAt(d.totalTime - d.remainingTime, d.totalTime));
            d.completionTime = now.plusSeconds(60L * d.remainingTime).truncatedTo(ChronoUnit.SECONDS);
        }
    }

    // ---------------------------------------------------------------------
    // 명령 처리
    // ---------------------------------------------------------------------

    /**
     * 명령 한 건을 적용한다. 실제 SmartThings 와 마찬가지로 기기가 받아들이지 못하는 명령도 HTTP 응답은
     * ACCEPTED 이고, 상태만 바뀌지 않는다.
     */
    public void applyCommand(MockDevice d, String capability, String command, List<Object> args) {
        String arg0 = (args != null && !args.isEmpty() && args.get(0) != null) ? String.valueOf(args.get(0)) : null;

        synchronized (d) {
            if ("switch".equals(capability)) {
                if ("on".equals(command)) {
                    d.setSwitchOn(true);
                } else if ("off".equals(command)) {
                    d.setSwitchOn(false);
                    stop(d);
                }
                return;
            }

            // 원격 제어가 꺼진 기기는 전원 외의 제어 명령을 무시한다(실기기와 동일).
            if (!d.remoteControlEnabled) {
                return;
            }

            if (d.type.operatingCap.equals(capability) && "setMachineState".equals(command)) {
                switch (arg0 == null ? "" : arg0) {
                    case "run" -> run(d);
                    case "pause" -> pause(d);
                    case "stop" -> stop(d);
                    default -> {
                    }
                }
                return;
            }

            if (d.type.cycleCap.equals(capability) && d.type.setCycleCmd.equals(command) && arg0 != null) {
                d.setCycle(arg0);
                // 사이클을 바꾸면 다음에 run 할 때의 총 시간이 정해진다. 가동 중이면 적용하지 않는다.
                if (!"run".equals(d.machineState)) {
                    d.totalTime = d.type.minutesOf(arg0);
                }
            }
        }
    }

    private void run(MockDevice d) {
        d.setSwitchOn(true);
        d.finishedAt = null;
        if (d.remainingTime <= 0) {
            d.totalTime = d.type.minutesOf(d.cycle);
            d.setRemainingTime(d.totalTime);
        }
        d.setMachineState("run");
        d.setJobState(d.type.phaseAt(d.totalTime - d.remainingTime, d.totalTime));
        d.completionTime = Instant.now().plusSeconds(60L * d.remainingTime).truncatedTo(ChronoUnit.SECONDS);
    }

    private void pause(MockDevice d) {
        if ("run".equals(d.machineState)) {
            d.setMachineState("pause");
        }
    }

    private void stop(MockDevice d) {
        d.setMachineState("stop");
        d.setJobState(MockDevice.IDLE_JOB);
        d.setRemainingTime(0);
        d.finishedAt = null;
        d.completionTime = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // ---------------------------------------------------------------------
    // JSON 빌더 (SmartThings 응답 형태에 맞춤)
    // ---------------------------------------------------------------------

    public Map<String, Object> deviceInfo(MockDevice d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceId", d.deviceId);
        m.put("name", "[%s] Samsung".formatted(d.type.category.toLowerCase()));
        m.put("label", d.label);
        m.put("manufacturerName", "Samsung Electronics");
        m.put("presentationId", d.type.presentationId);
        m.put("deviceManufacturerCode", "Samsung Electronics");
        m.put("locationId", LOCATION_ID);
        m.put("roomId", roomId(d.floor));
        m.put("deviceTypeName", "Samsung OCF " + d.type.category);
        m.put("components", List.of(mainComponent(d)));
        m.put("createTime", "2025-01-01T00:00:00.000Z");
        m.put("profile", Map.of("id", UUID.nameUUIDFromBytes(d.type.presentationId.getBytes()).toString()));
        m.put("ocf", ocf(d));
        m.put("type", "OCF");
        m.put("restrictionTier", 0);
        m.put("allowed", List.of());
        m.put("executionContext", "CLOUD");
        return m;
    }

    private Map<String, Object> mainComponent(MockDevice d) {
        List<Map<String, Object>> caps = new ArrayList<>();
        for (String id : List.of("switch", d.type.operatingCap, d.type.samsungCap, d.type.cycleCap,
                "remoteControlStatus", "refresh", "execute", "ocf")) {
            caps.add(Map.of("id", id, "version", 1));
        }
        Map<String, Object> main = new LinkedHashMap<>();
        main.put("id", "main");
        main.put("label", "main");
        main.put("capabilities", caps);
        main.put("categories", List.of(Map.of("name", d.type.category, "categoryType", "manufacturer")));
        return main;
    }

    private Map<String, Object> ocf(MockDevice d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ocfDeviceType", d.type.ocfDeviceType);
        m.put("name", "[%s] Samsung".formatted(d.type.category.toLowerCase()));
        m.put("specVersion", "core.1.1.0");
        m.put("verticalDomainSpecVersion", "res.1.1.0,sh.1.1.0");
        m.put("manufacturerName", "Samsung Electronics");
        m.put("modelNumber", "DA_WM_TP2_20_COMMON|MOCK");
        m.put("platformVersion", "DAWIT 2.0");
        m.put("platformOS", "TizenRT 2.0 + IPv6");
        m.put("vendorId", d.type.presentationId);
        return m;
    }

    private String roomId(int floor) {
        return UUID.nameUUIDFromBytes(("mock-room-%dF".formatted(floor)).getBytes()).toString();
    }

    public Map<String, Object> status(MockDevice d) {
        synchronized (d) {
            Map<String, Object> main = new LinkedHashMap<>();

            main.put("switch", Map.of("switch", attr(d.switchOn ? "on" : "off", d.switchAt)));

            // 표준 operatingState — 관리 프로그램이 machineState/jobState/completionTime 을 여기서 읽는다.
            Map<String, Object> op = new LinkedHashMap<>();
            op.put("machineState", attr(d.machineState, d.machineStateAt));
            op.put(d.type.jobAttr, attr(d.jobState, d.jobStateAt));
            op.put("completionTime", attr(d.completionTime.toString(), d.machineStateAt));
            op.put("supportedMachineStates", attr(List.of("stop", "run", "pause"), d.switchAt));
            main.put(d.type.operatingCap, op);

            // 삼성 확장 operatingState
            Map<String, Object> sop = new LinkedHashMap<>();
            sop.put("operatingState", attr(d.operatingState(), d.machineStateAt));
            sop.put("supportedOperatingStates", attr(List.of("ready", "running", "paused"), d.switchAt));
            sop.put(d.type.jobAttr, attr(d.jobState, d.jobStateAt));
            sop.put("progress", attr(d.progress(), d.remainingTimeAt, "%"));
            sop.put("remainingTime", attr(d.remainingTime, d.remainingTimeAt, "min"));
            sop.put("remainingTimeStr", attr(d.remainingTimeStr(), d.remainingTimeAt));
            sop.put("operationTime", attr(d.totalTime, d.cycleAt, "min"));
            main.put(d.type.samsungCap, sop);

            // 사이클(코스) — 통세척 명령이 쓰는 capability
            Map<String, Object> cyc = new LinkedHashMap<>();
            cyc.put(d.type.cycleAttr, attr(d.cycle, d.cycleAt));
            cyc.put("supportedCycles", attr(supportedCycles(d.type), d.cycleAt));
            main.put(d.type.cycleCap, cyc);

            main.put("remoteControlStatus",
                    Map.of("remoteControlEnabled",
                            attr(String.valueOf(d.remoteControlEnabled), d.remoteControlAt)));

            return Map.of("components", Map.of("main", main));
        }
    }

    private List<Map<String, Object>> supportedCycles(DeviceType type) {
        List<Map<String, Object>> list = new ArrayList<>();
        type.cycleMinutes.forEach((code, minutes) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("cycle", code);
            m.put("cycleType", type == DeviceType.WASHER ? "washingOnly" : "dryingOnly");
            m.put("timeInMin", minutes);
            list.add(m);
        });
        return list;
    }

    public Map<String, Object> health(MockDevice d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceId", d.deviceId);
        m.put("state", d.online ? "ONLINE" : "OFFLINE");
        m.put("lastUpdatedDate", d.switchAt.toString());
        return m;
    }

    // attribute 한 칸: { "value": ..., "timestamp": ... } (+ 선택적 unit)
    private Map<String, Object> attr(Object value, Instant timestamp) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("timestamp", timestamp.toString());
        return m;
    }

    private Map<String, Object> attr(Object value, Instant timestamp, String unit) {
        Map<String, Object> m = attr(value, timestamp);
        m.put("unit", unit);
        return m;
    }
}
