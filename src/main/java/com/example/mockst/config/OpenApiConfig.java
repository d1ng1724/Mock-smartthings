package com.example.mockst.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger UI 설정.
 *
 * <p>
 * 우측 상단 <b>Authorize</b> 버튼에 아무 문자열이나 넣으면 (lenient 모드 기준) 기기 API 를
 * 브라우저에서 바로 호출해 볼 수 있다. strict 모드라면 {@code POST /oauth/token} 으로 받은
 * access token 을 넣으면 된다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mockSmartThingsOpenApi() {
        var bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("opaque")
                .description("SmartThings Personal Access Token 자리. lenient 모드에서는 아무 값이나 통과한다.");

        return new OpenAPI()
                .info(new Info()
                        .title("mock-smartthings")
                        .version("0.0.1")
                        .description("""
                                세탁기·건조기 관리 프로그램을 진짜 기기 없이 테스트하기 위한 가짜 SmartThings 서버.

                                - **SmartThings API** — 실제 api.smartthings.com 과 같은 요청/응답 형태
                                - **OAuth** — 로그인 화면 없이 곧바로 코드를 발급하는 목 인가 서버
                                - **Mock 조작 API** — 실기기로는 만들기 어려운 상황(권한 오류, 원격제어 꺼짐 등)을 강제로 재현
                                """))
                .components(new Components().addSecuritySchemes("bearerAuth", bearer))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .tags(List.of(
                        new Tag().name("SmartThings Devices").description("실제 SmartThings 기기 API 를 흉내 낸 엔드포인트"),
                        new Tag().name("OAuth").description("토큰 발급·갱신"),
                        new Tag().name("Mock 조작").description("목 전용. 실제 SmartThings 에는 없는 경로이며 토큰이 필요 없다"),
                        new Tag().name("Health").description("서버 기동 확인")));
    }
}
