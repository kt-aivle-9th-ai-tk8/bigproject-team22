import React, { Suspense, lazy } from "react";
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
} from "react-router-dom";

import LoadingOverlay from "./components/common/LoadingOverlay";

import "./App.css";

const MainScreen = lazy(() => import("./screens/MainScreen"));
const LoginScreen = lazy(() => import("./screens/LoginScreen"));
const SignupScreen = lazy(() => import("./screens/SignupScreen"));
const ReportListScreen = lazy(() => import("./screens/ReportListScreen"));
const AdminUserScreen = lazy(() => import("./screens/AdminUserScreen"));

function App() {
  return (
    <BrowserRouter>
      <Suspense
        fallback={
          <LoadingOverlay message="화면을 불러오는 중입니다..." />
        }
      >
        <Routes>
          {/* 기본 루트 진입 시 로그인 페이지로 이동 */}
          <Route
            path="/"
            element={<Navigate to="/login" replace />}
          />

          {/* 회원 관련 라우트 */}
          <Route
            path="/login"
            element={<LoginScreen />}
          />

          <Route
            path="/signup"
            element={<SignupScreen />}
          />

          {/* 대시보드 및 보고서 목록 라우트 */}
          <Route
            path="/main"
            element={<MainScreen />}
          />

          <Route
            path="/reportlist"
            element={<ReportListScreen />}
          />

          {/* 내 정보 / 관리자 페이지 라우트 */}
          <Route
            path="/admin/users"
            element={<AdminUserScreen />}
          />

          <Route
            path="/user"
            element={<AdminUserScreen />}
          />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;