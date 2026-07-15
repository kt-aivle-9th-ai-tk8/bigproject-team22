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
    const currentState = {
      screenMode,
      selectedPlant,
      selectedTurbine,
    };

    window.history.replaceState(currentState, "", window.location.href);
  }, []);

  useEffect(() => {
    const handlePopState = (event) => {
      const state = event.state;

      if (!state) {
        setScreenMode("map");
        setSelectedPlant(null);
        setSelectedTurbine(null);
        return;
      }

      setScreenMode(state.screenMode || "map");
      setSelectedPlant(state.selectedPlant || null);
      setSelectedTurbine(state.selectedTurbine || null);
    };

    window.addEventListener("popstate", handlePopState);

    return () => {
      window.removeEventListener("popstate", handlePopState);
    };
  }, []);

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

  const moveMode = (nextMode, nextPlant, nextTurbine) => {
    const nextState = {
      screenMode: nextMode,
      selectedPlant: nextPlant,
      selectedTurbine: nextTurbine,
    };

    setScreenMode(nextMode);
    setSelectedPlant(nextPlant);
    setSelectedTurbine(nextTurbine);

    window.history.pushState(nextState, "", window.location.href);
  };

  const handleLogout = () => {
    localStorage.removeItem("screenMode");
    localStorage.removeItem("selectedPlant");
    localStorage.removeItem("selectedTurbine");

    navigate("/login");
  };

  const handleSelectPlant = (plant) => {
    moveMode("plant", plant, null);
  };

  const handleSelectTurbine = (turbine) => {
    moveMode("turbine", selectedPlant, turbine);
  };

  const handleBackToMap = () => {
    moveMode("map", null, null);
  };

  return (
    <div className="main-screen">
      <Header onLogout={handleLogout} onTitleClick={handleBackToMap} />

      <div className="dashboard-layout">
        <MainBar
          mode={screenMode}
          onSelectPlant={handleSelectPlant}
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