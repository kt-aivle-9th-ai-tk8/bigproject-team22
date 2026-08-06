import React, { useState } from 'react';
import axios from 'axios';
import './SignupScreen.css';

function SignupScreen() {
  const [formData, setFormData] = useState({
    employeeId: '',
    password: '',
    confirmPassword: '',
    name: '',
    department: '',
    phone: '',
    email: '',
    agreedToTerms: false,
  });

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
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

  const handleSubmit = async (e) => {
    e.preventDefault();

    if (!validateForm()) return;

    // ★ API 명세서 param 기준 Key 값 매핑
    const payload = {
      employee_id: formData.employeeId,
      password: formData.password,
      user_name: formData.name,
      phone: formData.phone,
    };

    try {
      // ★ API 명세서 URL 적용: /api/users
      const response = await axios.post('/api/users', payload);

      if (response.status === 200 || response.status === 201) {
        alert("회원가입이 완료되었습니다!");
      }
    } catch (error) {
      console.error("회원가입 실패:", error);
      alert(error.response?.data?.message || "회원가입 처리 중 오류가 발생했습니다.");
    }
  };

  return (
    <div className="signup-container">
      <form onSubmit={handleSubmit} className="signup-form">
        <h2>회원가입</h2>

        <div className="input-group">
          <label>사번</label>
          <input
            type="text"
            name="employeeId"
            value={formData.employeeId}
            onChange={handleChange}
            placeholder="입력"
          />
        </div>

        <div className="input-group">
          <label>비밀번호</label>
          <input
            type="password"
            name="password"
            value={formData.password}
            onChange={handleChange}
            placeholder="입력"
          />
        </div>

        <div className="input-group">
          <label>비밀번호 확인</label>
          <input
            type="password"
            name="confirmPassword"
            value={formData.confirmPassword}
            onChange={handleChange}
            placeholder="입력"
          />
        </div>

        <div className="input-group">
          <label>사용자 이름</label>
          <input
            type="text"
            name="name"
            value={formData.name}
            onChange={handleChange}
            placeholder="입력"
          />
        </div>

        <div className="input-group">
          <label>소속 부서</label>
          <input
            type="text"
            name="department"
            value={formData.department}
            onChange={handleChange}
            placeholder="입력"
          />
        </div>

        <div className="input-group">
          <label>연락처</label>
          <input
            type="text"
            name="phone"
            value={formData.phone}
            onChange={handleChange}
            placeholder="입력"
          />
        </div>

        <div className="input-group">
          <label>메일</label>
          <input
            type="email"
            name="email"
            value={formData.email}
            onChange={handleChange}
            placeholder="입력"
          />
        </div>

        <div className="checkbox-group">
          <label>
            개인정보 수집 및 이용에 동의합니다.
            <input
              type="checkbox"
              name="agreedToTerms"
              checked={formData.agreedToTerms}
              onChange={handleChange}
            />
          </label>
        </div>

        <button type="submit" className="submit-btn">가입하기</button>
      </form>
    </div>
  );
}

export default SignupScreen;