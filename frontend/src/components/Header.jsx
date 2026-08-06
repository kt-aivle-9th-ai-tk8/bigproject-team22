import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";

import alarmIcon from "../assets/icon/alarm.png";
import AlarmPopup from "./AlarmPopup/AlarmPopup";

import "./Header.css";

function Header({ onLogout, onTitleClick, onMyPage, alarm = [] }) {
  const navigate = useNavigate();
  const [now, setNow] = useState(new Date());
  const [isAlarmOpen, setIsAlarmOpen] = useState(false);

  const hasAlarm = Array.isArray(alarm) && alarm.length > 0;

  useEffect(() => {
    const timer = setInterval(() => {
      setNow(new Date());
    }, 1000);

    return () => clearInterval(timer);
  }, []);

  const formattedDate = now
    .toLocaleDateString("ko-KR", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    })
    .replaceAll(". ", ".")
    .replace(/\.$/, "");

  const formattedTime = now.toLocaleTimeString("ko-KR", {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });

  const handleOpenAlarm = () => {
    setIsAlarmOpen(true);
  };

  const handleCloseAlarm = () => {
    setIsAlarmOpen(false);
  };

  const handleUserClick = () => {
    if (onMyPage) {
      onMyPage();
    } else {
      navigate("/user");
    }
  };

  return (
    <>
      <header className="header">
        <div className="header-left">
          <h1 className="header-title" onClick={onTitleClick}>
            발전소 통합 관제 시스템
          </h1>

          <div className="header-status">
            <span className="header-datetime">
              {formattedDate} &nbsp;{formattedTime}
            </span>

            {hasAlarm && (
              <button
                className="header-alarm-button"
                type="button"
                title="알림 리스트"
                aria-label={`알림 보고서 ${alarm.length}개 열기`}
                onClick={handleOpenAlarm}
              >
                <span className="header-alarm-dot" />
                <span className="header-alarm-text">경고</span>
              </button>
            )}
          </div>
        </div>

        {/* 3. 우측 '마이페이지' 텍스트 버튼 및 로그아웃 영역 */}
        <div className="header-right" style={{ display: "flex", gap: "16px", alignItems: "center" }}>
          <span
            className="mypage-link-text"
            style={{ cursor: "pointer", fontWeight: "600", fontSize: "14px", color: "#333" }}
            onClick={handleUserClick}
          >
            마이페이지
          </span>

          {onLogout && (
            <button
              type="button"
              className="btn-logout-text"
              style={{ cursor: "pointer", background: "none", border: "none", color: "#666" }}
              onClick={onLogout}
            >
              로그아웃
            </button>
          )}
        </div>
      </header>

      <AlarmPopup
        alarm={alarm}
        isOpen={isAlarmOpen}
        onClose={handleCloseAlarm}
      />
    </>
  );
}

export default Header;