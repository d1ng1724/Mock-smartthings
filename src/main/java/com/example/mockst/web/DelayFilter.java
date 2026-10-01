package com.example.mockst.web;

import com.example.mockst.store.DelayStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 예약된 지연을 기기 API 응답에 적용한다.
 *
 * <p>
 * 인증({@code @Order(1)})과 장애 주입({@code @Order(2)}) 다음인 3 번이다.
 * 401 이나 주입된 5xx 처럼 어차피 실패할 요청까지 굳이 붙잡아 둘 이유가 없고,
 * 순서를 앞에 두면 "토큰 없는 요청이 5초 뒤 401" 같은 헷갈리는 상황이 생긴다.
 *
 * <p>
 * 지연 뒤에는 그대로 체인을 태워 <b>정상 응답</b>을 준다.
 * 클라이언트가 읽기 타임아웃을 낼지 끝까지 기다릴지는 클라이언트 몫이다.
 */
@Component
@Order(3)
public class DelayFilter extends OncePerRequestFilter {

    private final DelayStore delays;

    public DelayFilter(DelayStore delays) {
        this.delays = delays;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if (ApiErrors.isDeviceApi(req.getRequestURI())) {
            long millis = delays.consume();
            if (millis > 0L) {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {
                    // 톰캣이 워커 스레드를 접으려는 신호일 수 있다. 플래그를 복원해
                    // 상위(컨테이너)가 인터럽트를 알아챌 수 있게 하고, 요청 자체는 그대로 처리한다.
                    Thread.currentThread().interrupt();
                }
            }
        }
        chain.doFilter(req, res);
    }
}
