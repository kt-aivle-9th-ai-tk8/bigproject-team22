import { useState } from "react";

import {
  readNotification,
} from "../api/notificationApi";


export const useReadNotification = () => {
  const [
    isReadingNotification,
    setIsReadingNotification,
  ] = useState(false);

  const [
    readNotificationError,
    setReadNotificationError,
  ] = useState(null);


  const markNotificationAsRead =
    async (notificationId) => {
      if (!notificationId) {
        throw new Error(
          "알림 ID가 필요합니다."
        );
      }

      try {
        setIsReadingNotification(true);
        setReadNotificationError(null);

        return await readNotification(
          notificationId
        );
      } catch (error) {
        

        setReadNotificationError(
          error.message
        );

        throw error;
      } finally {
        setIsReadingNotification(false);
      }
    };


  return {
    markNotificationAsRead,
    isReadingNotification,
    readNotificationError,
  };
};