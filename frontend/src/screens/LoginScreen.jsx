import React, { useState } from "react";
import "./LoginScreen.css";

function LoginScreen() {
  const [employeeId, setEmployeeId] = useState("");
  const [password, setPassword] = useState("");
  
  // 보안 및 오류 관련 상태 관리
  const [errorCount, setErrorCount] = useState(0);
  const [isLocked, setIsLocked] = useState(false);

  // 커스텀 알림/모달 상태 관리
  const [modalType, setModalType] = useState(null); // 'success' 또는 'fail'
  const [modalMessage, setModalMessage] = useState("");

  const handleLogin = (e) => {
    if (e) e.preventDefault();

    if (isLocked) {
      setModalType("fail");
      setModalMessage("비밀번호 5회 오류로 인해 계정이 잠겼습니다. 관리자에게 문의하세요.");
      return;
    }

    if (!employeeId.trim() || !password.trim()) {
      setModalType("fail");
      setModalMessage("사번과 비밀번호를 모두 입력해주세요.");
      return;
    }

    // 테스트용 임시 로그인 정보 검증
    const isLoginSuccess = employeeId === "123" && password === "123!";

    if (isLoginSuccess) {
      setErrorCount(0);
      setModalType("success");
      setModalMessage("로그인에 성공했습니다!");
    } else {
      const nextErrorCount = errorCount + 1;
      setErrorCount(nextErrorCount);

      setModalType("fail");
      if (nextErrorCount >= 5) {
        setIsLocked(true);
        setModalMessage("비밀번호 5회 오류로 인해 계정이 잠겼습니다. 관리자에게 문의하세요.");
      } else {
        setModalMessage(`로그인에 실패했습니다.\n다시 시도해 주세요. (오류 횟수: ${nextErrorCount}/5)`);
      }
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter") {
      handleLogin();
    }
  };

  const handleCloseModal = () => {
    const currentType = modalType;
    setModalType(null);
    setModalMessage("");
    
    // 성공 모달을 닫았을 때만 메인 페이지로 이동
    if (currentType === "success") {
      window.location.href = "/Main";
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
              value={employeeId}
              onChange={(e) => setEmployeeId(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={isLocked}
            />
          </div>
          <div className="input-group">
            <input
              type="password"
              placeholder="비밀번호"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={isLocked}
            />
          </div>

          <button type="submit" className="login-button" disabled={isLocked}>
            로그인
          </button>
        </form>

        <div className="login-nav">
          <span onClick={() => window.location.href = "/signup"}>회원가입</span>
          <span className="divider">|</span>
          <span onClick={() => alert("인증 코드를 발송합니다.")}>아이디 찾기</span>
          <span className="divider">|</span>
          <span onClick={() => alert("인증 코드를 발송합니다.")}>비밀번호 찾기</span>
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
            <span>이용약관</span>
            <span className="policy-highlight">개인정보처리방침</span>
            <span>사이트맵</span>
          </div>
        </footer>
      </div>

      {/* 피그마 시안 반영 커스텀 결과 모달 (성공/실패 공용) */}
      {modalType && (
        <div className="modal-overlay">
          <div className="modal-window">
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
    </div>
  );
}

export default LoginScreen;