import Feature from "ol/Feature";
import Point from "ol/geom/Point";

const easeOutCubic = (progress) => {
  return 1 - Math.pow(1 - progress, 3);
};

const easeInCubic = (progress) => {
  return progress * progress * progress;
};

export function createPlantClusterInteraction({
  map,
  view,
  mapElement,
  plantLayer,
  expandedPlantLayer,
  expandedPlantSource,
  onSelectPlant,
}) {
  let expandedClusterFeature = null;
  let closeTimerId = null;
  let animationFrameId = null;
  let isClosing = false;

  const cancelAnimation = () => {
    if (animationFrameId === null) {
      return;
    }

    cancelAnimationFrame(animationFrameId);
    animationFrameId = null;
  };

  const cancelScheduledClose = () => {
    if (closeTimerId === null) {
      return;
    }

    window.clearTimeout(closeTimerId);
    closeTimerId = null;
  };

  const clearExpandedFeatures = () => {
    expandedPlantSource.clear();
  };

  const showClusterFeature = (clusterFeature) => {
    if (!clusterFeature) {
      return;
    }

    clusterFeature.set("isExpanded", false);
    plantLayer.changed();
  };

  const hideClusterFeature = (clusterFeature) => {
    if (!clusterFeature) {
      return;
    }

    clusterFeature.set("isExpanded", true);
    plantLayer.changed();
  };

  const closeImmediately = () => {
    cancelScheduledClose();
    cancelAnimation();

    clearExpandedFeatures();

    showClusterFeature(
      expandedClusterFeature
    );

    expandedClusterFeature = null;
    isClosing = false;
  };

  /*
   * 아이콘 개수에 따라 원형 배치 반지름 계산
   */
  const calculateRadiusInPixels = (count) => {
    const itemSizeInPixels = 52;

    const calculatedRadius =
      (count * itemSizeInPixels) /
      (2 * Math.PI);

    return Math.max(
      38,
      Math.min(calculatedRadius, 120)
    ) + 5;
  };

  /*
   * 아이콘 배치 시작 각도
   */
  const getStartAngle = (count) => {
    /*
     * 두 개일 때 숫자 아이콘 중심 기준 좌우 배치
     */
    if (count === 2) {
      return 0;
    }

    /*
     * 세 개 이상은 위쪽부터 배치
     */
    return -Math.PI / 2;
  };

  /*
   * 클러스터 펼치기
   */
  const expandCluster = (clusterFeature) => {
    cancelScheduledClose();

    if (
      expandedClusterFeature === clusterFeature &&
      !isClosing
    ) {
      return;
    }

    cancelAnimation();

    /*
     * 다른 클러스터가 펼쳐져 있었다면
     * 기존 숫자 아이콘을 다시 표시
     */
    if (
      expandedClusterFeature &&
      expandedClusterFeature !== clusterFeature
    ) {
      showClusterFeature(
        expandedClusterFeature
      );
    }

    isClosing = false;
    clearExpandedFeatures();

    const clusteredFeatures =
      clusterFeature.get("features") || [];

    if (clusteredFeatures.length <= 1) {
      expandedClusterFeature = null;
      return;
    }

    const clusterGeometry =
      clusterFeature.getGeometry();

    const resolution =
      view.getResolution();

    if (!clusterGeometry || !resolution) {
      return;
    }

    const centerCoordinate =
      clusterGeometry.getCoordinates();

    const count =
      clusteredFeatures.length;

    const radiusInPixels =
      calculateRadiusInPixels(count);

    const radiusInMapUnits =
      radiusInPixels * resolution;

    const startAngle =
      getStartAngle(count);

    /*
     * 숫자 클러스터 숨김
     */
    hideClusterFeature(clusterFeature);

    const animatedItems =
      clusteredFeatures.map(
        (plantFeature, index) => {
          const angle =
            startAngle +
            (Math.PI * 2 * index) /
              count;

          const targetCoordinate = [
            centerCoordinate[0] +
              Math.cos(angle) *
                radiusInMapUnits,

            centerCoordinate[1] +
              Math.sin(angle) *
                radiusInMapUnits,
          ];

          const expandedFeature =
            new Feature({
              geometry: new Point([
                centerCoordinate[0],
                centerCoordinate[1],
              ]),

              plantId:
                plantFeature.get("plantId"),

              plantName:
                plantFeature.get(
                  "plantName"
                ),

              plantData:
                plantFeature.get(
                  "plantData"
                ),

              parentCluster:
                clusterFeature,
            });

          expandedPlantSource.addFeature(
            expandedFeature
          );

          return {
            feature: expandedFeature,

            startCoordinate: [
              centerCoordinate[0],
              centerCoordinate[1],
            ],

            targetCoordinate,
          };
        }
      );

    expandedClusterFeature =
      clusterFeature;

    const duration = 250;
    const startTime =
      performance.now();

    const animateExpansion = (
      currentTime
    ) => {
      const elapsed =
        currentTime - startTime;

      const progress = Math.min(
        elapsed / duration,
        1
      );

      const easedProgress =
        easeOutCubic(progress);

      animatedItems.forEach(
        ({
          feature,
          startCoordinate,
          targetCoordinate,
        }) => {
          const currentCoordinate = [
            startCoordinate[0] +
              (targetCoordinate[0] -
                startCoordinate[0]) *
                easedProgress,

            startCoordinate[1] +
              (targetCoordinate[1] -
                startCoordinate[1]) *
                easedProgress,
          ];

          feature
            .getGeometry()
            ?.setCoordinates(
              currentCoordinate
            );
        }
      );

      if (progress < 1) {
        animationFrameId =
          requestAnimationFrame(
            animateExpansion
          );

        return;
      }

      animationFrameId = null;
    };

    animationFrameId =
      requestAnimationFrame(
        animateExpansion
      );
  };

  /*
   * 펼쳐진 아이콘들을 다시 중심으로 모은 뒤 닫기
   */
  const closeExpandedCluster = () => {
    const expandedFeatures =
      expandedPlantSource.getFeatures();

    if (expandedFeatures.length === 0) {
      showClusterFeature(
        expandedClusterFeature
      );

      expandedClusterFeature = null;
      isClosing = false;
      return;
    }

    if (isClosing) {
      return;
    }

    cancelAnimation();

    const clusterGeometry =
      expandedClusterFeature?.getGeometry();

    if (!clusterGeometry) {
      closeImmediately();
      return;
    }

    isClosing = true;

    const centerCoordinate =
      clusterGeometry.getCoordinates();

    const animatedItems =
      expandedFeatures.map((feature) => {
        const coordinate =
          feature
            .getGeometry()
            ?.getCoordinates();

        return {
          feature,

          startCoordinate: coordinate
            ? [...coordinate]
            : [...centerCoordinate],
        };
      });

    const duration = 180;
    const startTime =
      performance.now();

    const animateClosing = (
      currentTime
    ) => {
      const elapsed =
        currentTime - startTime;

      const progress = Math.min(
        elapsed / duration,
        1
      );

      const easedProgress =
        easeInCubic(progress);

      animatedItems.forEach(
        ({
          feature,
          startCoordinate,
        }) => {
          const currentCoordinate = [
            startCoordinate[0] +
              (centerCoordinate[0] -
                startCoordinate[0]) *
                easedProgress,

            startCoordinate[1] +
              (centerCoordinate[1] -
                startCoordinate[1]) *
                easedProgress,
          ];

          feature
            .getGeometry()
            ?.setCoordinates(
              currentCoordinate
            );
        }
      );

      if (progress < 1) {
        animationFrameId =
          requestAnimationFrame(
            animateClosing
          );

        return;
      }

      clearExpandedFeatures();

      /*
       * 닫힘 애니메이션이 끝난 뒤
       * 숫자 클러스터 다시 표시
       */
      showClusterFeature(
        expandedClusterFeature
      );

      expandedClusterFeature = null;
      animationFrameId = null;
      isClosing = false;
    };

    animationFrameId =
      requestAnimationFrame(
        animateClosing
      );
  };

  /*
   * 마우스가 숫자 클러스터에서 펼쳐진 아이콘으로
   * 이동하는 동안 바로 닫히지 않도록 지연
   */
  const scheduleClose = () => {
    cancelScheduledClose();

    closeTimerId =
      window.setTimeout(() => {
        closeExpandedCluster();
        closeTimerId = null;
      }, 250);
  };

  const getFeatureAtPixel = (
    pixel,
    targetLayer,
    hitTolerance = 10
  ) => {
    return map.forEachFeatureAtPixel(
      pixel,

      (feature, layer) => {
        if (layer === targetLayer) {
          return feature;
        }

        return undefined;
      },

      {
        hitTolerance,
      }
    );
  };

  /*
   * 클릭 이벤트
   */
  const handleSingleClick = (event) => {
    /*
     * 펼쳐진 개별 발전소 먼저 검사
     */
    const expandedFeature =
      getFeatureAtPixel(
        event.pixel,
        expandedPlantLayer,
        15
      );

    if (expandedFeature) {
      const selectedPlant =
        expandedFeature.get(
          "plantData"
        );

      if (selectedPlant) {
        onSelectPlant?.(
          selectedPlant
        );
      }

      return;
    }

    /*
     * 기본 발전소 또는 클러스터 검사
     */
    const clusterFeature =
      getFeatureAtPixel(
        event.pixel,
        plantLayer,
        10
      );

    if (!clusterFeature) {
      return;
    }

    const clusteredFeatures =
      clusterFeature.get("features") ||
      [];

    /*
     * 단일 발전소면 바로 선택
     */
    if (
      clusteredFeatures.length === 1
    ) {
      const selectedPlant =
        clusteredFeatures[0].get(
          "plantData"
        );

      if (selectedPlant) {
        onSelectPlant?.(
          selectedPlant
        );
      }

      return;
    }

    /*
     * 여러 발전소면 펼침
     */
    if (
      clusteredFeatures.length > 1
    ) {
      expandCluster(
        clusterFeature
      );
    }
  };

  /*
   * hover 이벤트
   */
  const handlePointerMove = (event) => {
    /*
     * 펼쳐진 개별 발전소 위인지 검사
     */
    const expandedFeature =
      getFeatureAtPixel(
        event.pixel,
        expandedPlantLayer,
        20
      );

    if (expandedFeature) {
      cancelScheduledClose();

      mapElement.style.cursor =
        "pointer";

      return;
    }

    /*
     * 클러스터 또는 단일 발전소 위인지 검사
     */
    const clusterFeature =
      getFeatureAtPixel(
        event.pixel,
        plantLayer,
        10
      );

    if (!clusterFeature) {
      mapElement.style.cursor =
        "default";

      scheduleClose();

      return;
    }

    cancelScheduledClose();

    mapElement.style.cursor =
      "pointer";

    const clusteredFeatures =
      clusterFeature.get("features") ||
      [];

    if (
      clusteredFeatures.length > 1
    ) {
      expandCluster(
        clusterFeature
      );

      return;
    }

    closeExpandedCluster();
  };

  /*
   * 지도 영역 밖으로 마우스가 나갔을 때
   */
  const handlePointerLeave = () => {
    mapElement.style.cursor =
      "default";

    scheduleClose();
  };

  map.on(
    "singleclick",
    handleSingleClick
  );

  map.on(
    "pointermove",
    handlePointerMove
  );

  mapElement.addEventListener(
    "pointerleave",
    handlePointerLeave
  );

  /*
   * 컴포넌트 정리
   */
  const destroy = () => {
    cancelScheduledClose();
    cancelAnimation();

    map.un(
      "singleclick",
      handleSingleClick
    );

    map.un(
      "pointermove",
      handlePointerMove
    );

    mapElement.removeEventListener(
      "pointerleave",
      handlePointerLeave
    );

    clearExpandedFeatures();

    showClusterFeature(
      expandedClusterFeature
    );

    expandedClusterFeature = null;
    isClosing = false;

    mapElement.style.cursor =
      "default";
  };

  return {
    destroy,
    closeImmediately,
  };
}