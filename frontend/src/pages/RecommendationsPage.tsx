import { useQuery } from "@tanstack/react-query";
import { ArrowRight, BadgeCheck, Lightbulb, LockKeyhole, Sparkles } from "lucide-react";
import { Link } from "react-router-dom";
import { api, getErrorMessage } from "../api/client";
import type { Audience } from "../api/types";
import { getActiveAnalysisId } from "../api/workspace";
import { useAuth } from "../auth/AuthContext";
import { Card, EmptyState, ErrorState, EvidenceList, LoadingState, PageHeading } from "../components/ui";
import { useState } from "react";

const audiences = [
  { value: "WARD_OFFICER", label: "Ward Officer" },
  { value: "HOUSING_SOCIETY", label: "Housing Society" },
  { value: "SCHOOL", label: "School" },
] as const;

export default function RecommendationsPage() {
  const { user } = useAuth();
  const [audience, setAudience] = useState<Audience>(user?.role === "ADMIN" ? "SCHOOL" : user?.role ?? "SCHOOL");
  const analysisId = getActiveAnalysisId();
  const analysisQuery = useQuery({ queryKey: ["analysis", analysisId], queryFn: () => api.analysis(analysisId), enabled: Boolean(analysisId) });
  const recommendationQuery = useQuery({
    queryKey: ["recommendations", analysisId, audience],
    queryFn: () => api.recommendations(analysisId, audience),
    enabled: Boolean(analysisId && user?.role),
  });

  if (!analysisId) return <div className="page-stack"><PageHeading eyebrow="ROLE-SPECIFIC ACTIONS" title="Recommendations grounded in evidence." description="Recommendations are retrieved for your account’s authorized audience and cite verified analysis findings." /><EmptyState title="Analyze data first" body="Recommendations are available after a dataset has been analyzed." action={<Link className="button button-primary button-small" to="/quality">Review a dataset <ArrowRight size={15} /></Link>} /></div>;
  if (analysisQuery.isLoading || recommendationQuery.isLoading) return <LoadingState label="Loading verified recommendations…" />;
  if (analysisQuery.isError) return <ErrorState error={getErrorMessage(analysisQuery.error)} onRetry={() => void analysisQuery.refetch()} />;
  if (recommendationQuery.isError) return <ErrorState error={getErrorMessage(recommendationQuery.error)} onRetry={() => void recommendationQuery.refetch()} />;

  const recommendations = recommendationQuery.data ?? [];
  return <div className="page-stack">
    <PageHeading eyebrow="ACTION PLANNING" title="Recommendations, with receipts." description="Actions are tied to the analysis findings returned by the backend—not generated from frontend assumptions." />
    <Card className="audience-panel"><div className="audience-copy"><div className="eyebrow">AUTHORIZED AUDIENCE</div><h2>Recommendations for your role</h2><p>{user?.role === "ADMIN" ? "Local administrator access can retrieve recommendations for any audience." : "Your account can retrieve actions for its assigned role only."}</p></div>{user?.role === "ADMIN" ? <label className="sr-only" htmlFor="recommendation-audience">Recommendation audience</label> : null}{user?.role === "ADMIN" ? <select id="recommendation-audience" className="text-input" value={audience} onChange={(event) => setAudience(event.target.value as Audience)}>{audiences.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}</select> : <div className="audience-pills" role="group" aria-label="Recommendation audience">{audiences.map((item) => <span className={`audience-pill ${user?.role === item.value ? "audience-selected" : "audience-locked"}`} key={item.value}>{user?.role === item.value ? <BadgeCheck size={15} /> : <LockKeyhole size={14} />}{item.label}</span>)}</div>}</Card>
    <div className="analysis-context"><Sparkles size={16} /><span>Analysis <code>{analysisQuery.data!.id.slice(0, 8)}…</code> · {analysisQuery.data!.standard} · based on verified findings</span><Link to={`/analysis/${analysisId}`}>View analysis <ArrowRight size={14} /></Link></div>
    {recommendations.length === 0 ? <EmptyState title="No recommendations returned" body="The backend returned no recommendations for this analysis and audience. No actions have been inferred." /> :
      <div className="recommendation-grid">{recommendations.map((item) => <Card className="recommendation-card" key={item.id}><div className="recommendation-head"><span className={`priority priority-${item.priority.toLowerCase()}`}>{item.priority} PRIORITY</span><span className="recommendation-audience">{item.audience.replaceAll("_", " ")}</span></div><div className="recommendation-icon"><Lightbulb size={19} /></div><h2>{item.action}</h2><p className="recommendation-reason">{item.reason}</p><div className="recommendation-evidence"><strong>Supporting evidence</strong><EvidenceList ids={item.evidenceIds} /></div>{item.limitations.length > 0 && <div className="limitations-block"><strong>Limitations</strong><ul>{item.limitations.map((limitation) => <li key={limitation}>{limitation}</li>)}</ul></div>}</Card>)}</div>}
  </div>;
}
