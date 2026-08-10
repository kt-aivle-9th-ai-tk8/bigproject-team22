package kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.Notification;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.monitoringdiagnosis.domain.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    @Override
    public Notification save(Notification notification) {
        return jpaRepository.save(notification);
    }

    @Override
    public void saveAll(List<Notification> notifications) {
        jpaRepository.saveAll(notifications);
    }

    @Override
    public Optional<Notification> findById(Long notificationId) {
        return jpaRepository.findById(notificationId);
    }

    @Override
    public void delete(Notification notification) {
        jpaRepository.delete(notification);
    }

    @Override
    public List<Notification> findByUserIdOrderBySentAtDesc(Long userId) {
        return jpaRepository.findByUserIdOrderBySentAtDesc(userId);
    }

    @Override
    public List<Notification> findByUserIdAndReadIsFalseOrderBySentAtDesc(Long userId) {
        return jpaRepository.findByUserIdAndReadIsFalseOrderBySentAtDesc(userId);
    }
}
