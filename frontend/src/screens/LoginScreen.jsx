import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { loginApi } from "../api/authApi";
import "./LoginScreen.css";

function LoginScreen() {
  const navigate = useNavigate();

  const [employee_id, setEmployeeId] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);

  const [modalType, setModalType] = useState(null); // 'success' 또는 'fail'
  const [modalMessage, setModalMessage] = useState("");

  const handleCloseModal = () => {
    const currentType = modalType;
    setModalType(null);
    setModalMessage("");

    if (currentType === "success") {
      navigate("/main");
    }
  };

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

  const handleLogin = async (e) => {
    if (e) e.preventDefault();

    if (!employee_id.trim() || !password.trim()) {
      setModalType("fail");
      setModalMessage("사번과 비밀번호를 모두 입력해주세요.");
      return;
    }

    // 테스트용 하드코딩 계정
    if (employee_id === "123" && password === "123!") {
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