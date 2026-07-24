import { useFrame } from "@react-three/fiber";
import { useGLTF } from "@react-three/drei";
import { useEffect, useRef } from "react";
import * as THREE from "three";

const TWO_PI = Math.PI * 2;

function TurbineModel({
  modelPath = "/models/turbine_fix.glb",
  isRunning,
  bladeSpeed,
  bladeGroupName = "Plane",
  bladeMeshNames = ["Blade_01001", "Blade_02001", "Blade_03001"],
  position = [0, -3.6, 0],
  scale = 2,
  stopSpeed = 0.025,
  onBladeClick,
}) {
  // 실제 회전할 부모 Object3D
  const bladeGroupRef = useRef(null);

  // 클릭 가능한 개별 블레이드 Mesh
  const bladeMeshRefs = useRef([]);

  // Plane의 처음 회전 상태
  const initialGroupQuaternionRef = useRef(null);

  // 처음 위치 기준 누적 회전량
  const accumulatedRotationRef = useRef(0);

  const isStoppingRef = useRef(false);
  const remainingStopRotationRef = useRef(0);
  const prevIsRunningRef = useRef(isRunning);

  const { scene } = useGLTF(modelPath);

  useEffect(() => {
    if (!scene) return;

    bladeMeshRefs.current = [];
    bladeGroupRef.current = null;

    console.log("====== turbine_fix.glb 회전/클릭 대상 확인 ======");

    scene.traverse((object) => {
      if (object.isMesh) {
        object.castShadow = true;
        object.receiveShadow = true;
      }

      if (object.name === bladeGroupName) {
        bladeGroupRef.current = object;

        initialGroupQuaternionRef.current = object.quaternion.clone();
        accumulatedRotationRef.current = 0;

        console.log("회전 대상 Plane 찾음:", object.name, object.type);
      }

      if (object.isMesh && bladeMeshNames.includes(object.name)) {
        bladeMeshRefs.current.push(object);
        console.log("클릭 대상 블레이드 찾음:", object.name);
      }
    });

    console.log(
      "클릭 가능한 블레이드:",
      bladeMeshRefs.current.map((mesh) => mesh.name)
    );

    console.log("============================================");
  }, [scene, bladeGroupName]);

  const restoreInitialBladeGroup = () => {
    if (!bladeGroupRef.current || !initialGroupQuaternionRef.current) return;

    bladeGroupRef.current.quaternion.copy(initialGroupQuaternionRef.current);
    bladeGroupRef.current.updateMatrixWorld(true);

    accumulatedRotationRef.current = 0;
    remainingStopRotationRef.current = 0;
    isStoppingRef.current = false;
  };

  useEffect(() => {
    if (!bladeGroupRef.current || !initialGroupQuaternionRef.current) {
      prevIsRunningRef.current = isRunning;
      return;
    }

    const wasRunning = prevIsRunningRef.current;
    const isJustStopped = wasRunning && !isRunning;

    if (isRunning) {
      isStoppingRef.current = false;
      remainingStopRotationRef.current = 0;
      prevIsRunningRef.current = isRunning;
      return;
    }

    if (isJustStopped) {
      const currentLoopAngle =
        ((accumulatedRotationRef.current % TWO_PI) + TWO_PI) % TWO_PI;

      let remainingToInitialPosition = TWO_PI - currentLoopAngle;

      if (remainingToInitialPosition >= TWO_PI - 0.001) {
        remainingToInitialPosition = 0;
      }

      remainingStopRotationRef.current = remainingToInitialPosition;

      if (remainingToInitialPosition <= 0.001) {
        restoreInitialBladeGroup();
      } else {
        isStoppingRef.current = true;
      }
    }

    prevIsRunningRef.current = isRunning;
  }, [isRunning]);

  useFrame(() => {
    if (!bladeGroupRef.current || !initialGroupQuaternionRef.current) return;

    if (isRunning) {
      bladeGroupRef.current.rotateX(bladeSpeed);
      accumulatedRotationRef.current += bladeSpeed;
      return;
    }

    if (!isStoppingRef.current) return;

    const remainingRotation = remainingStopRotationRef.current;

    if (remainingRotation <= 0.001) {
      restoreInitialBladeGroup();
      return;
    }

    const nextStep = Math.min(
      remainingRotation,
      Math.max(stopSpeed, 0.01)
    );

    bladeGroupRef.current.rotateX(nextStep);
    accumulatedRotationRef.current += nextStep;
    remainingStopRotationRef.current -= nextStep;

    if (remainingStopRotationRef.current <= 0.001) {
      restoreInitialBladeGroup();
    }
  });

  const isClickableBlade = (object) => {
    return bladeMeshRefs.current.includes(object);
  };

  const handlePointerDown = (event) => {
    if (isRunning) return;
    if (isStoppingRef.current) return;
    if (!isClickableBlade(event.object)) return;

    event.stopPropagation();

    const bladeBox = new THREE.Box3().setFromObject(event.object);
    const bladeCenter = new THREE.Vector3();

    bladeBox.getCenter(bladeCenter);

    onBladeClick?.({
      name: event.object.name,
      position: bladeCenter,
    });
  };

  const handlePointerOver = (event) => {
    if (isRunning) return;
    if (isStoppingRef.current) return;
    if (!isClickableBlade(event.object)) return;

    document.body.style.cursor = "pointer";
  };

  const handlePointerOut = () => {
    document.body.style.cursor = "default";
  };

  return (
    <primitive
      object={scene}
      position={position}
      scale={scale}
      onPointerDown={handlePointerDown}
      onPointerOver={handlePointerOver}
      onPointerOut={handlePointerOut}
    />
  );
}

export default TurbineModel;