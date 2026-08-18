import { useState } from "react";

import {
  forceLogoutAdminUser,
} from "../api/adminUserApi";

export const useForceLogoutUser = () => {
  const [isForceLoggingOut, setIsForceLoggingOut] =
    useState(false);

  const [forceLogoutError, setForceLogoutError] =
    useState(null);

  const forceLogoutUser = async (userId) => {
    try {
      setIsForceLoggingOut(true);
      setForceLogoutError(null);

      const response =
        await forceLogoutAdminUser(userId);

      return response;
    } catch (error) {
      console.error(
        "사용자 강제 로그아웃 에러:",
        error
      );

      setForceLogoutError(error.message);

      throw error;
    } finally {
      setIsForceLoggingOut(false);
    }
  };

  return {
    forceLogoutUser,
    isForceLoggingOut,
    forceLogoutError,
  };
};