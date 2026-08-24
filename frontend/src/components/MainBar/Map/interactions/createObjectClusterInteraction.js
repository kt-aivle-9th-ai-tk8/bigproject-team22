import Feature from "ol/Feature";
import Point from "ol/geom/Point";

const easeOutCubic = (progress) => {
  return 1 - Math.pow(1 - progress, 3);
};

const easeInCubic = (progress) => {
  return progress * progress * progress;
};

export function createObjectClusterInteraction({
  map,
  view,
  mapElement,

  objectLayer,
  expandedObjectLayer,
  expandedObjectSource,

  onSelectObject,
}) {
  let expandedClusterFeature = null;
  let closeTimerId = null;
  let animationFrameId = null;
  let isClosing = false;

  /*
   * 현재 실행 중인 애니메이션 취소
   */
  const cancelAnimation = () => {
    if (animationFrameId === null) {
      return;
    }

    cancelAnimationFrame(animationFrameId);
    animationFrameId = null;
  };

  /*
   * 예약된 닫기 작업 취소
   */
  const cancelScheduledClose = () => {
    if (closeTimerId === null) {
      return;
    }

    window.clearTimeout(closeTimerId);
    closeTimerId = null;
  };

  /*
   * 펼쳐진 객체 Feature 제거
   */
  const clearExpandedFeatures = () => {
    expandedObjectSource.clear();
  };

  /*
   * 숨겨진 숫자 클러스터 다시 표시
   */
  const showClusterFeature = (clusterFeature) => {
    if (!clusterFeature) {
      return;
    }

    clusterFeature.set("isExpanded", false);
    objectLayer.changed();
  };

  /*
   * 펼치는 동안 숫자 클러스터 숨기기
   */
  const hideClusterFeature = (clusterFeature) => {
    if (!clusterFeature) {
      return;
    }

    clusterFeature.set("isExpanded", true);
    objectLayer.changed();
  };

  /*
   * 애니메이션 없이 즉시 닫기
   */
  const closeImmediately = () => {
    cancelScheduledClose();
    cancelAnimation();

    clearExpandedFeatures();
    showClusterFeature(expandedClusterFeature);

    expandedClusterFeature = null;
    isClosing = false;
  };

  /*
   * 객체 개수에 따라 원형 배치 반지름 계산
   */
  const calculateRadiusInPixels = (count) => {
    const itemSizeInPixels = 52;

    const calculatedRadius =
      (count * itemSizeInPixels) /
      (2 * Math.PI);

    return (
      Math.max(
        38,
        Math.min(calculatedRadius, 120)
      ) + 5
    );
  };

  /*
   * 객체 원형 배치 시작 각도
   */
  const getStartAngle = (count) => {
    /*
     * 두 개일 때 중심을 기준으로 좌우 배치
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
     * 기존 클러스터 숫자를 다시 표시
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

    const resolution = view.getResolution();

    if (!clusterGeometry || !resolution) {
      return;
    }

    const centerCoordinate =
      clusterGeometry.getCoordinates();

    const count = clusteredFeatures.length;

    const radiusInPixels =
      calculateRadiusInPixels(count);

    const radiusInMapUnits =
      radiusInPixels * resolution;

    const startAngle =
      getStartAngle(count);

    /*
     * 펼치는 동안 숫자 클러스터 숨기기
     */
    hideClusterFeature(clusterFeature);

    const animatedItems =
      clusteredFeatures.map(
        (objectFeature, index) => {
          const angle =
            startAngle +
            (Math.PI * 2 * index) / count;

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

              objectId:
                objectFeature.get("objectId"),

              objectName:
                objectFeature.get(
                  "objectName"
                ),

              objectData:
                objectFeature.get(
                  "objectData"
                ),

              parentCluster:
                clusterFeature,
            });

          expandedObjectSource.addFeature(
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

    /*
     * 펼침 애니메이션
     */
    const duration = 250;
    const startTime = performance.now();

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
   * 펼쳐진 객체들을 중심으로 모은 뒤 닫기
   */
  const closeExpandedCluster = () => {
    const expandedFeatures =
      expandedObjectSource.getFeatures();

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

    /*
     * 닫힘 애니메이션
     */
    const duration = 180;
    const startTime = performance.now();

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
       * 닫힌 뒤 숫자 클러스터 다시 표시
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
   * 클러스터에서 펼쳐진 아이콘으로 이동할 때
   * 즉시 닫히는 현상을 막기 위한 지연
   */
  const scheduleClose = () => {
    cancelScheduledClose();

    closeTimerId = window.setTimeout(() => {
      closeExpandedCluster();
      closeTimerId = null;
    }, 50);
  };

  /*
   * 지정한 레이어의 Feature 찾기
   */
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
   * 객체 클릭 이벤트
   */
  const handleSingleClick = (event) => {
    /*
     * 펼쳐진 개별 객체 먼저 검사
     */
    const expandedFeature =
      getFeatureAtPixel(
        event.pixel,
        expandedObjectLayer,
        15
      );

    if (expandedFeature) {
      const selectedObject =
        expandedFeature.get("objectData");

      if (selectedObject) {
        onSelectObject?.(selectedObject);
      }

      return;
    }

    /*
     * 단일 객체 또는 클러스터 검사
     */
    const clusterFeature =
      getFeatureAtPixel(
        event.pixel,
        objectLayer,
        10
      );

    if (!clusterFeature) {
      return;
    }

    const clusteredFeatures =
      clusterFeature.get("features") || [];

    /*
     * 객체가 하나면 바로 선택
     */
    if (clusteredFeatures.length === 1) {
      const selectedObject =
        clusteredFeatures[0].get(
          "objectData"
        );

      if (selectedObject) {
        onSelectObject?.(selectedObject);
      }

      return;
    }

    /*
     * 여러 객체면 펼치기
     */
    if (clusteredFeatures.length > 1) {
      expandCluster(clusterFeature);
    }
  };

  /*
   * 객체 hover 이벤트
   */
  const handlePointerMove = (event) => {
    /*
     * 펼쳐진 개별 객체 위인지 검사
     */
    const expandedFeature =
      getFeatureAtPixel(
        event.pixel,
        expandedObjectLayer,
        20
      );

    if (expandedFeature) {
      cancelScheduledClose();
      mapElement.style.cursor = "pointer";
      return;
    }

    /*
     * 클러스터 또는 단일 객체 위인지 검사
     */
    const clusterFeature =
      getFeatureAtPixel(
        event.pixel,
        objectLayer,
        10
      );

    if (!clusterFeature) {
      mapElement.style.cursor = "default";
      scheduleClose();
      return;
    }

    cancelScheduledClose();
    mapElement.style.cursor = "pointer";

    const clusteredFeatures =
      clusterFeature.get("features") || [];

    if (clusteredFeatures.length > 1) {
      expandCluster(clusterFeature);
      return;
    }

    closeExpandedCluster();
  };

  /*
   * 지도 밖으로 마우스가 나갔을 때
   */
  const handlePointerLeave = () => {
    mapElement.style.cursor = "default";
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
   * 이벤트 및 애니메이션 정리
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

    mapElement.style.cursor = "default";
  };

  return {
    destroy,
    closeImmediately,
  };
}