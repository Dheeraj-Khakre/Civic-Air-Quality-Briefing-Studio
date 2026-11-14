import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { api } from "../api/client";
import type { UserSummary } from "../api/types";

interface AuthContextValue {
  user: UserSummary | null;
  authenticate: (email: string, password: string, register: boolean) => Promise<void>;
  signOut: () => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(() => {
    const saved = localStorage.getItem("civic-air-user");
    try { return saved ? JSON.parse(saved) as UserSummary : null; } catch { return null; }
  });

  const signOut = useCallback(() => {
    localStorage.removeItem("civic-air-token");
    localStorage.removeItem("civic-air-user");
    setUser(null);
  }, []);

  useEffect(() => {
    window.addEventListener("civic-air:unauthorized", signOut);
    return () => window.removeEventListener("civic-air:unauthorized", signOut);
  }, [signOut]);

  const authenticate = useCallback(async (email: string, password: string, register: boolean) => {
    const response = register ? await api.register(email, password) : await api.login(email, password);
    localStorage.setItem("civic-air-token", response.accessToken);
    localStorage.setItem("civic-air-user", JSON.stringify(response.user));
    setUser(response.user);
  }, []);

  const value = useMemo(() => ({ user, authenticate, signOut }), [user, authenticate, signOut]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used within AuthProvider");
  return value;
}
