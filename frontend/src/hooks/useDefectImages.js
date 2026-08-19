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
} = {}) => {
  const [
    defectImages,
    setDefectImages,
  ] = useState([]);

  const [
    isDefectImagesLoading,
    setIsDefectImagesLoading,
  ] = useState(false);

  const [
    defectImagesError,
    setDefectImagesError,
  ] = useState(null);


  const loadDefectImages =
    useCallback(async () => {
      if (!bladeId) {
        setDefectImages([]);
        return;
      }

      try {
        setIsDefectImagesLoading(true);
        setDefectImagesError(null);

        const responseBody =
          await fetchDefectImages(
            bladeId
          );

        console.log(
          "결함 이미지 API 응답:",
          responseBody
        );

        const rawImages =
          Array.isArray(responseBody?.data)
            ? responseBody.data
            : Array.isArray(responseBody)
              ? responseBody
              : [];

        const mappedImages =
          rawImages.map((item) => ({
            ...item,

            id:
              item.image_path,

            imageUrl:
              item.thumbnail_url,

            bladePosition:
              item.part_side,

            defectCount:
              Array.isArray(item.defects)
                ? item.defects.length
                : 0,

            maxSeverity:
              item.max_severity,

            inspectedAt:
              item.created_at
                ? item.created_at.slice(
                    0,
                    10
                  )
                : "",
          }));

        console.log(
          "결함 이미지 가공 결과:",
          mappedImages
        );

        setDefectImages(
          mappedImages
        );
      } catch (error) {
        console.error(
          "블레이드 결함 이미지 조회 오류:",
          error
        );

        setDefectImagesError(
          error.message
        );

        setDefectImages([]);
      } finally {
        setIsDefectImagesLoading(false);
      }
    }, [bladeId]);


  useEffect(() => {
    loadDefectImages();
  }, [loadDefectImages]);


  return {
    defectImages,
    isDefectImagesLoading,
    defectImagesError,
    refetchDefectImages:
      loadDefectImages,
  };
};