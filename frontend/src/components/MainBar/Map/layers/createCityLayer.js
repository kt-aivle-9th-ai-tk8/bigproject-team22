import Feature from "ol/Feature";
import Point from "ol/geom/Point";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";

import { fromLonLat } from "ol/proj";

import Style from "ol/style/Style";
import Text from "ol/style/Text";
import Fill from "ol/style/Fill";
import Stroke from "ol/style/Stroke";
import CircleStyle from "ol/style/Circle";

export function createCityLayer(cities) {

    const cityFeatures = cities.map((city) => {
        return new Feature({
            geometry: new Point(fromLonLat(city.coordinate)),
            cityName: city.name,
        });
    });

    return new VectorLayer({
        source: new VectorSource({features: cityFeatures,}),
        style: (feature) => {
            return new Style({
                image: new CircleStyle({
                    radius: 4,
                    fill: new Fill({
                        color: "#ffffff",
                    }),
                    stroke: new Stroke({
                        color: "#222222",
                        width: 2,
                    }),
                }),

                text: new Text({
                    text: feature.get("cityName"),
                    offsetY: -15,
                    font: "bold 15px sans-serif",
                    fill: new Fill({
                        color: "#ffffff",
                    }),
                    stroke: new Stroke({
                        color: "#222222",
                        width: 4,
                    }),
                }),
            });
        },
        declutter: true,
        zIndex: 5,
    });
}