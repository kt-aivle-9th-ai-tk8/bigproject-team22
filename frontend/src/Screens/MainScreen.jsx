import { useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";

import Header from "../components/Header";
import MainBar from "../components/MainBar";
import UnderBar from "../components/UnderBar";
import SideBar from "../components/SideBar";

import "./MainScreen.css";
import "../components/Bar.css";

function MainScreen() {
  const navigate = useNavigate();

  const [screenMode, setScreenMode] = useState(() => {
    return localStorage.getItem("screenMode") || "map";
  });

  const [selectedPlant, setSelectedPlant] = useState(() => {
    const savedPlant = localStorage.getItem("selectedPlant");
    return savedPlant ? JSON.parse(savedPlant) : null;
  });

  const [selectedTurbine, setSelectedTurbine] = useState(() => {
    const savedTurbine = localStorage.getItem("selectedTurbine");
    return savedTurbine ? JSON.parse(savedTurbine) : null;
  });

  useEffect(() => {
    localStorage.setItem("screenMode", screenMode);
  }, [screenMode]);

  useEffect(() => {
    if (selectedPlant) {
      localStorage.setItem("selectedPlant", JSON.stringify(selectedPlant));
    } else {
      localStorage.removeItem("selectedPlant");
    }
  }, [selectedPlant]);

  useEffect(() => {
    if (selectedTurbine) {
      localStorage.setItem("selectedTurbine", JSON.stringify(selectedTurbine));
    } else {
      localStorage.removeItem("selectedTurbine");
    }
  }, [selectedTurbine]);

  const handleLogout = () => {
    localStorage.removeItem("screenMode");
    localStorage.removeItem("selectedPlant");
    localStorage.removeItem("selectedTurbine");
    navigate("/login");
  };

  const handleSelectPlant = (plant) => {
    setSelectedPlant(plant);
    setSelectedTurbine(null);
    setScreenMode("plant");
  };

  const handleSelectTurbine = (turbine) => {
    setSelectedTurbine(turbine);
    setScreenMode("turbine");
  };

  const handleBackToMap = () => {
    setSelectedPlant(null);
    setSelectedTurbine(null);
    setScreenMode("map");
  };

  return (
    <div className="main-screen">
      <Header onLogout={handleLogout} onTitleClick={handleBackToMap} />

      <div className="dashboard-layout">
        <MainBar
          mode={screenMode}
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
          onSelectPlant={handleSelectPlant}
          onBackToMap={handleBackToMap}
        />

        <SideBar
          mode={screenMode}
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
          onSelectPlant={handleSelectPlant}
          onSelectTurbine={handleSelectTurbine}
        />

        <UnderBar
          mode={screenMode}
          selectedPlant={selectedPlant}
          selectedTurbine={selectedTurbine}
        />
      </div>
    </div>
  );
}

export default MainScreen;