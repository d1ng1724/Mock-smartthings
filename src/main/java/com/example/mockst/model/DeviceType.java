package com.example.mockst.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 세탁기/건조기의 SmartThings capability 이름 차이를 한곳에 모아둔 것.
 * 관리 프로그램(Washer-Backend-v2)이 실제로 읽고 쓰는 capability 만 정의한다.
 */
public enum DeviceType {

    WASHER(
            "Samsung Washer", "Washer", "oic.d.washer", "DA-WM-WM-000001",
            "washerOperatingState",
            "samsungce.washerOperatingState",
            "washerJobState",
            "samsungce.washerCycle",
            "washerCycle",
            "setWasherCycle",
            "finish",
            // 진행 단계와 비중(%). remainingTime 진행에 따라 jobState 가 순서대로 바뀐다.
            List.of(new Phase("wash", 50), new Phase("rinse", 30), new Phase("spin", 20)),
            cycles(
                    "01", 60,   // 표준
                    "02", 90,   // 강력
                    "03", 30,   // 쾌속
                    "04", 20,   // 헹굼+탈수
                    "6C", 90    // 무세제 통세척
            )),

    DRYER(
            "Samsung Dryer", "Dryer", "oic.d.dryer", "DA-WM-WD-000001",
            "dryerOperatingState",
            "samsungce.dryerOperatingState",
            "dryerJobState",
            "samsungce.dryerCycle",
            "dryerCycle",
            "setDryerCycle",
            "finished",
            List.of(new Phase("drying", 90), new Phase("cooling", 10)),
            cycles(
                    "01", 80,   // 표준
                    "02", 100,  // 강력
                    "03", 40,   // 쾌속
                    "04", 20    // 에어살균
            ));

    /** 사이클 진행 단계. weight 는 전체 사이클에서 차지하는 비중(%). */
    public record Phase(String name, int weight) {
    }

    public static final String DEFAULT_CYCLE = "01";
    public static final int DEFAULT_CYCLE_MINUTES = 60;

    public final String deviceTypeName;
    public final String category;
    public final String ocfDeviceType;
    public final String presentationId;
    public final String operatingCap;    // washerOperatingState / dryerOperatingState
    public final String samsungCap;      // samsungce.washerOperatingState / ...
    public final String jobAttr;         // washerJobState / dryerJobState
    public final String cycleCap;        // samsungce.washerCycle / samsungce.dryerCycle
    public final String cycleAttr;       // washerCycle / dryerCycle
    public final String setCycleCmd;     // setWasherCycle / setDryerCycle
    public final String doneJob;         // finish / finished
    public final List<Phase> phases;
    public final Map<String, Integer> cycleMinutes;

    DeviceType(String deviceTypeName, String category, String ocfDeviceType, String presentationId,
            String operatingCap, String samsungCap, String jobAttr, String cycleCap, String cycleAttr,
            String setCycleCmd, String doneJob, List<Phase> phases, Map<String, Integer> cycleMinutes) {
        this.deviceTypeName = deviceTypeName;
        this.category = category;
        this.ocfDeviceType = ocfDeviceType;
        this.presentationId = presentationId;
        this.operatingCap = operatingCap;
        this.samsungCap = samsungCap;
        this.jobAttr = jobAttr;
        this.cycleCap = cycleCap;
        this.cycleAttr = cycleAttr;
        this.setCycleCmd = setCycleCmd;
        this.doneJob = doneJob;
        this.phases = phases;
        this.cycleMinutes = cycleMinutes;
    }

    /** 사이클 코드에 해당하는 소요 시간(분). 모르는 코드는 기본값으로 처리한다(목이므로 거부하지 않는다). */
    public int minutesOf(String cycle) {
        return cycleMinutes.getOrDefault(cycle, DEFAULT_CYCLE_MINUTES);
    }

    /** 전체 시간 중 elapsed 만큼 지났을 때의 jobState. */
    public String phaseAt(int elapsedMinutes, int totalMinutes) {
        if (totalMinutes <= 0) {
            return phases.get(0).name();
        }
        int acc = 0;
        for (Phase p : phases) {
            acc += p.weight();
            if (elapsedMinutes * 100 < acc * totalMinutes) {
                return p.name();
            }
        }
        return phases.get(phases.size() - 1).name();
    }

    private static Map<String, Integer> cycles(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }
}
