import React, { useState, useEffect } from "react";
import axios from "axios";
import "./SignupScreen.css";
import windmillImg from "../assets/windmill.png";

function SignupScreen() {
  // 입력 필드 상태 관리
  const [formData, setFormData] = useState({
    employeeId: "",
    password: "",
    confirmPassword: "",
    name: "",
    department: "",
    phone: "",
    email: "",
    agreedToTerms: false,
  });

  const handleChange = (event) => {
    const { name, value, type, checked } = event.target;

    setFormData((prev) => ({
      ...prev,
      [name]: type === "checkbox" ? checked : value,
    }));
  };

  const validateForm = () => {
    if (!formData.employeeId.trim()) {
      alert("사번을 입력해 주세요.");
      return false;
    }

    if (!formData.password) {
      alert("비밀번호를 입력해 주세요.");
      return false;
    }

    if (formData.password !== formData.confirmPassword) {
      alert("비밀번호가 일치하지 않습니다.");
      return false;
    }

    if (!formData.name.trim()) {
      alert("사용자 이름을 입력해 주세요.");
      return false;
    }

    if (!formData.agreedToTerms) {
      alert("개인정보 수집 및 이용 동의가 필요합니다.");
      return false;
    }

    return true;
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (!validateForm()) {
      return;
    }

    const payload = {
      employee_id: employeeId,
      password: password,
      user_name: username,
      phone: phone,
      // 백엔드 명세 추가 필드가 필요할 경우 이곳에 추가
    };

    console.log("[회원가입 요청 payload]", payload);

    try {
      const responseBody = await signupApi(payload);

      console.log("[회원가입 응답]", responseBody);

      alert(responseBody?.message || "회원가입이 완료되었습니다!");
    } catch (error) {
      console.error("회원가입 실패:", error);
      alert(error.message || "회원가입 처리 중 오류가 발생했습니다.");
    }
  };

  // 로그인 페이지로 이동
  const handleModalClose = () => {
    setShowSuccessModal(false);
    window.location.href = "/login";
  };

  return (
    <div className="signup-container">
      {/* 왼쪽 입력 폼 영역 */}
      <div className="signup-form-section">
        <div className="signup-wrapper">
          
          {/* 헤더: 회원가입 - 공백 - 희미한 로그인 버튼 */}
          <div className="signup-header">
            <h2>회원가입</h2>
            <button 
              type="button" 
              className="header-login-btn"
              onClick={() => window.location.href = "/login"}
            >
              로그인
            </button>
          </div>
          
          <form onSubmit={handleSubmit} className="signup-form">
            <div className="signup-input-group">
              <label>사번</label>
              <input type="text" name="employeeId" value={formData.employeeId} onChange={handleChange} placeholder="입력" />
            </div>

            <div className="signup-input-group">
              <label>비밀번호</label>
              <input type="password" name="password" value={formData.password} onChange={handleChange} placeholder="입력" />
              {isPasswordValid !== null && (
                <div className={`validation-msg ${isPasswordValid ? "valid" : "invalid"}`}>
                  {isPasswordValid ? "✅ 안전한 비밀번호입니다." : "❌ 영문/숫자/특수문자 중 2종류 10자리 혹은 3종류 8자리 이상 필요"}
                </div>
              )}
            </div>

            <div className="signup-input-group">
              <label>비밀번호 확인</label>
              <input type="password" name="confirmPassword" value={formData.confirmPassword} onChange={handleChange} placeholder="입력" />
              {isConfirmValid !== null && (
                <div className={`validation-msg ${isConfirmValid ? "valid" : "invalid"}`}>
                  {isConfirmValid ? "✅ 비밀번호가 일치합니다." : "❌ 비밀번호가 일치하지 않거나 규칙에 맞지 않습니다."}
                </div>
              )}
            </div>

            <div className="signup-input-group">
              <label>사용자 이름</label>
              <input type="text" name="username" value={formData.username} onChange={handleChange} placeholder="입력" />
            </div>

            <div className="signup-input-group">
              <label>소속 부서</label>
              <input type="text" name="department" value={formData.department} onChange={handleChange} placeholder="입력" />
            </div>

            <div className="signup-input-group">
              <label>연락처</label>
              <input type="text" name="phone" value={formData.phone} onChange={handleChange} placeholder="입력" />
            </div>

            <div className="signup-input-group">
              <label>메일</label>
              <input type="email" name="email" value={formData.email} onChange={handleChange} placeholder="입력" />
            </div>

            <div className="terms-group">
              <label className="checkbox-container">
                개인정보 수집 및 이용에 동의합니다.
                <input type="checkbox" checked={agreeTerms} onChange={(e) => setAgreeTerms(e.target.checked)} />
                <span className="checkmark"></span>
              </label>
            </div>

            <button type="submit" className="signup-button">
              가입하기
            </button>
          </form>
        </div>
      </div>

      {/* 오른쪽 이미지 섹션 (windmill.png 직접 연결) */}
      <div 
        className="signup-image-section"
        style={{ backgroundImage: `url(${windmillImg})` }}
      ></div>

      {/* 가입 완료 커스텀 모달 */}
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