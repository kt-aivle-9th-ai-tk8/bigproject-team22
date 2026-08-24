import { useFrame, useThree } from "@react-three/fiber";
import { AdditiveBlending, DoubleSide, Vector3 } from "three";
import { useMemo, useRef } from "react";

const rainVertexShader = `
  attribute vec3 aOffset;
  attribute float aSeed;
  attribute float aLength;
  attribute float aWidth;

  uniform float uTime;
  uniform vec3 uCameraRight;
  uniform vec3 uCameraUp;
  uniform vec3 uAreaSize;
  uniform vec3 uCenter;
  uniform float uFallSpeed;
  uniform vec2 uWind;
  uniform float uWindStrength;

  varying vec2 vUv;
  varying vec3 vWorldPosition;
  varying float vSeed;

  float hash(float n) {
    return fract(sin(n) * 43758.5453123);
  }

  void main() {
    vUv = uv;
    vSeed = aSeed;

    vec3 offset = aOffset;

    float fall = uTime * uFallSpeed * (0.75 + hash(aSeed * 17.13) * 0.55);

    offset.y = mod(offset.y - fall + uAreaSize.y * 0.5, uAreaSize.y) - uAreaSize.y * 0.5;

    float windPhase = uTime * 0.7 + aSeed * 9.31;
    float windNoise = sin(windPhase) * 0.35 + sin(windPhase * 0.37) * 0.65;

    offset.x += uWind.x * uWindStrength * fall * 0.04;
    offset.z += uWind.y * uWindStrength * fall * 0.04;

    offset.x += windNoise * uWind.x * 0.8;
    offset.z += windNoise * uWind.y * 0.8;

    offset.x = mod(offset.x + uAreaSize.x * 0.5, uAreaSize.x) - uAreaSize.x * 0.5;
    offset.z = mod(offset.z + uAreaSize.z * 0.5, uAreaSize.z) - uAreaSize.z * 0.5;

    vec3 worldCenter = uCenter + offset;

    vec2 local = position.xy;

    vec3 billboardPosition =
      worldCenter +
      uCameraRight * local.x * aWidth +
      uCameraUp * local.y * aLength;

    vWorldPosition = billboardPosition;

    gl_Position = projectionMatrix * viewMatrix * vec4(billboardPosition, 1.0);
  }
`;

const rainFragmentShader = `
  precision highp float;

  uniform vec3 uCameraPosition;
  uniform vec3 uColor;
  uniform float uOpacity;
  uniform float uDensity;
  uniform float uNearFade;
  uniform float uFarFade;
  uniform float uFogStrength;
  uniform float uTime;

  varying vec2 vUv;
  varying vec3 vWorldPosition;
  varying float vSeed;

  float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.11, 0.17, 0.13));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
  }

  float valueNoise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);

    f = f * f * (3.0 - 2.0 * f);

    float n000 = hash(i + vec3(0.0, 0.0, 0.0));
    float n100 = hash(i + vec3(1.0, 0.0, 0.0));
    float n010 = hash(i + vec3(0.0, 1.0, 0.0));
    float n110 = hash(i + vec3(1.0, 1.0, 0.0));
    float n001 = hash(i + vec3(0.0, 0.0, 1.0));
    float n101 = hash(i + vec3(1.0, 0.0, 1.0));
    float n011 = hash(i + vec3(0.0, 1.0, 1.0));
    float n111 = hash(i + vec3(1.0, 1.0, 1.0));

    float nx00 = mix(n000, n100, f.x);
    float nx10 = mix(n010, n110, f.x);
    float nx01 = mix(n001, n101, f.x);
    float nx11 = mix(n011, n111, f.x);

    float nxy0 = mix(nx00, nx10, f.y);
    float nxy1 = mix(nx01, nx11, f.y);

    return mix(nxy0, nxy1, f.z);
  }

  float fbm(vec3 p) {
    float value = 0.0;
    float amp = 0.5;

    for (int i = 0; i < 4; i++) {
      value += valueNoise(p) * amp;
      p *= 2.05;
      amp *= 0.5;
    }

    return value;
  }

  void main() {
    vec2 centeredUv = vUv - 0.5;

    float thinLine = 1.0 - smoothstep(0.04, 0.42, abs(centeredUv.x));
    float verticalFade = smoothstep(0.0, 0.22, vUv.y) * (1.0 - smoothstep(0.78, 1.0, vUv.y));

    float streakAlpha = thinLine * verticalFade;

    vec3 densityPosition = vWorldPosition * 0.035 + vec3(uTime * 0.025, 0.0, uTime * 0.015);
    float densityNoise = fbm(densityPosition);

    float densityMask = smoothstep(1.0 - uDensity, 1.0, densityNoise);

    float distanceToCamera = distance(vWorldPosition, uCameraPosition);

    float nearFade = smoothstep(0.0, uNearFade, distanceToCamera);
    float farFade = 1.0 - smoothstep(uFarFade * 0.65, uFarFade, distanceToCamera);

    float fogFade = mix(1.0, farFade, uFogStrength);

    float seedVariation = 0.65 + fract(sin(vSeed * 91.17) * 43758.5453) * 0.35;

    float alpha =
      streakAlpha *
      densityMask *
      nearFade *
      farFade *
      fogFade *
      seedVariation *
      uOpacity;

    if (alpha < 0.01) discard;

    vec3 color = uColor;

    gl_FragColor = vec4(color, alpha);
  }
`;

function createBlueNoiseLikeOffsets(count, areaSize) {
  const offsets = new Float32Array(count * 3);
  const seeds = new Float32Array(count);
  const lengths = new Float32Array(count);
  const widths = new Float32Array(count);

  const columns = Math.ceil(Math.sqrt(count));
  const rows = Math.ceil(count / columns);

  const cellX = areaSize[0] / columns;
  const cellZ = areaSize[2] / rows;

  let index = 0;

  for (let row = 0; row < rows; row += 1) {
    for (let column = 0; column < columns; column += 1) {
      if (index >= count) break;

      const seed = index * 12.9898 + row * 78.233 + column * 37.719;

      const randomA = Math.abs(Math.sin(seed) * 43758.5453) % 1;
      const randomB = Math.abs(Math.sin(seed + 19.19) * 24634.6345) % 1;
      const randomC = Math.abs(Math.sin(seed + 91.31) * 13513.5312) % 1;
      const randomD = Math.abs(Math.sin(seed + 43.73) * 19731.9173) % 1;

      const x =
        -areaSize[0] / 2 +
        column * cellX +
        cellX * (0.25 + randomA * 0.5);

      const y = -areaSize[1] / 2 + randomC * areaSize[1];

      const z =
        -areaSize[2] / 2 +
        row * cellZ +
        cellZ * (0.25 + randomB * 0.5);

      offsets[index * 3 + 0] = x;
      offsets[index * 3 + 1] = y;
      offsets[index * 3 + 2] = z;

      seeds[index] = seed;
      lengths[index] = 0.28 + randomC * 0.45;
      widths[index] = 0.035 + randomD * 0.035;

      index += 1;
    }
  }

  return {
    offsets,
    seeds,
    lengths,
    widths,
  };
}

function RainLayer({
  enabled = true,
  count = 1800,
  center = [0, 6, 0],
  areaSize = [55, 32, 55],
  fallSpeed = 18,
  wind = [0.65, -0.15],
  windStrength = 1.4,
  opacity = 0.55,
  density = 0.72,
  color = "#d7ecff",
  nearFade = 2.5,
  farFade = 42,
  fogStrength = 0.45,
}) {
  const materialRef = useRef(null);
  const { camera } = useThree();

  const geometryData = useMemo(
    () => createBlueNoiseLikeOffsets(count, areaSize),
    [count, areaSize]
  );

  const uniforms = useMemo(
    () => ({
      uTime: { value: 0 },
      uCameraRight: { value: new Vector3(1, 0, 0) },
      uCameraUp: { value: new Vector3(0, 1, 0) },
      uCameraPosition: { value: new Vector3() },
      uCenter: { value: new Vector3(center[0], center[1], center[2]) },
      uAreaSize: { value: new Vector3(areaSize[0], areaSize[1], areaSize[2]) },
      uFallSpeed: { value: fallSpeed },
      uWind: { value: wind },
      uWindStrength: { value: windStrength },
      uColor: { value: new Vector3(0.84, 0.93, 1.0) },
      uOpacity: { value: opacity },
      uDensity: { value: density },
      uNearFade: { value: nearFade },
      uFarFade: { value: farFade },
      uFogStrength: { value: fogStrength },
    }),
    [
      center,
      areaSize,
      fallSpeed,
      wind,
      windStrength,
      opacity,
      density,
      nearFade,
      farFade,
      fogStrength,
    ]
  );

  useFrame((state, delta) => {
    if (!materialRef.current || !enabled) return;

    const material = materialRef.current;

    material.uniforms.uTime.value += delta;

    camera.matrixWorld.extractBasis(
      material.uniforms.uCameraRight.value,
      material.uniforms.uCameraUp.value,
      new Vector3()
    );

    material.uniforms.uCameraPosition.value.copy(camera.position);

    material.uniforms.uCenter.value.set(center[0], center[1], center[2]);
    material.uniforms.uAreaSize.value.set(areaSize[0], areaSize[1], areaSize[2]);
    material.uniforms.uFallSpeed.value = fallSpeed;
    material.uniforms.uWind.value = wind;
    material.uniforms.uWindStrength.value = windStrength;
    material.uniforms.uOpacity.value = opacity;
    material.uniforms.uDensity.value = density;
    material.uniforms.uNearFade.value = nearFade;
    material.uniforms.uFarFade.value = farFade;
    material.uniforms.uFogStrength.value = fogStrength;
  });

  if (!enabled) return null;

  return (
    <instancedMesh args={[null, null, count]} frustumCulled={false}>
      <planeGeometry args={[1, 1]}>
        <instancedBufferAttribute
          attach="attributes-aOffset"
          args={[geometryData.offsets, 3]}
        />
        <instancedBufferAttribute
          attach="attributes-aSeed"
          args={[geometryData.seeds, 1]}
        />
        <instancedBufferAttribute
          attach="attributes-aLength"
          args={[geometryData.lengths, 1]}
        />
        <instancedBufferAttribute
          attach="attributes-aWidth"
          args={[geometryData.widths, 1]}
        />
      </planeGeometry>

      <shaderMaterial
        ref={materialRef}
        vertexShader={rainVertexShader}
        fragmentShader={rainFragmentShader}
        uniforms={uniforms}
        transparent
        depthWrite={false}
        depthTest
        side={DoubleSide}
        blending={AdditiveBlending}
      />
    </instancedMesh>
  );
}

export default RainLayer;