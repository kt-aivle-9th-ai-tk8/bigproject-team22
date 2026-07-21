import React, { useState, useEffect } from "react";
import "./SignupScreen.css";
// assets 폴더 내의 windmill.png 이미지를 올바르게 불러옵니다.
import windmillImg from "../assets/windmill.png";

function SignupScreen() {
  // 입력 필드 상태 관리
  const [formData, setFormData] = useState({
    employeeId: "",
    password: "",
    confirmPassword: "",
    username: "",
    department: "",
    phone: "",
    email: "",
  });

  // 동의 여부 및 모달 상태 관리
  const [agreeTerms, setAgreeTerms] = useState(false);
  const [showSuccessModal, setShowSuccessModal] = useState(false);

  // 실시간 비밀번호 유효성 검사 상태
  const [isPasswordValid, setIsPasswordValid] = useState(null);
  const [isConfirmValid, setIsConfirmValid] = useState(null);

  // 입력 변경 핸들러
  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
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
  const handleSubmit = (e) => {
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
      alert("개인정보 수집 및 이용에 동의해야 가입이 가능합니다.");
      return;
    }

    setShowSuccessModal(true);
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
            <p>회원가입이 완료되었습니다.</p>
            <button onClick={handleModalClose}>로그인으로 이동</button>
          </div>
        </div>
      )}
    </div>
  );
}

export default SignupScreen;