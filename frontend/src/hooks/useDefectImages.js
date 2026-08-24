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

        const rawImages =
          Array.isArray(responseBody?.data)
            ? responseBody.data
            : Array.isArray(responseBody)
              ? responseBody
              : [];

        

        const mappedImages =
          rawImages.map((item) => ({
            ...item,

            id: item.image_path,

            imagePath:
              item.image_path,

            thumbnailUrl:
              item.thumbnail_url,

            imageUrl:
              item.image_url,

            defects:
              Array.isArray(item.defects)
                ? item.defects
                : [],

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
                ? item.created_at.slice(0, 10)
                : "",
          }));

        setDefectImages(
          mappedImages
        );
      } catch (error) {
        

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