import React, { Suspense, lazy } from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
} from "react-router-dom";

import MainScreen from "./screens/MainScreen";
import LoginScreen from "./screens/LoginScreen";
import SignupScreen from "./screens/SignupScreen";
import ReportListScreen from "./screens/ReportListScreen";
import AdminUserScreen from "./screens/AdminUserScreen";
import UserScreen from "./screens/UserScreen";

import "./App.css";

const MainScreen = lazy(() => import("./screens/MainScreen"));
const LoginScreen = lazy(() => import("./screens/LoginScreen"));
const SignupScreen = lazy(() => import("./screens/SignupScreen"));
const ReportListScreen = lazy(() => import("./screens/ReportListScreen"));
const AdminUserScreen = lazy(() => import("./screens/AdminUserScreen"));

function App() {
  return (
    <BrowserRouter>
      <Routes>
        {/* 기본 루트 진입 시 로그인 페이지로 이동 */}
        <Route path="/" element={<Navigate to="/login" replace />} />

        {/* 회원 관련 라우트 */}
        <Route path="/login" element={<LoginScreen />} />
        <Route path="/signup" element={<SignupScreen />} />

        {/* 대시보드 및 보고서 목록 라우트 */}
        <Route path="/main" element={<MainScreen />} />
        <Route path="/reportlist" element={<ReportListScreen />} />

        {/* 마이페이지 및 관리자 페이지 라우트 */}
        <Route path="/user" element={<UserScreen />} />
        <Route path="/admin/users" element={<AdminUserScreen />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;