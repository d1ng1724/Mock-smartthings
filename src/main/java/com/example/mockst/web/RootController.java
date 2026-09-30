package com.example.mockst.web;

import com.example.mockst.model.MockDevice;
import com.example.mockst.store.DeviceStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 서버가 떴는지 확인하는 용도 (토큰 없이 접근 가능). */
@RestController
public class RootController {

    private final DeviceStore store;

    public RootController(DeviceStore store) {
        this.store = store;
    }

    @GetMapping("/")
    public Map<String, Object> index() {
        List<Map<String, Object>> ds = new ArrayList<>();
        for (MockDevice d : store.all()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deviceId", d.deviceId);
            m.put("type", d.type.name());
            m.put("label", d.label);
            ds.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("service", "mock-smartthings");
        out.put("status", "up");
        out.put("devices", ds);
        return out;
    }
}
