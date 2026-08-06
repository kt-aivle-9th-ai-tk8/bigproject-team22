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
const ReportEditScreen = lazy(() => import("./screens/ReportEditScreen"));
const AdminUserScreen = lazy(() => import("./screens/AdminUserScreen"));
const UserScreen = lazy(() => import("./screens/UserScreen"));

function App() {
  return (
    <BrowserRouter>
      <Suspense fallback={<LoadingOverlay />}>
        <Routes>
          {/* 기본 경로 리다이렉트 */}
          <Route path="/" element={<Navigate to="/login" replace />} />

          {/* 인증 및 메인 라우트 */}
          <Route path="/login" element={<LoginScreen />} />
          <Route path="/signup" element={<SignupScreen />} />
          <Route path="/main" element={<MainScreen />} />

          {/* 마이페이지 및 관리자 페이지 라우트 */}
          <Route path="/user" element={<UserScreen />} />
          <Route path="/admin/users" element={<AdminUserScreen />} />

          {/* 보고서 관련 라우트 */}
          <Route path="/reportlist" element={<ReportListScreen />} />
          <Route path="/report-edit" element={<ReportEditScreen />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;