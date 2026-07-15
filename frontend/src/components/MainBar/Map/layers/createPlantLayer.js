import Feature from "ol/Feature";
import Point from "ol/geom/Point";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import { fromLonLat } from "ol/proj";
import Style from "ol/style/Style";
import Icon from "ol/style/Icon";
import Text from "ol/style/Text";
import Fill from "ol/style/Fill";
import Stroke from "ol/style/Stroke";

import plantIcon from "../../../../assets/icon/plant.png";

export function createPlantLayer(plants) {
    const plantFeatures = plants.map((plant) => {
        return new Feature({
            geometry: new Point(fromLonLat(plant.coordinate)),
            plantId: plant.id,
            plantName: plant.name,
            plantData: plant,
        });
    });

    return new VectorLayer({
        source: new VectorSource({
            features: plantFeatures,
        }),
        style: (feature) => {
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
        },
        declutter: true,
        zIndex: 10,
    });
}