import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowRight, Eye, EyeOff, Leaf, LockKeyhole, Mail, Wind } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, Navigate, useNavigate, useSearchParams } from "react-router-dom";
import { z } from "zod";
import { getErrorMessage } from "../api/client";
import { FieldError } from "../components/ui";
import { useAuth } from "./AuthContext";

const schema = z.object({
  email: z.email("Enter a valid email address."),
  password: z.string().min(12, "Password must be at least 12 characters.").max(72, "Password must be 72 characters or fewer."),
});
type AuthFields = z.infer<typeof schema>;

export default function AuthPage() {
  const { user, authenticate } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const registering = params.get("mode") === "register";
  const [error, setError] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const { register, handleSubmit, formState: { errors } } = useForm<AuthFields>({
    resolver: zodResolver(schema),
    defaultValues: { email: "", password: "" },
  });

  if (user) return <Navigate to="/" replace />;

  async function submit(values: AuthFields) {
    setError("");
    setSubmitting(true);
    try {
      await authenticate(values.email, values.password, registering);
      navigate("/", { replace: true });
    } catch (cause) {
      setError(getErrorMessage(cause));
    } finally {
      setSubmitting(false);
    }
  }

  return <main className="auth-page">
    <div className="auth-art">
      <div className="auth-art-grid" />
      <div className="auth-brand"><div className="brand-mark"><Wind size={23} /></div><strong>Civic Air</strong></div>
      <div className="auth-message"><div className="eyebrow eyebrow-light"><Leaf size={14} /> CIVIC INTELLIGENCE</div><h1>Better air starts with better evidence.</h1><p>Turn the data you collect into clear, traceable insight for every community.</p></div>
      <div className="auth-art-footer"><span>Evidence-led, community-focused.</span><span>01 — 03</span></div>
      <div className="auth-orbit"><div /><div /><div /><Wind /></div>
    </div>
    <div className="auth-panel"><div className="auth-panel-top"><span className="workspace-chip"><span /> SECURE WORKSPACE</span><Link to="/login" className="back-home">Public workspace <ArrowRight size={14} /></Link></div>
      <div className="auth-form-wrap"><div className="auth-form-icon"><LockKeyhole size={21} /></div><div className="eyebrow">{registering ? "CREATE YOUR ACCOUNT" : "WELCOME BACK"}</div><h2>{registering ? "Start with your data." : "Sign in to your workspace."}</h2><p>{registering ? "Create a school workspace to securely review air quality datasets." : "Your verified datasets and briefings are ready when you are."}</p>
        <form className="form-stack auth-form" onSubmit={handleSubmit(submit)} noValidate>
          <label className="field-label" htmlFor="email">Email address</label><div className="input-icon"><Mail size={17} /><input id="email" type="email" autoComplete="email" placeholder="you@organization.org" {...register("email")} /></div><FieldError>{errors.email?.message}</FieldError>
          <label className="field-label" htmlFor="password">Password</label><div className="input-icon"><LockKeyhole size={17} /><input id="password" type={showPassword ? "text" : "password"} autoComplete={registering ? "new-password" : "current-password"} placeholder="At least 12 characters" {...register("password")} /><button type="button" className="password-toggle" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? "Hide password" : "Show password"}>{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button></div><FieldError>{errors.password?.message}</FieldError>
          {error && <div className="form-alert" role="alert">{error}</div>}
          <button type="submit" className="button button-primary auth-submit" disabled={submitting}>{submitting ? "Connecting…" : registering ? "Create secure account" : "Sign in"} <ArrowRight size={17} /></button>
        </form>
        <div className="auth-switch">{registering ? "Already have a workspace?" : "New to Civic Air?"} <Link to={registering ? "/login" : "/login?mode=register"}>{registering ? "Sign in" : "Create account"}</Link></div>
        {registering && <div className="auth-footnote">Self-service registration creates a School account. Other roles are assigned by an authorized workspace administrator.</div>}
      </div>
      <div className="auth-legal"><span>© 2026 Civic Air Briefing Studio</span><span>Privacy-first · Data stays in your workspace</span></div>
    </div>
  </main>;
}
