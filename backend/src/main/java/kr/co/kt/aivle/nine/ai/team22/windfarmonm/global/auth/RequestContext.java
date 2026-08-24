package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 현재 HTTP 요청에서 '누가·어디서' 를 읽는다. 감사 로그처럼 요청을 직접 받지 않는 계층이 접속 주체와
 * 접속지를 알아야 할 때 쓴다 — 그 목적으로 모든 서비스 시그니처에 요청 객체를 끌고 다니지 않기 위함이다.
 * <p>
 * 요청 스레드 밖(스케줄러·큐 폴러)에서는 두 메서드 모두 null 을 준다. 호출측이 그 경우를 다뤄야 한다.
 */
@Component
public class RequestContext {

    /** ALB/CloudFront 를 거치면 remoteAddr 은 프록시 주소라 원 클라이언트가 아니다. */
    private static final String FORWARDED_FOR = "X-Forwarded-For";

    /** audit_log.ip_address 폭(IPv6 최대 길이). */
    private static final int MAX_IP_LENGTH = 45;

    /** 현재 세션의 로그인 사용자 id. 비로그인·요청 밖이면 null. */
    public Long currentUserId() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object member = session.getAttribute(SessionConst.LOGIN_MEMBER);
        return member instanceof LoginMember loginMember ? loginMember.userId() : null;
    }

    /**
     * 접속지 IP. 요청 밖이면 null.
     * <p>
     * X-Forwarded-For 의 <b>맨 앞</b> 값을 우선한다(프록시들이 뒤로 덧붙이므로 원 클라이언트에 가장 가깝다).
     * 다만 이 헤더는 클라이언트가 임의로 채워 보낼 수 있어 <b>참고값</b>이다 — 위조를 배제하려면 신뢰
     * 프록시 홉 수를 알고 뒤에서 세어야 하는데, CloudFront→ALB 구성이 확정되면 그때 조일 것.
     */
    public String currentClientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            if (!first.isEmpty()) {
                return truncate(first);
            }
        }
        return truncate(request.getRemoteAddr());
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest()
                : null;
    }

    /** 컬럼 폭을 넘는 값(헤더 위조 등)이 적재 실패로 요청 전체를 깨지 않도록 자른다. */
    private static String truncate(String ip) {
        if (ip == null) {
            return null;
        }
        return ip.length() > MAX_IP_LENGTH ? ip.substring(0, MAX_IP_LENGTH) : ip;
    }
}
