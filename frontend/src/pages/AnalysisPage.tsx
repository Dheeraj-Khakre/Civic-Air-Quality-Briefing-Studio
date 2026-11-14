import { useQuery } from "@tanstack/react-query";
import { ArrowRight, BarChart3, CircleHelp, FilePlus2, MapPinned, Sparkles } from "lucide-react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, getErrorMessage } from "../api/client";
import { Card, EmptyState, ErrorState, EvidenceList, LoadingState, PageHeading, percent } from "../components/ui";
import { getActiveAnalysisId } from "../api/workspace";

export default function AnalysisPage({ view = "overview" }: { view?: "overview" | "findings" }) {
  const route = useParams();
  const analysisId = route.analysisId ?? getActiveAnalysisId();
  const navigate = useNavigate();
  const analysisQuery = useQuery({ queryKey: ["analysis", analysisId], queryFn: () => api.analysis(analysisId), enabled: Boolean(analysisId) });
  const findingsQuery = useQuery({ queryKey: ["findings", analysisId], queryFn: () => api.findings(analysisId), enabled: Boolean(analysisId) });

  if (!analysisId) return <div className="page-stack"><PageHeading eyebrow={view === "findings" ? "FINDINGS & HOTSPOTS" : "AIR QUALITY ANALYSIS"} title="Turn readings into clear findings." description="Run deterministic analysis on a dataset you own. Results use the backend’s descriptive standard." /><EmptyState title="Choose a dataset to analyze" body="Analysis is calculated from a dataset after its quality report is reviewed. Start with one of your saved datasets." action={<Link to="/quality" className="button button-primary button-small">Choose a dataset <ArrowRight size={15} /></Link>} /></div>;
  if (analysisQuery.isLoading || findingsQuery.isLoading) return <LoadingState label="Loading verified analysis…" />;
  if (analysisQuery.isError) return <ErrorState error={getErrorMessage(analysisQuery.error)} onRetry={() => void analysisQuery.refetch()} />;
  if (findingsQuery.isError) return <ErrorState error={getErrorMessage(findingsQuery.error)} onRetry={() => void findingsQuery.refetch()} />;
  const analysis = analysisQuery.data!;
  const findings = findingsQuery.data!;

  return <div className="page-stack">
    <PageHeading eyebrow={view === "findings" ? "FINDINGS & HOTSPOTS" : `ANALYSIS · ${analysis.standard}`} title={view === "findings" ? "Evidence-linked findings." : "Measured data, made legible."} description="All values below are returned by the deterministic backend analysis. Findings link to persisted evidence." actions={<div className="inline-actions">{view === "findings" && <button className="button button-outline" onClick={() => navigate(`/analysis/${analysisId}`)}><BarChart3 size={16} /> Analysis overview</button>}<button className="button button-outline" onClick={() => navigate("/recommendations")}><Sparkles size={16} /> Recommendations</button><button className="button button-primary" onClick={() => navigate("/brief/internal")}><FilePlus2 size={16} /> Generate a brief</button></div>} />
    {view === "overview" && <div className="analysis-summary">
      <div><span className="summary-label">Data completeness</span><strong>{percent(analysis.dataCompleteness)}</strong><small>Valid measured rows ÷ submitted rows</small></div>
      <div><span className="summary-label">Pollutants analyzed</span><strong>{analysis.metrics.length}</strong><small>Returned metrics</small></div>
      <div><span className="summary-label">Analysis standard</span><strong className="summary-standard">{analysis.standard}</strong><small>Descriptive statistics only</small></div>
      <div className="summary-action"><Link to="/brief/advisory" className="text-link">Create citizen advisory <ArrowRight size={14} /></Link></div>
    </div>}
    {view === "overview" && <div className="dashboard-grid analysis-grid">
      <Card className="chart-card"><div className="section-heading"><div><div className="eyebrow">MEASURED CONCENTRATION</div><h2>Pollutant range</h2></div><span className="legend-note"><span className="legend-dot" /> Average · min / max</span></div>
        {analysis.metrics.length === 0 ? <EmptyState title="No pollutant metrics returned" body="The API returned no supported measurements for this analysis." /> :
          <div className="chart-wrap"><ResponsiveContainer width="100%" height={280}><BarChart data={analysis.metrics} margin={{ left: 0, right: 12, top: 10, bottom: 4 }}><CartesianGrid stroke="#e9eee8" vertical={false} /><XAxis dataKey="pollutant" axisLine={false} tickLine={false} tick={{ fill: "#64746a", fontSize: 12 }} /><YAxis axisLine={false} tickLine={false} tick={{ fill: "#839087", fontSize: 11 }} /><Tooltip formatter={(value, name) => [`${Number(value).toFixed(2)}`, String(name)]} contentStyle={{ borderRadius: 12, border: "1px solid #e6ece5", fontSize: 12 }} /><Legend wrapperStyle={{ fontSize: 12, paddingTop: 10 }} /><Bar dataKey="minimum" name="Minimum" fill="#cfe2d2" radius={[4, 4, 0, 0]} /><Bar dataKey="average" name="Average" fill="#34795a" radius={[4, 4, 0, 0]} /><Bar dataKey="maximum" name="Maximum" fill="#e5b474" radius={[4, 4, 0, 0]} /></BarChart></ResponsiveContainer></div>}
      </Card>
      <Card className="location-card"><div className="section-heading"><div><div className="eyebrow">LOCATION BREAKDOWN</div><h2>Ward comparison</h2></div><MapPinned size={17} className="muted-icon" /></div>
        {analysis.locations.length === 0 ? <EmptyState title="No location breakdown" body="The analysis response did not include location-level metrics." /> : <div className="location-table-wrap"><table className="data-table"><thead><tr><th>Location</th><th>Pollutant</th><th>Average</th><th>Readings</th></tr></thead><tbody>{analysis.locations.map((item, index) => <tr key={`${item.location}-${item.pollutant}-${index}`}><td><strong>{item.location}</strong></td><td><span className="pollutant-tag">{item.pollutant}</span></td><td>{item.average.toFixed(2)} <small>{item.unit}</small></td><td>{item.observationCount}</td></tr>)}</tbody></table></div>}
      </Card>
    </div>}
    <Card className="findings-card"><div className="section-heading"><div><div className="eyebrow">EVIDENCE-LINKED RESULTS</div><h2>Findings & potential hotspots <span className="count-pill">{findings.length}</span></h2></div><span className="muted-note"><CircleHelp size={15} /> A hotspot is relative to submitted locations</span></div>
      {findings.length === 0 ? <EmptyState title="No findings returned" body="There are no verified findings in this analysis. The UI will not infer hotspots or fabricate conclusions." /> :
        <div className="finding-grid">{findings.map((finding) => <article className="finding-card" key={finding.id}><div className="finding-top"><span className={`finding-category ${finding.category === "POTENTIAL_HOTSPOT" ? "hotspot-tag" : ""}`}>{finding.category.replaceAll("_", " ")}</span>{finding.pollutant && <span className="pollutant-tag">{finding.pollutant}</span>}</div><h3>{finding.summary}</h3>{finding.value != null && <div className="finding-value">{finding.value.toFixed(2)} <small>{finding.unit}</small></div>}<div className="confidence-line"><span>Data completeness confidence</span><strong>{percent(finding.confidence)}</strong></div><div className="confidence-track"><span style={{ width: `${Math.min(100, Math.max(0, finding.confidence * 100))}%` }} /></div>{finding.limitations.length > 0 && <p className="finding-limit">{finding.limitations.join(" ")}</p>}<div className="finding-evidence"><span>Evidence IDs</span><EvidenceList ids={finding.evidenceIds} /></div></article>)}</div>}
    </Card>
    <div className="notice-banner"><BarChart3 size={18} /><p><strong>Not AQI.</strong> Descriptive measurement summaries and findings do not represent regulatory exceedances, health effects, or causation.</p></div>
  </div>;
}
