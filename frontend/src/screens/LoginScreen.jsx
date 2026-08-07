import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { loginApi } from "../api/authApi";
import "./LoginScreen.css";

function LoginScreen() {
  const navigate = useNavigate();

  const [employee_id, setEmployeeId] = useState("");
  const [password, setPassword] = useState("");

  // 보안 및 오류 관련 상태 관리
  const [errorCount, setErrorCount] = useState(0);
  const [isLocked, setIsLocked] = useState(false);
  const [loading, setLoading] = useState(false);

  // 커스텀 알림/모달 상태 관리
  const [modalType, setModalType] = useState(null); // 'success' 또는 'fail'
  const [modalMessage, setModalMessage] = useState("");

  // 1. 모달 닫기 / 엔터키 확정을 처리하는 공통 함수
  const handleCloseModal = () => {
    const currentType = modalType;
    setModalType(null);
    setModalMessage("");

    // 성공 모달인 경우 '확인' 클릭 혹은 '엔터키' 누름 시 바로 메인 화면으로 이동
    if (currentType === "success") {
      navigate("/main");
    }
  };

  // 2. 글로벌 엔터키 감지 이벤트 (모달이 떠 있을 때 엔터를 누르면 handleCloseModal 실행)
  useEffect(() => {
    const handleGlobalKeyDown = (e) => {
      if (e.key === "Enter" && modalType) {
        e.preventDefault();
        handleCloseModal();
      }
    };

    window.addEventListener("keydown", handleGlobalKeyDown);
    return () => {
      window.removeEventListener("keydown", handleGlobalKeyDown);
    };
  }, [modalType]);

  // 3. 로그인 제출 이벤트
  const handleLogin = async (e) => {
    if (e) e.preventDefault();

    if (isLocked) {
      setModalType("fail");
      setModalMessage(
        "비밀번호 5회 오류로 인해 계정이 잠겼습니다. 관리자에게 문의하세요."
      );
      return;
    }

    // 아이디나 비밀번호 중 하나만 비어있는 경우
    if (!employee_id.trim() || !password.trim()) {
      setModalType("fail");
      setModalMessage("사번과 비밀번호를 모두 입력해주세요.");
      return;
    }

    // ⚡ [테스트 계정 우회 처리] (123 / 123!)
    if (employee_id === "123" && password === "123!") {
      setErrorCount(0);
      setModalType("success");
      setModalMessage("로그인에 성공했습니다!");
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

      setErrorCount(0);
      setModalType("success");
      setModalMessage(responseBody.message || "로그인에 성공했습니다!");
    } catch (err) {
      const nextErrorCount = errorCount + 1;
      setErrorCount(nextErrorCount);

      setModalType("fail");

      if (nextErrorCount >= 5) {
        setIsLocked(true);
        setModalMessage(
          "비밀번호 5회 오류로 인해 계정이 잠겼습니다. 관리자에게 문의하세요."
        );
      } else {
        setModalMessage(
          `${err.message || "로그인에 실패했습니다."}\n다시 시도해 주세요. (오류 횟수: ${nextErrorCount}/5)`
        );
      }
    } finally {
      setLoading(false);
    }
  };

  const handleKeyDown = (e) => {
    // 모달이 닫혀 있을 때 폼 입력창에서 엔터를 누르면 로그인 진행
    if (e.key === "Enter" && !modalType) {
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
              disabled={isLocked || loading}
            />
          </div>
          <div className="input-group">
            <input
              type="password"
              placeholder="비밀번호"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              onKeyDown={handleKeyDown}
              disabled={isLocked || loading}
            />
          </div>

          <button
            type="submit"
            className="login-button"
            disabled={isLocked || loading}
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
            <span className="policy-highlight">이용약관</span>
            <span className="policy-highlight">개인정보처리방침</span>
            <span>사이트맵</span>
          </div>
        </footer>
      </div>

      {/* 성공/실패 모달 */}
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