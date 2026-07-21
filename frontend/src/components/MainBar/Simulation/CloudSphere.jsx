import { useFrame } from "@react-three/fiber";
import { BackSide, Color, NormalBlending } from "three";
import { useMemo, useRef } from "react";

const cloudVertexShader = `
  varying vec3 vPosition;

  void main() {
    vPosition = normalize(position);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
  }
`;

const cloudFragmentShader = `
  uniform float uTime;
  uniform float uOpacity;
  uniform vec3 uCloudColor;
  uniform vec3 uShadowColor;

  varying vec3 vPosition;

  float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.1, 0.2, 0.3));
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
    float amplitude = 0.5;

    for (int i = 0; i < 6; i++) {
      value += valueNoise(p) * amplitude;
      p *= 2.15;
      amplitude *= 0.5;
    }

    return value;
  }

  float worley(vec3 p) {
    vec3 cell = floor(p);
    vec3 localPosition = fract(p);

    float minDistance = 1.0;

    for (int x = -1; x <= 1; x++) {
      for (int y = -1; y <= 1; y++) {
        for (int z = -1; z <= 1; z++) {
          vec3 neighbor = vec3(float(x), float(y), float(z));

          vec3 point = vec3(
            hash(cell + neighbor + vec3(1.7, 9.2, 4.3)),
            hash(cell + neighbor + vec3(8.3, 2.8, 5.1)),
            hash(cell + neighbor + vec3(3.4, 7.1, 6.6))
          );

          vec3 diff = neighbor + point - localPosition;
          float distanceToPoint = length(diff);

          minDistance = min(minDistance, distanceToPoint);
        }
      }
    }

    return minDistance;
  }

  void main() {
    vec3 p = vPosition;

    p.xz += uTime * 0.018;

    float base = fbm(p * 1.8);
    float billow = fbm(p * 4.2 + base * 1.8);
    float detail = fbm(p * 10.0 + billow * 1.5);

    float cellA = 1.0 - worley(p * 3.0 + uTime * 0.01);
    float cellB = 1.0 - worley(p * 6.5 - uTime * 0.008);

    float density =
      base * 0.45 +
      billow * 0.35 +
      detail * 0.18 +
      cellA * 0.18 +
      cellB * 0.08;

    density = smoothstep(0.52, 0.78, density);

    float holes = fbm(p * 7.5 + 10.0);
    density *= smoothstep(0.28, 0.65, holes);

    float horizonMask = smoothstep(-0.2, 0.2, p.y);
    float topFade = 1.0 - smoothstep(0.75, 1.0, p.y);

    float alpha = density * horizonMask * topFade * uOpacity;

    float shade = smoothstep(0.0, 0.85, density);
    vec3 color = mix(uShadowColor, uCloudColor, shade);

    color *= mix(0.82, 1.15, p.y * 0.5 + 0.5);

    gl_FragColor = vec4(color, alpha);
  }
`;

function CloudSphere({
  position = [0, 19, 0],
  scale = [78, 78, 78],
  opacity = 0.72,
  color = "#ffffff",
  shadowColor = "#9fb0bc",
}) {
  const materialRef = useRef(null);

  const uniforms = useMemo(
    () => ({
      uTime: { value: 0 },
      uOpacity: { value: opacity },
      uCloudColor: { value: new Color(color) },
      uShadowColor: { value: new Color(shadowColor) },
    }),
    [opacity, color, shadowColor]
  );

  useFrame(({ clock }) => {
    if (!materialRef.current) return;

    materialRef.current.uniforms.uTime.value = clock.getElapsedTime();
  });

  return (
    <mesh position={position} scale={scale}>
      <sphereGeometry args={[1, 128, 128]} />
      <shaderMaterial
        ref={materialRef}
        vertexShader={cloudVertexShader}
        fragmentShader={cloudFragmentShader}
        uniforms={uniforms}
        transparent
        depthWrite={false}
        side={BackSide}
        blending={NormalBlending}
      />
    </mesh>
  );
}

export default CloudSphere;