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


    // console.log(
    //   "[useNotifications] polling 시작",
    //   {
    //     refreshInterval,
    //   }
    // );


    const loadNotifications = async (isInitial = false) => {
      try {
        if (isInitial) {
          setIsNotificationsLoading(true);
        }


        setNotificationsError(null);


        // console.log(
        //   isInitial
        //     ? "[useNotifications] 최초 알림 API 호출"
        //     : "[useNotifications] polling 알림 API 호출",
        //   new Date().toLocaleString()
        // );


        const responseBody =
          await fetchNotifications();


        // console.log(
        //   "[useNotifications] API 응답:",
        //   responseBody
        // );


        if (isMounted) {
          const notificationList =
            Array.isArray(responseBody?.data)
              ? responseBody.data
              : [];


          setNotifications(
            notificationList
          );


          // console.log(
          //   "[useNotifications] 알림 목록 갱신:",
          //   notificationList
          // );
        }
      } catch (error) {
        // console.error(
        //   "[useNotifications] 알림 목록 조회 API 오류:",
        //   error
        // );


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


      // console.log(
      //   "[useNotifications] polling 등록:",
      //   `${refreshInterval}ms`
      // );
    }


    return () => {
      isMounted = false;


      if (intervalId) {
        clearInterval(intervalId);
      }


      // console.log(
      //   "[useNotifications] polling 종료"
      // );
    };
  }, [refreshInterval]);


  return {
    notifications,
    isNotificationsLoading,
    notificationsError,
  };
};