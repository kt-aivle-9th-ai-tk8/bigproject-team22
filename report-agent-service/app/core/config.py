"""env 기반 공유 설정. 로컬(CSV) ↔ 배포(RDS) 전환을 한 곳에서.

.env 를 여기서 로드하므로, 어떤 모듈이든 config를 import하면 환경변수가 준비된다.
비밀키는 값을 직접 노출하지 않고 os.getenv 로만 참조한다.
"""
import os

from dotenv import load_dotenv

load_dotenv()

# ── 관측성(LangSmith 트레이싱) ──────────────────────────────────────────────
# .env에 LANGSMITH_TRACING=true + LANGSMITH_API_KEY 를 넣으면 langchain/langgraph가
# 자동 계측한다(코드 변경 불필요 — 이 파일이 .env를 먼저 로드하므로 env가 준비됨).
# 주의: 켜면 프롬프트·출력이 LangSmith(smith.langchain.com)로 전송된다 — opt-in.
# 신·구 변수명 모두 지원(LANGSMITH_* 권장, 레거시 LANGCHAIN_* 호환).
TRACING = (
    os.getenv("LANGSMITH_TRACING") or os.getenv("LANGCHAIN_TRACING_V2") or ""
).lower() == "true"
# 트레이스가 묶일 프로젝트명(미지정 시 기본값 주입 → 모든 실행이 한 곳에 모임)
os.environ.setdefault("LANGSMITH_PROJECT", os.getenv("LANGCHAIN_PROJECT", "report-agent"))
LANGSMITH_PROJECT = os.environ["LANGSMITH_PROJECT"]

# 프로젝트 루트 = 이 파일(common/config.py) 기준 상위 디렉토리
ROOT_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DATA_DIR = os.path.join(ROOT_DIR, "data")
OUTPUT_DIR = os.path.join(ROOT_DIR, "outputs")

# LLM
MODEL = os.getenv("REPORT_MODEL", "gpt-4o-mini")

# LLM 견고성 — 외부 API는 간헐적으로 실패(429/5xx/네트워크)하므로 배포엔 필수.
#   TIMEOUT: 요청당 상한(초). 무한 대기 방지.
#   MAX_RETRIES: 일시적 오류에 대한 지수 backoff 재시도 (langchain 내장). 하드 오류(인증/쿼터)는 즉시 실패.
LLM_TIMEOUT = float(os.getenv("REPORT_LLM_TIMEOUT", "60"))
LLM_MAX_RETRIES = int(os.getenv("REPORT_LLM_MAX_RETRIES", "4"))

# (critic 재시도 상한 max_retries 는 config가 아니라 각 report_type이 REGISTRY에서 소유 — 필수.)

# 분석(## 분석) 섹션 생성 여부. on이면 LLM이 '숫자 없는 정성 해석'을 쓰고 critic이 검증.
#   off면 완전 결정론(수치·차트만, LLM/critic 미사용). 콘솔/배치는 off로 비용 0.
WITH_ANALYSIS = os.getenv("REPORT_WITH_ANALYSIS", "true").lower() == "true"

# 데이터 소스: "csv"(로컬) | "rds"(배포). tools.py 가 이 값으로 구현 분기.
DATA_SOURCE = os.getenv("DATA_SOURCE", "csv")
DB_URL = os.getenv("DB_URL", "")
