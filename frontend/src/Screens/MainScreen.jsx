import Header from "../components/Header";
import MainBar from "../components/MainBar";
import UnderBar from "../components/UnderBar";
import SideBar from "../components/SideBar";

import "./MainScreen.css";
import "../components/Bar.css";

function MainScreen() {
  return (
    <div className="main-screen">
      <Header />

      <div className="dashboard-layout">
        <MainBar />
        <SideBar />
        <UnderBar />
      </div>
    </div>
  );
}

export default MainScreen;