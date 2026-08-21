import { useEffect, useState } from "react";

import { fetchNotifications } from "../api/notificationApi";


export const useNotifications = ({
  refreshInterval = 10000,
} = {}) => {
  const [notifications, setNotifications] = useState([]);
  const [isNotificationsLoading, setIsNotificationsLoading] =
    useState(false);
  const [notificationsError, setNotificationsError] =
    useState(null);


  useEffect(() => {
    let isMounted = true;


    // 


    const loadNotifications = async (isInitial = false) => {
      try {
        if (isInitial) {
          setIsNotificationsLoading(true);
        }


        setNotificationsError(null);


        // 


        const responseBody =
          await fetchNotifications();


        // 


        if (isMounted) {
          const notificationList =
            Array.isArray(responseBody?.data)
              ? responseBody.data
              : [];


          setNotifications(
            notificationList
          );


          // 
        }
      } catch (error) {
        // 


        if (isMounted) {
          setNotificationsError(
            error.message
          );
        }
      } finally {
        if (
          isInitial &&
          isMounted
        ) {
          setIsNotificationsLoading(
            false
          );
        }
      }
    };


    // 최초 진입 시 즉시 1회 호출
    loadNotifications(true);


    let intervalId = null;


    // 이후 refreshInterval마다 계속 호출
    if (refreshInterval > 0) {
      intervalId = setInterval(() => {
        loadNotifications(false);
      }, refreshInterval);


      // 
    }


    return () => {
      isMounted = false;


      if (intervalId) {
        clearInterval(intervalId);
      }


      // 
    };
  }, [refreshInterval]);


  return {
    notifications,
    isNotificationsLoading,
    notificationsError,
  };
};