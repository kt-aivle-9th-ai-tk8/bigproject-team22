FROM python:3.11-slim

# 로그가 버퍼링 없이 즉시 stdout으로 나가게 함 — CloudWatch Logs에서 실시간 확인 가능,
# 태스크가 비정상 종료돼도 마지막 로그가 유실되지 않음
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

# ca-certificates: RDS SSL 연결 시 인증서 체인 검증에 필요.
# 설치 후 apt 캐시를 같은 레이어에서 정리해 이미지 크기를 늘리지 않음.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 필요한 파일만 명시적으로 복사 (COPY . . 대신) — .dockerignore와 이중으로 방어
COPY detection/ ./detection/
COPY lightgbm/ ./lightgbm/   
COPY db_client.py run_tier_a.py run_tier_b.py ./

# 비root 사용자 생성 후 전환 — 컨테이너 프로세스가 root 권한으로 돌지 않게 함
RUN groupadd --system app && useradd --system --gid app --no-create-home app \
    && chown -R app:app /app
USER app

# ENTRYPOINT는 지정하지 않는다 — EventBridge Scheduler가 ECS RunTask 시
# container overrides의 command로 run_tier_a.py / run_tier_b.py를 골라 실행한다.