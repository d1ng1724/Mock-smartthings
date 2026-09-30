package com.example.mockst.web.dto;

import java.util.List;

/**
 * SmartThings 명령 한 건.
 * { "component": "main", "capability": "washerOperatingState",
 *   "command": "setMachineState", "arguments": ["run"] }
 */
public record CommandDto(String component, String capability, String command, List<Object> arguments) {
}
