import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { Activity, ArrowUpRight, BarChart3, Bell, BookOpenText, ChevronDown, ClipboardCheck, FileClock, FilePlus2, GitCompareArrows, LayoutDashboard, Leaf, LogOut, Menu, Microscope, ShieldCheck, Sparkles, Upload, Wind, X } from "lucide-react";
import { useState } from "react";
import { useAuth } from "../auth/AuthContext";

const navItems = [
  { to: "/", label: "Overview", icon: LayoutDashboard },
  { to: "/upload", label: "Add dataset", icon: Upload },
  { to: "/quality", label: "Data quality", icon: ClipboardCheck },
  { to: "/analysis", label: "Analysis & metrics", icon: BarChart3 },
  { to: "/findings", label: "Findings & hotspots", icon: Sparkles },
  { to: "/recommendations", label: "Recommendations", icon: Microscope },
  { to: "/brief/internal", label: "Internal briefing", icon: FilePlus2 },
  { to: "/brief/advisory", label: "Citizen advisory", icon: BookOpenText },
  { to: "/comparison", label: "Compare periods", icon: GitCompareArrows },
  { to: "/history", label: "Brief history", icon: FileClock },
];

export default function AppShell() {
  const { user, signOut } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const title = navItems.find((item) => item.to === location.pathname)?.label ?? "Air quality intelligence";

  return <div className="app-shell">
    {open && <button className="mobile-scrim" aria-label="Close navigation" onClick={() => setOpen(false)} />}
    <aside className={`sidebar ${open ? "sidebar-open" : ""}`}>
      <div className="brand"><div className="brand-mark"><Wind size={22} /></div><div><strong>Civic Air</strong><span>BRIEFING STUDIO</span></div><button className="icon-button mobile-close" onClick={() => setOpen(false)} aria-label="Close menu"><X size={18} /></button></div>
      <div className="workspace-label">WORKSPACE</div>
      <nav className="side-nav" aria-label="Main navigation">
        {navItems.map(({ to, label, icon: Icon }) => <NavLink key={to} to={to} end={to === "/"} onClick={() => setOpen(false)} className={({ isActive }) => `nav-link ${isActive ? "nav-active" : ""}`}><Icon size={18} strokeWidth={1.8} /><span>{label}</span>{label === "Data quality" && <span className="nav-indicator" />}</NavLink>)}
      </nav>
      <div className="sidebar-bottom">
        <div className="privacy-card"><ShieldCheck size={18} /><div><strong>Evidence first</strong><span>Every insight links to source data.</span></div></div>
        <button className="profile-button" onClick={() => { signOut(); navigate("/login"); }}><div className="avatar">{user?.email.slice(0, 1).toUpperCase() ?? "U"}</div><div className="profile-copy"><strong>{user?.email ?? "Workspace user"}</strong><span>{user?.role?.replaceAll("_", " ") ?? "Signed in"}</span></div><LogOut size={17} /></button>
      </div>
    </aside>
    <main className="main-area">
      <header className="topbar"><button className="icon-button mobile-menu" onClick={() => setOpen(true)} aria-label="Open menu"><Menu size={20} /></button><div className="breadcrumbs"><span>Workspace</span><ArrowUpRight size={13} /><strong>{title}</strong></div><div className="topbar-right"><span className="live-status"><span /> Live data</span><button className="icon-button notification-button" aria-label="Data quality notifications" onClick={() => navigate("/quality")}><Bell size={18} /></button><div className="topbar-user">{user?.role?.replaceAll("_", " ") ?? "Account"}<ChevronDown size={14} /></div></div></header>
      <div className="content"><Outlet /></div>
      <footer className="footer-note"><Leaf size={14} /> Descriptive analysis only · No AQI thresholds or health risks inferred <span>•</span> <Activity size={14} /> Data sourced from your workspace</footer>
    </main>
  </div>;
}
