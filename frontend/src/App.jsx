import AdminUserScreen from "./screens/AdminUserScreen";

<Route path="/admin/users" element={<AdminUserScreen />} />

import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";

import MainScreen from "./screens/MainScreen";
import LoginScreen from "./screens/LoginScreen";
import SignupScreen from "./screens/SignupScreen";
import ReportListScreen from "./screens/ReportListScreen";
import UserScreen from "./screens/AdminUserScreen";

// App.jsx <Routes> 내부
<Route path="/user" element={<UserScreen />} />
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
        <Route path="/reportlist" element={<ReportListScreen />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;