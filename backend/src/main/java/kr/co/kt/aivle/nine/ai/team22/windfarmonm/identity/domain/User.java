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
@Table(name = "`user`") // user 는 SQL 예약어라 백틱으로 인용한다(Hibernate 가 방언별 인용으로 변환)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /** 로그인 실패 허용 횟수. 도달 시 계정이 {@link UserStatus#SUSPENDED} 로 전이된다. */
    public static final int MAX_LOGIN_FAIL_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    /** 사번 */
    @Column(nullable = false, unique = true, updatable = false)
    private String employeeId;

    /** bcrypt 해시된 비밀번호 */
    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String userName;

    /**
     * 연락처. 회원가입 시 필수로 받는다(요청 검증).
     * 컬럼이 nullable 인 것은 이 필드가 없던 시절의 기존 계정 때문이다.
     */
    @Column(length = 20)
    private String phone;

    /**
     * 소속 부서(ERD 의 users.department, V6 에서 추가된 컬럼).
     * <p>
     * 가입 시 <b>선택</b>으로 받는다 — 보내지 않았거나 공백이면 null 이다(값을 지어내지 않는다).
     * 이 컬럼이 생긴 뒤 한동안 가입 경로에 연결되지 않아, 그 시기에 만들어진 계정은 모두 null 이다.
     * 마이페이지가 이 값을 그대로 보여준다.
     */
    @Column(length = 50)
    private String department;

    // @JdbcTypeCode(VARCHAR): Hibernate 6.3+ 가 MySQL 네이티브 ENUM 컬럼을 생성하지 않도록 강제.
    // 표준 VARCHAR 로 저장해 DB 이식성(PostgreSQL 등)·구버전 호환·enum 값 추가 시 무마이그레이션 확보.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    /** 로그인 가능 여부의 단일 근거. {@link #loginFailCount} 는 잠금 판단에 쓰이지 않는다. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    /**
     * 로그인 실패 누적 횟수. 임계 도달 시 {@link #status} 를 SUSPENDED 로 전이시키는 트리거이자
     * <b>감사 기록</b>이다. 잠긴 뒤에도 값을 유지하며, 관리자가 계정을 다시 활성화할 때만 0 이 된다.
     */
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
    // TODO: DB 유출 시 발생가능한 세션 탈취 문제 해결 필요(https://github.com/kt-aivle-9th-ai-tk8/bigproject-team22/pull/23#discussion_r3618676953)
    @Column(length = 64)
    private String latestSessionId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private User(String employeeId, String encodedPassword, String userName, String phone, Role role) {
        this.employeeId = employeeId;
        this.password = encodedPassword;
        this.userName = userName;
        this.phone = phone;
        this.role = role;
        this.status = UserStatus.ACTIVE;
        this.loginFailCount = 0;
    }

    public static User create(String employeeId, String encodedPassword, String userName, String phone, Role role) {
        return create(employeeId, encodedPassword, userName, phone, role, null);
    }

    /**
     * 부서까지 받아 만든다. 부서는 선택값이라 인자를 <b>맨 뒤</b>에 둔다 — 기존 5-인자 호출과 자리를
     * 다투지 않아 인자 순서를 헷갈릴 여지가 없다.
     */
    public static User create(String employeeId, String encodedPassword, String userName, String phone,
                              Role role, String department) {
        User user = new User(employeeId, encodedPassword, userName, phone, role);
        user.department = department;
        return user;
    }

    /** 로그인 차단 여부. 자동 잠금과 관리자 차단을 구분하지 않고 상태 하나로 판단한다. */
    public boolean isLocked() {
        return status == UserStatus.SUSPENDED;
    }

    /**
     * 로그인 실패 누적. 임계에 도달하면 계정을 정지시킨다.
     * 카운트는 잠긴 뒤에도 감사 목적으로 그대로 남긴다.
     */
    public void increaseLoginFailCount() {
        this.loginFailCount++;
        if (this.loginFailCount >= MAX_LOGIN_FAIL_COUNT) {
            this.status = UserStatus.SUSPENDED;
        }
    }

    public void resetLoginFailCount() {
        this.loginFailCount = 0;
    }

    /**
     * 계정 상태 변경(관리자).
     * <p>
     * 활성화할 때 실패 카운트를 함께 0 으로 되돌린다. 그러지 않으면 카운트가 임계에 머문 채 풀려
     * <b>다음 1회 실패만으로 즉시 재잠금</b>되기 때문이다.
     */
    public void changeStatus(UserStatus status) {
        this.status = status;
        if (status == UserStatus.ACTIVE) {
            this.loginFailCount = 0;
        }
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
