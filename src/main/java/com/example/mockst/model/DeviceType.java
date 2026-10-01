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
            // 실기기의 samsungce.washerCycle 은 "Table_02_Course_85" 처럼 읽히고,
            // setWasherCycle 명령에는 테이블 접두사를 뗀 "Course_85" 를 인자로 넣는다.
            cycles(
                    "Course_85", 60,   // 표준 (스팀 노멀)
                    "Course_56", 90,   // 강력 (침구/이불)
                    "Course_8B", 30,   // 쾌속
                    "Course_5B", 20,   // 소량 세탁
                    "Course_82", 90    // 무세제 통세척 (SMARTTHINGS_TUB_CLEAN_CYCLE)
            ),
            aliases(
                    "01", 60,   // (구) 표준
                    "02", 90,   // (구) 강력
                    "03", 30,   // (구) 쾌속
                    "04", 20,   // (구) 헹굼+탈수
                    "6C", 90    // (구) 무세제 통세척
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
            // 건조기는 samsungce.dryerCycle 이 "Table_03_Course_32" 처럼 읽히고,
            // setDryerCycle 인자는 마찬가지로 "Course_32" 형식이다.
            cycles(
                    "Course_32", 80,   // 표준 (구김 방지)
                    "Course_2F", 100,  // 강력 (두꺼운 옷)
                    "Course_3E", 40,   // 소량 건조
                    "Course_34", 20    // 스팀 살균/리프레시
            ),
            aliases(
                    "01", 80,   // (구) 표준
                    "02", 100,  // (구) 강력
                    "03", 40,   // (구) 쾌속
                    "04", 20    // (구) 에어살균
            ));

    /** 사이클 진행 단계. weight 는 전체 사이클에서 차지하는 비중(%). */
    public record Phase(String name, int weight) {
    }

    /** MockDevice 의 초기 사이클. 세탁기 표준 코스 코드. */
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
    /** 기기가 광고하는 코스 목록. DeviceStore.supportedCycles() 가 이 순서대로 내보낸다. */
    public final Map<String, Integer> cycleMinutes;
    /** 구버전 코드("01"~"04", "6C") 하위호환용. supportedCycles 에는 노출하지 않는다. */
    private final Map<String, Integer> cycleAliases;

    DeviceType(String deviceTypeName, String category, String ocfDeviceType, String presentationId,
            String operatingCap, String samsungCap, String jobAttr, String cycleCap, String cycleAttr,
            String setCycleCmd, String doneJob, List<Phase> phases, Map<String, Integer> cycleMinutes,
            Map<String, Integer> cycleAliases) {
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
        this.cycleAliases = cycleAliases;
    }

    /**
     * 기기가 켜졌을 때 선택돼 있는 코스. 세탁기와 건조기의 코스 코드 체계는 서로 겹치지 않으므로
     * 공용 상수를 두지 않고 각자 목록의 첫 번째(표준 코스)를 쓴다.
     */
    public String defaultCycle() {
        return cycleMinutes.keySet().iterator().next();
    }

    /**
     * 사이클 코드에 해당하는 소요 시간(분).
     * 현재 코스 목록 → 구버전 별칭 순으로 찾고, 그래도 모르면 기본값으로 처리한다(목이므로 거부하지 않는다).
     */
    public int minutesOf(String cycle) {
        Integer minutes = cycleMinutes.get(cycle);
        if (minutes == null) {
            minutes = cycleAliases.get(cycle);
        }
        return minutes != null ? minutes : DEFAULT_CYCLE_MINUTES;
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

    /** 구버전 코드 별칭. cycles() 와 같은 형태지만 supportedCycles 응답에는 나가지 않는다. */
    private static Map<String, Integer> aliases(Object... pairs) {
        return cycles(pairs);
    }

    private static Map<String, Integer> cycles(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            m.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(m);
    }
}
