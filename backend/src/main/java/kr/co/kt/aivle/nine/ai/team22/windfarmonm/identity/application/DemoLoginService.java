package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config.DemoProperties;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.LoginResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모(읽기 전용) 세션의 주체를 해석한다. 비밀번호 검증 없이 기존 계정({@code demo.employee-id})을
 * 재사용하되, 세션에 실릴 role 은 <b>계정의 실제 role 과 무관하게 항상 {@code Role.DEMO} 로 강제</b>한다.
 * <p>
 * 이유: 데모는 로그인 없는 공개 링크다. {@code Role.DEMO} 는 담당(Assignment) 기반 열람 권한은 일반
 * 사용자와 동일하게 받되, 쓰기(비 GET)는 {@code DemoReadOnlyInterceptor} 가 막고, 관리자 경로
 * ({@code /admin/**})는 ADMIN 이 아니라 {@code AdminRoleInterceptor} 가 자연히 막는다. 계정의 DB role
 * 은 건드리지 않으므로(세션 전용) 이 계정은 평소 정상 로그인으로도 쓸 수 있다. 열람 범위는 이 계정의
 * 담당 배정을 그대로 따른다({@code DemoAccountInitializer} 가 데모 단지 배정을 보장).
 */
@Service
@RequiredArgsConstructor
public class DemoLoginService {

    private final UserRepository userRepository;
    private final DemoProperties demoProperties;

    /**
     * 데모 계정을 조회해 세션에 저장할 주체 정보를 만든다.
     *
     * @throws BusinessException 데모 계정이 존재하지 않으면 {@link ErrorCode#USER_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public LoginResult resolveDemoMember() {
        User user = userRepository.findByEmployeeId(demoProperties.employeeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // role 은 계정의 실제 값이 아니라 DEMO 로 고정한다(위 클래스 주석 참고).
        return new LoginResult(user.getId(), user.getEmployeeId(), user.getUserName(), Role.DEMO);
    }
}
