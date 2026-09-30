package com.example.mockst.store;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 장애 주입 상태. /mock/fail 로 예약해 두면 이후 기기 API 호출이 지정한 횟수만큼 실패한다.
 *
 * <p>
 * 관리 프로그램의 403 권한 오류 처리(SmartThingsFeignErrorDecoder)와 5xx 재시도(Retryer)를
 * 실제로 돌려보기 위한 장치다.
 */
@Component
public class FaultStore {

    private volatile int status = 0;
    private final AtomicInteger remaining = new AtomicInteger(0);

    public void queue(int status, int count) {
        this.status = status;
        this.remaining.set(Math.max(0, count));
    }

    public void clear() {
        this.remaining.set(0);
        this.status = 0;
    }

    /** 이번 요청을 실패시켜야 하면 HTTP 상태코드를, 아니면 0 을 반환한다. */
    public int consume() {
        if (status == 0) {
            return 0;
        }
        int left = remaining.getAndUpdate(n -> n > 0 ? n - 1 : 0);
        return left > 0 ? status : 0;
    }

    public int status() {
        return status;
    }

    public int remaining() {
        return remaining.get();
    }

    /** SmartThings 가 해당 상태코드에 쓰는 에러 코드. */
    public static String codeFor(int status) {
        return switch (status) {
            case 400 -> "ConstraintViolationError";
            case 401 -> "UnauthorizedError";
            case 403 -> "ForbiddenError";
            case 404 -> "NotFoundError";
            case 422 -> "UnprocessableEntityError";
            case 429 -> "TooManyRequests";
            default -> "UnexpectedError";
        };
    }
}
