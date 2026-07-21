package kr.co.kt.aivle.nine.ai.team22.windfarmonm.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /** 로그인 실패 허용 횟수. 초과 시 계정 잠금 */
    public static final int MAX_LOGIN_FAIL_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사번 */
    @Column(nullable = false, unique = true, updatable = false)
    private String employeeId;

    /** bcrypt 해시된 비밀번호 */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String userName;

    // @JdbcTypeCode(VARCHAR): Hibernate 6.3+ 가 MySQL 네이티브 ENUM 컬럼을 생성하지 않도록 강제.
    // 표준 VARCHAR 로 저장해 DB 이식성(PostgreSQL 등)·구버전 호환·enum 값 추가 시 무마이그레이션 확보.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private int loginFailCount;

    /**
     * 마지막으로 발급된 세션 id(1인 1세션 축출 포인터).
     * <p>
     * 주의: 이 값이 있다고 해서 세션이 살아있다는 뜻이 아니다. 세션은 Redis TTL 로 만료되며
     * 그때 애플리케이션에 콜백이 오지 않으므로 이 컬럼은 stale 해질 수 있다.
     * 따라서 "현재 로그인 중" 여부는 반드시 Redis(SessionManager#exists)로 확인해야 한다.
     * 로그아웃/강제종료 시에도 지우지 않으며, 다음 로그인 때 덮어쓴다.
     */
    @Column(length = 64)
    private String latestSessionId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private User(String employeeId, String encodedPassword, String userName, Role role) {
        this.employeeId = employeeId;
        this.password = encodedPassword;
        this.userName = userName;
        this.role = role;
        this.loginFailCount = 0;
    }

    public static User create(String employeeId, String encodedPassword, String userName, Role role) {
        return new User(employeeId, encodedPassword, userName, role);
    }

    public boolean isLocked() {
        return loginFailCount >= MAX_LOGIN_FAIL_COUNT;
    }

    public void increaseLoginFailCount() {
        this.loginFailCount++;
    }

    public void resetLoginFailCount() {
        this.loginFailCount = 0;
    }

    /** 관리자 승인/권한 변경 */
    public void changeRole(Role role) {
        this.role = role;
    }

    /** 로그인 시 최신 세션 id 로 갱신(1인 1세션 축출 포인터) */
    public void updateLatestSessionId(String sessionId) {
        this.latestSessionId = sessionId;
    }
}
