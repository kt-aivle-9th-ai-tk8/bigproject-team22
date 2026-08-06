import React, { useState, useEffect, useRef } from "react";
import axios from "axios";
import "./SignupScreen.css";
import windmillImg from "../assets/windmill.png";

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

  // 약관 스크롤 감지 핸들러
  const handleScroll = () => {
    if (termsBoxRef.current) {
      const { scrollTop, scrollHeight, clientHeight } = termsBoxRef.current;
      // 스크롤이 끝까지 내려왔는지 검증 (오차범위 5px 허용)
      if (scrollTop + clientHeight >= scrollHeight - 5) {
        setIsScrolledToBottom(true);
      }
    }
  };

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

  useEffect(() => {
    const { password, confirmPassword } = formData;
    if (!confirmPassword) {
      setIsConfirmValid(null);
      return;
    }
    setIsConfirmValid(password === confirmPassword && isPasswordValid);
  }, [formData.password, formData.confirmPassword, isPasswordValid]);

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
      alert("개인정보 수집 및 이용 동의(약관 최하단까지 확인 필요)가 완료되어야 합니다.");
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
    <div className="signup-container">
      <div className="signup-form-section">
        <div className="signup-wrapper">
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

            {/* 규제 가이드 준수: 이용약관 아코디언 및 스크롤 감지 동의 박스 */}
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
                          <th>수집/이용 목적</th>
                          <th>수집 항목</th>
                          <th>보유 및 이용 기간</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr>
                          <td>회원가입, 본인 확인 및 서비스 제공·유지</td>
                          <td>사번, 비밀번호, 성명, 부서, 연락처, 이메일</td>
                          <td>회원 탈퇴 시 또는 서비스 종료 시까지</td>
                        </tr>
                      </tbody>
                    </table>
                    <p className="terms-text">
                      제1조 (목적) 본 약관은 서비스 제공을 위해 최소한의 개인정보를 수집 및 이용하는 것을 목적을 합니다.<br />
                      제2조 (동의 거부 권리) 귀하는 개인정보 수집 및 이용에 대한 동의를 거부할 권리가 있으나, 거부 시 회원가입 및 서비스 이용이 제한됩니다.<br />
                      제3조 (안전성 확보 조치) 수집된 개인정보는 암호화하여 안전하게 관리됩니다.<br />
                      (약관의 끝입니다. 상기 내용을 모두 확인하셨습니다.)
                    </p>
                  </div>
                  {!isScrolledToBottom && (
                    <div className="terms-scroll-notice">
                      ⚠️ 약관을 끝까지 내려서 읽으셔야 체크박스가 활성화됩니다.
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
            <button onClick={handleModalClose}>로그인으로 이동</button>
          </div>
        </div>
      )}
    </div>
  );
}

export default SignupScreen;