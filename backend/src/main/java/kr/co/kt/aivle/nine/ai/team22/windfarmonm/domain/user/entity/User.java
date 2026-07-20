package kr.co.kt.aivle.nine.ai.team22.windfarmonm.domain.user.entity;

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
}
