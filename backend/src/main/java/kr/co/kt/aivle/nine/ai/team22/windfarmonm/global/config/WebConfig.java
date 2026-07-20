package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginCheckInterceptor;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMemberArgumentResolver;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginMemberArgumentResolver loginMemberArgumentResolver;
    private final LoginCheckInterceptor loginCheckInterceptor;

    /** 인증 없이 접근 가능한 공개 경로 */
    // TODO: 개발과정에서만 필요한 h2-console 이하부를 Spring Profile 기능을 통해 분리
    private static final List<String> PUBLIC_PATHS = List.of(
            "/users",            // 회원가입
            "/auth/login",       // 로그인
            "/auth/logout",      // 로그아웃
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
        registry.addInterceptor(loginCheckInterceptor)
                .order(1)
                .addPathPatterns("/**")
                .excludePathPatterns(PUBLIC_PATHS);
    }
}
