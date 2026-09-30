package com.example.mockst.model;

import java.util.List;

/**
 * 세탁기/건조기의 SmartThings capability 이름 차이를 한곳에 모아둔 것.
 * 새 기기 타입을 추가하고 싶으면 여기에 상수 하나만 더 넣으면 된다.
 */
public enum DeviceType {

    WASHER(
            "Samsung Washer",
            "Washer",
            "washerOperatingState",
            "samsungce.washerOperatingState",
            "washerJobState",
            "washerMode",
            "setWasherMode",
            "supportedWasherModes",
            "wash",      // 가동 중 job 상태
            "finish",    // 완료 시 job 상태
            List.of("normal", "heavy", "delicates", "quick", "rinseSpin", "spinOnly")
    ),

    DRYER(
            "Samsung Dryer",
            "Dryer",
            "dryerOperatingState",
            "samsungce.dryerOperatingState",
            "dryerJobState",
            "dryerMode",
            "setDryerMode",
            "supportedDryerModes",
            "drying",
            "finished",
            List.of("normal", "heavy", "delicates", "timeDry", "airFluff", "cottons")
    );

    public final String deviceTypeName;
    public final String category;
    public final String operatingCap;   // 표준 operatingState capability
    public final String samsungCap;     // samsungce 확장 capability
    public final String jobAttr;        // washerJobState / dryerJobState
    public final String modeCap;
    public final String setModeCmd;
    public final String supportedModesAttr;
    public final String runJob;
    public final String doneJob;
    public final List<String> supportedModes;

    DeviceType(String deviceTypeName, String category, String operatingCap, String samsungCap,
               String jobAttr, String modeCap, String setModeCmd, String supportedModesAttr,
               String runJob, String doneJob, List<String> supportedModes) {
        this.deviceTypeName = deviceTypeName;
        this.category = category;
        this.operatingCap = operatingCap;
        this.samsungCap = samsungCap;
        this.jobAttr = jobAttr;
        this.modeCap = modeCap;
        this.setModeCmd = setModeCmd;
        this.supportedModesAttr = supportedModesAttr;
        this.runJob = runJob;
        this.doneJob = doneJob;
        this.supportedModes = supportedModes;
    }
}
