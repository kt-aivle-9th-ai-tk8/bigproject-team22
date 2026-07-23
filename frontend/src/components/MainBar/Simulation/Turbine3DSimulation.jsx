import { Canvas, useFrame, useThree } from "@react-three/fiber";
import {
  OrbitControls,
  Environment,
  useGLTF,
  useTexture,
} from "@react-three/drei";
import { useRef, useState } from "react";
import * as THREE from "three";

import "./Turbine3DSimulation.css";

import SkySphere from "./layers/SkySphere";
import GroundPlane from "./layers/GroundPlane";
import GroundEdgeFog from "./layers/GroundEdgeFog";
import HorizonFog from "./layers/HorizonFog";
import ValleyModel from "./layers/ValleyModel";
import TurbineModel from "./layers/TurbineModel";
import RainLayer from "./layers/RainLayer";
import CloudyLayer from "./layers/CloudyLayer";
function CameraZoomController({ isZoomed, controlsRef, zoomTargetPosition }) {
  const { camera } = useThree();

  const normalCameraPosition = useRef(
    new THREE.Vector3(17.9, 3.583, 0.397)
  );

  const normalTarget = useRef(
    new THREE.Vector3(0, -0.8, 0)
  );

  useFrame(() => {
    const targetControlTarget =
      isZoomed && zoomTargetPosition
        ? zoomTargetPosition
        : normalTarget.current;

    let targetCameraPosition = normalCameraPosition.current;

    if (isZoomed && zoomTargetPosition) {
      targetCameraPosition = new THREE.Vector3(
        zoomTargetPosition.x + 5,
        zoomTargetPosition.y + 1.2,
        zoomTargetPosition.z + 0.4
      );
    }

    camera.position.lerp(targetCameraPosition, 0.08);

    if (controlsRef.current) {
      controlsRef.current.target.lerp(targetControlTarget, 0.08);
      controlsRef.current.update();
    }
  });

  return null;
}

function Turbine3DSimulation({
  plantName = "장흥 발전소",
  turbineName = "터빈 A",
  onRunSimulation,
}) {
  const [isRunning, setIsRunning] = useState(true);
  const [bladeSpeed, setBladeSpeed] = useState(0.01);
  const [status, setStatus] = useState("normal");
  const [weather, setWeather] = useState("sunny");
  const [isBladeZoomed, setIsBladeZoomed] = useState(false);
  const [bladeZoomTarget, setBladeZoomTarget] = useState(null);

  const controlsRef = useRef(null);

  const isCloudy = weather === "cloudy";
  const isRainy = weather === "rainy";
  const isCloudyWeather = isCloudy || isRainy;

  const handleRunSimulation = () => {
    const simulationData = {
      simulationType: "turbine-3d",
      plantName,
      turbineName,
      isRunning,
      bladeSpeed,
      status,
      weather,
      isBladeZoomed,
    };

    console.log("3D 터빈 시뮬레이션 JSON:", simulationData);
    onRunSimulation?.(simulationData);
  };

  const handleToggleRunning = () => {
    setIsRunning((prev) => {
      const next = !prev;

      if (next) {
        setIsBladeZoomed(false);
      }

      return next;
    });
  };

  return (
    <div className="turbine-3d-simulation">
      <div className="turbine-3d-viewer">
        <Canvas
          dpr={[1, 2]}
          gl={{ antialias: true }}
          camera={{
            position: [17.9, 3.583, 0.397],
            fov: 45,
            near: 0.1,
            far: 500,
          }}
        >
          <CameraZoomController
            isZoomed={isBladeZoomed}
            controlsRef={controlsRef}
            zoomTargetPosition={bladeZoomTarget}
          />

          <SkySphere
            texturePath={
              isCloudyWeather
                ? "/images/cloudy-bg.png"
                : "/images/simulation-bg.png"
            }
            position={[0, 19, 0]}
            scale={[80, 80, 80]}
            rotation={[0, Math.PI / 2, 0]}
          />

          <CloudyLayer mode={weather} />

          <RainLayer
            enabled={isRainy}
            count={4200}
            center={[0, 6, 0]}
            areaSize={[55, 34, 55]}
            fallSpeed={24}
            wind={[0.7, -0.2]}
            windStrength={1.7}
            opacity={0.85}
            density={0.9}
            nearFade={2.5}
            farFade={42}
            fogStrength={0.55}
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
            rotation={[0, (150 * Math.PI) / 180, 0]}
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

          <ambientLight
            intensity={isRainy ? 0.12 : isCloudy ? 0.22 : 0.45}
          />

          <directionalLight
            position={[4, 6, 4]}
            intensity={isRainy ? 0.35 : isCloudy ? 0.75 : 3.0}
          />

          <directionalLight
            position={[4, 6, 4]}
            intensity={isCloudyWeather ? 1.5 : 3.0}
          />

          <TurbineModel
            modelPath="/models/turbine.glb"
            isRunning={isRunning}
            bladeSpeed={bladeSpeed}
            bladeObjectName="Plane002"
            position={[0, -3.6, 0]}
            scale={2}
            stopSpeed={0.025}
            onBladeClick={(bladeWorldPosition) => {
              setBladeZoomTarget(bladeWorldPosition);
              setIsBladeZoomed((prev) => !prev);
            }}
          />

          <OrbitControls
            ref={controlsRef}
            enablePan={false}
            minDistance={5}
            maxDistance={30}
            maxPolarAngle={50}
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
          <label>날씨</label>

          <div className="turbine-weather-button-row">
            <button
              type="button"
              className={weather === "sunny" ? "active" : ""}
              onClick={() => setWeather("sunny")}
            >
              맑음
            </button>

            <button
              type="button"
              className={weather === "cloudy" ? "active" : ""}
              onClick={() => setWeather("cloudy")}
            >
              흐림
            </button>

            <button
              type="button"
              className={weather === "rainy" ? "active" : ""}
              onClick={() => setWeather("rainy")}
            >
              비
            </button>
          </div>
        </div>

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
          onClick={handleToggleRunning}
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
useTexture.preload("/images/cloudy-bg.png");

export default Turbine3DSimulation;