import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowRight, CheckCircle2, FileCheck2, Gauge, Info, LoaderCircle, RotateCw, ShieldAlert, TriangleAlert } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, getErrorMessage } from "../api/client";
import { trackAnalysis } from "../api/workspace";
import { Card, EmptyState, ErrorState, formatDate, LoadingState, PageHeading } from "../components/ui";

export default function QualityPage() {
  const { datasetId: routeId } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState(routeId ?? localStorage.getItem("civic-air-selected-dataset") ?? "");
  const datasetsQuery = useQuery({ queryKey: ["datasets"], queryFn: api.datasets });
  const datasetId = routeId ?? selectedId;
  const datasetQuery = useQuery({ queryKey: ["dataset", datasetId], queryFn: () => api.dataset(datasetId), enabled: Boolean(datasetId) });
  const qualityQuery = useQuery({ queryKey: ["quality", datasetId], queryFn: () => api.quality(datasetId), enabled: Boolean(datasetId) });
  const [analysisError, setAnalysisError] = useState("");

  useEffect(() => {
    if (!routeId && !selectedId && datasetsQuery.data?.length) {
      const recent = localStorage.getItem("civic-air-selected-dataset");
      const found = datasetsQuery.data.find((item) => item.id === recent) ?? datasetsQuery.data[0];
      setSelectedId(found.id);
      localStorage.setItem("civic-air-selected-dataset", found.id);
    }
  }, [routeId, selectedId, datasetsQuery.data]);

  const analysisMutation = useMutation({
    mutationFn: () => api.analyze(datasetId),
    onSuccess: (analysis) => {
      const dataset = datasetQuery.data;
      trackAnalysis({
        id: analysis.id,
        datasetId: analysis.datasetId,
        datasetName: dataset?.name ?? "Dataset",
        locationName: dataset?.locationName ?? "",
        createdAt: analysis.createdAt,
      });
      void queryClient.invalidateQueries({ queryKey: ["analysis", analysis.id] });
      navigate(`/analysis/${analysis.id}`);
    },
    onError: (error) => setAnalysisError(getErrorMessage(error)),
  });

  if (datasetsQuery.isLoading) return <LoadingState />;
  if (datasetsQuery.isError) return <ErrorState error={getErrorMessage(datasetsQuery.error)} onRetry={() => void datasetsQuery.refetch()} />;
  if (!datasetsQuery.data?.length) return <div className="page-stack"><PageHeading eyebrow="DATA QUALITY" title="Quality before conclusions." description="Review source data quality before starting an analysis." /><EmptyState title="No datasets to review" body="Upload or paste an air-quality dataset to create a data quality report." action={<Link className="button button-primary button-small" to="/upload">Add dataset <ArrowRight size={15} /></Link>} /></div>;
  if (!datasetId) return <LoadingState />;
  if (datasetQuery.isLoading || qualityQuery.isLoading) return <LoadingState label="Checking source quality…" />;
  if (datasetQuery.isError) return <ErrorState error={getErrorMessage(datasetQuery.error)} onRetry={() => void datasetQuery.refetch()} />;
  if (qualityQuery.isError) return <ErrorState error={getErrorMessage(qualityQuery.error)} onRetry={() => void qualityQuery.refetch()} />;

  const report = qualityQuery.data!;
  const errors = report.issues.filter((issue) => issue.severity === "ERROR").length;
  const warnings = report.issues.filter((issue) => issue.severity === "WARNING").length;
  const information = report.issues.filter((issue) => issue.severity === "INFO").length;

  return <div className="page-stack">
    <PageHeading eyebrow="SOURCE REVIEW" title="Data quality report." description="Inspect what the validator found. Measurements and source values are not silently replaced." actions={<button className="button button-primary" onClick={() => analysisMutation.mutate()} disabled={analysisMutation.isPending || report.status !== "COMPLETE"}>{analysisMutation.isPending ? <LoaderCircle className="spin" size={16} /> : <Gauge size={17} />} {analysisMutation.isPending ? "Analyzing…" : "Run descriptive analysis"}</button>} />
    <Card className="dataset-summary-card"><div className="dataset-type dataset-summary-icon"><FileCheck2 size={20} /></div><div className="dataset-summary-info"><strong>{datasetQuery.data!.name}</strong><span>{datasetQuery.data!.locationName} · {datasetQuery.data!.sourceType} · Added {formatDate(datasetQuery.data!.createdAt)}</span></div><div className="dataset-summary-select"><label className="field-label" htmlFor="dataset-switch">Review another dataset</label><select id="dataset-switch" className="text-input" value={datasetId} onChange={(event) => { setSelectedId(event.target.value); localStorage.setItem("civic-air-selected-dataset", event.target.value); navigate(`/datasets/${event.target.value}/quality`); }}><option value="" disabled>Select dataset</option>{datasetsQuery.data!.map((dataset) => <option value={dataset.id} key={dataset.id}>{dataset.name} — {dataset.locationName}</option>)}</select></div></Card>
    <div className="metric-grid quality-metrics">
      <QualityMetric icon={<FileCheck2 size={18} />} label="Source rows" value={report.rowCount.toLocaleString()} text="As received" color="sage" />
      <QualityMetric icon={<ShieldAlert size={18} />} label="Errors" value={errors.toLocaleString()} text="Rows requiring review" color="red" />
      <QualityMetric icon={<TriangleAlert size={18} />} label="Warnings" value={warnings.toLocaleString()} text="Potential data issues" color="amber" />
      <QualityMetric icon={<Info size={18} />} label="Information" value={information.toLocaleString()} text="Normalization notes" color="blue" />
    </div>
    {analysisError && <div className="form-alert" role="alert">{analysisError}<button className="button button-outline button-small" onClick={() => analysisMutation.mutate()}><RotateCw size={14} /> Retry analysis</button></div>}
    <div className="dashboard-grid quality-grid">
      <Card><div className="section-heading"><div><div className="eyebrow">VALIDATION</div><h2>Reported issues <span className="count-pill">{report.issues.length}</span></h2></div></div>
        {report.issues.length === 0 ? <div className="success-panel"><CheckCircle2 size={20} /><div><strong>No quality issues reported</strong><span>The backend returned no validation issues for this dataset. This does not imply AQI compliance or health safety.</span></div></div> :
          <div className="issue-list">{report.issues.map((issue) => <div className="issue-row" key={issue.id}><span className={`severity-dot severity-${issue.severity.toLowerCase()}`} /><div className="issue-content"><strong>{issue.code.replaceAll("_", " ")}</strong><span>{issue.message}</span>{issue.rowNumber && <small>Source row {issue.rowNumber}</small>}</div><span className={`severity-label severity-text-${issue.severity.toLowerCase()}`}>{issue.severity}</span></div>)}</div>}
      </Card>
      <Card><div className="section-heading"><div><div className="eyebrow">NORMALIZATION</div><h2>Recorded decisions</h2></div></div>
        {report.normalizationDecisions.length === 0 ? <EmptyState title="No normalization decisions" body="The backend returned no unit or format normalization details for this dataset." /> :
          <div className="decision-list">{report.normalizationDecisions.map((decision, index) => <div className="decision-row" key={`${decision.rowNumber}-${decision.field}-${index}`}><div><strong>{decision.field}</strong><span>Row {decision.rowNumber} · {decision.decision}</span></div><div className="decision-values"><code>{decision.originalValue || "(empty)"}</code>{decision.normalizedValue && <><ArrowRight size={13} /><code>{decision.normalizedValue}</code></>}</div></div>)}</div>}
      </Card>
    </div>
    <div className="notice-banner"><Info size={18} /><p><strong>Source data is retained.</strong> Missing readings are not filled with zero, unsupported units are not converted, and potential outliers are flagged rather than removed.</p></div>
  </div>;
}

function QualityMetric({ icon, label, value, text, color }: { icon: React.ReactNode; label: string; value: string; text: string; color: string }) {
  return <Card className="metric-card"><div className={`metric-icon metric-${color}`}>{icon}</div><div className="metric-label">{label}</div><div className="metric-value">{value}</div><div className="metric-note">{text}</div></Card>;
}
