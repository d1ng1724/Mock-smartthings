package com.example.mockst.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 가상 기기 한 대의 상태. 상태 변경은 전부 DeviceStore 가 기기 단위 락을 잡고 수행한다.
 *
 * <p>
 * 속성별 timestamp 는 "값이 바뀐 시각"이다. 실제 SmartThings 도 조회 시각이 아니라 마지막 변경 시각을 주므로,
 * 관리 프로그램이 timestamp 로 상태 변화를 감지하는 로직을 그대로 테스트할 수 있다.
 */
public class MockDevice {

    public static final String IDLE_JOB = "none";

    public final String deviceId;
    public final DeviceType type;
    public final String label;   // Washer-3F-L1 형식. 관리 프로그램이 이 형식만 인식한다.
    public final int floor;

    // 상태 (읽기는 직접, 쓰기는 아래 setter 로 — setter 가 변경 시각을 찍는다)
    public boolean switchOn = false;
    public String machineState = "stop";        // stop / run / pause
    public String jobState = IDLE_JOB;
    public String cycle = DeviceType.DEFAULT_CYCLE;
    public boolean remoteControlEnabled = true;
    public boolean online = true;

    public int totalTime;                       // 현재 사이클 총 시간(분)
    public int remainingTime = 0;               // 남은 시간(분)
    public Instant completionTime = truncSeconds();
    /** 완료(finish/finished) 상태로 들어간 시각. 일정 시간 뒤 jobState 가 none 으로 리셋된다. */
    public Instant finishedAt = null;

    // 속성별 마지막 변경 시각
    public Instant switchAt = truncMillis();
    public Instant machineStateAt = truncMillis();
    public Instant jobStateAt = truncMillis();
    public Instant cycleAt = truncMillis();
    public Instant remoteControlAt = truncMillis();
    public Instant remainingTimeAt = truncMillis();

    public MockDevice(DeviceType type, String label, int floor) {
        this.type = type;
        this.label = label;
        this.floor = floor;
        // 라벨에서 결정적으로 뽑아내므로 재시작해도 deviceId 가 유지된다.
        this.deviceId = UUID.nameUUIDFromBytes(label.getBytes(StandardCharsets.UTF_8)).toString();
        this.totalTime = type.minutesOf(cycle);
    }

    public void setSwitchOn(boolean value) {
        if (value != switchOn) {
            switchOn = value;
            switchAt = truncMillis();
        }
    }

    public void setMachineState(String value) {
        if (!value.equals(machineState)) {
            machineState = value;
            machineStateAt = truncMillis();
        }
    }

    public void setJobState(String value) {
        if (!value.equals(jobState)) {
            jobState = value;
            jobStateAt = truncMillis();
        }
    }

    public void setCycle(String value) {
        if (!value.equals(cycle)) {
            cycle = value;
            cycleAt = truncMillis();
        }
    }

    public void setRemoteControlEnabled(boolean value) {
        if (value != remoteControlEnabled) {
            remoteControlEnabled = value;
            remoteControlAt = truncMillis();
        }
    }

    public void setRemainingTime(int value) {
        if (value != remainingTime) {
            remainingTime = value;
            remainingTimeAt = truncMillis();
        }
    }

    /** samsungce operatingState 값. 실기기가 보고하는 supportedOperatingStates 는 ready/running/paused 뿐이다. */
    public String operatingState() {
        return switch (machineState) {
            case "run" -> "running";
            case "pause" -> "paused";
            default -> "ready";
        };
    }

    /** 진행률 %. */
    public int progress() {
        if (totalTime <= 0) {
            return 0;
        }
        int p = (totalTime - remainingTime) * 100 / totalTime;
        return Math.max(0, Math.min(100, p));
    }

    /** 실기기의 remainingTimeStr 형식("01:15"). */
    public String remainingTimeStr() {
        return String.format("%02d:%02d", remainingTime / 60, remainingTime % 60);
    }

    /** SmartThings 의 Iso8601Date 스키마는 소수점 이하 3자리까지만 허용한다. */
    private static Instant truncMillis() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    /** 실기기의 completionTime 은 초 단위로 내려온다. */
    private static Instant truncSeconds() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
