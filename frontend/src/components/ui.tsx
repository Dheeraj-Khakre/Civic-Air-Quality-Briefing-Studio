import type { ReactNode } from "react";
import { AlertCircle, LoaderCircle, RefreshCw } from "lucide-react";

export function PageHeading({ eyebrow, title, description, actions }: { eyebrow?: string; title: string; description?: string; actions?: ReactNode }) {
  return <div className="page-heading"><div>{eyebrow && <div className="eyebrow">{eyebrow}</div>}<h1>{title}</h1>{description && <p>{description}</p>}</div>{actions && <div className="heading-actions">{actions}</div>}</div>;
}

export function LoadingState({ label = "Loading verified data…" }: { label?: string }) {
  return <div className="state-card" role="status"><LoaderCircle className="spin" size={22} />{label}</div>;
}

export function ErrorState({ error, onRetry }: { error: string; onRetry?: () => void }) {
  return <div className="state-card error-state" role="alert"><AlertCircle size={21} /><div><strong>We couldn’t load this information</strong><p>{error}</p>{onRetry && <button className="button button-outline button-small" onClick={onRetry}><RefreshCw size={14} /> Try again</button>}</div></div>;
}

export function EmptyState({ title, body, action }: { title: string; body: string; action?: ReactNode }) {
  return <div className="empty-state"><div className="empty-mark"><AlertCircle size={22} /></div><h3>{title}</h3><p>{body}</p>{action}</div>;
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <section className={`card ${className}`}>{children}</section>;
}

export function FieldError({ children }: { children?: ReactNode }) {
  return children ? <span className="field-error">{children}</span> : null;
}

export function EvidenceList({ ids }: { ids: string[] }) {
  if (!ids.length) return <span className="muted">No evidence references returned.</span>;
  return <div className="evidence-list" aria-label="Evidence references">{ids.map((id) => <code key={id} title={id}>{id.slice(0, 8)}…</code>)}</div>;
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

export function percent(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}
