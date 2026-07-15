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

import plantIcon from "../../../../assets/icon/plant.png";

/*
 * 단일 발전소 아이콘 스타일
 */
const createSinglePlantStyle = (plantFeature) => {
  return new Style({
    image: new Icon({
      src: plantIcon,

      // 아이콘 아래쪽 중앙을 발전소 좌표에 맞춤
      anchor: [0.5, 1],

      scale: 0.07,
    }),

    text: new Text({
      text: plantFeature.get("plantName"),

      // 아이콘 위쪽에 발전소 이름 표시
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
 * 여러 발전소가 묶였을 때 표시되는 숫자 원 스타일
 */
const createClusterStyle = (count) => {
  return new Style({
    image: new CircleStyle({
      radius: 30,

      fill: new Fill({
        color: "#0B50D1",
      }),

      stroke: new Stroke({
        color: "#ffffff",
        width: 3,
      }),
    }),

    text: new Text({
      text: String(count),

      font: "bold 20px sans-serif",

      fill: new Fill({
        color: "#ffffff",
      }),
    }),
  });
};

/*
 * 클러스터를 펼쳤을 때 표시되는 개별 발전소 스타일
 */
const createExpandedPlantStyle = (feature) => {
  return new Style({
    image: new Icon({
      src: plantIcon,
      anchor: [0.5, 1],
      scale: 0.07,
    }),

    text: new Text({
      text: feature.get("plantName"),
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

export function createPlantLayer(plants) {
  /*
   * 실제 발전소 Feature 생성
   */
  const plantFeatures = plants.map((plant) => {
    return new Feature({
      geometry: new Point(
        fromLonLat(plant.coordinate)
      ),

      plantId: plant.id,
      plantName: plant.name,
      plantData: plant,
    });
  });

  /*
   * 실제 발전소 Feature를 저장하는 원본 Source
   */
  const plantSource = new VectorSource({
    features: plantFeatures,
  });

  /*
   * 가까운 발전소 Feature들을 하나로 묶는 Cluster Source
   *
   * distance와 minDistance는 화면 픽셀 기준
   */
  const clusterSource = new Cluster({
    distance: 40,
    minDistance: 15,
    source: plantSource,
  });

  /*
   * 단일 발전소와 숫자 클러스터를 표시하는 레이어
   */
    const plantLayer = new VectorLayer({
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
            return createSinglePlantStyle(
                clusteredFeatures[0]
            );
            }

            return createClusterStyle(count);
        },

        declutter: true,
        zIndex: 10,
    });

  /*
   * 펼쳐진 개별 발전소 Feature를 저장하는 Source
   *
   * createPlantClusterInteraction.js에서
   * 임시 Feature들을 추가하고 제거한다.
   */
  const expandedPlantSource =
    new VectorSource();

  /*
   * 펼쳐진 개별 발전소 아이콘을 표시하는 레이어
   */
  const expandedPlantLayer =
    new VectorLayer({
      source: expandedPlantSource,

      style: (feature) => {
        return createExpandedPlantStyle(feature);
      },

      /*
       * 펼쳐진 발전소 이름끼리는 겹칠 수 있어도
       * 아이콘이 사라지지 않게 false로 설정
       */
      declutter: false,

      /*
       * 기본 발전소 레이어보다 위에 표시
       */
      zIndex: 20,
    });

  return {
    plantLayer,
    expandedPlantLayer,
    expandedPlantSource,
  };
}