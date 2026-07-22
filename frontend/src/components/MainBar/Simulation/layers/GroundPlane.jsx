import { useTexture } from "@react-three/drei";
import { RepeatWrapping } from "three";
import { useEffect } from "react";

function GroundPlane({
  texturePath = "/images/ground-bg.png",
  position = [0, -3.6, 0],
  radius = 55,
  segments = 128,
  repeat = [14, 14],
}) {
  const texture = useTexture(texturePath);

  useEffect(() => {
    if (!texture) return;

    texture.wrapS = RepeatWrapping;
    texture.wrapT = RepeatWrapping;
    texture.repeat.set(repeat[0], repeat[1]);
    texture.needsUpdate = true;
  }, [texture, repeat]);

  return (
    <mesh rotation={[-Math.PI / 2, 0, 0]} position={position}>
      <circleGeometry args={[radius, segments]} />
      <meshStandardMaterial map={texture} />
    </mesh>
  );
}

export default GroundPlane;