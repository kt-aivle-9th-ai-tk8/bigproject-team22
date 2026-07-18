package kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user.dto.SignUpCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user.dto.UserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.repository.UserRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * 신규 관리자 계정 생성. 비밀번호는 bcrypt 해시하여 저장한다.
     */
    @Transactional
    public UserResult signUp(SignUpCommand command) {
        if (userRepository.existsByEmployeeId(command.employeeId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMPLOYEE_ID);
        }

        String encodedPassword = passwordEncoder.encode(command.password());
        User user = User.create(command.employeeId(), encodedPassword, command.userName(), command.role());

        return UserResult.from(userRepository.save(user));
    }
}
