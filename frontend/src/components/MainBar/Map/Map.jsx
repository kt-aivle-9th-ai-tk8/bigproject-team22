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
import { createPlantLayer } from "./layers/createPlantLayer";

import { createPlantClusterInteraction } from "./interactions/createPlantClusterInteraction";

import "ol/ol.css";
import "./Map.css";

function Map({
  plants = [],
  onSelectPlant,
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

    const validPlants = plants.filter(
      (plant) => {
        return (
          Array.isArray(plant.coordinate) &&
          plant.coordinate.length === 2 &&
          Number.isFinite(
            plant.coordinate[0]
          ) &&
          Number.isFinite(
            plant.coordinate[1]
          )
        );
      }
    );

    const plantLatitudes =
      validPlants.map((plant) => {
        return plant.coordinate[1];
      });

    const minLatitude =
      plantLatitudes.length > 0
        ? Math.min(...plantLatitudes)
        : 33.0;

    const maxLatitude =
      plantLatitudes.length > 0
        ? Math.max(...plantLatitudes)
        : 38.5;

    const mapExtent = transformExtent(
      [
        124.0,
        minLatitude - 0.5,
        131.5,
        maxLatitude + 0.5,
      ],
      "EPSG:4326",
      "EPSG:3857"
    );

    const satelliteLayer =
      createSatelliteLayer(apiKey);

    const darkOverlayLayer =
      createDarkOverlayLayer(mapExtent);

    const cityLayer =
      createCityLayer(cities);

    const {
        plantLayer,
        expandedPlantLayer,
        expandedPlantSource,
    } = createPlantLayer(validPlants);

    const view = new View({
      center: fromLonLat([
        127.75,
        (minLatitude + maxLatitude) / 2,
      ]),

      extent: mapExtent,

      constrainOnlyCenter: false,
      smoothExtentConstraint: false,
    });

    const map = new OlMap({
        target: mapElement,

        layers: [
            satelliteLayer,
            darkOverlayLayer,
            cityLayer,
            plantLayer,
            expandedPlantLayer,
        ],

        view,

        controls: [],
        interactions: [],
    });

    const clusterInteraction =
        createPlantClusterInteraction({
            map,
            view,
            mapElement,
            plantLayer,
            expandedPlantLayer,
            expandedPlantSource,
            onSelectPlant,
        });

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

      view.fit(mapExtent, {
        size: [width, height],
        padding: [20, 20, 20, 20],
        nearest: false,
      });
    };

    const animationFrameId =
      requestAnimationFrame(() => {
        fitMapToExtent();
      });

    const resizeObserver =
      new ResizeObserver(() => {
        fitMapToExtent();
      });

    resizeObserver.observe(
      wrapperElement
    );

    return () => {
      cancelAnimationFrame(
        animationFrameId
      );

      resizeObserver.disconnect();

      clusterInteraction.destroy();

      map.setTarget(undefined);
    };
  }, [plants, onSelectPlant]);

  return (
    <div
      ref={mapWrapperRef}
      className="map-wrapper"
    >
      <div
        ref={mapContainerRef}
        className="vworld-map"
        aria-label="발전소 위치 지도"
      />
    </div>
  );
}

export default Map;