import React, { useState, useEffect, useRef } from "react";
import axios from "axios";
import "./SignupScreen.css";

function SignupScreen() {
  const [formData, setFormData] = useState({
    employeeId: "",
    password: "",
    confirmPassword: "",
    username: "",
    department: "",
    phone: "",
    email: "",
  });

  const [agreeTerms, setAgreeTerms] = useState(false);
  const [showSuccessModal, setShowSuccessModal] = useState(false);

  // 약관 UI 제어 상태
  const [isTermsOpen, setIsTermsOpen] = useState(false);
  const [isScrolledToBottom, setIsScrolledToBottom] = useState(false);
  const termsBoxRef = useRef(null);

  const [isPasswordValid, setIsPasswordValid] = useState(null);
  const [isConfirmValid, setIsConfirmValid] = useState(null);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
  };

  // 약관 스크롤 감지
  const handleScroll = () => {
    if (termsBoxRef.current) {
      const { scrollTop, scrollHeight, clientHeight } = termsBoxRef.current;
      if (scrollTop + clientHeight >= scrollHeight - 5) {
        setIsScrolledToBottom(true);
      }
    }
  };

  // 비밀번호 복잡도 실시간 검증
  useEffect(() => {
    const pwd = formData.password;
    if (!pwd) {
      setIsPasswordValid(null);
      return;
    }

    const hasLetter = /[a-zA-Z]/.test(pwd) ? 1 : 0;
    const hasNumber = /[0-9]/.test(pwd) ? 1 : 0;
    const hasSpecial = /[{}[\]/?.,;:|)*~`!^\-_+<>@#\$%&\\\=\(\'\"]/.test(pwd) ? 1 : 0;
    const typesCount = hasLetter + hasNumber + hasSpecial;

    const condition1 = typesCount >= 2 && pwd.length >= 10;
    const condition2 = typesCount >= 3 && pwd.length >= 8;

    setIsPasswordValid(condition1 || condition2);
  }, [formData.password]);

  // 비밀번호 확인 일치 검증
  useEffect(() => {
    const { password, confirmPassword } = formData;
    if (!confirmPassword) {
      setIsConfirmValid(null);
      return;
    }
    setIsConfirmValid(password === confirmPassword && isPasswordValid);
  }, [formData.password, formData.confirmPassword, isPasswordValid]);

  // 회원가입 제출 핸들러
  const handleSubmit = async (e) => {
    e.preventDefault();

    const { employeeId, password, confirmPassword, username, department, phone, email } = formData;

    if (!employeeId || !password || !confirmPassword || !username || !department || !phone || !email) {
      alert("모든 정보를 입력해주세요.");
      return;
    }

    if (!isPasswordValid || !isConfirmValid) {
      alert("비밀번호 규칙을 확인해 주세요.");
      return;
    }

    if (!agreeTerms) {
      alert("개인정보 수집 및 이용 동의가 완료되어야 합니다.");
      return;
    }

    const payload = {
      employee_id: employeeId,
      password: password,
      user_name: username,
      phone: phone,
    };

    try {
      const response = await axios.post("/api/users", payload);
      if (response.status === 200 || response.status === 201) {
        setShowSuccessModal(true);
      }
    } catch (error) {
      console.error("회원가입 요청 에러:", error);
      alert(error.response?.data?.message || "회원가입 중 오류가 발생했습니다.");
    }
  };

  const handleModalClose = () => {
    setShowSuccessModal(false);
    window.location.href = "/login";
  };

  return (
    <div className="signup-page-container">
      {/* 중앙 하얀 둥근 카드 박스 */}
      <div className="signup-card">
        <div className="signup-header">
          <h2>회원가입</h2>
          <p className="signup-subtitle">사용자 정보를 입력하세요</p>
        </div>

        <form onSubmit={handleSubmit} className="signup-form">
          <div className="signup-input-group">
            <input
              type="text"
              name="employeeId"
              value={formData.employeeId}
              onChange={handleChange}
              placeholder="사번"
            />
          </div>

          <div className="signup-input-group">
            <input
              type="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              placeholder="비밀번호"
            />
            {isPasswordValid !== null && (
              <div className={`validation-msg ${isPasswordValid ? "valid" : "invalid"}`}>
                {isPasswordValid ? "✅ 안전한 비밀번호입니다." : "❌ 영문/숫자/특수문자 2종 10자 이상 또는 3종 8자 이상"}
              </div>
            )}
          </div>

          <div className="signup-input-group">
            <input
              type="password"
              name="confirmPassword"
              value={formData.confirmPassword}
              onChange={handleChange}
              placeholder="비밀번호 확인"
            />
            {isConfirmValid !== null && (
              <div className={`validation-msg ${isConfirmValid ? "valid" : "invalid"}`}>
                {isConfirmValid ? "✅ 비밀번호가 일치합니다." : "❌ 비밀번호 일치하지 않습니다."}
              </div>
            )}
          </div>

          <div className="signup-input-group">
            <input
              type="text"
              name="username"
              value={formData.username}
              onChange={handleChange}
              placeholder="사용자 이름"
            />
          </div>

          <div className="signup-input-group">
            <input
              type="text"
              name="department"
              value={formData.department}
              onChange={handleChange}
              placeholder="소속 부서"
            />
          </div>

          <div className="signup-input-group">
            <input
              type="text"
              name="phone"
              value={formData.phone}
              onChange={handleChange}
              placeholder="연락처 (예: 01012345678)"
            />
          </div>

          <div className="signup-input-group">
            <input
              type="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              placeholder="이메일"
            />
          </div>

          {/* 규제 가이드 준수: 필수 약관 스크롤 동의 영역 */}
          <div className="terms-accordion-container">
            <div
              className="terms-header"
              onClick={() => setIsTermsOpen((prev) => !prev)}
            >
              <span>[필수] 개인정보 수집 및 이용 동의</span>
              <span className="arrow">{isTermsOpen ? "▲" : "▼"}</span>
            </div>

            {isTermsOpen && (
              <div className="terms-content">
                <div
                  className="terms-scroll-box"
                  ref={termsBoxRef}
                  onScroll={handleScroll}
                >
                  <p className="terms-title">개인정보 수집 및 이용 약관</p>
                  <table className="terms-table">
                    <thead>
                      <tr>
                        <th>목적</th>
                        <th>항목</th>
                        <th>보유기간</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td>회원가입 및 서비스 제공</td>
                        <td>사번, 비밀번호, 성명, 부서, 연락처, 이메일</td>
                        <td>회원 탈퇴 시까지</td>
                      </tr>
                    </tbody>
                  </table>
                  <p className="terms-text">
                    제1조 (목적) 본 약관은 서비스 제공을 위해 최소한의 개인정보를 수집 및 이용하는 것을 목적을 합니다.<br />
                    제2조 (동의 거부) 동의를 거부할 권리가 있으나, 거부 시 회원가입이 제한됩니다.<br />
                    (약관을 모두 확인하셨습니다.)
                  </p>
                </div>
                {!isScrolledToBottom && (
                  <div className="terms-scroll-notice">
                    ⚠️ 약관을 끝까지 내려야 동의 체크가 가능합니다.
                  </div>
                )}
              </div>
            )}

            <div className="terms-checkbox-wrapper">
              <label className={`checkbox-container ${!isScrolledToBottom ? "disabled" : ""}`}>
                개인정보 수집 및 이용에 동의합니다.
                <input
                  type="checkbox"
                  checked={agreeTerms}
                  disabled={!isScrolledToBottom}
                  onChange={(e) => setAgreeTerms(e.target.checked)}
                />
                <span className="checkmark"></span>
              </label>
            </div>
          </div>

          <button type="submit" className="signup-button">
            가입하기
          </button>
        </form>

        <div className="signup-footer-links">
          <span>이미 계정이 있으신가요?</span>
          <button
            type="button"
            className="link-btn"
            onClick={() => window.location.href = "/login"}
          >
            로그인
          </button>
        </div>
      </div>

      {/* 성공 모달 */}
      {showSuccessModal && (
        <div className="signup-modal-overlay">
          <div className="signup-modal-window">
            <div className="signup-modal-title">Success!</div>
            <p>회원가입이 완료되었습니다. 관리자의 승인을 기다려주세요.</p>
            <button onClick={handleModalClose}>로그인으로 이동</button>
          </div>
        </div>
      )}
    </div>
  );
}

export default SignupScreen;