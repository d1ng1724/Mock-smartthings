package com.example.mockst.web;

import com.example.mockst.store.FaultStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** /mock/fail 로 예약된 장애를 기기 API 응답에 적용한다. 인증 통과 뒤에 동작한다. */
@Component
@Order(2)
public class FaultInjectionFilter extends OncePerRequestFilter {

    private final FaultStore faults;

    public FaultInjectionFilter(FaultStore faults) {
        this.faults = faults;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        if (ApiErrors.isDeviceApi(req.getRequestURI())) {
            int status = faults.consume();
            if (status != 0) {
                ApiErrors.write(res, status, FaultStore.codeFor(status), "Injected by mock-smartthings");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
