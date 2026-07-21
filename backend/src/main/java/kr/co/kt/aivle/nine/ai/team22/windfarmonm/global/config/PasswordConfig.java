package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해시(bcrypt) 인코더. spring-security-crypto 모듈만 사용하며
 * Spring Security 필터체인은 활성화하지 않는다(세션 인증은 자체 구현).
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
