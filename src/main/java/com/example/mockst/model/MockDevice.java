package com.example.mockst.model;

import java.time.Instant;

/**
 * 가상 기기 한 대의 상태를 담는 객체. 값은 store에서 직접 읽고 쓴다.
 */
public class MockDevice {

    public final String deviceId;
    public final DeviceType type;
    public final String label;

    // 상태
    public boolean switchOn = false;
    public String machineState = "stop";   // stop / run / pause
    public String jobState = "none";
    public String mode = "normal";
    public int totalTime = 60;             // 현재 사이클 총 시간(분)
    public int remainingTime = 0;          // 남은 시간(분)
    public boolean finished = false;
    public boolean remoteControlEnabled = true;
    public Instant completionTime = Instant.now();

    public MockDevice(String deviceId, DeviceType type, String label) {
        this.deviceId = deviceId;
        this.type = type;
        this.label = label;
    }

    /** samsungce operatingState 값 (running / paused / finished / ready) */
    public String operatingState() {
        if (finished) return "finished";
        return switch (machineState) {
            case "run" -> "running";
            case "pause" -> "paused";
            default -> "ready";
        };
    }

    /** 진행률 % */
    public int progress() {
        if (finished) return 100;
        if (totalTime <= 0) return 0;
        int p = (totalTime - remainingTime) * 100 / totalTime;
        return Math.max(0, Math.min(100, p));
    }
}
