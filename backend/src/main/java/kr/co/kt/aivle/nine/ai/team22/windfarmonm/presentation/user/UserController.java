package kr.co.kt.aivle.nine.ai.team22.windfarmonm.presentation.user;

import jakarta.validation.Valid;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user.UserService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.application.user.dto.UserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.presentation.user.dto.SignUpRequest;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.presentation.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원(관리자 계정) 관련 API. context-path(/api) 기준 경로.
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 회원가입: POST /api/users */
    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> signUp(@Valid @RequestBody SignUpRequest request) {
        UserResult result = userService.signUp(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다.", UserResponse.from(result)));
    }
}
