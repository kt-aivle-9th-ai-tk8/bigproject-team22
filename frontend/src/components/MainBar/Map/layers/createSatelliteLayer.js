import TileLayer from "ol/layer/Tile";
import XYZ from "ol/source/XYZ";

export function createSatelliteLayer(apiKey) {
    return new TileLayer({
        source: new XYZ({
            url: `https://api.vworld.kr/req/wmts/1.0.0/${apiKey}/Satellite/{z}/{y}/{x}.jpeg`,
            minZoom: 6,
            maxZoom: 19,
            crossOrigin: "anonymous",
        }),
        zIndex: 0,
    });

}