package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.MyProfileResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.SignUpCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.UserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 신규 계정 생성. 비밀번호는 bcrypt 해시하여 저장한다.
     * 권한은 요청값을 신뢰하지 않고 항상 {@link Role#GUEST}(가입 승인 대기)로 고정하며,
     * 실질 권한은 관리자 승인 흐름에서 승격된다.
     */
    @Transactional
    public UserResult signUp(SignUpCommand command) {
        if (userRepository.existsByEmployeeId(command.employeeId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMPLOYEE_ID);
        }

        String encodedPassword = passwordEncoder.encode(command.password());
        User user = User.create(command.employeeId(), encodedPassword, command.userName(), command.phone(),
                Role.GUEST, command.department());

        try {
            return UserResult.from(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            // 사전 존재 확인과 save() 사이의 동시 가입 경합. users 테이블의 유일 제약은
            // employee_id 뿐이므로 무결성 위반은 사번 중복으로 간주해 409 로 변환한다.
            throw new BusinessException(ErrorCode.DUPLICATE_EMPLOYEE_ID);
        }
    }

    /**
     * 마이페이지용 내 계정 정보. 조회 주체가 곧 대상이라 별도 인가가 없다 —
     * 로그인 인터셉터를 통과한 세션의 userId 만으로 대상이 결정된다.
     * <p>
     * 세션은 살아 있는데 계정이 지워진 경우(가입 거절 직후 등)는 {@link ErrorCode#USER_NOT_FOUND}.
     */
    @Transactional(readOnly = true)
    public MyProfileResult getMyProfile(Long userId) {
        return MyProfileResult.from(userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)));
    }
}