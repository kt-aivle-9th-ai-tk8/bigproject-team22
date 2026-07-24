import { Canvas } from "@react-three/fiber";
import {
  OrbitControls,
  Environment,
  Html,
  useGLTF,
  useTexture,
} from "@react-three/drei";
import { useRef, useState } from "react";

import "./Turbine3DSimulation.css";

import SkySphere from "./layers/SkySphere";
import GroundPlane from "./layers/GroundPlane";
import GroundEdgeFog from "./layers/GroundEdgeFog";
import HorizonFog from "./layers/HorizonFog";
import ValleyModel from "./layers/ValleyModel";
import TurbineModel from "./layers/TurbineModel";
import RainLayer from "./layers/RainLayer";
import CloudyLayer from "./layers/CloudyLayer";
import CameraZoomController from "./CameraZoomController";
import BladeInfoPopup from "./BladeInfoPopup";
import Turbine3DSidebar from "./Turbine3DSidebar";


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
  const [selectedBladeName, setSelectedBladeName] = useState("");
  const [isBladePopupOpen, setIsBladePopupOpen] = useState(false);

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
        setBladeZoomTarget(null);
        setSelectedBladeName("");
        setIsBladePopupOpen(false);
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
            isRunning={isRunning}
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

          <ValleyModel
            modelPath="/models/valley.glb"
            position={[-7, -9.5, 1]}
            scale={0.08}
            rotation={[0, (150 * Math.PI) / 180, 0]}
          />

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
            modelPath="/models/turbine_fix.glb"
            isRunning={isRunning}
            bladeSpeed={bladeSpeed}
            bladeGroupName="Plane"
            bladeMeshNames={["Blade_01001", "Blade_02001", "Blade_03001"]}
            position={[0, -3.6, 0]}
            scale={2}
            stopSpeed={0.1}
            onBladeClick={(bladeData) => {
              console.log("클릭한 블레이드:", bladeData.name);
              setBladeZoomTarget(bladeData.position);
              setSelectedBladeName(bladeData.name);
              setIsBladePopupOpen(false);
              setIsBladeZoomed(true);
            }}
          />
          {isBladeZoomed && bladeZoomTarget && !isBladePopupOpen && (
            <Html
              position={[
                bladeZoomTarget.x,
                bladeZoomTarget.y,
                bladeZoomTarget.z,
              ]}
              center
              zIndexRange={[30, 0]}
            >
              <button
                className="blade-center-action-button"
                type="button"
                onClick={(event) => {
                  event.stopPropagation();
                  setIsBladePopupOpen(true);
                }}
                aria-label="블레이드 상세 열기"
                title="블레이드 상세"
              />
            </Html>
          )}

          <OrbitControls
            ref={controlsRef}
            enablePan={false}
            minDistance={5}
            maxDistance={30}
            maxPolarAngle={50}
            target={[0, -0.8, 0]}
          />
        </Canvas>

        {isBladeZoomed && (
          <button
            className="blade-zoom-close-button"
            type="button"
            onClick={() => {
              setIsBladeZoomed(false);
              setBladeZoomTarget(null);
              setSelectedBladeName("");
              setIsBladePopupOpen(false);
            }}
            aria-label="블레이드 확대 닫기"
          >
            ×
          </button>
        )}

        {isBladePopupOpen && (
          <BladeInfoPopup
            selectedBladeName={selectedBladeName}
            onClose={() => setIsBladePopupOpen(false)}
          />
        )}


        <div className="turbine-3d-label">
          <strong>{plantName}</strong>
          <span>{turbineName} 3D Simulation</span>
        </div>
      </div>

      <Turbine3DSidebar
        weather={weather}
        onChangeWeather={setWeather}
        bladeSpeed={bladeSpeed}
        onChangeBladeSpeed={setBladeSpeed}
        isRunning={isRunning}
        onToggleRunning={handleToggleRunning}
      />

    </div>
  );
}

useGLTF.preload("/models/turbine_fix.glb");
useGLTF.preload("/models/valley.glb");
useTexture.preload("/images/simulation-bg.png");
useTexture.preload("/images/cloudy-bg.png");

export default Turbine3DSimulation;