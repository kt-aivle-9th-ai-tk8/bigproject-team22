package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.SignUpCommand;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.UserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.Role;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserRepository;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.BusinessException;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    PasswordEncoder passwordEncoder;
    @InjectMocks
    UserService userService;

    private SignUpCommand command() {
        return new SignUpCommand("E1001", "pw12345!", "홍길동", "010-1234-5678");
    }

    @Test
    @DisplayName("가입 시 권한은 요청과 무관하게 GUEST 로 고정되고 비밀번호는 해시된다")
    void signUp_fixesRoleToGuestAndEncodesPassword() {
        when(userRepository.existsByEmployeeId("E1001")).thenReturn(false);
        when(passwordEncoder.encode("pw12345!")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResult result = userService.signUp(command());

        assertThat(result.role()).isEqualTo(Role.GUEST);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.GUEST);
        assertThat(captor.getValue().getPassword()).isEqualTo("hashed");
    }

    @Test
    @DisplayName("사전 확인에서 중복 사번이면 DUPLICATE_EMPLOYEE_ID 를 던지고 저장하지 않는다")
    void signUp_duplicateEmployeeId() {
        when(userRepository.existsByEmployeeId("E1001")).thenReturn(true);

        assertThatThrownBy(() -> userService.signUp(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMPLOYEE_ID);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("동시 가입 경합으로 save 가 무결성 예외를 던지면 DUPLICATE_EMPLOYEE_ID 로 변환한다")
    void signUp_concurrentDuplicate() {
        when(userRepository.existsByEmployeeId("E1001")).thenReturn(false);
        when(passwordEncoder.encode("pw12345!")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> userService.signUp(command()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMPLOYEE_ID);
    }
}
