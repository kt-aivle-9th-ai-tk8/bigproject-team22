import React, { useState } from 'react';
import './UserScreen.css';

// 라우터를 사용하신다면 useNavigate를 사용하시고, 
// 여기서는 테스트용으로 props로 화면 전환 함수를 받거나 바로 사용할 수 있게 구성했습니다.
const UserScreen = ({ onNavigateToAdmin }) => {
  // 관리자 여부 상태 (실제 서비스에선 로그인 정보에서 받아옴)
  const [isAdmin, setIsAdmin] = useState(true);

  // 내 정보 더미 데이터
  const [userInfo, setUserInfo] = useState({
    name: '김관리',
    employeeId: '2401000',
    email: 'admin@powerplant.co.kr',
    department: '관제운영팀',
    phone: '010-1234-5678',
  });

  const handleChange = (e) => {
    const { name, value } = e.target;
    setUserInfo({ ...userInfo, [name]: value });
  };

  const handleSave = () => {
    alert('내 정보가 수정되었습니다.');
  };

  return (
    <div className="user-container">
      <div className="user-card">
        
        {/* 상단 헤더 & 관리자 페이지 이동 버튼 */}
        <div className="user-header">
          <h1 className="user-title">내 정보 관리</h1>
          
          {/* 관리자(Admin) 권한이 있는 경우에만 버튼 노출 */}
          {isAdmin && (
            <button 
              className="btn-admin-link"
              onClick={onNavigateToAdmin}
            >
              관리자 페이지 이동
            </button>
          )}
        </div>

        {/* 프로필 요약 */}
        <div className="profile-section">
          <div className="avatar-large">👤</div>
          <div className="profile-info">
            <h2>{userInfo.name} {isAdmin && <span style={{ fontSize: '0.75rem', color: '#2563eb', backgroundColor: '#eff6ff', padding: '0.2rem 0.5rem', borderRadius: '0.25rem', marginLeft: '0.5rem' }}>관리자</span>}</h2>
            <p>사번: {userInfo.employeeId} | {userInfo.department}</p>
          </div>
        </div>

        {/* 정보 수정 폼 */}
        <div className="info-grid">
          <div className="info-group">
            <label>이름</label>
            <input 
              type="text" 
              name="name" 
              value={userInfo.name} 
              onChange={handleChange}
              className="info-input" 
            />
          </div>

          <div className="info-group">
            <label>사번 (수정 불가)</label>
            <input 
              type="text" 
              value={userInfo.employeeId} 
              disabled 
              className="info-input"
              style={{ opacity: 0.7, cursor: 'not-allowed' }}
            />
          </div>

          <div className="info-group">
            <label>이메일</label>
            <input 
              type="email" 
              name="email" 
              value={userInfo.email} 
              onChange={handleChange}
              className="info-input" 
            />
          </div>

          <div className="info-group">
            <label>연락처</label>
            <input 
              type="text" 
              name="phone" 
              value={userInfo.phone} 
              onChange={handleChange}
              className="info-input" 
            />
          </div>
        </div>

        <button className="btn-save" onClick={handleSave}>
          회원정보 수정 저장
        </button>

      </div>
    </div>
  );
};

export default UserScreen;