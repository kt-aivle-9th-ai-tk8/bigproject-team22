import { useEffect, useState } from "react";

import "./LoadingOverlay.css";

import movingBladeIcon from "../../assets/icon/moving_blade.png";
import movingStickIcon from "../../assets/icon/moving_stick.png";

function LoadingOverlay({
  message = "로딩 중...",
}) {
  const [dotCount, setDotCount] = useState(0);

  const hasDots = message.endsWith("...");
  const baseMessage = hasDots
    ? message.slice(0, -3)
    : message;

  useEffect(() => {
    if (!hasDots) return;

    const intervalId = setInterval(() => {
      setDotCount((prev) => (prev + 1) % 4);
    }, 500);

    return () => {
      clearInterval(intervalId);
    };
  }, [hasDots]);

  return (
    <div className="loading-overlay">
      <div className="loading-turbine">
        <img
          className="loading-stick"
          src={movingStickIcon}
          alt=""
        />

        <img
          className="loading-blade"
          src={movingBladeIcon}
          alt=""
        />
      </div>

      <div className="loading-message">
        {baseMessage}
        {hasDots && (
          <span className="loading-message-dots">
            {".".repeat(dotCount)}
          </span>
        )}
      </div>
    </div>
  );
}

export default LoadingOverlay;