import Feature from "ol/Feature";
import { fromExtent } from "ol/geom/Polygon";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Style from "ol/style/Style";
import Fill from "ol/style/Fill";



export function createDarkOverlayLayer(mapExtent) {
    const overlayFeature = new Feature({
        geometry: fromExtent(mapExtent),
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