package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.health;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.AdminRoleInterceptor;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginCheckInterceptor;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMemberArgumentResolver;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config.WebConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통신 체크 엔드포인트 테스트.
 * 인증 인터셉터 체인(WebConfig)을 실제로 올려, /health 가 '인증 없이' 200 을 반환하는지(공개 경로) 검증한다.
 * DB/Redis/Testcontainers 없이 MVC 슬라이스만 사용한다.
 */
@WebMvcTest(HealthController.class)
@Import({WebConfig.class, LoginCheckInterceptor.class, AdminRoleInterceptor.class, LoginMemberArgumentResolver.class})
@ActiveProfiles("test") // main application.yaml 의 spring.profiles.active 플레이스홀더 해석을 위해 필요
class HealthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("인증 없이 GET /health 는 200 과 success=true 를 반환한다(공개 경로)")
    void health_isPubliclyAccessible() throws Exception {
        MvcResult result = mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("\"success\":true")
                .contains("healthy");
    }
}
