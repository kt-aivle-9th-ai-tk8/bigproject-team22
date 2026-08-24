import { useFrame, useThree } from "@react-three/fiber";
import { useRef } from "react";
import * as THREE from "three";

function CameraZoomController({
  isZoomed,
  controlsRef,
  zoomTargetPosition,
  isRunning,
}) {
  const { camera } = useThree();

  const normalCameraPosition = useRef(
    new THREE.Vector3(8, 5.5, 0.397)
  );

  const normalTarget = useRef(
    new THREE.Vector3(0, 3, 0)
  );

  const zoomDistance = 3;

  useFrame(() => {
    // 가동 상태에서는 카메라를 자동 제어하지 않음
    if (isRunning) return;

    const targetControlTarget =
      isZoomed && zoomTargetPosition
        ? zoomTargetPosition
        : normalTarget.current;

    let targetCameraPosition = normalCameraPosition.current;

    if (isZoomed && zoomTargetPosition) {
      const viewDirection = new THREE.Vector3()
        .subVectors(normalCameraPosition.current, normalTarget.current)
        .normalize();

      targetCameraPosition = new THREE.Vector3()
        .copy(zoomTargetPosition)
        .add(viewDirection.multiplyScalar(zoomDistance));
    }

    camera.position.lerp(targetCameraPosition, 0.08);

    if (controlsRef.current) {
      controlsRef.current.target.lerp(targetControlTarget, 0.08);
      controlsRef.current.update();
    }
  });

  return null;
}

export default CameraZoomController;