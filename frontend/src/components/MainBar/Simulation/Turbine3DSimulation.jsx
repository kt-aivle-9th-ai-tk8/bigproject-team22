import { Canvas, useFrame } from "@react-three/fiber";
import {
  OrbitControls,
  Environment,
  useGLTF,
  useTexture,
} from "@react-three/drei";
import { BackSide, CanvasTexture, RepeatWrapping, DoubleSide } from "three";
import { useEffect, useMemo, useRef, useState } from "react";
import "./Turbine3DSimulation.css";
import CloudSphere from "./CloudSphere";

function SkySphere() {
  const texture = useTexture("/images/simulation-bg.png");

  return (
    <mesh position={[0, 19, 0]} scale={[80, 80, 80]}>
      <sphereGeometry args={[1, 64, 64]} />
      <meshBasicMaterial map={texture} side={BackSide} />
    </mesh>
  );
}

function GroundPlane() {
  const texture = useTexture("/images/ground-bg.png");

  useEffect(() => {
    if (!texture) return;

    texture.wrapS = RepeatWrapping;
    texture.wrapT = RepeatWrapping;
    texture.repeat.set(14, 14);
    texture.needsUpdate = true;
  }, [texture]);

  return (
    <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -3.6, 0]}>
      <circleGeometry args={[55, 128]} />
      <meshStandardMaterial map={texture} />
    </mesh>
  );
}
function GroundEdgeFog() {
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

    gradient.addColorStop(0, "rgba(219, 231, 238, 0)");
    gradient.addColorStop(0.55, "rgba(219, 231, 238, 0)");
    gradient.addColorStop(0.78, "rgba(219, 231, 238, 0.28)");
    gradient.addColorStop(1, "rgba(219, 231, 238, 0.75)");

    context.fillStyle = gradient;
    context.fillRect(0, 0, canvas.width, canvas.height);

    const texture = new CanvasTexture(canvas);
    texture.needsUpdate = true;

    return texture;
  }, []);

  return (
    <mesh rotation={[-Math.PI / 2, 0, 0]} position={[0, -3.58, 0]}>
      <circleGeometry args={[55, 128]} />
      <meshBasicMaterial
        map={edgeFogTexture}
        transparent
        side={DoubleSide}
        depthWrite={false}
      />
    </mesh>
  );
}
function HorizonFog() {
  const fogTexture = useMemo(() => {
    const canvas = document.createElement("canvas");
    canvas.width = 1024;
    canvas.height = 256;

    const context = canvas.getContext("2d");
    const gradient = context.createLinearGradient(0, 0, 0, canvas.height);

    gradient.addColorStop(0, "rgba(219, 231, 238, 0)");
    gradient.addColorStop(0.35, "rgba(219, 231, 238, 0.28)");
    gradient.addColorStop(0.5, "rgba(219, 231, 238, 0.55)");
    gradient.addColorStop(0.65, "rgba(219, 231, 238, 0.28)");
    gradient.addColorStop(1, "rgba(219, 231, 238, 0)");
    gradient.addColorStop(1, "rgba(219, 231, 238, 0)");
    
    context.fillStyle = gradient;
    context.fillRect(0, 0, canvas.width, canvas.height);

    const texture = new CanvasTexture(canvas);
    texture.needsUpdate = true;

    return texture;
  }, []);

  return (
    <mesh position={[0, -4, 0]}>
      <cylinderGeometry args={[80, 35, 12, 96, 1, true]} />
      <meshBasicMaterial
        map={fogTexture}
        transparent
        side={BackSide}
        depthWrite={false}
      />
    </mesh>
  );
}

function TurbineModel({ isRunning, bladeSpeed }) {
  const bladeRef = useRef(null);
  const { scene } = useGLTF("/models/turbine.glb");

  useFrame(() => {
    if (!bladeRef.current || !isRunning) return;

    bladeRef.current.rotateY(bladeSpeed);
  });

  useEffect(() => {
    if (!scene) return;

    scene.traverse((object) => {
      if (!object.isMesh) return;

      if (object.name === "Plane002") {
        bladeRef.current = object;
      }
    });
  }, [scene]);

  return (
    <primitive
      object={scene}
      position={[0, -3.6, 0]}
      scale={2}
    />
  );
}

function Turbine3DSimulation({
  plantName = "장흥 발전소",
  turbineName = "터빈 A",
  onRunSimulation,
}) {
  const [isRunning, setIsRunning] = useState(true);
  const [bladeSpeed, setBladeSpeed] = useState(0.01);
  const [status, setStatus] = useState("normal");

  const handleRunSimulation = () => {
    const simulationData = {
      simulationType: "turbine-3d",
      plantName,
      turbineName,
      isRunning,
      bladeSpeed,
      status,
    };

    console.log("3D 터빈 시뮬레이션 JSON:", simulationData);
    onRunSimulation?.(simulationData);
  };

  return (
    <div className="turbine-3d-simulation">
      <div className="turbine-3d-viewer">
        <Canvas camera={{ position: [18, 8, 0], fov: 42 }}>
            <SkySphere />
            <CloudSphere
              position={[0, 19, 0]}
              scale={[78, 78, 78]}
              opacity={0.85}
              color="#ffffff"
              shadowColor="#ced4da"
            />
            <GroundPlane />
            <GroundEdgeFog />
            <HorizonFog />

            <ambientLight intensity={0.8} />
            <directionalLight position={[4, 6, 4]} intensity={1.4} />

            <TurbineModel
                isRunning={isRunning}
                bladeSpeed={bladeSpeed}
            />

            <Environment preset="city" />

            <OrbitControls
                enablePan={true}
                minDistance={5}
                maxDistance={50}
                target={[0, -0.8, 0]}
            />
        </Canvas>

        <div className="turbine-3d-label">
          <strong>{plantName}</strong>
          <span>{turbineName} 3D Simulation</span>
        </div>
      </div>

      <div className="turbine-3d-panel">
        <h3>3D 시뮬레이션 제어</h3>

        <div className="turbine-3d-field">
          <label>터빈 상태</label>
          <select
            value={status}
            onChange={(event) => setStatus(event.target.value)}
          >
            <option value="normal">정상</option>
            <option value="warning">경고</option>
            <option value="alert">위험</option>
          </select>
        </div>

        <div className="turbine-3d-field">
          <label>회전 속도</label>
          <input
            type="range"
            min="0"
            max="0.08"
            step="0.005"
            value={bladeSpeed}
            onChange={(event) => setBladeSpeed(Number(event.target.value))}
          />
          <span>{bladeSpeed.toFixed(3)}</span>
        </div>

        <button
          className="turbine-3d-toggle-button"
          type="button"
          onClick={() => setIsRunning((prev) => !prev)}
        >
          {isRunning ? "정지" : "가동"}
        </button>

        <button
          className="turbine-3d-run-button"
          type="button"
          onClick={handleRunSimulation}
        >
          시뮬레이션 저장
        </button>
      </div>
    </div>
  );
}

useGLTF.preload("/models/turbine.glb");
useTexture.preload("/images/simulation-bg.png");
useTexture.preload("/images/ground-bg.png");

export default Turbine3DSimulation;