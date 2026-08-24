import CloudSphere from "../CloudSphere";

function CloudyLayer({ mode = "sunny" }) {
  const isCloudyWeather = mode === "cloudy" || mode === "rainy";

  if (!isCloudyWeather) {
    return (
      <CloudSphere
        position={[0, 19, 0]}
        scale={[78, 78, 78]}
        opacity={0.85}
        color="#ffffff"
        shadowColor="#ced4da"
      />
    );
  }

  return (
    <>
      <CloudSphere
        position={[0, 30, 0]}
        scale={[76, 76, 76]}
        opacity={0.7}
        color="#a2a6b1"
        shadowColor="#c1c2cd"
      />

      <CloudSphere
        position={[0, 28, 0]}
        scale={[68, 68, 68]}
        opacity={0.8}
        color="#bdbeca"
        shadowColor="#d3d4dc"
      />
    </>
  );
}

export default CloudyLayer;