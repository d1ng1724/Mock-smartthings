package com.example.mockst.store;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 응답 지연 상태. 예약해 두면 이후 기기 API 호출이 지정한 횟수만큼 늦게 응답한다.
 *
 * <p>
 * 관리 프로그램의 Feign 타임아웃(연결 5000ms / 읽기 5000ms)과
 * {@code Retryer.Default(100, 1000, 2)} 재시도 경로를 실제로 돌려보기 위한 장치다.
 * 5000ms 를 넘겨 예약하면 읽기 타임아웃이, 그보다 짧게 잡으면 "느리지만 성공" 경로가 재현된다.
 *
 * <p>
 * {@link FaultStore} 와 달리 응답 자체는 정상이다. 늦게 줄 뿐이라,
 * 클라이언트가 기다릴지 끊을지는 온전히 클라이언트 설정에 달려 있다.
 */
@Component
public class DelayStore {

    /**
     * 지연 상한. 목을 띄워 둔 사람이 실수로 {@code 600000} 같은 값을 넣으면
     * 요청 스레드가 10분간 묶여 목 서버 전체가 멈춘 것처럼 보인다.
     * 타임아웃 테스트에는 5초 남짓이면 충분하므로 60초에서 잘라 둔다.
     */
    public static final long MAX_MILLIS = 60_000L;

    private volatile long millis = 0L;
    private final AtomicInteger remaining = new AtomicInteger(0);

    /** 다음 {@code count} 번의 기기 API 호출을 {@code millis} 만큼 지연시킨다. */
    public void queue(long millis, int count) {
        this.millis = Math.min(MAX_MILLIS, Math.max(0L, millis));
        this.remaining.set(Math.max(0, count));
    }

    public void clear() {
        this.remaining.set(0);
        this.millis = 0L;
    }

    /** 이번 요청에 적용할 지연 시간(ms)을, 예약이 남아있지 않으면 0 을 반환한다. */
    public long consume() {
        if (millis <= 0L) {
            return 0L;
        }
        int left = remaining.getAndUpdate(n -> n > 0 ? n - 1 : 0);
        return left > 0 ? millis : 0L;
    }

    public long millis() {
        return millis;
    }

    public int remaining() {
        return remaining.get();
    }
}
