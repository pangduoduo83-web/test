import { useEffect } from "react";
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import { getIoeduToken, redirectToIoeduLogin } from "@/lib/api";
import { AdminPage } from "@/pages/AdminPage";
import { ChatPage } from "@/pages/ChatPage";
import { LandingPage } from "@/pages/LandingPage";
import { LoginPage } from "@/pages/LoginPage";
import { useAuth } from "@/store/auth";
import { useBranding } from "@/store/branding";

const BASENAME = (import.meta.env.BASE_URL || "/").replace(/\/$/, "");

function Guard({ children, admin }: { children: React.ReactNode; admin?: boolean }) {
  const { user, loading } = useAuth();
  if (loading) {
    return (
      <div className="flex h-full items-center justify-center text-sm text-slate-400">
        <span className="animate-pulse">正在恢复会话…</span>
      </div>
    );
  }
  if (!user) {
    // 作为教学平台的子应用:没有登录态就回平台登录页,不再单独登录
    if (BASENAME) {
      redirectToIoeduLogin();
      return null;
    }
    return <Navigate to="/login" replace />;
  }
  if (admin && user.role !== "admin") return <Navigate to="/app" replace />;
  return <>{children}</>;
}

/** 首页:平台内嵌时直接进工作台(已登录)或回平台登录 */
function Home() {
  if (BASENAME) {
    if (getIoeduToken()) return <Navigate to="/app" replace />;
    redirectToIoeduLogin();
    return null;
  }
  return <LandingPage />;
}

export default function App() {
  const bootstrap = useAuth((s) => s.bootstrap);
  const loadBranding = useBranding((s) => s.load);
  useEffect(() => {
    bootstrap();
    loadBranding();
  }, [bootstrap, loadBranding]);

  return (
    <BrowserRouter basename={BASENAME || undefined}>
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<LoginPage />} />
        <Route
          path="/app/*"
          element={
            <Guard>
              <ChatPage />
            </Guard>
          }
        />
        <Route
          path="/admin/*"
          element={
            <Guard admin>
              <AdminPage />
            </Guard>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
