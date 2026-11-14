import { useQuery, useQueries } from "@tanstack/react-query";
import { ArrowRight, ArrowUpRight, BarChart3, CircleAlert, Database, FileClock, FileText, MapPinned, Plus, Rows3, Sparkles } from "lucide-react";
import { Link, useNavigate } from "react-router-dom";
import { api, getErrorMessage } from "../api/client";
import { Card, EmptyState, ErrorState, formatDate, LoadingState } from "../components/ui";
import { getActiveAnalysisId, getTrackedAnalyses } from "../api/workspace";

export default function DashboardPage() {
  const navigate = useNavigate();
  const datasetsQuery = useQuery({ queryKey: ["datasets"], queryFn: api.datasets });
  const briefsQuery = useQuery({ queryKey: ["briefs"], queryFn: api.briefs });
  const analysisHistory = getTrackedAnalyses();
  const activeAnalysisId = getActiveAnalysisId() || analysisHistory[0]?.id || "";
  const activeAnalysisQuery = useQuery({
    queryKey: ["analysis", activeAnalysisId],
    queryFn: () => api.analysis(activeAnalysisId),
    enabled: Boolean(activeAnalysisId),
  });
  const activeFindingsQuery = useQuery({
    queryKey: ["findings", activeAnalysisId],
    queryFn: () => api.findings(activeAnalysisId),
    enabled: Boolean(activeAnalysisId),
  });
  const trackedAnalysisQueries = useQueries({
    queries: analysisHistory.slice(0, 5).map((analysis) => ({
      queryKey: ["analysis", analysis.id],
      queryFn: () => api.analysis(analysis.id),
    })),
  });
  const datasets = datasetsQuery.data ?? [];
  const qualityQueries = useQueries({
    queries: datasets.slice(0, 6).map((dataset) => ({
      queryKey: ["quality", dataset.id],
      queryFn: () => api.quality(dataset.id),
    })),
  });
  const totalRows = datasets.reduce((total, dataset) => total + dataset.rowCount, 0);
  const issueCount = qualityQueries.reduce((total, query) => total + (query.data?.issueCount ?? 0), 0);
  const qualityReady = datasets.length > 0 && qualityQueries.every((query) => query.isSuccess);
  const activeAnalysis = activeAnalysisQuery.data;
  const validatedReadings = activeAnalysis?.metrics.reduce((total, metric) => total + metric.observationCount, 0);
  const verifiedHistory = analysisHistory.slice(0, 5).filter((_, index) => trackedAnalysisQueries[index]?.isSuccess);

  if (datasetsQuery.isLoading || briefsQuery.isLoading) return <LoadingState label="Loading your workspace data…" />;
  if (datasetsQuery.isError) return <ErrorState error={getErrorMessage(datasetsQuery.error)} onRetry={() => void datasetsQuery.refetch()} />;
  if (briefsQuery.isError) return <ErrorState error={getErrorMessage(briefsQuery.error)} onRetry={() => void briefsQuery.refetch()} />;

  return <div className="page-stack">
    <div className="welcome-strip"><div className="welcome-icon"><Sparkles size={18} /></div><div><strong>Good work starts with reliable data.</strong><span>Your workspace only shows information returned by your datasets and analyses.</span></div><Link to="/upload" className="button button-light button-small">Add a dataset <ArrowRight size={15} /></Link></div>
    <div className="page-heading"><div><div className="eyebrow">CIVIC AIR QUALITY · OVERVIEW</div><h1>Your workspace, at a glance.</h1><p>A grounded view of the datasets and briefings your team has created.</p></div><Link to="/upload" className="button button-primary"><Plus size={17} /> Add dataset</Link></div>
    <div className="metric-grid">
      <Metric icon={<Database size={18} />} label="Datasets" value={datasets.length.toLocaleString()} note="In this workspace" tone="sage" />
      <Metric icon={<Rows3 size={18} />} label="Source records" value={totalRows.toLocaleString()} note="Rows submitted to analysis" tone="blue" />
      <Metric icon={<CircleAlert size={18} />} label="Quality issues" value={qualityReady ? issueCount.toLocaleString() : "—"} note={qualityReady ? "Across the 6 most recent datasets" : "Quality reports loading"} tone="amber" />
      <Metric icon={<FileText size={18} />} label="Briefings" value={(briefsQuery.data ?? []).length.toLocaleString()} note="Generated from verified findings" tone="violet" />
      <Metric icon={<Rows3 size={18} />} label="Validated readings" value={validatedReadings?.toLocaleString() ?? "—"} note="Latest verified analysis" tone="sage" />
      <Metric icon={<BarChart3 size={18} />} label="Data completeness" value={activeAnalysis ? `${(activeAnalysis.dataCompleteness * 100).toFixed(1)}%` : "—"} note={activeAnalysis ? "Latest verified analysis" : "Run an analysis to calculate"} tone="blue" />
    </div>
    <div className="dashboard-intelligence-grid">
      <Card><div className="section-heading"><div><div className="eyebrow">LATEST VERIFIED ANALYSIS</div><h2>Pollutant summaries</h2></div><Link className="icon-link" to={activeAnalysisId ? `/analysis/${activeAnalysisId}` : "/analysis"} aria-label="Open analysis"><BarChart3 size={16} /></Link></div>
        {!activeAnalysisId ? <EmptyState title="No completed analysis yet" body="After you review data quality and run an analysis, pollutant summaries will appear here." action={<Link className="text-link" to="/quality">Review a dataset <ArrowRight size={14} /></Link>} /> :
          activeAnalysisQuery.isLoading ? <LoadingState label="Loading the latest verified metrics…" /> :
            activeAnalysisQuery.isError ? <ErrorState error={getErrorMessage(activeAnalysisQuery.error)} onRetry={() => void activeAnalysisQuery.refetch()} /> :
              activeAnalysis!.metrics.length === 0 ? <EmptyState title="No pollutant metrics returned" body="The selected analysis contains no pollutant metric rows." /> :
                <div className="location-table-wrap"><table className="data-table"><thead><tr><th>Pollutant</th><th>Average</th><th>Range</th><th>Readings</th></tr></thead><tbody>{activeAnalysis!.metrics.map((metric) => <tr key={metric.pollutant}><td><span className="pollutant-tag">{metric.pollutant}</span></td><td><strong>{metric.average.toFixed(2)}</strong> <small>{metric.unit}</small></td><td>{metric.minimum.toFixed(2)}–{metric.maximum.toFixed(2)}</td><td>{metric.observationCount}</td></tr>)}</tbody></table><div className="data-source-note">Deterministic descriptive averages · no time-series endpoint is available</div></div>}
      </Card>
      <Card><div className="section-heading"><div><div className="eyebrow">LOCATION BREAKDOWN</div><h2>Ward-level comparisons</h2></div><MapPinned size={17} className="muted-icon" /></div>
        {!activeAnalysisId ? <EmptyState title="No location metrics" body="Ward comparisons appear when an analysis returns location-level results." /> :
          activeAnalysisQuery.isLoading ? <LoadingState label="Loading location metrics…" /> :
            activeAnalysisQuery.isError ? <ErrorState error={getErrorMessage(activeAnalysisQuery.error)} onRetry={() => void activeAnalysisQuery.refetch()} /> :
              activeAnalysis!.locations.length === 0 ? <EmptyState title="No ward comparison returned" body="The API returned no location-level metrics for the selected analysis." /> :
                <div className="location-table-wrap"><table className="data-table"><thead><tr><th>Location</th><th>Pollutant</th><th>Average</th></tr></thead><tbody>{activeAnalysis!.locations.slice(0, 8).map((item, index) => <tr key={`${item.location}-${item.pollutant}-${index}`}><td><strong>{item.location}</strong></td><td>{item.pollutant}</td><td>{item.average.toFixed(2)} <small>{item.unit}</small></td></tr>)}</tbody></table></div>}
      </Card>
      <Card><div className="section-heading"><div><div className="eyebrow">EVIDENCE CHECK</div><h2>Potential hotspots</h2></div><Link className="icon-link" to="/findings" aria-label="Open findings"><Sparkles size={16} /></Link></div>
        {!activeAnalysisId ? <EmptyState title="No verified findings" body="Potential hotspots are displayed only when returned by a completed backend analysis." /> :
          activeFindingsQuery.isLoading ? <LoadingState label="Loading analysis findings…" /> :
            activeFindingsQuery.isError ? <ErrorState error={getErrorMessage(activeFindingsQuery.error)} onRetry={() => void activeFindingsQuery.refetch()} /> :
              (activeFindingsQuery.data ?? []).filter((finding) => finding.category === "POTENTIAL_HOTSPOT").length === 0 ? <EmptyState title="No potential hotspots returned" body="No hotspot category was returned for the selected analysis. The interface does not infer a hotspot." /> :
                <div className="hotspot-list">{activeFindingsQuery.data!.filter((finding) => finding.category === "POTENTIAL_HOTSPOT").slice(0, 5).map((finding) => <Link className="hotspot-row" to="/findings" key={finding.id}><span className="hotspot-mark"><Sparkles size={15} /></span><span><strong>{finding.pollutant ?? finding.summary}</strong><small>{finding.summary}</small></span><ArrowUpRight size={15} /></Link>)}</div>}
      </Card>
      <Card><div className="section-heading"><div><div className="eyebrow">ANALYSIS ACTIVITY</div><h2>Recent analyses</h2></div><FileClock size={17} className="muted-icon" /></div>
        {verifiedHistory.length === 0 ? <EmptyState title="No recent analyses in this browser" body="New analyses are listed here after the API confirms them. Existing analyses from other browsers cannot be listed by the current API." action={<Link className="text-link" to="/quality">Start an analysis <ArrowRight size={14} /></Link>} /> :
          <div className="brief-list">{verifiedHistory.map((analysis) => <Link className="brief-row" to={`/analysis/${analysis.id}`} key={analysis.id}><span className="brief-type-icon icon-internal"><BarChart3 size={16} /></span><span className="brief-row-copy"><strong>{analysis.datasetName}</strong><small>{analysis.locationName} · {trackedAnalysisQueries[analysisHistory.indexOf(analysis)]?.data?.standard}</small></span><time>{formatDate(analysis.createdAt)}</time></Link>)}</div>}
      </Card>
    </div>
    <div className="dashboard-grid">
      <Card className="dataset-card"><div className="section-heading"><div><div className="eyebrow">YOUR DATA</div><h2>Recent datasets</h2></div><Link className="text-link" to="/upload">Add data <ArrowRight size={14} /></Link></div>
        {datasets.length === 0 ? <EmptyState title="Your workspace is ready" body="Upload a CSV, Excel workbook, or paste tabular readings to begin. No sample statistics are shown here." action={<Link className="button button-primary button-small" to="/upload"><Plus size={15} /> Upload your first dataset</Link>} /> :
          <div className="dataset-list">{datasets.slice(0, 6).map((dataset) => <button className="dataset-row" key={dataset.id} onClick={() => navigate(`/datasets/${dataset.id}/quality`)}><div className="dataset-type">{dataset.sourceType === "PASTED" ? <Rows3 size={18} /> : <Database size={18} />}</div><div className="dataset-name"><strong>{dataset.name}</strong><span>{dataset.locationName} · {dataset.rowCount.toLocaleString()} records</span></div><time>{formatDate(dataset.createdAt)}</time><ArrowUpRight size={16} className="row-arrow" /></button>)}</div>}
      </Card>
      <Card className="recent-brief-card"><div className="section-heading"><div><div className="eyebrow">COMMUNICATIONS</div><h2>Recent briefings</h2></div><Link className="icon-link" to="/history" aria-label="View all briefings"><ArrowRight size={17} /></Link></div>
        {(briefsQuery.data ?? []).length === 0 ? <EmptyState title="No briefings yet" body="Generate a sourced internal note or citizen advisory after you analyze a dataset." action={<Link to="/analysis" className="text-link">Go to analysis <ArrowRight size={14} /></Link>} /> :
          <div className="brief-list">{briefsQuery.data!.slice(0, 5).map((brief) => <Link className="brief-row" to={`/briefs/${brief.id}`} key={brief.id}><span className={`brief-type-icon ${brief.type === "INTERNAL" ? "icon-internal" : "icon-advisory"}`}>{brief.type === "INTERNAL" ? <FileText size={16} /> : <Sparkles size={16} />}</span><span className="brief-row-copy"><strong>{brief.type === "INTERNAL" ? "Internal briefing" : "Citizen advisory"}</strong><small>{brief.audience.replaceAll("_", " ")} · {brief.language.toUpperCase()}</small></span><time>{formatDate(brief.createdAt)}</time></Link>)}</div>}
      </Card>
    </div>
    <div className="insight-grid">
      <Card className="insight-card"><span className="insight-icon insight-green"><BarChart3 size={18} /></span><div><strong>Analysis is descriptive</strong><p>Pollutant averages and comparisons are calculated from submitted measurements. AQI is not calculated unless a standard is configured.</p></div></Card>
      <Card className="insight-card"><span className="insight-icon insight-amber"><CircleAlert size={18} /></span><div><strong>Quality before conclusions</strong><p>Review missing, duplicate, or unsupported values before using any finding in a public briefing.</p></div></Card>
    </div>
  </div>;
}

function Metric({ icon, label, value, note, tone }: { icon: React.ReactNode; label: string; value: string; note: string; tone: string }) {
  return <Card className="metric-card"><div className={`metric-icon metric-${tone}`}>{icon}</div><div className="metric-label">{label}</div><div className="metric-value">{value}</div><div className="metric-note">{note}</div></Card>;
}
