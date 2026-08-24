-- identity BC 베이스라인 스키마.
-- 현재 User 엔티티가 Hibernate ddl-auto 로 생성하던 것과 동일한 구조를 명시적으로 고정한다.
-- 이후 스키마 변경은 V2, V3... 마이그레이션으로만 수행한다(ddl-auto 는 validate 로 교차검증).

CREATE TABLE users
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id       VARCHAR(255) NOT NULL,
    password          VARCHAR(255) NOT NULL,
    user_name         VARCHAR(255) NOT NULL,
    -- role 은 이식성을 위해 네이티브 ENUM 이 아닌 VARCHAR 로 저장한다(@JdbcTypeCode(VARCHAR)).
    -- 값 검증은 애플리케이션의 Role enum 이 담당하므로 DB CHECK 제약은 두지 않는다
    -- (권한 값 추가 시 마이그레이션 없이 확장 가능하게 하기 위함).
    role              VARCHAR(20)  NOT NULL,
    login_fail_count  INT          NOT NULL,
    -- 마지막으로 발급된 세션 id(1인 1세션 축출 포인터). 활성 여부의 근거로 쓰지 않는다.
    latest_session_id VARCHAR(64)  DEFAULT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_employee_id UNIQUE (employee_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
