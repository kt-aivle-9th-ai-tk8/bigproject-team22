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
const UserScreen = lazy(() => import("./screens/UserScreen"));

function App() {
  return (
    <BrowserRouter>
      <Suspense
        fallback={
          <LoadingOverlay/>
        }
      >
        <Routes>
          <Route
            path="/"
            element={<Navigate to="/login" replace />}
          />

          <Route
            path="/login"
            element={<LoginScreen />}
          />

          <Route
            path="/signup"
            element={<SignupScreen />}
          />

          <Route
            path="/main"
            element={<MainScreen />}
          />

          <Route
            path="/reportlist"
            element={<ReportListScreen />}
          />

          <Route
            path="/user"
            element={<UserScreen />}
          />

          <Route
            path="/admin/users"
            element={<AdminUserScreen />}
          />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default App;