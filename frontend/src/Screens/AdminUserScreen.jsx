import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import "./UserScreen.css";

function UserScreen() {
  const navigate = useNavigate();

  // 관리자 여부 상태 (현재는 개발용으로 true 설정, 일반 사용자는 false)
  const [isAdmin, setIsAdmin] = useState(true);

  // 편집 모드 상태
  const [isEditing, setIsEditing] = useState(false);

  // 사용자 정보 상태
  const [userInfo, setUserInfo] = useState({
    name: "홍길동",
    employeeId: "12345678",
    mobile: "010-1234-5678",
    email: "admin@company.com",
    department: "IT 운영팀",
  });

  // 입력값 변경 핸들러
  const handleChange = (field, value) => {
    setUserInfo((prev) => ({
      ...prev,
      [field]: value,
    }));
  };

  // 연필(수정) 버튼 클릭 토글
  const handleToggleEdit = () => {
    if (isEditing) {
      alert("정보 수정이 완료되었습니다.");
    }
    setIsEditing(!isEditing);
  };

  // 닫기(X) 버튼 클릭 - 이전 화면으로 이동
  const handleClose = () => {
    navigate(-1);
  };

  // 로그아웃 버튼 클릭
  const handleLogout = () => {
    if (window.confirm("로그아웃 하시겠습니까?")) {
      navigate("/login");
    }
  };

  // 관리자 페이지 진입 버튼 클릭 (사용자 관리 콘솔로 이동)
  const handleAdminConsoleNav = () => {
    navigate("/admin/users");
  };

  return (
    <div className="user-screen-overlay">
      <div className="user-screen-card">
        {/* 상단 헤더: 타이틀 & 우측 아이콘(연필, 닫기) */}
        <div className="card-header">
          <h2>내 정보 관리</h2>
          <div className="header-icons">
            <button
              className={`icon-btn edit-btn ${isEditing ? "active" : ""}`}
              onClick={handleToggleEdit}
              title={isEditing ? "저장" : "수정"}
            >
              ✏️
            </button>
            <button
              className="icon-btn close-btn"
              onClick={handleClose}
              title="닫기"
            >
              ✕
            </button>
          </div>
        </div>

        {/* 프로필 이미지 & 이름 헤더 */}
        <div className="profile-header">
          <div className="avatar-circle">
            <span className="avatar-icon">👤</span>
          </div>
          <div className="profile-name-group">
            <span className="profile-title">
              {isAdmin ? "관리자" : "사용자"} {userInfo.name}
            </span>
            {isAdmin && <span className="admin-badge">Admin</span>}
          </div>
        </div>

        {/* 정보 항목 리스트 */}
        <div className="info-list">
          {/* 이름 */}
          <div className="info-item">
            <span className="info-label">이름</span>
            {isEditing ? (
              <input
                type="text"
                className="info-input"
                value={userInfo.name}
                onChange={(e) => handleChange("name", e.target.value)}
              />
            ) : (
              <span className="info-value">{userInfo.name}</span>
            )}
          </div>

          {/* 사번 */}
          <div className="info-item">
            <span className="info-label">사번</span>
            <span className="info-value read-only">{userInfo.employeeId}</span>
          </div>

          {/* 연락처 */}
          <div className="info-item">
            <span className="info-label">연락처 (Mobile)</span>
            {isEditing ? (
              <input
                type="text"
                className="info-input"
                value={userInfo.mobile}
                onChange={(e) => handleChange("mobile", e.target.value)}
              />
            ) : (
              <span className="info-value">{userInfo.mobile}</span>
            )}
          </div>

          {/* 이메일 */}
          <div className="info-item">
            <span className="info-label">이메일</span>
            {isEditing ? (
              <input
                type="email"
                className="info-input"
                value={userInfo.email}
                onChange={(e) => handleChange("email", e.target.value)}
              />
            ) : (
              <span className="info-value">{userInfo.email}</span>
            )}
          </div>

          {/* 소속 부서 */}
          <div className="info-item">
            <span className="info-label">소속 부서</span>
            {isEditing ? (
              <input
                type="text"
                className="info-input"
                value={userInfo.department}
                onChange={(e) => handleChange("department", e.target.value)}
              />
            ) : (
              <span className="info-value">{userInfo.department}</span>
            )}
          </div>

          {/* 비밀번호 변경 */}
          <div
            className="info-item clickable"
            onClick={() => alert("비밀번호 변경 창으로 이동합니다.")}
          >
            <span className="info-label">비밀번호 변경</span>
            <div className="pw-change-right">
              <span className="info-value masked-pw">********</span>
              <span className="arrow-icon">›</span>
            </div>
          </div>
        </div>

        {/* 하단 액션 버튼 영역 */}
        <div className="action-footer">
          {/* 관리자일 때만 진입 버튼 노출 */}
          {isAdmin && (
            <button
              className="admin-entry-btn"
              onClick={handleAdminConsoleNav}
            >
              관리자 페이지 진입
            </button>
          )}

          <button className="logout-link-btn" onClick={handleLogout}>
            로그아웃
          </button>
        </div>
      </div>
    </div>
  );
}

export default UserScreen;