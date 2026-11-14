import { Component, Suspense, lazy, type ErrorInfo, type ReactNode } from "react";
import { Navigate, Outlet, Route, Routes, useLocation } from "react-router-dom";
import AppShell from "./components/AppShell";
import { LoadingState } from "./components/ui";
import { useAuth } from "./auth/AuthContext";

const AuthPage = lazy(() => import("./auth/AuthPage"));
const AnalysisPage = lazy(() => import("./pages/AnalysisPage"));
const DashboardPage = lazy(() => import("./pages/DashboardPage"));
const QualityPage = lazy(() => import("./pages/QualityPage"));
const RecommendationsPage = lazy(() => import("./pages/RecommendationsPage"));
const UploadPage = lazy(() => import("./pages/UploadPage"));
const BriefHistoryPage = lazy(() => import("./pages/BriefPages").then((module) => ({ default: module.BriefHistoryPage })));
const BriefDetailPage = lazy(() => import("./pages/BriefPages").then((module) => ({ default: module.BriefDetailPage })));
const BriefInternalPage = lazy(() => import("./pages/BriefPages").then((module) => ({ default: () => <module.BriefGeneratorPage type="INTERNAL" /> })));
const CitizenAdvisoryPage = lazy(() => import("./pages/BriefPages").then((module) => ({ default: () => <module.BriefGeneratorPage type="CITIZEN_ADVISORY" /> })));
const ComparisonPage = lazy(() => import("./pages/BriefPages").then((module) => ({ default: module.ComparisonPage })));

function ProtectedApp() {
  const { user } = useAuth();
  const location = useLocation();
  if (!user || !localStorage.getItem("civic-air-token")) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}

class ApplicationErrorBoundary extends Component<{ children: ReactNode }, { hasError: boolean }> {
  state = { hasError: false };

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Frontend rendering failed", error, info.componentStack);
  }

  render() {
    if (this.state.hasError) {
      return <main className="fatal-error"><div className="fatal-error-mark">!</div><h1>This view couldn’t be displayed.</h1><p>Your saved data is still in the workspace. Reload to try again.</p><button className="button button-primary" onClick={() => window.location.reload()}>Reload application</button></main>;
    }
    return this.props.children;
  }
}

export default function App() {
  return <ApplicationErrorBoundary><Suspense fallback={<LoadingState label="Opening workspace…" />}><Routes>
    <Route path="/login" element={<AuthPage />} />
    <Route element={<ProtectedApp />}>
      <Route element={<AppShell />}>
        <Route index element={<DashboardPage />} />
        <Route path="upload" element={<UploadPage />} />
        <Route path="quality" element={<QualityPage />} />
        <Route path="datasets/:datasetId/quality" element={<QualityPage />} />
        <Route path="analysis" element={<AnalysisPage />} />
        <Route path="analysis/:analysisId" element={<AnalysisPage />} />
        <Route path="findings" element={<AnalysisPage view="findings" />} />
        <Route path="recommendations" element={<RecommendationsPage />} />
        <Route path="brief/internal" element={<BriefInternalPage />} />
        <Route path="brief/advisory" element={<CitizenAdvisoryPage />} />
        <Route path="comparison" element={<ComparisonPage />} />
        <Route path="history" element={<BriefHistoryPage />} />
        <Route path="briefs/:briefId" element={<BriefDetailPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Route>
  </Routes></Suspense></ApplicationErrorBoundary>;
}
