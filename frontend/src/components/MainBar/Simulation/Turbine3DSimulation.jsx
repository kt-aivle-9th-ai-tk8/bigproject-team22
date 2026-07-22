import { Canvas } from "@react-three/fiber";
import {
  OrbitControls,
  Environment,
  useGLTF,
  useTexture,
} from "@react-three/drei";
import { useState } from "react";

import "./Turbine3DSimulation.css";

import CloudSphere from "./CloudSphere";
import SkySphere from "./layers/SkySphere";
import GroundPlane from "./layers/GroundPlane";
import GroundEdgeFog from "./layers/GroundEdgeFog";
import HorizonFog from "./layers/HorizonFog";
import ValleyModel from "./layers/ValleyModel";
import TurbineModel from "./layers/TurbineModel";

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
        <Canvas
          dpr={[1, 2]}
          gl={{ antialias: true }}
          camera={{ position: [17.9, 3.583, 0.397], fov: 45, near: 0.1, far: 500 }}
        >
          <SkySphere
            texturePath="/images/simulation-bg.png"
            position={[0, 19, 0]}
            scale={[80, 80, 80]}
          />

          <CloudSphere
            position={[0, 19, 0]}
            scale={[78, 78, 78]}
            opacity={0.85}
            color="#ffffff"
            shadowColor="#ced4da"
          />
          
          {/* <GroundPlane
            texturePath="/images/ground-bg.png"
            position={[0, -3.6, 0]}
            radius={55}
            segments={128}
            repeat={[14, 14]}
          /> */}

          <ValleyModel
            modelPath="/models/valley.glb"
            position={[-7, -9.5, 1]}
            scale={0.08}
            rotation={[0, (150 * Math.PI) / 180 , 0]}
          />

          {/* <GroundEdgeFog
            position={[0, -3.58, 0]}
            radius={55}
            segments={128}
            edgeOpacity={0.75}
          />

          <HorizonFog
            position={[0, -4, 0]}
            radiusTop={80}
            radiusBottom={35}
            height={12}
            segments={96}
          /> */}

          <ambientLight intensity={0.45} />
          <directionalLight position={[4, 6, 4]} intensity={3.0} />

          <TurbineModel
            modelPath="/models/turbine.glb"
            isRunning={isRunning}
            bladeSpeed={bladeSpeed}
            bladeObjectName="Plane002"
            position={[0, -3.6, 0]}
            scale={2}
          />

          {/* <Environment preset="city" /> */}

          <OrbitControls
            enablePan={false}
            minDistance={5}
            maxDistance={30}
            maxPolarAngle={1.8}
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
useGLTF.preload("/models/valley.glb");
useTexture.preload("/images/simulation-bg.png");
useTexture.preload("/images/ground-bg.png");

export default Turbine3DSimulation;