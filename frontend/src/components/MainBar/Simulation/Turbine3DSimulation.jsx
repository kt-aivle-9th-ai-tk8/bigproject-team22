import { Canvas } from "@react-three/fiber";
import {
  OrbitControls,
  Html,
  useGLTF,
  useTexture,
} from "@react-three/drei";
import { useRef, useState } from "react";

import "./Turbine3DSimulation.css";

import SkySphere from "./layers/SkySphere";
import ValleyModel from "./layers/ValleyModel";
import TurbineModel from "./layers/TurbineModel";
import RainLayer from "./layers/RainLayer";
import CloudyLayer from "./layers/CloudyLayer";
import CameraZoomController from "./CameraZoomController";
import BladeInfoPopup from "./BladeInfoPopup";
import Turbine3DSidebar from "./Turbine3DSidebar";
// Vite를 사용 중이라면 import.meta.env로 접근합니다.

/*
 * 풍력 터빈 운전 기준
 */
const CUT_IN_WIND_SPEED = 3;
const RATED_WIND_SPEED = 12;
const CUT_OUT_WIND_SPEED = 25;

const MIN_BLADE_SPEED = 0.01;
const MAX_BLADE_SPEED = 0.08;

/*
 * 풍속에 따른 터빈 회전 속도 계산
 *
 * 0 ~ 3m/s:
 *   시동 풍속 미달, 회전하지 않음
 *
 * 3 ~ 12m/s:
 *   풍속에 비례하여 회전 속도 증가
 *
 * 12 ~ 25m/s:
 *   정격 회전 속도 유지
 *
 * 25m/s 이상:
 *   강풍으로 인한 안전 정지
 */
function calculateBladeSpeed(windSpeed) {
  if (!Number.isFinite(windSpeed)) {
    return 0;
  }

  if (windSpeed < CUT_IN_WIND_SPEED) {
    return 0;
  }

  if (windSpeed >= CUT_OUT_WIND_SPEED) {
    return 0;
  }

  if (windSpeed >= RATED_WIND_SPEED) {
    return MAX_BLADE_SPEED;
  }

  const windSpeedRatio =
    (windSpeed - CUT_IN_WIND_SPEED) /
    (RATED_WIND_SPEED - CUT_IN_WIND_SPEED);

  return (
    MIN_BLADE_SPEED +
    windSpeedRatio * (MAX_BLADE_SPEED - MIN_BLADE_SPEED)
  );
}

/*
 * 현재 터빈 운전 상태 계산
 */
function calculateTurbineStatus(windSpeed, isRunning) {
  if (!isRunning) {
    return "manual-stop";
  }

  if (windSpeed < CUT_IN_WIND_SPEED) {
    return "below-cut-in";
  }

  if (windSpeed >= CUT_OUT_WIND_SPEED) {
    return "cut-out";
  }

  if (windSpeed >= RATED_WIND_SPEED) {
    return "rated";
  }

  return "normal";
}

function Turbine3DSimulation({
  plantName = "장흥 발전소",
  turbineName = "터빈 A",
  onRunSimulation,
}) {
  /*
   * 풍속만 상태로 관리합니다.
   * bladeSpeed는 풍속을 기준으로 자동 계산합니다.
   */
  const [windSpeed, setWindSpeed] = useState(8);
  const [isRunning, setIsRunning] = useState(true);
  const [weather, setWeather] = useState("sunny");

  const [isBladeZoomed, setIsBladeZoomed] = useState(false);
  const [bladeZoomTarget, setBladeZoomTarget] = useState(null);
  const [selectedBladeName, setSelectedBladeName] = useState("");
  const [isBladePopupOpen, setIsBladePopupOpen] = useState(false);

  const controlsRef = useRef(null);

  const isCloudy = weather === "cloudy";
  const isRainy = weather === "rainy";
  const isCloudyWeather = isCloudy || isRainy;

  /*
   * 현재 풍속으로 계산한 이론적인 회전 속도
   */
  const calculatedBladeSpeed = calculateBladeSpeed(windSpeed);

  /*
   * 사용자가 수동 정지했으면 실제 모델 회전 속도는 0
   */
  const bladeSpeed = isRunning ? calculatedBladeSpeed : 0;

  const status = calculateTurbineStatus(windSpeed, isRunning);

  /*
   * 풍속 변경
   */
  const handleChangeWindSpeed = (newWindSpeed) => {
    const normalizedWindSpeed = Math.min(
      Math.max(Number(newWindSpeed), 0),
      30
    );

    setWindSpeed(normalizedWindSpeed);
  };

  const handleRunSimulation = () => {
    const simulationData = {
      simulationType: "turbine-3d",
      plantName,
      turbineName,
      isRunning,
      windSpeed,
      bladeSpeed,
      status,
      weather,
      isBladeZoomed,
      operatingLimits: {
        cutInWindSpeed: CUT_IN_WIND_SPEED,
        ratedWindSpeed: RATED_WIND_SPEED,
        cutOutWindSpeed: CUT_OUT_WIND_SPEED,
        minimumBladeSpeed: MIN_BLADE_SPEED,
        maximumBladeSpeed: MAX_BLADE_SPEED,
      },
    };

    console.log("3D 터빈 시뮬레이션 JSON:", simulationData);
    onRunSimulation?.(simulationData);
  };

  const handleToggleRunning = () => {
    setIsRunning((previousIsRunning) => {
      const nextIsRunning = !previousIsRunning;

      /*
       * 다시 가동할 때 블레이드 확대 상태 초기화
       */
      if (nextIsRunning) {
        setIsBladeZoomed(false);
        setBladeZoomTarget(null);
        setSelectedBladeName("");
        setIsBladePopupOpen(false);
      }

      return nextIsRunning;
    });
  };

  return (
    <div className="turbine-3d-simulation">
      <div className="turbine-3d-viewer">
        <Canvas
          shadows
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
            castShadow
            position={[4, 8, 4]}
            intensity={isRainy ? 0.8 : isCloudy ? 1.5 : 4}
            shadow-mapSize-width={2048}
            shadow-mapSize-height={2048}
            shadow-camera-near={0.5}
            shadow-camera-far={80}
            shadow-camera-left={-25}
            shadow-camera-right={25}
            shadow-camera-top={25}
            shadow-camera-bottom={-25}
          />

          <mesh
            rotation={[-Math.PI / 2, 0, 0]}
            position={[0, -3.62, 0]}
            receiveShadow
          >
            <planeGeometry args={[80, 80]} />
            <shadowMaterial transparent opacity={0.28} />
          </mesh>

          <TurbineModel
            modelPath="/models/turbine_fix.glb"
            /*
             * 수동 정지뿐 아니라 시동 풍속 미달 및
             * 차단 풍속에서도 회전하지 않도록 처리합니다.
             */
            isRunning={isRunning && bladeSpeed > 0}
            bladeSpeed={bladeSpeed}
            bladeGroupName="Plane"
            bladeMeshNames={[
              "Blade_01001",
              "Blade_02001",
              "Blade_03001",
            ]}
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

          {isBladeZoomed &&
            bladeZoomTarget &&
            !isBladePopupOpen && (
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
        windSpeed={windSpeed}
        onChangeWindSpeed={handleChangeWindSpeed}
        bladeSpeed={bladeSpeed}
        status={status}
        isRunning={isRunning}
        onToggleRunning={handleToggleRunning}
        onRunSimulation={handleRunSimulation}
      />
    </div>
  );
}

useGLTF.preload("/models/turbine_fix.glb");
useGLTF.preload("/models/valley.glb");
useTexture.preload("/images/simulation-bg.png");
useTexture.preload("/images/cloudy-bg.png");

export default Turbine3DSimulation;