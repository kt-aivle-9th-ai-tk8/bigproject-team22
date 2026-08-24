package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation;

import jakarta.validation.Valid;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.UserService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.MyProfileResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.application.dto.UserResult;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.Login;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.auth.LoginMember;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto.MyProfileResponse;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto.SignUpRequest;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.presentation.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    /**
     * 마이페이지(내 정보) 조회: GET /api/users/mypage
     * <p>
     * 대상은 <b>세션의 사용자</b>다 — 경로에 id 를 받지 않으므로 남의 정보를 요청할 방법 자체가 없다.
     * <p>
     * {@code /auth} 가 아니라 {@code /users} 아래에 둔다. {@code /auth/*} 는 세션을 만들고 없애는
     * <b>인증 행위</b>이고, 마이페이지는 회원 자원 조회다 — 컨트롤러 분리도 그 선을 따른다.
     * 사번·이름·전화번호는 관리자 화면과 같은 규칙으로 마스킹해 내려간다.
     * 이메일은 스키마에 없어 담지 않는다.
     */
    @GetMapping("/mypage")
    public ResponseEntity<ApiResponse<MyProfileResponse>> getMyProfile(@Login LoginMember member) {
        MyProfileResult result = userService.getMyProfile(member.userId());
        return ResponseEntity.ok(ApiResponse.success(MyProfileResponse.from(result)));
    }
}
