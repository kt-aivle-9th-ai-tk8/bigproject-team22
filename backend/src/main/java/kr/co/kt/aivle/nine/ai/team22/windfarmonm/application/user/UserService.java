package kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user.dto.SignUpCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user.dto.UserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.repository.UserRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.exception.ErrorCode;
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
        User user = User.create(command.employeeId(), encodedPassword, command.userName(), Role.GUEST);

        try {
            return UserResult.from(userRepository.save(user));
        } catch (DataIntegrityViolationException e) {
            // 사전 존재 확인과 save() 사이의 동시 가입 경합. users 테이블의 유일 제약은
            // employee_id 뿐이므로 무결성 위반은 사번 중복으로 간주해 409 로 변환한다.
            throw new BusinessException(ErrorCode.DUPLICATE_EMPLOYEE_ID);
        }
    }
}
