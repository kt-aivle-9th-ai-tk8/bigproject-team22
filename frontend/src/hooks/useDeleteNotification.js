import { useState } from "react";

import {
  deleteNotification,
} from "../api/notificationApi";


export const useDeleteNotification = () => {
  const [
    isDeletingNotification,
    setIsDeletingNotification,
  ] = useState(false);

  const [
    deleteNotificationError,
    setDeleteNotificationError,
  ] = useState(null);


  const removeNotification =
    async (notificationId) => {
      if (!notificationId) {
        throw new Error(
          "알림 ID가 필요합니다."
        );
      }

      try {
        setIsDeletingNotification(true);
        setDeleteNotificationError(null);

        return await deleteNotification(
          notificationId
        );
      } catch (error) {
        console.error(
          "알림 삭제 오류:",
          error
        );

        setDeleteNotificationError(
          error.message
        );

        throw error;
      } finally {
        setIsDeletingNotification(false);
      }
    };


  return {
    removeNotification,
    isDeletingNotification,
    deleteNotificationError,
  };
};