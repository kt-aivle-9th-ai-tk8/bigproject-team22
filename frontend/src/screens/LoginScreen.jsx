import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { loginApi } from "../api/authApi";
import "./LoginScreen.css";

// 약관, 방침, 사이트맵 텍스트 데이터 정의
const POLICY_MODAL_DATA = {
  terms: {
    title: "풍력 발전 통합 관제 시스템 이용약관",
    content: `제1조 (목적)
본 약관은 '화성갈22니까'가 개발 및 운영하는 풍력 발전 통합 관제 및 O&M AI 분석 플랫폼(이하 '시스템')의 이용 조건, 절차 및 운영자와 이용자의 권리·의무 사항을 규정함을 목적으로 합니다.

제2조 (용어의 정의)
1. '시스템'이란 풍력 터빈 SCADA 데이터 모니터링, 드론 기반 블레이드 결함 탐지, BEM 물리 모델 기반 손실 산출 및 자동 보고서 생성을 지원하는 통합 플랫폼을 의미합니다.
2. '이용자'란 사번 및 계정 인가를 받아 시스템에 접속하는 풍력 발전 단지 운영 엔지니어 및 관리자를 의미합니다.

제3조 (계정 관리 및 보안 준수 의무)
1. 이용자는 부여받은 사번과 비밀번호를 성실히 관리해야 하며, 타인에게 양도·대여할 수 없습니다.
2. 이용자는 시스템 내 발전 데이터, 드론 결함 진단 이미지, 분석 보고서 등 핵심 운영 자산을 인가 없이 외부로 유출하거나 제3자에게 제공할 수 없습니다.
3. 비정상적인 접근 시도(세션 탈취, 파라미터 변조 등) 확인 시 즉시 계정이 잠금 처리될 수 있습니다.

제4조 (서비스 제공 및 면책)
1. 시스템이 제공하는 결함 심각도 및 발전 손실 추정치는 엔지니어의 의사결정을 보조하기 위한 참조 지표이며, 최종 정비 승인 및 물리적 조치는 현장 관리자의 판단과 책임하에 이루어집니다.
2. 천재지변, 정전, 클라우드 인프라 긴급 점검 등 불가항력적 사유로 인한 일시적 서비스 중단 시 운영팀은 지체 없이 복구를 진행합니다.`,
  },
  privacy: {
    title: "개인정보처리방침",
    content: `[주식회사 화성갈22니까 개인정보처리방침]

'화성갈22니까'는 「개인정보 보호법」 제30조에 따라 정보주체의 개인정보를 보호하고 이와 관련한 고충을 신속하고 원활하게 처리할 수 있도록 하기 위하여 다음과 같이 개인정보 처리방침을 수립·공개합니다.

제1조 (개인정보의 처리 목적)
회사는 다음의 목적을 위하여 최소한의 개인정보를 처리하며, 목적 이외의 용도로는 활용하지 않습니다.
1. 사용자 인증 및 식별 (사번 기반 로그인, 권한별 차등 인가)
2. 풍력 발전 단지 관제 및 이상 감지 알림 발송
3. AI 기반 3종 보고서 생성 이력 관리 및 작업 로그 추적

제2조 (개인정보의 수집 항목 및 보유 기간)
1. 필수 항목: 사번, 성명, 비밀번호(일방향 암호화), 소속 부서/발전소, 이메일
2. 자동 수집 항목: 접속 IP 주소, 로그인 일시, 작업 이력(Audit Log)
3. 보유 및 이용 기간: 회원 탈퇴 또는 권한 회수 시까지 (단, 보안 감사 로그는 관계 법령에 따라 1년간 보관 후 파기)

제3조 (개인정보의 파기절차 및 방법)
1. 파기절차: 수집 목적이 달성된 개인정보는 내부 방침에 따라 별도 DB로 분리 보관 후 지체 없이 파기합니다.
2. 파기방법: 전자적 파일 형태의 정보는 기록을 재생할 수 없는 기술적 방법을 사용하여 영구 삭제합니다.

제4조 (개인정보의 안전성 확보 조치)
회사는 「개인정보 보호법」 제29조에 따라 다음과 같은 기술적·관리적 보호조치를 적용하고 있습니다.
1. 비밀번호의 일방향 해시(SHA-256 등) 암호화 저장
2. 웹 구간 SSL/TLS 암호화 통신 적용 (HTTPS)
3. 개인정보 취급자 접근 권한의 최소화 및 차등 부여
4. 화면 내 주요 개인정보 마스킹(홍*동, 010-****-1234 등) 표시 제한 조치
5. 5회 이상 로그인 실패 시 계정 잠금 및 세션 관리 적용

제5조 (개인정보 보호책임자 및 고충처리)
- 개인정보 보호책임자: 화성갈22니까 O&M 개발팀
- 문의처: KT북대구지사 8층 (연락처: 1234-1234 / 평일 09:00~18:00)`,
  },
  sitemap: {
    title: "사이트맵 안내",
    content: `[시스템 메뉴 구성]

1. 관제 대시보드
  - 전국 발전소 종합 관제 맵 & 실시간 날씨
  - 호기별 3D 디지털 트윈 시뮬레이션 및 실시간 발전량 추이

2. 블레이드 결함 관리
  - 3D 가상 환경 기반 블레이드 면(전연/후연/압력/흡입) 선택
  - 드론 촬영 이미지 기반 YOLOv11 고해상도 결함 바운딩 박스 확인
  - 부위별 점검 이력 및 심각도 등급 조회

3. 이상 감지 및 보고서 관리
  - SCADA 기반 급성/만성 발전 이상 실시간 감지 알림
  - Multi-Agent 기반 3종(이상/결함/운영) 자동 생성 보고서 조회 및 편집

4. 계정 및 시스템 관리
  - 사번 기반 로그인, 회원가입 승인 대기, 발전소별 사용자 권한 설정`,
  },
};

function LoginScreen() {
  const navigate = useNavigate();

  const [employee_id, setEmployeeId] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);

  // 로그인 성공/실패 알림 모달 상태
  const [modalType, setModalType] = useState(null); // 'success' 또는 'fail'
  const [modalMessage, setModalMessage] = useState("");

  // 하단 이용약관/개인정보처리방침/사이트맵 모달 상태 (null, 'terms', 'privacy', 'sitemap')
  const [policyModalType, setPolicyModalType] = useState(null);

  const handleCloseModal = () => {
    const currentType = modalType;
    setModalType(null);
    setModalMessage("");

    if (currentType === "success") {
      navigate("/main");
    }
  };

  const handleClosePolicyModal = () => {
    setPolicyModalType(null);
  };

  useEffect(() => {
    const handleGlobalKeyDown = (e) => {
      if (e.key === "Enter") {
        if (modalType) {
          e.preventDefault();
          handleCloseModal();
        } else if (policyModalType) {
          e.preventDefault();
          handleClosePolicyModal();
        }
      } else if (e.key === "Escape" && policyModalType) {
        handleClosePolicyModal();
      }
    };

    window.addEventListener("keydown", handleGlobalKeyDown);
    return () => {
      window.removeEventListener("keydown", handleGlobalKeyDown);
    };
  }, [modalType, policyModalType]);

  const handleLogin = async (e) => {
    if (e) e.preventDefault();

    if (!employee_id.trim() || !password.trim()) {
      setModalType("fail");
      setModalMessage("사번과 비밀번호를 모두 입력해주세요.");
      return;
    }

    setLoading(true);

    try {
      const responseBody = await loginApi({
        employee_id,
        password,
      });

      console.log("[로그인 응답]", responseBody);

      if (responseBody.data) {
        localStorage.setItem("userInfo", JSON.stringify(responseBody.data));
      }
      localStorage.setItem("screenMode", "map");
      localStorage.removeItem("selectedPlant");
      localStorage.removeItem("selectedTurbine");

      setModalType("success");
      setModalMessage(responseBody.message || "로그인에 성공했습니다!");
    } catch (err) {
      console.error("로그인 실패:", err);
      setModalType("fail");

      const serverMessage =
        err.response?.data?.message || err.message || "로그인 정보가 올바르지 않습니다.";

      setModalMessage(serverMessage);
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter" && !modalType && !policyModalType) {
      handleLogin(e);
    }
  };

  return (
    <div className="login-container">
      <div className="login-card">
        <div className="login-header">
          <h2>로그인</h2>
          <p>사용자 정보를 입력하세요</p>
        </div>

        <form className="login-form" onSubmit={handleLogin}>
          <div className="input-group">
            <input
              type="text"
              placeholder="사번"
              value={employee_id}
              onChange={(e) => setEmployeeId(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={loading}
            />
          </div>
          <div className="input-group">
            <input
              type="password"
              placeholder="비밀번호"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={loading}
            />
          </div>

          <button
            type="submit"
            className="login-button"
            disabled={loading}
          >
            {loading ? "로그인 중..." : "로그인"}
          </button>
        </form>

        <div className="login-nav">
          <span onClick={() => navigate("/signup")}>회원가입</span>
          <span className="divider">|</span>
          <span onClick={() => alert("인증 코드를 발송합니다.")}>
            아이디 찾기
          </span>
          <span className="divider">|</span>
          <span onClick={() => alert("인증 코드를 발송합니다.")}>
            비밀번호 찾기
          </span>
        </div>

        <footer className="login-footer">
          <div className="company-info">
            <div className="logo-placeholder">
              <span className="logo-icon">🪐</span> 화성갈22니까
            </div>
            <p>(41596) 대구광역시 북구 고성로 141 KT북대구지사</p>
            <p>홈페이지 전산 이용 문의 1234-1234 (평일 09시 - 18시)</p>
          </div>
          <div className="footer-links">
            <span
              className="policy-highlight clickable-link"
              onClick={() => setPolicyModalType("terms")}
            >
              이용약관
            </span>
            <span
              className="policy-highlight clickable-link"
              onClick={() => setPolicyModalType("privacy")}
            >
              개인정보처리방침
            </span>
            <span
              className="clickable-link"
              onClick={() => setPolicyModalType("sitemap")}
            >
              사이트맵
            </span>
          </div>
        </footer>
      </div>

      {/* 로그인 성공/실패 알림 모달 */}
      {modalType && (
        <div className="modal-overlay" onClick={handleCloseModal}>
          <div className="modal-window" onClick={(e) => e.stopPropagation()}>
            {modalType === "success" ? (
              <div className="modal-title success-title">Success!</div>
            ) : (
              <div className="modal-icon">⚠️</div>
            )}
            <div className="modal-body">
              {modalMessage.split("\n").map((line, i) => (
                <p key={i}>{line}</p>
              ))}
            </div>
            <button className="modal-close-button" onClick={handleCloseModal}>
              {modalType === "success" ? "확인" : "뒤로가기"}
            </button>
          </div>
        </div>
      )}

      {/* 이용약관 / 개인정보처리방침 / 사이트맵 모달 */}
      {policyModalType && (
        <div className="modal-overlay" onClick={handleClosePolicyModal}>
          <div
            className="policy-modal-window"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="policy-modal-header">
              <h3>{POLICY_MODAL_DATA[policyModalType]?.title}</h3>
              <button
                className="policy-modal-close-btn"
                onClick={handleClosePolicyModal}
              >
                ✕
              </button>
            </div>
            <div className="policy-modal-body">
              <pre>{POLICY_MODAL_DATA[policyModalType]?.content}</pre>
            </div>
            <div className="policy-modal-footer">
              <button
                className="policy-confirm-btn"
                onClick={handleClosePolicyModal}
              >
                확인
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default LoginScreen;