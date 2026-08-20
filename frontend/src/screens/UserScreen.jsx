import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { fetchMyPage } from "../api/userApi";
import "./UserScreen.css";

const formatPhoneNumber = (phone) => {
  if (!phone || phone === "-") {
    return "-";
  }

  if (phone.includes("-")) {
    return phone;
  }

  if (phone.length === 11) {
    return `${phone.slice(0, 3)}-${phone.slice(3, 7)}-${phone.slice(7)}`;
  }

  return phone;
};

const UserScreen = ({ onClose }) => {
  const navigate = useNavigate();

  // 사용자 정보 상태 관리
  const [userData, setUserData] = useState({
    role: "사용자",
    name: "사용자",
    isAdmin: false,
    employeeId: "-",
    phone: "-",
    department: "-",
  });

  // 에러/알림 모달 상태 관리
  const [modalMessage, setModalMessage] = useState(null);

  // 로그인 유저 정보 로드
  useEffect(() => {
    let isMounted = true;

    const loadMyPage = async () => {
      try {
        const responseBody =
          await fetchMyPage();

        const data =
          responseBody?.data ??
          responseBody;

        if (!isMounted) {
          return;
        }

        setUserData({
          role:
            data.role === "ADMIN"
              ? "관리자"
              : "사용자",

          name:
            data.user_name ||
            "사용자",

          isAdmin:
            data.role === "ADMIN",

          employeeId:
            data.employee_id || "-",

          phone:
            data.phone || "-",

          department:
            data.department || "-",
        });
      } catch (error) {
        if (!isMounted) {
          return;
        }

        setModalMessage(
          error.message ||
            "사용자 정보를 불러오지 못했습니다."
        );
      }
    };

    loadMyPage();

    return () => {
      isMounted = false;
    };
  }, []);

  // 닫기 핸들러 (props가 없으면 뒤로가기)
  const handleCloseModal = () => {
    if (onClose) {
      onClose();
    } else {
      navigate(-1);
    }
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
      //localStorage.removeItem("userInfo");
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
            <span className="info-label">
              이름
            </span>

            <span className="info-value">
              {userData.name}
            </span>
          </div>

          <div className="info-row">
            <span className="info-label">사번</span>
            <span className="info-value readonly-text">{userData.employeeId}</span>
          </div>

          <div className="info-row">
            <span className="info-label">
              연락처 (Mobile)
            </span>

            <span className="info-value">
              {formatPhoneNumber(userData.phone)}
            </span>
          </div>

          <div className="info-row">
            <span className="info-label">
              소속 부서
            </span>

            <span className="info-value">
              {userData.department}
            </span>
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