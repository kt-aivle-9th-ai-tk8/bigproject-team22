package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.domain.AuditLog;
import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.domain.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuditLogRepositoryImpl implements AuditLogRepository {

    private final AuditLogJpaRepository jpaRepository;

    @Override
    public AuditLog save(AuditLog auditLog) {
        return jpaRepository.save(auditLog);
    }
}
