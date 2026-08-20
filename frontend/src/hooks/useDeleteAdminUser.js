import { useState } from "react";

import {
  deleteAdminUser,
} from "../api/adminUserApi";

export const useDeleteAdminUser = () => {
  const [
    isDeletingUser,
    setIsDeletingUser,
  ] = useState(false);

  const [
    deleteUserError,
    setDeleteUserError,
  ] = useState(null);

  const removeUser = async (
    userId
  ) => {
    if (!userId) {
      throw new Error(
        "사용자 ID가 필요합니다."
      );
    }

    try {
      setIsDeletingUser(true);
      setDeleteUserError(null);

      return await deleteAdminUser(
        userId
      );
    } catch (error) {
      console.error(
        "사용자 삭제 API 오류:",
        error
      );

      setDeleteUserError(
        error.message
      );

      throw error;
    } finally {
      setIsDeletingUser(false);
    }
  };

  return {
    removeUser,
    isDeletingUser,
    deleteUserError,
  };
};