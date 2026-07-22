import { BackSide, CanvasTexture } from "three";
import { useMemo } from "react";

function HorizonFog({
  position = [0, -4, 0],
  radiusTop = 80,
  radiusBottom = 35,
  height = 12,
  segments = 96,
  color = [219, 231, 238],
}) {
  const fogTexture = useMemo(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 1024;
    canvas.height = 256;

    const context = canvas.getContext("2d");
    const gradient = context.createLinearGradient(0, 0, 0, canvas.height);

    const [r, g, b] = color;

    gradient.addColorStop(0, `rgba(${r}, ${g}, ${b}, 0)`);
    gradient.addColorStop(0.35, `rgba(${r}, ${g}, ${b}, 0.28)`);
    gradient.addColorStop(0.5, `rgba(${r}, ${g}, ${b}, 0.55)`);
    gradient.addColorStop(0.65, `rgba(${r}, ${g}, ${b}, 0.28)`);
    gradient.addColorStop(1, `rgba(${r}, ${g}, ${b}, 0)`);

    context.fillStyle = gradient;
    context.fillRect(0, 0, canvas.width, canvas.height);

    const texture = new CanvasTexture(canvas);
    texture.needsUpdate = true;

    return texture;
  }, [color]);

  return (
    <mesh position={position}>
      <cylinderGeometry
        args={[radiusTop, radiusBottom, height, segments, 1, true]}
      />
      <meshBasicMaterial
        map={fogTexture}
        transparent
        side={BackSide}
        depthWrite={false}
      />
    </mesh>
  );
}

export default HorizonFog;