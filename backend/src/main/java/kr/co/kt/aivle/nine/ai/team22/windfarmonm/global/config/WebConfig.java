package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.AdminRoleInterceptor;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.DemoReadOnlyInterceptor;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginCheckInterceptor;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMemberArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * {@code @Login} 인자 리졸버 등록 + 인증 인터셉터 등록.
 * 경로는 context-path(/api) 기준 상대 경로다.
 */
@Configuration
@EnableConfigurationProperties(DemoProperties.class)
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginMemberArgumentResolver loginMemberArgumentResolver;
    private final LoginCheckInterceptor loginCheckInterceptor;
    private final AdminRoleInterceptor adminRoleInterceptor;
    private final DemoReadOnlyInterceptor demoReadOnlyInterceptor;

    /** 인증 없이 접근 가능한 공개 경로 */
    // TODO: 개발과정에서만 필요한 h2-console 이하부를 Spring Profile 기능을 통해 분리
    private static final List<String> PUBLIC_PATHS = List.of(
            "/users",            // 회원가입
            "/auth/login",       // 로그인
            "/auth/logout",      // 로그아웃
            "/auth/demo",        // 데모 진입(로그인 없이 세션 발급) — demo.enabled=false 면 컨트롤러가 404
            "/health",           // ECS Target Group Health Check
            "/error",
            "/h2-console/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**"
    );

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginMemberArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 데모(role=DEMO) 세션의 쓰기 요청을 로그인 검사보다 먼저 차단한다(항상 등록 — 읽기전용은 DEMO role 의 성질).
        // 로그인/로그아웃(POST)은 제외해, 데모 쿠키를 든 브라우저에서도 실제 로그인/로그아웃은 가능하게 둔다.
        registry.addInterceptor(demoReadOnlyInterceptor)
                .order(0)
                .addPathPatterns("/**")
                .excludePathPatterns("/auth/login", "/auth/logout");

        registry.addInterceptor(loginCheckInterceptor)
                .order(1)
                .addPathPatterns("/**")
                .excludePathPatterns(PUBLIC_PATHS);

        // 관리자 경로는 로그인 검사(order 1) 이후 ADMIN 권한을 추가 검사
        registry.addInterceptor(adminRoleInterceptor)
                .order(2)
                .addPathPatterns("/admin/**");
    }
}
