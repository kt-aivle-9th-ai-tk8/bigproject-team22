import { useTexture } from "@react-three/drei";
import { BackSide } from "three";

function SkySphere({
  texturePath = "/images/simulation-bg.png",
  position = [0, 19, 0],
  scale = [80, 80, 80],
}) {
  const texture = useTexture(texturePath);

  return (
    <mesh position={position} scale={scale}>
      <sphereGeometry args={[1, 64, 64]} />
      <meshBasicMaterial map={texture} side={BackSide} />
    </mesh>
  );
}

export default SkySphere;