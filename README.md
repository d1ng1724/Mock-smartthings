# mock-smartthings

세탁기·건조기 관리 프로그램(**Washer-Backend-v2**)을 **진짜 기기를 건드리지 않고** 테스트하기 위한
가짜 SmartThings 서버.

실제 `https://api.smartthings.com` 대신 이 서버를 바라보게 하면, 관리 프로그램의 코드는 그대로 둔 채
(= URL 설정만 교체) 기기 동기화 · 상태 조회 · 제어 · OAuth 토큰 발급/갱신까지 전부 돌려볼 수 있다.

Spring Boot(Java 17) 단일 모듈. 상태는 전부 메모리에 있고, 재시작하면 초기화된다.

> 노출하는 엔드포인트와 capability 는 **관리 프로그램이 실제로 쓰는 것**에 맞춰져 있다
> (`SmartThingsFeignClient`, `SmartThingsOAuthClient`, `SmartThingsDeviceStatusResDto`,
> `SmartThingsCommandReqDto`). 관리 프로그램이 안 쓰는 capability 는 일부러 넣지 않았다.

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

## Swagger UI

브라우저에서 **<http://localhost:8080/swagger-ui.html>** 를 열면 모든 엔드포인트를 바로 호출해 볼 수 있다.
OpenAPI 문서 자체는 <http://localhost:8080/v3/api-docs> 에 있다.

- 기기 API 는 토큰이 필요하다. 우측 상단 **Authorize** 를 눌러 아무 값이나 넣으면 된다
  (`lenient` 모드 기준. `strict` 모드라면 `POST /oauth/token` 으로 받은 access token 을 넣는다).
- `POST /v1/devices/{id}/commands` 에는 **가동 / 정지 / 무세제 통세척 / 전원 끄기** 예제가 들어 있어서
  드롭다운에서 고르기만 하면 된다.
- `deviceId` 는 `GET /mock/devices` 에서 라벨과 함께 볼 수 있다.
- 기기 API 는 `/v1/devices` 와 `/devices` 둘 다 받지만, 문서에는 `/v1` 쪽만 싣는다(중복 제거).

## 관리 프로그램에 붙이는 법

환경변수 5개만 바꾸면 된다.

```bash
SMARTTHINGS_API_URL=http://localhost:8080
SMARTTHINGS_OAUTH_URL=http://localhost:8080/oauth/token
SMARTTHINGS_AUTHORIZE_URL=http://localhost:8080/oauth/authorize
SMARTTHINGS_CLIENT_ID=mock-client        # 값은 아무거나
SMARTTHINGS_CLIENT_SECRET=mock-secret    # 값은 아무거나
SMARTTHINGS_TUB_CLEAN_CYCLE=6C           # 통세척 코스 코드 (아래 표 참고)
```

같은 docker network 안이면 `localhost` 대신 `mock-smartthings` 를 쓴다.

### 연동 순서

1. 관리 프로그램에서 `GET /api/v2/admin/smartthings/oauth/authorize` 를 호출해 나온 URL을 브라우저로 연다.
2. 목이 로그인 화면 없이 곧바로 `redirect_uri?code=...&state=...` 로 302 리다이렉트한다.
3. 관리 프로그램의 콜백이 `POST /oauth/token` 으로 코드를 교환하고 토큰을 저장한다.
4. 이후 기기 동기화 → 24대가 Machine 으로 생성된다.

## 기본 기기 (24대)

라벨은 관리 프로그램의 파싱 정규식 `^(Washer|Dryer)-(\d+)F-(L|R)(\d+)$` 에 맞춰 만든다.
**이 형식을 벗어나면 동기화 단계에서 그냥 버려지니 주의.**

| 층 | 세탁기 | 건조기 |
|---|---|---|
| 3층 | `Washer-3F-L1` ~ `L3`, `R1` ~ `R3` | `Dryer-3F-L1` ~ `L3`, `R1` ~ `R3` |
| 4층 | `Washer-4F-L1` ~ `L3`, `R1` ~ `R3` | `Dryer-4F-L1` ~ `L3`, `R1` ~ `R3` |

`deviceId` 는 라벨에서 결정적으로 생성되므로 **재시작해도 바뀌지 않는다**. 목록은
`GET /mock/devices` 로 한눈에 볼 수 있다.

기기 구성을 바꾸려면 `DeviceStore` 의 `FLOORS` / `POSITIONS` 상수만 고치면 된다.

## 지원 엔드포인트

### SmartThings API (토큰 필요)

| Method | Path | 설명 |
|--------|------|------|
| GET  | `/v1/devices` | 기기 목록 |
| GET  | `/v1/devices/{id}` | 기기 상세 |
| GET  | `/v1/devices/{id}/status` | 전체 상태 |
| GET  | `/v1/devices/{id}/health` | ONLINE/OFFLINE |
| POST | `/v1/devices/{id}/commands` | 명령 실행 |

`/v1/devices` 와 `/devices` 두 접두어를 모두 받는다.

### OAuth

| Method | Path | 설명 |
|--------|------|------|
| GET  | `/oauth/authorize` | 로그인 없이 곧바로 `redirect_uri` 로 302 |
| POST | `/oauth/token` | `authorization_code` / `refresh_token` 교환 (form-urlencoded + Basic) |

실제 SmartThings 처럼 **refresh 할 때마다 access/refresh 토큰을 함께 교체**하고, 이미 쓴
refresh 토큰은 `400 invalid_grant` 로 거절한다.

## 노출하는 capability

| capability | 속성 |
|---|---|
| `switch` | `switch` |
| `washerOperatingState` / `dryerOperatingState` | `machineState`, `washerJobState`/`dryerJobState`, `completionTime`, `supportedMachineStates` |
| `samsungce.washerOperatingState` / `samsungce.dryerOperatingState` | `operatingState`, `supportedOperatingStates`, `progress`, `remainingTime`, `remainingTimeStr`, `operationTime` |
| `samsungce.washerCycle` / `samsungce.dryerCycle` | `washerCycle`/`dryerCycle`, `supportedCycles` |
| `remoteControlStatus` | `remoteControlEnabled` |

속성의 `timestamp` 는 **값이 바뀐 시각**이다(조회 시각이 아니다). 실제 SmartThings 와 같으므로,
timestamp 로 상태 변화를 감지하는 로직을 그대로 테스트할 수 있다.

## 동작하는 명령

| capability | command | 인자 | 효과 |
|------------|---------|------|------|
| `switch` | `on` / `off` | – | 전원. off 하면 정지된다 |
| `washerOperatingState` / `dryerOperatingState` | `setMachineState` | `run` `pause` `stop` | 가동/일시정지/정지 |
| `samsungce.washerCycle` / `samsungce.dryerCycle` | `setWasherCycle` / `setDryerCycle` | 코스 코드 | 코스 변경(다음 사이클 길이 결정) |

실제 API 와 마찬가지로 **기기가 받아들이지 못하는 명령도 HTTP 응답은 `ACCEPTED`** 이고 상태만 안 바뀐다.
원격 제어(`remoteControlEnabled=false`)가 꺼진 기기는 전원 외의 명령을 무시한다.

### 코스 코드

| 기기 | 코드 | 코스 | 소요(분) |
|---|---|---|---|
| 세탁기 | `01` / `02` / `03` / `04` / `6C` | 표준 / 강력 / 쾌속 / 헹굼+탈수 / **무세제 통세척** | 60 / 90 / 30 / 20 / 90 |
| 건조기 | `01` / `02` / `03` / `04` | 표준 / 강력 / 쾌속 / 에어살균 | 80 / 100 / 40 / 20 |

표에 없는 코드를 보내도 거절하지 않고 60분짜리로 처리한다.

### 사이클 진행

`run` 하면 남은시간이 `tick` 마다 줄고, jobState 가 실제 기기처럼 단계별로 바뀐다.

```
세탁기: wash(50%) → rinse(30%) → spin(20%) → finish → (대기 후) none
건조기: drying(90%) → cooling(10%)          → finished → (대기 후) none
```

완료 상태(`finish`/`finished`)는 `MOCK_FINISH_HOLD_SECONDS` 동안 유지된 뒤 `none` 으로 돌아간다.
관리 프로그램의 완료 감지와 리셋 처리를 순서대로 밟게 하기 위한 것이다.

## 목 전용 조작 API (`/mock`, 토큰 불필요)

실기기에서는 만들기 어려운 상황을 강제로 만든다.

| Method | Path | 설명 |
|--------|------|------|
| GET | `/mock/devices` | 라벨·deviceId·현재 상태 한눈에 보기 |
| POST | `/mock/reset` | 모든 기기 초기화 |
| POST | `/mock/fail?status=403&count=2` | 다음 N번의 기기 API 호출을 해당 상태코드로 실패 |
| DELETE | `/mock/fail` | 장애 주입 해제 |
| POST | `/mock/devices/{id}/remote-control?enabled=false` | 원격 제어 끄기 |
| POST | `/mock/devices/{id}/online?value=false` | 기기 오프라인 |
| POST | `/mock/tokens/expire` | 발급된 access token 전부 만료 |

`status=403` 은 관리 프로그램의 `SmartThingsFeignErrorDecoder` → `SmartThingsPermissionException`
경로를, `status=500` 은 Feign `Retryer` 재시도를 타게 한다.

## 설정

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `MOCK_TICK_MS` | `1000` | 상태 갱신 주기(ms) |
| `MOCK_MINUTES_PER_TICK` | `1` | tick 한 번에 줄어드는 분. 기본값이면 60분 코스가 60초 |
| `MOCK_FINISH_HOLD_SECONDS` | `30` | 완료 상태를 유지하는 시간(초) |
| `MOCK_TOKEN_VALIDATION` | `lenient` | `lenient`: 헤더만 있으면 통과 / `strict`: 목이 발급한 유효 토큰만 통과 |
| `MOCK_OAUTH_EXPIRES_IN` | `3600` | 발급 토큰 유효 시간(초) |

토큰 갱신 스케줄러를 테스트하려면 `MOCK_TOKEN_VALIDATION=strict` + `MOCK_OAUTH_EXPIRES_IN=120`
정도로 두거나, `POST /mock/tokens/expire` 로 즉시 만료시키면 된다.

## 예시 (curl)

```bash
B=http://localhost:8080
H="Authorization: Bearer dummy"          # lenient 모드에서는 아무 값이나 가능

# 기기 목록에서 3층 왼쪽 첫 세탁기 찾기
ID=$(curl -s -H "$H" $B/mock/devices | grep -o '"label":"Washer-3F-L1","deviceId":"[^"]*"' | cut -d'"' -f8)

# 상태 조회
curl -s -H "$H" $B/v1/devices/$ID/status

# 무세제 통세척 실행 (관리 프로그램이 보내는 것과 동일한 형태)
curl -s -X POST -H "$H" -H "Content-Type: application/json" $B/v1/devices/$ID/commands -d '{
  "commands": [
    {"component":"main","capability":"samsungce.washerCycle","command":"setWasherCycle","arguments":["6C"]},
    {"component":"main","capability":"washerOperatingState","command":"setMachineState","arguments":["run"]}
  ]}'

# 정지
curl -s -X POST -H "$H" -H "Content-Type: application/json" $B/v1/devices/$ID/commands \
  -d '{"commands":[{"component":"main","capability":"washerOperatingState","command":"setMachineState","arguments":["stop"]}]}'
```

## 참고 / 한계

- 상태는 메모리 저장이라 재시작 시 초기화된다(테스트용이므로 의도된 동작).
- `/oauth/authorize` 는 로그인·동의 화면 없이 바로 코드를 발급한다. `client_id`/`client_secret` 값도
  검증하지 않고, Basic 헤더가 있는지만 본다.
- 실제 API 는 **토큰 값이 틀리면** nginx 가 만든 **HTML** 401 을 준다(헤더가 아예 없을 때만 JSON).
  목은 두 경우 모두 JSON 으로 응답하므로, 관리 프로그램이 401 바디를 JSON 으로 파싱한다면
  그 문제는 목으로 잡히지 않는다.
- 실기기는 `samsungce.washerCycle` 의 코스 코드가 모델마다 다르다. 실제 코드는
  `GET /v1/devices/{id}/status` 의 `supportedCycles` 나 SmartThings CLI
  (`smartthings capabilities samsungce.washerCycle`)로 확인해야 한다.
- 관리 프로그램이 쓰지 않는 capability(`powerConsumptionReport`, `custom.*` 등)는 넣지 않았다.
  필요해지면 `DeviceStore.status()` 에 한 칸 추가하면 된다.
