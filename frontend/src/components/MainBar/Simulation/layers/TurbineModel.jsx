import { useFrame } from "@react-three/fiber";
import { useGLTF } from "@react-three/drei";
import { useEffect, useRef } from "react";
import * as THREE from "three";

const TWO_PI = Math.PI * 2;

function TurbineModel({
  modelPath = "/models/turbine.glb",
  isRunning,
  bladeSpeed,
  bladeObjectName = "Plane002",
  position = [0, -3.6, 0],
  scale = 2,
  stopSpeed = 0.025,
  onBladeClick,
}) {
  const bladeRef = useRef(null);

  // 처음 A 블레이드 위치
  const initialRotationYRef = useRef(null);

  // 처음 위치 기준으로 얼마나 돌았는지 누적 저장
  const accumulatedRotationRef = useRef(0);

  // 정지 중인지
  const isStoppingRef = useRef(false);

  // 정지 위치까지 남은 회전량
  const remainingStopRotationRef = useRef(0);

  // 이전 isRunning 값
  const prevIsRunningRef = useRef(isRunning);

  const { scene } = useGLTF(modelPath);

  useEffect(() => {
    if (!scene) return;

    scene.traverse((object) => {
      if (!object.isMesh) return;

      if (object.name === bladeObjectName) {
        bladeRef.current = object;

        if (initialRotationYRef.current === null) {
          initialRotationYRef.current = object.rotation.y;
          accumulatedRotationRef.current = 0;
        }
      }
    });
  }, [scene, bladeObjectName]);

  useEffect(() => {
    if (!bladeRef.current || initialRotationYRef.current === null) {
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

      const remainingToInitialPosition =
        currentLoopAngle === 0
          ? 0
          : TWO_PI - currentLoopAngle;

      remainingStopRotationRef.current = remainingToInitialPosition;
      isStoppingRef.current = remainingToInitialPosition > 0.001;
    }

    prevIsRunningRef.current = isRunning;
  }, [isRunning]);

  useFrame(() => {
    if (!bladeRef.current || initialRotationYRef.current === null) return;

    if (isRunning) {
      bladeRef.current.rotateY(bladeSpeed);
      accumulatedRotationRef.current += bladeSpeed;
      return;
    }

    if (!isStoppingRef.current) return;

    const remainingRotation = remainingStopRotationRef.current;

    if (remainingRotation <= 0.001) {
      bladeRef.current.rotation.y = initialRotationYRef.current;
      accumulatedRotationRef.current = 0;
      remainingStopRotationRef.current = 0;
      isStoppingRef.current = false;
      return;
    }

    const nextStep = Math.min(
      remainingRotation,
      Math.max(stopSpeed, 0.01)
    );

    bladeRef.current.rotateY(nextStep);
    accumulatedRotationRef.current += nextStep;
    remainingStopRotationRef.current -= nextStep;

    if (remainingStopRotationRef.current <= 0.001) {
      bladeRef.current.rotation.y = initialRotationYRef.current;
      accumulatedRotationRef.current = 0;
      remainingStopRotationRef.current = 0;
      isStoppingRef.current = false;
    }
  });
  const handlePointerDown = (event) => {
    if (!bladeRef.current) return;
    if (isRunning) return;
    if (isStoppingRef.current) return;

    if (event.object !== bladeRef.current) return;

    event.stopPropagation();

    const bladeWorldPosition = new THREE.Vector3();
    bladeRef.current.getWorldPosition(bladeWorldPosition);

    onBladeClick?.(bladeWorldPosition);
  };

  const handlePointerOver = (event) => {
    if (!bladeRef.current) return;
    if (isRunning) return;
    if (isStoppingRef.current) return;

    if (event.object !== bladeRef.current) return;

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