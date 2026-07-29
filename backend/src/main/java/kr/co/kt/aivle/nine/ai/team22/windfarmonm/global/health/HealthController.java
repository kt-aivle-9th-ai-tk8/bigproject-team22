package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.health;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통신 체크. Health Check 용 공개 엔드포인트 (for AWS ECS Target Group)
 * 인증 없이 200 return
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Void>> health() {
        return ResponseEntity.ok(ApiResponse.success("healthy", null));
    }
}
