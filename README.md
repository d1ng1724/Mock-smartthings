# mock-smartthings

세탁기·건조기 관리 프로그램을 **진짜 기기를 건드리지 않고** 테스트하기 위한 가짜 SmartThings 서버.
실제 `https://api.smartthings.com` 대신 이 서버를 바라보게 하면, 관리 프로그램의 코드 로직은
그대로 둔 채(= base URL 만 교체) 세탁기/건조기 제어를 테스트할 수 있다.

Spring Boot(Java 17) 단일 모듈. 상태는 전부 메모리에 있고, 재시작하면 초기화된다.

## 실행

### Docker (권장)
```bash
docker compose up --build
# → http://localhost:8080
```

### 로컬
```bash
mvn spring-boot:run
```

떴는지 확인:
```bash
curl http://localhost:8080/
```

## 기본 기기

| 종류 | deviceId | label |
|------|----------|-------|
| 세탁기 | `11111111-1111-1111-1111-111111111111` | Test Washer |
| 건조기 | `22222222-2222-2222-2222-222222222222` | Test Dryer |

기기를 더 늘리려면 `DeviceStore.seed()` 에 `add(...)` 한 줄만 추가하면 된다.

## 관리 프로그램에 붙이는 법

관리 프로그램의 SmartThings base URL 설정만 이 서버로 바꾼다.

```
https://api.smartthings.com   →   http://localhost:8080
(같은 docker network 안이면  http://mock-smartthings:8080 )
```

토큰은 **아무 값이나** 넣으면 된다. 값 검증은 안 하지만, `Authorization` 헤더 자체가 없으면
실제 API처럼 401을 돌려준다(관리 프로그램의 인증 처리 경로도 테스트되도록).

`/v1/devices` 와 `/devices` 두 접두어를 모두 받으므로, base 를 `api.smartthings.com` 으로 잡든
`api.smartthings.com/v1` 으로 잡든 상관없다.

## 지원 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| GET  | `/v1/devices` | 기기 목록 |
| GET  | `/v1/devices/{id}` | 기기 상세 |
| GET  | `/v1/devices/{id}/status` | 전체 상태 (표준 + samsungce 둘 다 포함) |
| GET  | `/v1/devices/{id}/health` | ONLINE/OFFLINE |
| POST | `/v1/devices/{id}/commands` | 명령 실행 |

## 예시 (curl)

세탁기 상태 조회:
```bash
curl -H "Authorization: Bearer dummy" \
  http://localhost:8080/v1/devices/11111111-1111-1111-1111-111111111111/status
```

세탁기 가동:
```bash
curl -X POST -H "Authorization: Bearer dummy" -H "Content-Type: application/json" \
  http://localhost:8080/v1/devices/11111111-1111-1111-1111-111111111111/commands \
  -d '{"commands":[{"component":"main","capability":"washerOperatingState","command":"setMachineState","arguments":["run"]}]}'
```

모드 변경 후 가동:
```bash
curl -X POST -H "Authorization: Bearer dummy" -H "Content-Type: application/json" \
  http://localhost:8080/v1/devices/11111111-1111-1111-1111-111111111111/commands \
  -d '{"commands":[{"component":"main","capability":"washerMode","command":"setWasherMode","arguments":["heavy"]}]}'
```

정지:
```bash
curl -X POST -H "Authorization: Bearer dummy" -H "Content-Type: application/json" \
  http://localhost:8080/v1/devices/11111111-1111-1111-1111-111111111111/commands \
  -d '{"commands":[{"component":"main","capability":"washerOperatingState","command":"setMachineState","arguments":["stop"]}]}'
```

## 동작하는 명령

| capability | command | 인자 | 효과 |
|------------|---------|------|------|
| `switch` | `on` / `off` | – | 전원. off 하면 정지된다 |
| `washerOperatingState` / `dryerOperatingState` | `setMachineState` | `run` `pause` `stop` | 가동/일시정지/정지 |
| `washerMode` / `dryerMode` | `setWasherMode` / `setDryerMode` | 모드명 | 모드 변경(다음 사이클 길이 결정) |

`run` 하면 남은시간이 `tick` 마다 줄고, 0이 되면 자동으로 `stop` + 완료 상태가 된다.
`status` 응답의 `remainingTime` `progress` `completionTime` `machineState` `operatingState` 로 진행을 확인할 수 있다.

## 시간 가속 (테스트 편의)

기본값은 **tick 1초마다 남은시간 1분 감소** → 60분 사이클이 약 60초 만에 끝난다.
더 빠르게 하려면 `docker-compose.yml` 이나 환경변수로 조절:

```
MOCK_MINUTES_PER_TICK=5   # tick 당 5분씩 감소 → 60분 사이클이 12초
MOCK_TICK_MS=500          # tick 주기 0.5초
```

## 참고 / 한계

- 상태는 메모리 저장이라 재시작 시 초기화된다(테스트용이므로 의도된 동작).
- 토큰 값은 검증하지 않는다. OAuth 흐름/토큰 만료까지 테스트하려면 별도 목이 필요하다.
- 실제 삼성 기기가 노출하는 capability 중 이 목에 없는 게 있으면
  `DeviceStore.status()` 에 한 칸 추가하면 된다(형식만 맞추면 됨).
