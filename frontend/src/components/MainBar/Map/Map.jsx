import { useEffect, useRef } from "react";

import OlMap from "ol/Map";
import View from "ol/View";

import {
  fromLonLat,
  transformExtent,
} from "ol/proj";

import { cities } from "./data/cities";

import { createSatelliteLayer } from "./layers/createSatelliteLayer";
import { createDarkOverlayLayer } from "./layers/createDarkOverlayLayer";
import { createCityLayer } from "./layers/createCityLayer";
import { createObjectLayer } from "./layers/createObjectLayer";

import { createObjectClusterInteraction } from "./interactions/createObjectClusterInteraction";

import "ol/ol.css";
import "./Map.css";

function Map({
  objects = [],
  iconSrc,
  iconScale = 0.07,
  clusterDistance = 40,
  clusterMinDistance = 15,

  singleMarkerFillColor,
  singleMarkerStrokeColor,

  onSelectObject,
}) {
  const mapWrapperRef = useRef(null);
  const mapContainerRef = useRef(null);

  useEffect(() => {
    const wrapperElement =
      mapWrapperRef.current;

    const mapElement =
      mapContainerRef.current;

    if (!wrapperElement || !mapElement) {
      return undefined;
    }

    const apiKey =
      import.meta.env.VITE_VWORLD_API_KEY;

    if (!apiKey) {
      console.error(
        "브이월드 API 키가 없습니다."
      );

      return undefined;
    }

    if (!iconSrc) {
      console.error(
        "Map 컴포넌트에 iconSrc가 없습니다."
      );

      return undefined;
    }

    /*
     * 정상적인 좌표를 가진 객체만 사용
     *
     * coordinate: [경도, 위도]
     */
    const validObjects = objects.filter(
      (object) => {
        return (
          Array.isArray(object.coordinate) &&
          object.coordinate.length === 2 &&
          Number.isFinite(
            object.coordinate[0]
          ) &&
          Number.isFinite(
            object.coordinate[1]
          )
        );
      }
    );

    /*
     * 객체들의 경도와 위도 추출
     */
    const objectLongitudes =
      validObjects.map((object) => {
        return object.coordinate[0];
      });

    const objectLatitudes =
      validObjects.map((object) => {
        return object.coordinate[1];
      });

    /*
     * 객체들의 최소·최대 경도
     *
     * 경도는 지도 중심 계산에만 사용
     */
    const minLongitude =
      objectLongitudes.length > 0
        ? Math.min(...objectLongitudes)
        : 126.0;

    const maxLongitude =
      objectLongitudes.length > 0
        ? Math.max(...objectLongitudes)
        : 130.0;

    /*
     * 객체들의 최소·최대 위도
     *
     * 지도 확대·축소 범위를 결정하는 기준
     */
    const minLatitude =
      objectLatitudes.length > 0
        ? Math.min(...objectLatitudes)
        : 33.0;

    const maxLatitude =
      objectLatitudes.length > 0
        ? Math.max(...objectLatitudes)
        : 38.5;

    /*
     * 객체 범위의 중앙 좌표
     */
    const centerLongitude =
      (minLongitude + maxLongitude) / 2;

    const centerLatitude =
      (minLatitude + maxLatitude) / 2;

    /*
     * 위도 범위 길이
     */
    const latitudeLength =
      maxLatitude - minLatitude;

    /*
     * 위도 범위의 40%를 위아래 여백으로 사용
     *
     * 객체가 하나뿐이거나 모든 위도가 같으면
     * latitudeLength가 0이 되므로 최소 여백 적용
     */
    const latitudePadding = Math.max(
      latitudeLength * 0.2,
      0.0004
    );

    /*
     * view.fit()에 사용할 범위
     *
     * 경도 폭을 매우 좁게 만들어
     * 위도 범위가 확대·축소 수준을 결정하도록 함
     */
    const fitLongitudePadding = 0.000001;

    const fitExtent = transformExtent(
      [
        centerLongitude -
          fitLongitudePadding,

        minLatitude -
          latitudePadding,

        centerLongitude +
          fitLongitudePadding,

        maxLatitude +
          latitudePadding,
      ],
      "EPSG:4326",
      "EPSG:3857"
    );

    /*
     * 어두운 오버레이 표시 범위
     *
     * fitExtent와 분리해서 경도 방향으로
     * 충분히 넓게 표시
     */

    /*
     * 기본 지도 레이어 생성
     */
    const satelliteLayer = createSatelliteLayer(apiKey);

    const darkOverlayLayer = createDarkOverlayLayer();

    const cityLayer =
      createCityLayer(cities);

    /*
     * 발전소 또는 터빈 객체 레이어 생성
     */
    const {
      objectLayer,
      expandedObjectLayer,
      expandedObjectSource,
    } = createObjectLayer({
      objects: validObjects,
      iconSrc,
      iconScale,
      clusterDistance,
      clusterMinDistance,
      singleMarkerFillColor,
      singleMarkerStrokeColor,
    });

    /*
     * 지도 View 생성
     *
     * extent를 지정하지 않았기 때문에
     * 객체 경도 최소·최대 범위를 넘어 이동할 수 있음
     */
    const view = new View({
      center: fromLonLat([
        centerLongitude,
        centerLatitude,
      ]),

      constrainOnlyCenter: false,
      smoothExtentConstraint: false,
    });

    /*
     * OpenLayers 지도 생성
     */
    const map = new OlMap({
      target: mapElement,

      layers: [
        satelliteLayer,
        darkOverlayLayer,
        cityLayer,
        objectLayer,
        expandedObjectLayer,
      ],

      view,

      controls: [],
      interactions: [],
    });

    /*
     * 객체 클러스터 클릭, hover,
     * 펼침 애니메이션 처리
     */
    const clusterInteraction =
      createObjectClusterInteraction({
        map,
        view,
        mapElement,

        objectLayer,
        expandedObjectLayer,
        expandedObjectSource,

        onSelectObject,
      });

    /*
     * 지도 크기와 표시 범위 조정
     */
    const fitMapToExtent = () => {
      const width =
        wrapperElement.clientWidth;

      const height =
        wrapperElement.clientHeight;

      if (width <= 0 || height <= 0) {
        return;
      }

      clusterInteraction.closeImmediately();

      map.updateSize();

      /*
       * 위도 범위 기준으로 지도 확대·축소
       */
      view.fit(fitExtent, {
        size: [width, height],
        padding: [20, 20, 20, 20],
        nearest: false,
      });

      /*
       * 경도 중심은 객체들의 중앙에 고정
       *
       * view.fit() 후에도 중심 경도가
       * 확실히 유지되도록 다시 설정
       */
      const currentCenter =
        view.getCenter();

      const objectCenter =
        fromLonLat([
          centerLongitude,
          centerLatitude,
        ]);

      if (currentCenter && objectCenter) {
        view.setCenter([
          objectCenter[0],
          currentCenter[1],
        ]);
      }
    };

    /*
     * 최초 렌더링 이후 지도 크기 계산
     */
    const animationFrameId =
      requestAnimationFrame(() => {
        fitMapToExtent();
      });

    /*
     * 부모 크기 변경 감지
     */
    const resizeObserver =
      new ResizeObserver(() => {
        fitMapToExtent();
      });

    resizeObserver.observe(
      wrapperElement
    );

    /*
     * 컴포넌트 정리
     */
    return () => {
      cancelAnimationFrame(
        animationFrameId
      );

      resizeObserver.disconnect();

      clusterInteraction.destroy();

      map.setTarget(undefined);
    };
  }, [
    objects,
    iconSrc,
    iconScale,
    clusterDistance,
    clusterMinDistance,
    onSelectObject,
  ]);

  return (
    <div
      ref={mapWrapperRef}
      className="map-wrapper"
    >
      <div
        ref={mapContainerRef}
        className="vworld-map"
        aria-label="객체 위치 지도"
      />
    </div>
  );
}

export default Map;