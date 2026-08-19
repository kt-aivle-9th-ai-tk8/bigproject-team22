import {
  useCallback,
  useEffect,
  useState,
} from "react";

import {
  fetchDefectImages,
} from "../api/defectImageApi";


export const useDefectImages = ({
  bladeId,
}) => {
  const [
    defectImages,
    setDefectImages,
  ] = useState([]);

  const [
    loading,
    setLoading,
  ] = useState(false);

  const [
    error,
    setError,
  ] = useState(null);


  const loadDefectImages =
    useCallback(async () => {
      if (!bladeId) {
        setDefectImages([]);
        return;
      }

      try {
        setLoading(true);
        setError(null);

        const responseBody =
          await fetchDefectImages(
            bladeId
          );

        console.log(
            "결함 이미지 API 응답:",
        responseBody
        );

        const images =
          Array.isArray(responseBody?.data)
            ? responseBody.data
            : Array.isArray(responseBody)
              ? responseBody
              : [];

        console.log(
          "블레이드 결함 이미지 조회:",
          images
        );

        setDefectImages(images);
      } catch (error) {
        console.error(
          "블레이드 결함 이미지 조회 오류:",
          error
        );

        setError(error.message);
        setDefectImages([]);
      } finally {
        setLoading(false);
      }
    }, [bladeId]);


  useEffect(() => {
    loadDefectImages();
  }, [loadDefectImages]);


  return {
    defectImages,
    loading,
    error,
    refetch: loadDefectImages,
  };
};