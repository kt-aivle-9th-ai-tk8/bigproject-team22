package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationJpaRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderBySentAtDesc(Long userId);

    List<Notification> findByUserIdAndReadIsFalseOrderBySentAtDesc(Long userId);
}
