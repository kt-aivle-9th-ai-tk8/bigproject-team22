import { useState } from "react";
import { deleteReportById } from "../api/reportApi";

export const useDeleteReport = () => {
  const [isDeleting, setIsDeleting] =
    useState(false);

  const [deleteError, setDeleteError] =
    useState(null);

  const deleteReport = async (reportId) => {
    try {
      setIsDeleting(true);
      setDeleteError(null);

      const response =
        await deleteReportById(reportId);

      return response;
    } catch (error) {
      

      setDeleteError(error.message);

      throw error;
    } finally {
      setIsDeleting(false);
    }
  };

  return {
    deleteReport,
    isDeleting,
    deleteError,
  };
};