import React, { useEffect, useState } from "react";
import "./Header.css";

function Header() {
  const [now, setNow] = useState(new Date());

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

  return (
    <header className="header">
      <div className="header-left">
        <h1 className="header-title">발전소 통합 관제 시스템</h1>

        <span className="header-datetime">
          {formattedDate} &nbsp;{formattedTime}
        </span>
      </div>

      <button className="logout-button">
        로그아웃
      </button>
    </header>
  );
}

export default Header;