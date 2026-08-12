import { useEffect, useRef, useState } from "react";

import {
  createInspection,
  uploadInspectionImage,
  completeInspectionImageUpload,
} from "../api/inspectionApi";

import { fetchReportById } from "../api/reportApi";
import { fetchTurbineById } from "../api/turbineApi";


const BLADE_IDS = [
  "Blade_A",
  "Blade_B",
  "Blade_C",
];


export const useInspectionReport = ({
  refreshInterval = 5000,
  onGenerated,
} = {}) => {
  const [reportId, setReportId] = useState(null);
  const [reportDetail, setReportDetail] =
    useState(null);

  const [
    isInspectionCreating,
    setIsInspectionCreating,
  ] = useState(false);

  const [
    inspectionError,
    setInspectionError,
  ] = useState(null);

  const onGeneratedRef = useRef(onGenerated);


  useEffect(() => {
    onGeneratedRef.current = onGenerated;
  }, [onGenerated]);


  /*
   * filesBySurface에서 이미지 가져오기
   *
   * 예:
   * U1-Blade_A-LE
   */
  const getSurfaceImages = ({
    filesBySurface,
    turbineName,
    bladeId,
    surfaceId,
  }) => {
    const fileKey =
      `${turbineName}-${bladeId}-${surfaceId}`;

    return (
      filesBySurface?.[fileKey]?.images || []
    );
  };


  /*
   * presigned URL에 이미지 업로드
   */
  const uploadFilesToUrls = async (
    imageItems = [],
    uploadUrls = []
  ) => {
    if (
      imageItems.length !==
      uploadUrls.length
    ) {
      throw new Error(
        "이미지 개수와 업로드 URL 개수가 일치하지 않습니다."
      );
    }

    await Promise.all(
      imageItems.map((imageItem, index) => {
        const file =
          imageItem?.file ?? imageItem;

        return uploadInspectionImage({
          uploadUrl: uploadUrls[index],
          file,
        });
      })
    );
  };


  const createInspectionReport = async ({
    windFarmId,
    reportData,
  }) => {
    if (!windFarmId) {
      throw new Error(
        "발전소 정보가 없습니다."
      );
    }

    if (!reportData) {
      throw new Error(
        "점검 보고서 정보가 없습니다."
      );
    }

    if (
      !Array.isArray(reportData.turbines) ||
      reportData.turbines.length === 0
    ) {
      throw new Error(
        "점검할 터빈 정보가 없습니다."
      );
    }


    try {
      setIsInspectionCreating(true);
      setInspectionError(null);
      setReportDetail(null);


      const turbineIds =
        reportData.turbines;

      const turbineNames =
        reportData.turbineNames || [];

      const filesBySurface =
        reportData.filesBySurface || {};


      /*
       * 1.
       * 각 터빈 상세 조회
       *
       * blade_id를 알기 위해 필요
       */
      const turbineDetails =
        await Promise.all(
          turbineIds.map(
            async (turbineId, index) => {
              const responseBody =
                await fetchTurbineById(
                  turbineId
                );

              const detail =
                responseBody?.data ??
                responseBody;

              return {
                turbineId,

                turbineName:
                  turbineNames[index] ||
                  detail?.code ||
                  detail?.name ||
                  `터빈 ${turbineId}`,

                detail,
              };
            }
          )
        );


      /*
       * 2.
       * /api/inspections 요청 데이터 생성
       */
      const turbinesRequest =
        turbineDetails.map(
          ({
            turbineId,
            turbineName,
            detail,
          }) => {
            const blades =
              BLADE_IDS.map(
                (bladeUiId) => {
                  /*
                   * Blade_A → A
                   * Blade_B → B
                   * Blade_C → C
                   */
                  const bladeTag =
                    bladeUiId.replace(
                      "Blade_",
                      ""
                    );

                  /*
                   * /api/turbines/{id} 응답:
                   *
                   * blades: [
                   *   {
                   *     id: 1,
                   *     tag: "A"
                   *   }
                   * ]
                   */
                  const blade =
                    detail?.blades?.find(
                      (item) =>
                        item.tag ===
                        bladeTag
                    );


                  if (!blade) {
                    throw new Error(
                      `${turbineName}의 블레이드 ${bladeTag} 정보를 찾을 수 없습니다.`
                    );
                  }


                  const leadingEdgeImages =
                    getSurfaceImages({
                      filesBySurface,
                      turbineName,
                      bladeId: bladeUiId,
                      surfaceId: "LE",
                    });


                  const pressureSideImages =
                    getSurfaceImages({
                      filesBySurface,
                      turbineName,
                      bladeId: bladeUiId,
                      surfaceId: "PS",
                    });


                  const suctionSideImages =
                    getSurfaceImages({
                      filesBySurface,
                      turbineName,
                      bladeId: bladeUiId,
                      surfaceId: "SS",
                    });


                  const trailingEdgeImages =
                    getSurfaceImages({
                      filesBySurface,
                      turbineName,
                      bladeId: bladeUiId,
                      surfaceId: "TE",
                    });


                  return {
                    blade_id: blade.id,

                    leading_edge_count:
                      leadingEdgeImages.length,

                    pressure_side_count:
                      pressureSideImages.length,

                    suction_side_count:
                      suctionSideImages.length,

                    trailing_edge_count:
                      trailingEdgeImages.length,
                  };
                }
              );


            return {
              turbine_id: turbineId,
              blades,
            };
          }
        );


      console.log(
        "점검 생성 요청:",
        {
          wind_farm_id: windFarmId,
          turbines: turbinesRequest,
          context: null,
        }
      );


      /*
       * 3.
       * POST /api/inspections
       */
      const responseBody =
        await createInspection({
          windFarmId,
          turbines: turbinesRequest,
          context: null,
        });


      const inspectionResponse =
        responseBody?.data ??
        responseBody;


      console.log(
        "점검 생성 성공:",
        inspectionResponse
      );


      /*
       * 4.
       * 응답받은 URL에 이미지 업로드
       */
      for (
        const responseTurbine of
        inspectionResponse?.turbines || []
      ) {
        const turbineDetail =
          turbineDetails.find(
            (item) =>
              String(item.turbineId) ===
              String(
                responseTurbine.turbine_id
              )
          );


        if (!turbineDetail) {
          throw new Error(
            "업로드 대상 터빈 정보를 찾을 수 없습니다."
          );
        }


        const {
          turbineName,
          detail,
        } = turbineDetail;


        for (
          const responseBlade of
          responseTurbine.blades || []
        ) {
          /*
           * 응답 blade_id로 A/B/C 확인
           */
          const blade =
            detail?.blades?.find(
              (item) =>
                String(item.id) ===
                String(
                  responseBlade.blade_id
                )
            );


          if (!blade) {
            throw new Error(
              "업로드 대상 블레이드 정보를 찾을 수 없습니다."
            );
          }


          const bladeUiId =
            `Blade_${blade.tag}`;


          /*
           * LE
           */
          await uploadFilesToUrls(
            getSurfaceImages({
              filesBySurface,
              turbineName,
              bladeId: bladeUiId,
              surfaceId: "LE",
            }),

            responseBlade
              .leading_edge_upload_urls ||
              []
          );


          /*
           * PS
           */
          await uploadFilesToUrls(
            getSurfaceImages({
              filesBySurface,
              turbineName,
              bladeId: bladeUiId,
              surfaceId: "PS",
            }),

            responseBlade
              .pressure_side_upload_urls ||
              []
          );


          /*
           * SS
           */
          await uploadFilesToUrls(
            getSurfaceImages({
              filesBySurface,
              turbineName,
              bladeId: bladeUiId,
              surfaceId: "SS",
            }),

            responseBlade
              .suction_side_upload_urls ||
              []
          );


          /*
           * TE
           */
          await uploadFilesToUrls(
            getSurfaceImages({
              filesBySurface,
              turbineName,
              bladeId: bladeUiId,
              surfaceId: "TE",
            }),

            responseBlade
              .trailing_edge_upload_urls ||
              []
          );
        }


        /*
         * 5.
         * 해당 inspection 업로드 완료 처리
         */
        if (
          responseTurbine.inspection_id
        ) {
          await completeInspectionImageUpload(
            responseTurbine.inspection_id
          );
        }
      }


      /*
       * 6.
       * report_id 저장
       */
      const createdReportId =
        inspectionResponse?.report_id;


      if (!createdReportId) {
        throw new Error(
          "생성된 보고서 ID를 확인할 수 없습니다."
        );
      }


      setReportId(
        createdReportId
      );


      console.log(
        "점검 이미지 업로드 완료 / reportId:",
        createdReportId
      );


      return inspectionResponse;
    } catch (error) {
      console.error(
        "점검 보고서 생성 오류:",
        error
      );

      setInspectionError(
        error.message
      );

      throw error;
    } finally {
      setIsInspectionCreating(false);
    }
  };


  /*
   * report_id 생성 후
   * /api/reports/{report_id} polling
   */
  useEffect(() => {
    if (!reportId) {
      return;
    }

    let isMounted = true;
    let intervalId = null;


    const checkReportStatus =
      async () => {
        try {
          const responseBody =
            await fetchReportById(
              reportId
            );

          const report =
            responseBody?.data ??
            responseBody;


          if (!isMounted) {
            return;
          }


          setReportDetail(report);
          setInspectionError(null);


          const currentStatus =
            String(
              report?.status || ""
            ).toLowerCase();


          console.log(
            "점검 보고서 현재 상태:",
            currentStatus
          );


          /*
           * 이전 상태 상관없이
           * generated면 완료
           */
          if (
            currentStatus ===
            "generated"
          ) {
            onGeneratedRef.current?.(
              report
            );


            setReportId(null);


            if (intervalId) {
              clearInterval(
                intervalId
              );

              intervalId = null;
            }
          }
        } catch (error) {
          console.error(
            "점검 보고서 상태 조회 오류:",
            error
          );

          if (isMounted) {
            setInspectionError(
              error.message
            );
          }
        }
      };


    /*
     * 즉시 1회 조회
     */
    checkReportStatus();


    /*
     * 이후 polling
     */
    if (refreshInterval > 0) {
      intervalId =
        setInterval(() => {
          checkReportStatus();
        }, refreshInterval);
    }


    return () => {
      isMounted = false;

      if (intervalId) {
        clearInterval(
          intervalId
        );
      }
    };
  }, [
    reportId,
    refreshInterval,
  ]);


  return {
    reportId,
    reportDetail,
    isInspectionCreating,
    inspectionError,
    createInspectionReport,
  };
};