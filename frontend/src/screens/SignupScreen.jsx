import React, { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";

import { signupApi } from "../api/authApi";
import windmillImg from "../assets/windmill.png";

import "./SignupScreen.css";

function SignupScreen() {
  const navigate = useNavigate();

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

  const [showSuccessModal, setShowSuccessModal] = useState(false);

  const isPasswordValid = useMemo(() => {
    if (!formData.password) {
      return null;
    }

    const hasLetter = /[A-Za-z]/.test(formData.password);
    const hasNumber = /[0-9]/.test(formData.password);
    const hasSpecial = /[^A-Za-z0-9]/.test(formData.password);

    const typeCount = [hasLetter, hasNumber, hasSpecial].filter(Boolean).length;

    if (typeCount >= 3 && formData.password.length >= 8) {
      return true;
    }

    if (typeCount >= 2 && formData.password.length >= 10) {
      return true;
    }

    return false;
  }, [formData.password]);

  const isConfirmValid = useMemo(() => {
    if (!formData.confirmPassword) {
      return null;
    }

    return (
      isPasswordValid === true &&
      formData.password === formData.confirmPassword
    );
  }, [formData.password, formData.confirmPassword, isPasswordValid]);

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

    if (!isPasswordValid) {
      alert("비밀번호 규칙을 확인해 주세요.");
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

  const handleModalClose = () => {
    setShowSuccessModal(false);
    navigate("/login");
  };

  const handleSubmit = async (event) => {
    event.preventDefault();

    if (!validateForm()) {
      return;
    }

    const payload = {
      employee_id: formData.employeeId,
      password: formData.password,
      user_name: formData.name,
      phone: formData.phone,
    };

    console.log("[회원가입 요청 payload]", payload);

    try {
      const responseBody = await signupApi(payload);

      console.log("[회원가입 응답]", responseBody);

      setShowSuccessModal(true);
    } catch (error) {
      console.error("회원가입 실패:", error);
      alert(error.message || "회원가입 처리 중 오류가 발생했습니다.");
    }
  };

  return (
    <div className="signup-container">
      <div className="signup-form-section">
        <div className="signup-wrapper">
          <div className="signup-header">
            <h2>회원가입</h2>

            <button
              type="button"
              className="header-login-btn"
              onClick={() => navigate("/login")}
            >
              로그인
            </button>
          </div>

          <form onSubmit={handleSubmit} className="signup-form">
            <div className="signup-input-group">
              <label>사번</label>
              <input
                type="text"
                name="employeeId"
                value={formData.employeeId}
                onChange={handleChange}
                placeholder="입력"
              />
            </div>

            <div className="signup-input-group">
              <label>비밀번호</label>
              <input
                type="password"
                name="password"
                value={formData.password}
                onChange={handleChange}
                placeholder="입력"
              />

              {isPasswordValid !== null && (
                <div
                  className={`validation-msg ${
                    isPasswordValid ? "valid" : "invalid"
                  }`}
                >
                  {isPasswordValid
                    ? "✅ 안전한 비밀번호입니다."
                    : "❌ 영문/숫자/특수문자 중 2종류 10자리 혹은 3종류 8자리 이상 필요"}
                </div>
              )}
            </div>

            <div className="signup-input-group">
              <label>비밀번호 확인</label>
              <input
                type="password"
                name="confirmPassword"
                value={formData.confirmPassword}
                onChange={handleChange}
                placeholder="입력"
              />

              {isConfirmValid !== null && (
                <div
                  className={`validation-msg ${
                    isConfirmValid ? "valid" : "invalid"
                  }`}
                >
                  {isConfirmValid
                    ? "✅ 비밀번호가 일치합니다."
                    : "❌ 비밀번호가 일치하지 않거나 규칙에 맞지 않습니다."}
                </div>
              )}
            </div>

            <div className="signup-input-group">
              <label>사용자 이름</label>
              <input
                type="text"
                name="name"
                value={formData.name}
                onChange={handleChange}
                placeholder="입력"
              />
            </div>

            <div className="signup-input-group">
              <label>소속 부서</label>
              <input
                type="text"
                name="department"
                value={formData.department}
                onChange={handleChange}
                placeholder="입력"
              />
            </div>

            <div className="signup-input-group">
              <label>연락처</label>
              <input
                type="text"
                name="phone"
                value={formData.phone}
                onChange={handleChange}
                placeholder="입력"
              />
            </div>

            <div className="signup-input-group">
              <label>메일</label>
              <input
                type="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                placeholder="입력"
              />
            </div>

            <div className="terms-group">
              <label className="checkbox-container">
                개인정보 수집 및 이용에 동의합니다.
                <input
                  type="checkbox"
                  name="agreedToTerms"
                  checked={formData.agreedToTerms}
                  onChange={handleChange}
                />
                <span className="checkmark"></span>
              </label>
            </div>

            <button type="submit" className="signup-button">
              가입하기
            </button>
          </form>
        </div>
      </div>

      <div
        className="signup-image-section"
        style={{ backgroundImage: `url(${windmillImg})` }}
      ></div>

      {showSuccessModal && (
        <div className="signup-modal-overlay">
          <div className="signup-modal-window">
            <div className="signup-modal-title">Success!</div>
            <p>회원가입이 완료되었습니다. 관리자의 승인을 기다려주세요.</p>
            <button type="button" onClick={handleModalClose}>
              로그인으로 이동
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default SignupScreen;