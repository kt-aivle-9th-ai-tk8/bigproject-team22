import { useGLTF } from "@react-three/drei";
import { useThree } from "@react-three/fiber";
import { useEffect, useMemo } from "react";
import {
  Box3,
  Vector3,
  LinearFilter,
  SRGBColorSpace,
} from "three";

function ValleyModel({
  modelPath = "/models/valley.glb",
  position = [0, -4.8, 0],
  scale = 0.08,
  rotation = [0, 0, 0],
}) {
  const { scene } = useGLTF(modelPath);
  const { gl } = useThree();

  const centeredScene = useMemo(() => {
    if (!scene) return null;

    const clonedScene = scene.clone(true);

    const box = new Box3().setFromObject(clonedScene);
    const center = new Vector3();

    box.getCenter(center);
    clonedScene.position.sub(center);

    return clonedScene;
  }, [scene]);

  useEffect(() => {
    if (!centeredScene) return;

    const maxAnisotropy = gl.capabilities.getMaxAnisotropy();

    centeredScene.traverse((object) => {
      if (!object.isMesh) return;

      object.visible = true;
      object.receiveShadow = true;
      object.castShadow = false;

      const materials = Array.isArray(object.material)
        ? object.material
        : [object.material];

      materials.forEach((material) => {
        if (!material) return;

        material.transparent = false;
        material.opacity = 1;

        material.metalness = 0;
        material.roughness = 1;
        material.envMapIntensity = 0;

        const textureMaps = [
          material.map,
          material.normalMap,
          material.roughnessMap,
          material.metalnessMap,
          material.aoMap,
          material.emissiveMap,
        ];

        textureMaps.forEach((texture) => {
          if (!texture) return;

          texture.anisotropy = Math.min(16, maxAnisotropy);

          texture.magFilter = LinearFilter;
          texture.minFilter = LinearFilter;

          texture.generateMipmaps = false;
          texture.needsUpdate = true;
        });

        if (material.map) {
          material.map.colorSpace = SRGBColorSpace;
          material.map.needsUpdate = true;
        }

        if (material.emissiveMap) {
          material.emissiveMap.colorSpace = SRGBColorSpace;
          material.emissiveMap.needsUpdate = true;
        }

        material.needsUpdate = true;
      });
    });
  }, [centeredScene, gl]);

  if (!centeredScene) return null;

  return (
    <primitive
      object={centeredScene}
      position={position}
      rotation={rotation}
      scale={scale}
    />
  );
}

export default ValleyModel;