import { useNavigate } from "react-router-dom";
import { useState } from "react";

import Header from "../components/Header";
import MainBar from "../components/MainBar";
import UnderBar from "../components/UnderBar";
import SideBar from "../components/SideBar";

import "./MainScreen.css";
import "../components/Bar.css";

function MainScreen() {
  const navigate = useNavigate();
  const [screenMode, setScreenMode] = useState("map");
  const [selectedPlant, setSelectedPlant] = useState(null);

  const handleLogout = () => {
    navigate("/login");
  };

  const handleSelectPlant = (plant) => {
    setSelectedPlant(plant);
    setScreenMode("plant");
  };

  const handleBackToMap = () => {
    setSelectedPlant(null);
    setScreenMode("map");
  };
  return (
    <div className="main-screen">
      <Header onLogout={handleLogout} />

      <div className="dashboard-layout">
        <MainBar/>

        <SideBar
          mode={screenMode}
          selectedPlant={selectedPlant}
        />

        <UnderBar
          selectedPlant={selectedPlant}
        />
      </div>
    </div>
  );
}

export default MainScreen;