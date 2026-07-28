import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";

import MainScreen from "./screens/MainScreen";
import LoginScreen from "./screens/LoginScreen";
import SignupScreen from "./screens/SignupScreen";

import "./App.css";

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />

        <Route path="/login" element={<LoginScreen />} />
        <Route path="/signup" element={<SignupScreen />} />
        <Route path="/main" element={<MainScreen />} />
        
        {/* 보고서 목록 화면 라우트 추가 */}
        {/* <Route path="/reportlist" element={<ReportListScreen />} /> */}
      </Routes>
    </BrowserRouter>
  );
}

export default App;