import { useFrame } from "@react-three/fiber";
import { useGLTF } from "@react-three/drei";
import { useEffect, useRef } from "react";

function TurbineModel({
  modelPath = "/models/turbine.glb",
  isRunning,
  bladeSpeed,
  bladeObjectName = "Plane002",
  position = [0, -3.6, 0],
  scale = 2,
}) {
  const bladeRef = useRef(null);
  const { scene } = useGLTF(modelPath);

  useFrame(() => {
    if (!bladeRef.current || !isRunning) return;

    bladeRef.current.rotateY(bladeSpeed);
  });

  useEffect(() => {
    if (!scene) return;

    scene.traverse((object) => {
      if (!object.isMesh) return;

      if (object.name === bladeObjectName) {
        bladeRef.current = object;
      }
    });
  }, [scene, bladeObjectName]);

  return (
    <primitive
      object={scene}
      position={position}
      scale={scale}
    />
  );
}

export default TurbineModel;