import { useState } from "react";

import {
  updateAdminUser,
} from "../api/adminUserApi";

export const useApproveAdminUser = () => {
  const [isApproving, setIsApproving] =
    useState(false);

  const [approveError, setApproveError] =
    useState(null);

  const approveUser = async ({
    userId,
    windFarmIds,
  }) => {
    if (!userId) {
      throw new Error("사용자 ID가 필요합니다.");
    }

    if (
      !Array.isArray(windFarmIds) ||
      windFarmIds.length === 0
    ) {
      throw new Error(
        "최소 하나 이상의 담당 발전소를 선택해 주세요."
      );
    }

    try {
      setIsApproving(true);
      setApproveError(null);

      const response =
        await updateAdminUser({
          userId,
          role: "MANAGER",
          windFarmIds,
        });

      return response;
    } catch (error) {
      console.error(
        "사용자 승인 에러:",
        error
      );

      setApproveError(error.message);

      throw error;
    } finally {
      setIsApproving(false);
    }
  };

  return {
    approveUser,
    isApproving,
    approveError,
  };
};