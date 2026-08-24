import { useState } from "react";

import {
  updateReportContext,
} from "../api/reportApi";


export const useUpdateReport = () => {
  const [isUpdating, setIsUpdating] =
    useState(false);

  const [error, setError] =
    useState(null);


  const updateReport = async ({
    reportId,
    context,
  }) => {
    try {
      setIsUpdating(true);
      setError(null);


      const responseBody =
        await updateReportContext({
          reportId,
          context,
        });


      const responseData =
        responseBody?.data ??
        responseBody;


      


      return responseData;
    } catch (error) {
      


      setError(error.message);

      throw error;
    } finally {
      setIsUpdating(false);
    }
  };


  return {
    updateReport,
    isUpdating,
    error,
  };
};