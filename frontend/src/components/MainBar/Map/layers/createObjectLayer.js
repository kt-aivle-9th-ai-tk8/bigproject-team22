import Feature from "ol/Feature";
import Point from "ol/geom/Point";

import VectorLayer from "ol/layer/Vector";

import VectorSource from "ol/source/Vector";
import Cluster from "ol/source/Cluster";

import { fromLonLat } from "ol/proj";

import Style from "ol/style/Style";
import Icon from "ol/style/Icon";
import Text from "ol/style/Text";
import Fill from "ol/style/Fill";
import Stroke from "ol/style/Stroke";
import CircleStyle from "ol/style/Circle";

const getCssVariableColor = (variableName, fallbackColor) => {
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(variableName)
    .trim();

  return value || fallbackColor;
};
/*
 * 단일 객체 아이콘 스타일
 */
const createSingleObjectStyle = (
  objectFeature,
  iconSrc,
  iconScale
) => {
  return new Style({
    image: new Icon({
      src: iconSrc,
      anchor: [0.5, 1],
      scale: iconScale,
    }),

    text: new Text({
      text: objectFeature.get("objectName"),

      offsetY: -30,

      font: "bold 12px sans-serif",

      fill: new Fill({
        color: "#ffffff",
      }),

      stroke: new Stroke({
        color: "#222222",
        width: 3,
      }),
    }),
  });
};

/*
 * 여러 객체가 묶였을 때 표시되는 숫자 원 스타일
 */
const createClusterStyle = (count) => {
  return new Style({
    image: new CircleStyle({
      radius: 30,

      fill: new Fill({
        color: getCssVariableColor("--color-point", "#b4cfff;"),
      }),

      stroke: new Stroke({
        color: "#ffffff",
        width: 3,
      }),
    }),

    text: new Text({
      text: String(count),
      font: "bold 22px sans-serif",

      fill: new Fill({
        color: getCssVariableColor("--color-text-point-dark", "#0B50D1;"),
      }),
    }),
  });
};

/*
 * 클러스터를 펼쳤을 때 표시되는 개별 객체 스타일
 */
const createExpandedObjectStyle = (
  feature,
  iconSrc,
  iconScale
) => {
  return new Style({
    image: new Icon({
      src: iconSrc,
      anchor: [0.5, 1],
      scale: iconScale,
    }),

    text: new Text({
      text: feature.get("objectName"),
      offsetY: -30,
      font: "bold 12px sans-serif",

      fill: new Fill({
        color: "#ffffff",
      }),

      stroke: new Stroke({
        color: "#222222",
        width: 3,
      }),
    }),
  });
};

export function createObjectLayer({
  objects = [],
  iconSrc,
  iconScale = 0.07,
  clusterDistance = 40,
  clusterMinDistance = 15,
}) {
  if (!iconSrc) {
    throw new Error(
      "createObjectLayer에 iconSrc가 필요합니다."
    );
  }

  /*
   * 실제 객체 Feature 생성
   */
  const objectFeatures = objects.map((object) => {
    return new Feature({
      geometry: new Point(
        fromLonLat(object.coordinate)
      ),

      objectId: object.id,
      objectName: object.name,
      objectData: object,
    });
  });

  /*
   * 실제 객체 Feature를 저장하는 원본 Source
   */
  const objectSource = new VectorSource({
    features: objectFeatures,
  });

  /*
   * 가까운 객체 Feature들을 하나로 묶는 Cluster Source
   *
   * distance와 minDistance는 화면 픽셀 기준
   */
  const clusterSource = new Cluster({
    distance: clusterDistance,
    minDistance: clusterMinDistance,
    source: objectSource,
  });

  /*
   * 단일 객체와 숫자 클러스터를 표시하는 레이어
   */
  const objectLayer = new VectorLayer({
    source: clusterSource,

    style: (clusterFeature) => {
      /*
       * 펼쳐진 클러스터는 숨김
       */
      if (clusterFeature.get("isExpanded")) {
        return null;
      }

      const clusteredFeatures =
        clusterFeature.get("features") || [];

      const count = clusteredFeatures.length;

      if (count === 1) {
        return createSingleObjectStyle(
          clusteredFeatures[0],
          iconSrc,
          iconScale
        );
      }

      return createClusterStyle(count);
    },

    declutter: true,
    zIndex: 10,
  });

  /*
   * 펼쳐진 개별 객체 Feature를 저장하는 Source
   */
  const expandedObjectSource =
    new VectorSource();

  /*
   * 펼쳐진 개별 객체 아이콘을 표시하는 레이어
   */
  const expandedObjectLayer =
    new VectorLayer({
      source: expandedObjectSource,

      style: (feature) => {
        return createExpandedObjectStyle(
          feature,
          iconSrc,
          iconScale
        );
      },

      declutter: false,
      zIndex: 20,
    });

  return {
    objectLayer,
    objectSource,
    clusterSource,
    expandedObjectLayer,
    expandedObjectSource,
  };
}