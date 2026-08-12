import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import './UserScreen.css';

const UserScreen = ({ onClose }) => {
  const navigate = useNavigate();

  // 사용자 정보 상태 관리
  const [userData, setUserData] = useState({
    role: '일반사용자',
    name: '사용자',
    isAdmin: false,
    employeeId: '-',
    phone: '-',
    email: '-',
    department: '-',
  });

  // 에러/알림 모달 상태 관리
  const [modalMessage, setModalMessage] = useState(null);

  // 로컬스토리지 로그인 유저 정보 로드
  useEffect(() => {
    try {
      const storedUser = localStorage.getItem("userInfo");
      if (storedUser) {
        const parsed = JSON.parse(storedUser);
        setUserData({
          role: parsed.role === 'ADMIN' ? '관리자' : (parsed.role || '일반사용자'),
          name: parsed.name || parsed.username || '사용자',
          isAdmin: parsed.role === 'ADMIN' || parsed.isAdmin === true,
          employeeId: parsed.employee_id || parsed.employeeId || '-',
          phone: parsed.phone || parsed.mobile || '-',
          email: parsed.email || '-',
          department: parsed.department || '운영팀',
        });
      }
    } catch (err) {
      console.error("유저 정보 로드 실패:", err);
    }
  }, []);

  // 관리자 페이지 이동 핸들러
  const handleNavigateToAdmin = () => {
    if (!userData.isAdmin) {
      setModalMessage("권한이 없습니다");
      return;
    }
    if (onClose) onClose();
    navigate('/admin/users');
  };

  // 본인 로그아웃 처리 핸들러
  const handleLogout = () => {
    if (window.confirm("로그아웃 하시겠습니까?")) {
      localStorage.removeItem("accessToken");
      localStorage.removeItem("userInfo");
      if (onClose) onClose();
      navigate("/login");
    }
  };

  return (
    <div className="user-screen-overlay">
      <div className="user-card-modal">
        
        {/* 상단 헤더 */}
        <div className="user-modal-header">
          <h2 className="user-modal-title">내 정보 관리</h2>
          <div className="header-icon-group">
            <button className="icon-btn" title="수정">✏️</button>
            <button className="icon-btn" onClick={onClose} title="닫기">✕</button>
          </div>
        </div>

        {/* 프로필 이미지 & 이름 섹션 */}
        <div className="profile-summary">
          <div className="profile-avatar-circle">
            <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </div>
          <div className="profile-name-area">
            <span className="user-role-title">{userData.role} {userData.name}</span>
            {userData.isAdmin && <span className="admin-badge">Admin</span>}
          </div>
        </div>

        {/* 상세 정보 항목 리스트 */}
        <div className="info-list-container">
          <div className="info-row">
            <span className="info-label">이름</span>
            <span className="info-value">{userData.name}</span>
          </div>

          <div className="info-row">
            <span className="info-label">사번</span>
            <span className="info-value">{userData.employeeId}</span>
          </div>

          <div className="info-row">
            <span className="info-label">연락처 (Mobile)</span>
            <span className="info-value">{userData.phone}</span>
          </div>

          <div className="info-row">
            <span className="info-label">이메일</span>
            <span className="info-value">{userData.email}</span>
          </div>

          <div className="info-row">
            <span className="info-label">소속 부서</span>
            <span className="info-value">{userData.department}</span>
          </div>

          <div className="info-row password-change-row">
            <span className="info-label">비밀번호 변경</span>
            <div className="password-val-group">
              <span className="info-value">********</span>
              <span className="chevron-icon">❯</span>
            </div>
          </div>
        </div>

        {/* 하단 액션 버튼 영역 */}
        <div className="action-area">
          <button className="btn-admin-entry" onClick={handleNavigateToAdmin}>
            관리자 페이지 진입
          </button>
          <button className="btn-logout-text" onClick={handleLogout}>
            로그아웃
          </button>
        </div>

      </div>

      {/* 권한 제한 안내 모달 */}
      {modalMessage && (
        <div className="user-alert-modal-overlay">
          <div className="user-alert-modal-content">
            <p className="user-alert-message">{modalMessage}</p>
            <button className="user-alert-btn" onClick={() => setModalMessage(null)}>
              확인
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default UserScreen;