from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
# 라우터 모듈 import (api/routes.py가 작성된 후 주석 해제)
# from app.api.routes import router as anomaly_router

app = FastAPI(
    title="Windfarm Anomaly Service",
    description="풍력 발전기 이상 탐지 AI 서비스",
    version="1.0.0"
)

# CORS 설정 (Spring Boot 등 다른 서비스와의 통신 허용)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 라우터 등록
# app.include_router(anomaly_router, prefix="/api/v1/anomaly")

@app.get("/health")
def health_check():
    """AWS ECS Target Group Health Check를 위한 엔드포인트"""
    return {"status": "healthy", "service": "anomaly-service"}