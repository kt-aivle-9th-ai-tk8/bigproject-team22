-- users 스키마 정정(ERD 정합) + 계정 상태 도입.
--   1) PK 컬럼명을 다른 테이블과 같은 `<테이블명>_id` 규칙으로 통일한다(id → user_id).
--   2) 연락처/계정상태 컬럼을 추가한다.
--   3) 잠금 판단 근거를 login_fail_count 에서 status 로 옮기면서 기존 잠금 계정을 이관한다.
-- 이미 배포된 스키마를 변경하므로 기존 행의 의미가 보존되도록 3) 을 반드시 함께 수행한다.

-- 1) PK 컬럼명 통일. 참조 중인 FK 를 먼저 끊고 이름 변경 후 다시 건다.
--    (assignments 가 users 를 참조하는 유일한 FK 다.)
ALTER TABLE assignments
    DROP FOREIGN KEY fk_assignments_user;

ALTER TABLE users
    CHANGE COLUMN id user_id BIGINT NOT NULL AUTO_INCREMENT;

ALTER TABLE assignments
    ADD CONSTRAINT fk_assignments_user FOREIGN KEY (user_id) REFERENCES users (user_id);

-- 2) 신규 컬럼.
--    status 는 NOT NULL 이므로 기존 행을 위해 기본값 ACTIVE 를 둔다.
--    phone 은 신규 회원가입에서는 필수(요청 검증)지만, 이 컬럼이 없던 시절의 기존 행이 있으므로
--    DB 제약은 nullable 로 둔다(없는 값을 지어내지 않는다).
ALTER TABLE users
    ADD COLUMN phone  VARCHAR(20) DEFAULT NULL AFTER user_name,
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER role;

-- 3) 기존 잠금 계정 이관.
--    이 마이그레이션 이후 잠금은 status 로만 판단하므로, 이미 실패 임계를 넘긴 계정이
--    조용히 풀리지 않도록 상태를 맞춘다. 임계값 5 는 User.MAX_LOGIN_FAIL_COUNT 와 같다.
UPDATE users
SET status = 'SUSPENDED'
WHERE login_fail_count >= 5;
