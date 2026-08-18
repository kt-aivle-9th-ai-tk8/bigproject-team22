import { useState } from "react";

import {
  updateAdminUser,
} from "../api/adminUserApi";

export const useUpdateAdminUser = () => {
  const [isUpdatingUser, setIsUpdatingUser] =
    useState(false);

  const [updateUserError, setUpdateUserError] =
    useState(null);

  const updateUser = async ({
    userId,
    role,
    windFarmIds,
    }) => {
    if (!userId) {
        throw new Error("사용자 ID가 필요합니다.");
    }

    if (!role) {
        throw new Error("사용자 권한이 필요합니다.");
    }

    if (!Array.isArray(windFarmIds)) {
        throw new Error(
        "담당 발전소 ID 목록이 필요합니다."
        );
    }

    try {
        setIsUpdatingUser(true);
        setUpdateUserError(null);

        const response =
        await updateAdminUser({
            userId,
            role,
            windFarmIds,
        });

        return response;
    } catch (error) {
        console.error(
        "관리자 사용자 변경 에러:",
        error
        );

        setUpdateUserError(error.message);
        throw error;
    } finally {
        setIsUpdatingUser(false);
    }
    };

  return {
    updateUser,
    isUpdatingUser,
    updateUserError,
  };
};