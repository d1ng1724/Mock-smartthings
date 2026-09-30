package com.example.mockst;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 세탁기/건조기 테스트용 가짜 SmartThings 서버.
 * 실제 api.smartthings.com 대신 이 서버를 바라보게 하면
 * 진짜 세탁기를 건드리지 않고 관리 프로그램을 테스트할 수 있다.
 */
@SpringBootApplication
@EnableScheduling
public class MockStApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockStApplication.class, args);
    }
}
