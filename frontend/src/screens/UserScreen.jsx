import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import "./UserScreen.css";

const UserScreen = ({ onClose }) => {
  const navigate = useNavigate();

  // 사용자 정보 상태 관리
  const [userData, setUserData] = useState({
    role: "일반사용자",
    name: "사용자",
    isAdmin: false,
    employeeId: "-",
    phone: "-",
    email: "-",
    department: "-",
  });

  // 수정 모드 상태 관리
  const [isEditing, setIsEditing] = useState(false);
  const [editFormData, setEditFormData] = useState({
    name: "",
    phone: "",
    email: "",
    department: "",
  });

  // 비밀번호 변경 모달 상태
  const [isPasswordModalOpen, setIsPasswordModalOpen] = useState(false);
  const [passwordData, setPasswordData] = useState({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });

  // 에러/알림 모달 상태 관리
  const [modalMessage, setModalMessage] = useState(null);

  // 로컬스토리지 로그인 유저 정보 로드
  useEffect(() => {
    try {
      const storedUser = localStorage.getItem("userInfo");
      if (storedUser) {
        const parsed = JSON.parse(storedUser);
        const userInfo = {
          role: parsed.role === "ADMIN" ? "관리자" : (parsed.role || "일반사용자"),
          name: parsed.name || parsed.username || "사용자",
          isAdmin: parsed.role === "ADMIN" || parsed.isAdmin === true,
          employeeId: parsed.employee_id || parsed.employeeId || "-",
          phone: parsed.phone || parsed.mobile || "-",
          email: parsed.email || "-",
          department: parsed.department || "운영팀",
        };
        setUserData(userInfo);
        setEditFormData({
          name: userInfo.name,
          phone: userInfo.phone,
          email: userInfo.email,
          department: userInfo.department,
        });
      }
    } catch (err) {
      console.error("유저 정보 로드 실패:", err);
    }
  }, []);

  // 닫기 핸들러 (props가 없으면 뒤로가기)
  const handleCloseModal = () => {
    if (onClose) {
      onClose();
    } else {
      navigate(-1);
    }
  };

  // 수정 모드 토글
  const handleToggleEdit = () => {
    if (!isEditing) {
      setEditFormData({
        name: userData.name,
        phone: userData.phone,
        email: userData.email,
        department: userData.department,
      });
    }
    setIsEditing(!isEditing);
  };

  // 수정 입력값 변경 핸들러
  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setEditFormData((prev) => ({ ...prev, [name]: value }));
  };

  // 수정 정보 저장 핸들러
  const handleSaveProfile = () => {
    const updatedUser = {
      ...userData,
      name: editFormData.name,
      phone: editFormData.phone,
      email: editFormData.email,
      department: editFormData.department,
    };

    setUserData(updatedUser);
    setIsEditing(false);

    try {
      const storedUser = localStorage.getItem("userInfo");
      const parsed = storedUser ? JSON.parse(storedUser) : {};
      localStorage.setItem(
        "userInfo",
        JSON.stringify({
          ...parsed,
          name: editFormData.name,
          phone: editFormData.phone,
          email: editFormData.email,
          department: editFormData.department,
        })
      );
      setModalMessage("회원 정보가 성공적으로 수정되었습니다.");
    } catch (err) {
      console.error("로컬스토리지 저장 실패:", err);
    }
  };

  // 비밀번호 변경 입력 핸들러
  const handlePasswordInputChange = (e) => {
    const { name, value } = e.target;
    setPasswordData((prev) => ({ ...prev, [name]: value }));
  };

  // 비밀번호 변경 저장
  const handleSavePassword = () => {
    if (!passwordData.currentPassword || !passwordData.newPassword) {
      alert("모든 필드를 입력해주세요.");
      return;
    }
    if (passwordData.newPassword !== passwordData.confirmPassword) {
      alert("신규 비밀번호가 일치하지 않습니다.");
      return;
    }

    alert("비밀번호가 성공적으로 변경되었습니다.");
    setPasswordData({
      currentPassword: "",
      newPassword: "",
      confirmPassword: "",
    });
    setIsPasswordModalOpen(false);
  };

  // 관리자 페이지 이동 핸들러
  const handleNavigateToAdmin = () => {
    if (!userData.isAdmin) {
      setModalMessage("관리자 권한이 없습니다.");
      return;
    }
    if (onClose) onClose();
    navigate("/admin/users");
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
            <button
              className={`icon-btn ${isEditing ? "active" : ""}`}
              onClick={handleToggleEdit}
              title={isEditing ? "수정 취소" : "정보 수정"}
            >
              ✏️
            </button>
            <button className="icon-btn" onClick={handleCloseModal} title="닫기">
              ✕
            </button>
          </div>
        </div>

        {/* 프로필 이미지 & 이름 섹션 */}
        <div className="profile-summary">
          <div className="profile-avatar-circle">
            <svg
              width="36"
              height="36"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
            >
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </div>
          <div className="profile-name-area">
            <span className="user-role-title">
              {userData.role} {userData.name}
            </span>
            {userData.isAdmin && <span className="admin-badge">Admin</span>}
          </div>
        </div>

        {/* 상세 정보 항목 리스트 */}
        <div className="info-list-container">
          <div className="info-row">
            <span className="info-label">이름</span>
            {isEditing ? (
              <input
                type="text"
                name="name"
                className="edit-input-field"
                value={editFormData.name}
                onChange={handleInputChange}
              />
            ) : (
              <span className="info-value">{userData.name}</span>
            )}
          </div>

          <div className="info-row">
            <span className="info-label">사번</span>
            <span className="info-value readonly-text">{userData.employeeId}</span>
          </div>

          <div className="info-row">
            <span className="info-label">연락처 (Mobile)</span>
            {isEditing ? (
              <input
                type="text"
                name="phone"
                className="edit-input-field"
                value={editFormData.phone}
                onChange={handleInputChange}
                placeholder="010-0000-0000"
              />
            ) : (
              <span className="info-value">{userData.phone}</span>
            )}
          </div>

          <div className="info-row">
            <span className="info-label">이메일</span>
            {isEditing ? (
              <input
                type="email"
                name="email"
                className="edit-input-field"
                value={editFormData.email}
                onChange={handleInputChange}
                placeholder="user@example.com"
              />
            ) : (
              <span className="info-value">{userData.email}</span>
            )}
          </div>

          <div className="info-row">
            <span className="info-label">소속 부서</span>
            {isEditing ? (
              <input
                type="text"
                name="department"
                className="edit-input-field"
                value={editFormData.department}
                onChange={handleInputChange}
              />
            ) : (
              <span className="info-value">{userData.department}</span>
            )}
          </div>

          <div
            className="info-row password-change-row"
            onClick={() => setIsPasswordModalOpen(true)}
            style={{ cursor: "pointer" }}
          >
            <span className="info-label">비밀번호 변경</span>
            <div className="password-val-group">
              <span className="info-value">********</span>
              <span className="chevron-icon">❯</span>
            </div>
          </div>
        </div>

        {/* 수정 모드 전용 저장 버튼 */}
        {isEditing && (
          <div className="edit-action-box">
            <button className="btn-save-profile" onClick={handleSaveProfile}>
              수정 완료 저장
            </button>
          </div>
        )}

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

      {/* 비밀번호 변경 서브 모달 */}
      {isPasswordModalOpen && (
        <div className="user-alert-modal-overlay">
          <div className="user-alert-modal-content password-modal">
            <h3>비밀번호 변경</h3>
            <div className="password-input-group">
              <input
                type="password"
                name="currentPassword"
                placeholder="현재 비밀번호"
                value={passwordData.currentPassword}
                onChange={handlePasswordInputChange}
              />
              <input
                type="password"
                name="newPassword"
                placeholder="신규 비밀번호"
                value={passwordData.newPassword}
                onChange={handlePasswordInputChange}
              />
              <input
                type="password"
                name="confirmPassword"
                placeholder="신규 비밀번호 확인"
                value={passwordData.confirmPassword}
                onChange={handlePasswordInputChange}
              />
            </div>
            <div className="modal-btn-group">
              <button
                className="btn-modal-cancel"
                onClick={() => setIsPasswordModalOpen(false)}
              >
                취소
              </button>
              <button className="btn-modal-confirm" onClick={handleSavePassword}>
                변경
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 권한 제한 / 알림 모달 */}
      {modalMessage && (
        <div className="user-alert-modal-overlay">
          <div className="user-alert-modal-content">
            <p className="user-alert-message">{modalMessage}</p>
            <button
              className="user-alert-btn"
              onClick={() => setModalMessage(null)}
            >
              확인
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default UserScreen;