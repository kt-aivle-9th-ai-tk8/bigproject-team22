import Feature from "ol/Feature";
import Point from "ol/geom/Point";

import VectorLayer from "ol/layer/Vector";

import VectorSource from "ol/source/Vector";
import Cluster from "ol/source/Cluster";

import { fromLonLat } from "ol/proj";
import { TURBINE_STATUS } from "../../SideBar/Turbine/TurbineItem";

import Style from "ol/style/Style";
import Icon from "ol/style/Icon";
import Text from "ol/style/Text";
import Fill from "ol/style/Fill";
import Stroke from "ol/style/Stroke";
import CircleStyle from "ol/style/Circle";

const SINGLE_MARKER_RADIUS = 30;
const SINGLE_MARKER_STROKE_WIDTH = 3;
const SINGLE_MARKER_ICON_SIZE = 70;
const SINGLE_MARKER_TEXT_OFFSET_Y = 18;

const CLUSTER_RADIUS = 30;

const getCssVariableColor = (variableName, fallbackColor) => {
  const value = getComputedStyle(document.documentElement)
    .getPropertyValue(variableName)
    .trim();

  return value || fallbackColor;
};

const getTurbineMarkerColors = (
  status,
  defaultFillColor,
  defaultStrokeColor
) => {
  if (status === TURBINE_STATUS.ZERO_POWER) {
    return {
      fillColor: "#ffe8e8",
      strokeColor: "#fd474a",
    };
  }

  if (status === TURBINE_STATUS.NO_DATA) {
    return {
      fillColor: "#fcf9d6",
      strokeColor: "#e9d821",
    };
  }

  if (status === TURBINE_STATUS.NORMAL) {
    return {
      fillColor: defaultFillColor,
      strokeColor: defaultStrokeColor,
    };
  }

  return {
    fillColor: defaultFillColor,
    strokeColor: defaultStrokeColor,
  };
};

/*
 * 단일 객체 아이콘 스타일
 *
 * 이미지 합성 X
 * 원 배경은 CircleStyle
 * SVG 아이콘은 Icon
 */
const createSingleObjectStyle = (
  objectFeature,
  iconSrc,
  singleMarkerFillColor,
  singleMarkerStrokeColor
) => {
  const objectData = objectFeature.get("objectData");

  const {
    fillColor,
    strokeColor,
  } = getTurbineMarkerColors(
    objectData?.status,
    singleMarkerFillColor,
    singleMarkerStrokeColor
  );

  return [
    new Style({
      image: new CircleStyle({
        radius: SINGLE_MARKER_RADIUS,

        fill: new Fill({
          color: fillColor,
        }),

        stroke: new Stroke({
          color: strokeColor,
          width: SINGLE_MARKER_STROKE_WIDTH,
        }),
      }),
    }),

    new Style({
      image: new Icon({
        src: iconSrc,
        anchor: [0.5, 0.5],
        width: SINGLE_MARKER_ICON_SIZE,
        height: SINGLE_MARKER_ICON_SIZE,
      }),

      text: new Text({
        text: objectFeature.get("objectName"),
        offsetY: SINGLE_MARKER_TEXT_OFFSET_Y,

        font: "bold 12px sans-serif",

        fill: new Fill({
          color: "#ffffff",
        }),

        stroke: new Stroke({
          color: "#222222",
          width: 3,
        }),
      }),
    }),
  ];
};

/*
 * 여러 객체가 묶였을 때 표시되는 숫자 원 스타일
 */
const createClusterStyle = (count) => {
  return new Style({
    image: new CircleStyle({
      radius: CLUSTER_RADIUS,

      fill: new Fill({
        color: getCssVariableColor("--color-point", "#b4cfff"),
      }),

      stroke: new Stroke({
        color: getCssVariableColor(
          "--color-point-bright",
          "#ECF2FE"
        ),
        width: 3,
      }),
    }),

    text: new Text({
      text: String(count),
      font: "bold 22px sans-serif",

      fill: new Fill({
        color: "#000000",
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
  singleMarkerFillColor,
  singleMarkerStrokeColor
) => {
  const objectData = feature.get("objectData");

  const {
    fillColor,
    strokeColor,
  } = getTurbineMarkerColors(
    objectData?.status,
    singleMarkerFillColor,
    singleMarkerStrokeColor
  );
  return [
    new Style({
      image: new CircleStyle({
        radius: SINGLE_MARKER_RADIUS,

        fill: new Fill({
          color: fillColor,
        }),

        stroke: new Stroke({
          color: strokeColor,
          width: SINGLE_MARKER_STROKE_WIDTH,
        }),
      }),
    }),

    new Style({
      image: new Icon({
        src: iconSrc,
        anchor: [0.5, 0.5],
        width: SINGLE_MARKER_ICON_SIZE,
        height: SINGLE_MARKER_ICON_SIZE,
      }),

      text: new Text({
        text: feature.get("objectName"),
        offsetY: SINGLE_MARKER_TEXT_OFFSET_Y,

        font: "bold 12px sans-serif",

        fill: new Fill({
          color: "#ffffff",
        }),

        stroke: new Stroke({
          color: "#222222",
          width: 3,
        }),
      }),
    }),
  ];
};

export function createObjectLayer({
  objects = [],
  iconSrc,
  clusterDistance = 40,
  clusterMinDistance = 15,

  singleMarkerFillColor = getCssVariableColor(
    "--color-point-bright",
    "#ECF2FE"
  ),

  singleMarkerStrokeColor = getCssVariableColor(
    "--color-point",
    "#b4cfff"
  ),
}) {
  if (!iconSrc) {
    throw new Error("createObjectLayer에 iconSrc가 필요합니다.");
  }

  const objectFeatures = objects.map((object) => {
    return new Feature({
      geometry: new Point(fromLonLat(object.coordinate)),

      objectId: object.id,
      objectName: object.name,
      objectData: object,
    });
  });

  const objectSource = new VectorSource({
    features: objectFeatures,
  });

  const clusterSource = new Cluster({
    distance: clusterDistance,
    minDistance: clusterMinDistance,
    source: objectSource,
  });

  const objectLayer = new VectorLayer({
    source: clusterSource,

    style: (clusterFeature) => {
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
          singleMarkerFillColor,
          singleMarkerStrokeColor
        );
      }

      return createClusterStyle(count);
    },

    /*
     * 아이콘 누락 방지
     */
    declutter: false,
    zIndex: 10,
  });

  const expandedObjectSource = new VectorSource();

  const expandedObjectLayer = new VectorLayer({
    source: expandedObjectSource,

    style: (feature) => {
      return createExpandedObjectStyle(
        feature,
        iconSrc,
        singleMarkerFillColor,
        singleMarkerStrokeColor
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