import logging
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

# API 라우터 연결 (app/api/routes.py에서 router를 가져옵니다)
from app.api.routes import router as report_router

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("report-agent-service")

@asynccontextmanager
async def lifespan(app: FastAPI):
    """
    애플리케이션 수명 주기(Startup / Shutdown) 관리
    """
    # [Startup] 서비스 시작 시 실행할 작업 (예: LLM API 키 검증, LangGraph 모델 준비 등)
    logger.info("🚀 Report Agent Service starting up...")
    yield
    # [Shutdown] 서비스 종료 시 실행할 작업
    logger.info("🛑 Report Agent Service shutting down...")

app = FastAPI(
    title="Windfarm Report Agent Service",
    description="LangGraph 기반 풍력 발전기 진단 및 위험 분석 보고서 생성 에이전트 서비스",
    version="1.0.0",
    lifespan=lifespan
)

# 🌐 CORS 설정 (프론트엔드 및 Spring Boot Internal API 연동)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 실무 운영 환경에서는 필요 시 특정 도메인으로 제한
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 🩺 AWS ECS Target Group 및 로드밸런서용 Health Check 엔드포인트 (삭제 금지)
@app.get("/api/health", tags=["Health"])
def health_check():
    return {
        "status": "healthy",
        "service": "report-agent-service"
    }

# 📌 API 라우터 등록
# endpoints: /api-internal/reports  (BE 전용 내부 경로 — /api/... 는 BE 게이트웨이용이라 분리)
app.include_router(report_router, prefix="/api-internal/reports", tags=["Reports"])

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)