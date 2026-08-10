import { useEffect, useState } from "react";

import { fetchNotifications } from "../api/notificationApi";

export const useNotifications = ({
  refreshInterval = 600000,
} = {}) => {
  const [notifications, setNotifications] = useState([]);
  const [isNotificationsLoading, setIsNotificationsLoading] =
    useState(false);
  const [notificationsError, setNotificationsError] =
    useState(null);

  useEffect(() => {
    let isMounted = true;

    const loadNotifications = async (isInitial = false) => {
      try {
        if (isInitial) {
          setIsNotificationsLoading(true);
        }

        setNotificationsError(null);

        const responseBody =
          await fetchNotifications();

        if (isMounted) {
          setNotifications(
            Array.isArray(responseBody?.data)
              ? responseBody.data
              : []
          );
        }
      } catch (error) {
        console.error(
          "알림 목록 조회 API 오류:",
          error
        );

        if (isMounted) {
          setNotificationsError(error.message);
        }
      } finally {
        if (isInitial && isMounted) {
          setIsNotificationsLoading(false);
        }
      }
    };

    // 최초 진입 시 즉시 1회 호출
    loadNotifications(true);

    let intervalId = null;

    // 이후 10분마다 갱신
    if (refreshInterval > 0) {
      intervalId = setInterval(() => {
        loadNotifications(false);
      }, refreshInterval);
    }

    return () => {
      isMounted = false;

      if (intervalId) {
        clearInterval(intervalId);
      }
    };
  }, [refreshInterval]);

  return {
    notifications,
    isNotificationsLoading,
    notificationsError,
  };
};