"""공유 LLM 클라이언트. 모든 agent/critic이 이 인스턴스 하나를 재사용.

config를 먼저 import 하므로 .env(OPENAI_API_KEY) 가 로드된 상태에서 생성된다.

견고성: 요청 타임아웃 + 지수 backoff 재시도(langchain 내장)를 켜 둔다.
  - 일시적 오류(429 rate limit / 5xx / 연결 끊김)는 max_retries 만큼 자동 재시도.
  - 하드 오류(인증 실패·쿼터 소진)는 재시도해도 소용없어 예외로 올라가고,
    service.generate_report 가 이를 잡아 깔끔한 에러 결과로 변환한다.
"""
from langchain_openai import ChatOpenAI

from app.core.config import MODEL, LLM_TIMEOUT, LLM_MAX_RETRIES

llm = ChatOpenAI(
    model=MODEL,
    temperature=0.0,
    timeout=LLM_TIMEOUT,
    max_retries=LLM_MAX_RETRIES,
)
