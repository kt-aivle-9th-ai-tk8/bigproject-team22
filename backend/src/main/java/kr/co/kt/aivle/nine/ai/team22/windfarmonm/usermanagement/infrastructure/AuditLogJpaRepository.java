package kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.infrastructure;

import kr.co.kt.aivle.nine.ai.team22.windfarmonm.usermanagement.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogJpaRepository extends JpaRepository<AuditLog, Long> {
}
