#   빌드: docker build -t anomaly-service .
#   실행: docker run -d -p 8000:8000 --name anomaly-container anomaly-service

# ---- build stage ----
FROM python:3.11-slim
WORKDIR /app

# 캐시 파일 실행 X
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# ---- package 설치 ----
RUN apt-get update && apt-get install -y \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# ---- requirements 설치 ----
COPY requirements.txt .
RUN pip install --upgrade pip
RUN pip install --no-cache-dir -r requirements.txt

# ---- 소스 코드 복사 및 실행 ----
COPY . .

# 보안 설정
RUN useradd -m appuser
RUN chown -R appuser /app
USER appuser

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]