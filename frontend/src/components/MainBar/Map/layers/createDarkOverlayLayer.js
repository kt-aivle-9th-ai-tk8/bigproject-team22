import Feature from "ol/Feature";
import { fromExtent } from "ol/geom/Polygon";

import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";

import Style from "ol/style/Style";
import Fill from "ol/style/Fill";

/*
 * EPSG:3857에서 표시 가능한 전 세계 범위
 */
const WORLD_EXTENT = [
  -20037508.342789244,
  -20037508.342789244,
  20037508.342789244,
  20037508.342789244,
];

export function createDarkOverlayLayer() {
  const overlayFeature = new Feature({
    geometry: fromExtent(WORLD_EXTENT),
  });

  return new VectorLayer({
    source: new VectorSource({
      features: [overlayFeature],
    }),

    style: new Style({
      fill: new Fill({
        color: "rgba(0, 0, 0, 0.15)",
      }),
    }),

    zIndex: 1,
  });
}