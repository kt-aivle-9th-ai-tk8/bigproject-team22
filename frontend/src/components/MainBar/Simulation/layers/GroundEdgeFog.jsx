import { CanvasTexture, DoubleSide } from "three";
import { useMemo } from "react";

function GroundEdgeFog({
  position = [0, -3.58, 0],
  radius = 55,
  segments = 128,
  color = [219, 231, 238],
  innerTransparentAt = 0.55,
  fadeStart = 0.78,
  edgeOpacity = 0.75,
}) {
  const edgeFogTexture = useMemo(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 1024;
    canvas.height = 1024;

    const context = canvas.getContext("2d");

    const gradient = context.createRadialGradient(
      canvas.width / 2,
      canvas.height / 2,
      canvas.width * 0.25,
      canvas.width / 2,
      canvas.height / 2,
      canvas.width * 0.5
    );

    const [r, g, b] = color;

    gradient.addColorStop(0, `rgba(${r}, ${g}, ${b}, 0)`);
    gradient.addColorStop(innerTransparentAt, `rgba(${r}, ${g}, ${b}, 0)`);
    gradient.addColorStop(fadeStart, `rgba(${r}, ${g}, ${b}, 0.28)`);
    gradient.addColorStop(1, `rgba(${r}, ${g}, ${b}, ${edgeOpacity})`);

    context.fillStyle = gradient;
    context.fillRect(0, 0, canvas.width, canvas.height);

    const texture = new CanvasTexture(canvas);
    texture.needsUpdate = true;

    return texture;
  }, [color, innerTransparentAt, fadeStart, edgeOpacity]);

  return (
    <mesh rotation={[-Math.PI / 2, 0, 0]} position={position}>
      <circleGeometry args={[radius, segments]} />
      <meshBasicMaterial
        map={edgeFogTexture}
        transparent
        side={DoubleSide}
        depthWrite={false}
      />
    </mesh>
  );
}

export default GroundEdgeFog;