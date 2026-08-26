package kr.co.kt.aivle.nine.ai.team22.windfarmonm.global.config;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.AssetAccessGuard;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.assetmanagement.application.AssignmentCommandService;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.User;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 데모 계정이 데모 단지({@code demo.wind-farm-ids})를 열람할 수 있도록 담당(Assignment) 배정을
 * 시작 시 <b>멱등하게 보장</b>한다. {@code demo.enabled=true} 일 때만 빈으로 등록된다.
 * <p>
 * users 테이블은 만들지 않는다(그 테이블은 Flyway·시드로 건드리지 않는 규칙이다) — 기존 계정
 * {@code demo.employee-id} 를 재사용하며, 없으면 경고만 남기고 건너뛴다. 기존 배정은 지우지 않고
 * (현재 ∪ 데모 단지) 로 합쳐 재배정하므로, 계정이 원래 담당하던 단지도 유지된다.
 * <p>
 * 데모 부트스트랩이 앱 기동을 막지 않도록, 어떤 실패도 삼켜 로그로만 남긴다(설정 오류로 존재하지
 * 않는 단지 id 를 넣어도 기동은 계속된다).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "demo", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoAccountInitializer implements ApplicationRunner {

    private final DemoProperties demoProperties;
    private final UserRepository userRepository;
    private final AssetAccessGuard assetAccessGuard;
    private final AssignmentCommandService assignmentCommandService;

    @Override
    public void run(ApplicationArguments args) {
        String employeeId = demoProperties.employeeId();
        try {
            Optional<User> demoUser = userRepository.findByEmployeeId(employeeId);
            if (demoUser.isEmpty()) {
                log.warn("[demo] 데모 계정(사번={})이 없어 담당 배정을 건너뛴다 — 계정 생성 후 데모 링크가 데이터를 보여준다", employeeId);
                return;
            }

            Long userId = demoUser.get().getId();
            List<Long> current = assetAccessGuard.viewableWindFarmIds(userId, false); // MANAGER 관점(담당 목록)
            Set<Long> union = new LinkedHashSet<>();
            if (current != null) {
                union.addAll(current);
            }
            union.addAll(demoProperties.windFarmIds());

            assignmentCommandService.replaceAssignments(userId, List.copyOf(union));
            log.info("[demo] 데모 계정(사번={}, userId={}) 담당 단지 보장: {}", employeeId, userId, union);
        } catch (RuntimeException e) {
            log.error("[demo] 데모 계정(사번={}) 담당 배정 보장에 실패했다 — 기동은 계속한다", employeeId, e);
        }
    }
}
