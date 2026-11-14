import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, ArrowRight, BookOpenText, Check, Clipboard, FileText, Info, LoaderCircle, Sparkles } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useParams } from "react-router-dom";
import { z } from "zod";
import { api, getErrorMessage } from "../api/client";
import { getActiveAnalysisId } from "../api/workspace";
import { useAuth } from "../auth/AuthContext";
import type { Brief } from "../api/types";
import { getTrackedAnalyses } from "../api/workspace";
import { Card, EmptyState, ErrorState, EvidenceList, formatDate, LoadingState, PageHeading } from "../components/ui";

const briefSchema = z.object({
  audience: z.enum(["WARD_OFFICER", "HOUSING_SOCIETY", "SCHOOL"]),
  language: z.enum(["en", "hi"]),
  readingLevel: z.enum(["SIMPLE", "DETAILED"]),
});
type BriefFields = z.infer<typeof briefSchema>;

export function BriefGeneratorPage({ type }: { type: "INTERNAL" | "CITIZEN_ADVISORY" }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const analysisId = getActiveAnalysisId();
  const [generated, setGenerated] = useState<Brief | null>(null);
  const [errorMessage, setErrorMessage] = useState("");
  const { register, handleSubmit, formState: { errors } } = useForm<BriefFields>({
    resolver: zodResolver(briefSchema),
    defaultValues: { audience: user?.role === "ADMIN" ? "SCHOOL" : user?.role ?? "SCHOOL", language: "en", readingLevel: "SIMPLE" },
  });
  const analysisQuery = useQuery({ queryKey: ["analysis", analysisId], queryFn: () => api.analysis(analysisId), enabled: Boolean(analysisId) });
  const mutation = useMutation({
    mutationFn: (values: BriefFields) => api.generateBrief(analysisId, type === "INTERNAL" ? "briefs" : "citizen-advisories", values),
    onSuccess: (brief) => {
      setGenerated(brief);
      setErrorMessage("");
      void queryClient.invalidateQueries({ queryKey: ["briefs"] });
    },
    onError: (error) => setErrorMessage(getErrorMessage(error)),
  });

  if (!analysisId) return <div className="page-stack"><PageHeading eyebrow={type === "INTERNAL" ? "INTERNAL NOTE" : "PUBLIC COMMUNICATION"} title={type === "INTERNAL" ? "Generate an internal briefing." : "Create a citizen advisory."} description="Briefs can only be generated from an analysis with validated evidence." /><EmptyState title="No active analysis" body="Run a descriptive analysis before generating a briefing." action={<Link to="/quality" className="button button-primary button-small">Choose a dataset <ArrowRight size={15} /></Link>} /></div>;
  if (analysisQuery.isLoading) return <LoadingState label="Verifying the selected analysis…" />;
  if (analysisQuery.isError) return <ErrorState error={getErrorMessage(analysisQuery.error)} onRetry={() => void analysisQuery.refetch()} />;

  return <div className="page-stack">
    <PageHeading eyebrow={type === "INTERNAL" ? "INTERNAL NOTE" : "PUBLIC COMMUNICATION"} title={type === "INTERNAL" ? "Generate an internal briefing." : "Create a citizen advisory."} description="Choose the intended audience and reading style. Generated content must cite verified finding IDs and pass backend validation." actions={<Link to={`/analysis/${analysisId}`} className="button button-outline"><ArrowLeft size={15} /> Back to analysis</Link>} />
    <div className="brief-layout">
      <Card className="brief-config"><div className="section-heading"><div><div className="eyebrow">BRIEF SETTINGS</div><h2>Set the context</h2></div><span className="brief-setup-icon">{type === "INTERNAL" ? <FileText size={19} /> : <BookOpenText size={19} />}</span></div>
        <form className="form-stack" onSubmit={handleSubmit((values) => { setErrorMessage(""); mutation.mutate(values); })} noValidate>
          <label className="field-label" htmlFor="brief-audience">Target audience</label>
          <select id="brief-audience" className="text-input" {...register("audience")}>
            <option value="SCHOOL" disabled={user?.role !== "ADMIN" && user?.role !== "SCHOOL"}>School{user?.role !== "ADMIN" && user?.role !== "SCHOOL" ? " — requires authorized account" : ""}</option><option value="WARD_OFFICER" disabled={user?.role !== "ADMIN" && user?.role !== "WARD_OFFICER"}>Ward Officer{user?.role !== "ADMIN" && user?.role !== "WARD_OFFICER" ? " — requires authorized account" : ""}</option><option value="HOUSING_SOCIETY" disabled={user?.role !== "ADMIN" && user?.role !== "HOUSING_SOCIETY"}>Housing Society{user?.role !== "ADMIN" && user?.role !== "HOUSING_SOCIETY" ? " — requires authorized account" : ""}</option>
          </select><FieldErrorText message={errors.audience?.message} />
          <label className="field-label" htmlFor="brief-language">Language</label><select id="brief-language" className="text-input" {...register("language")}><option value="en">English</option><option value="hi">Hindi</option></select>
          <label className="field-label" htmlFor="brief-level">Reading level</label><select id="brief-level" className="text-input" {...register("readingLevel")}><option value="SIMPLE">Simple</option><option value="DETAILED">Detailed</option></select>
          {errorMessage && <div className="form-alert" role="alert">{errorMessage}</div>}
          <button type="submit" className="button button-primary submit-button" disabled={mutation.isPending}>{mutation.isPending ? <><LoaderCircle className="spin" size={16} /> Generating…</> : <><Sparkles size={16} /> Generate {type === "INTERNAL" ? "briefing" : "advisory"}</>}</button>
        </form>
        <div className="brief-analysis-ref"><Info size={15} /><span>Using analysis <code>{analysisId.slice(0, 8)}…</code> · {analysisQuery.data!.standard}</span></div>
      </Card>
      <Card className="brief-result-card"><div className="section-heading"><div><div className="eyebrow">VERIFIED OUTPUT</div><h2>{generated ? "Generated from evidence" : "Your brief will appear here"}</h2></div>{generated && <span className="verified-chip"><Check size={14} /> Validated</span>}</div>
        {generated ? <article className="generated-brief"><div className="generated-meta">{generated.audience.replaceAll("_", " ")} · {generated.language.toUpperCase()} · {generated.readingLevel.toLowerCase()}</div><div className="generated-copy">{generated.content}</div><div className="generated-evidence"><strong>Evidence references returned by the API</strong><EvidenceList ids={generated.evidenceIds} /></div>{generated.limitations.length > 0 && <div className="limitations-block"><strong>Limitations</strong><ul>{generated.limitations.map((limitation) => <li key={limitation}>{limitation}</li>)}</ul></div>}<button className="button button-outline button-small" onClick={() => navigate(`/briefs/${generated.id}`)}>Open saved brief <ArrowRight size={14} /></button></article> :
          <EmptyState title="No generated content" body="A brief is only displayed after the API returns successfully. Rejected or unavailable AI output is never replaced with sample text." action={<div className="evidence-placeholder"><Clipboard size={18} /><span>Evidence IDs and limitations will appear with the verified response.</span></div>} />}
      </Card>
    </div>
    <div className="notice-banner"><Info size={18} /><p><strong>Safety check:</strong> The backend validates evidence references and numerical claims. No AQI, regulatory exceedance, or medical advice is generated by this interface.</p></div>
  </div>;
}

function FieldErrorText({ message }: { message?: string }) {
  return message ? <span className="field-error">{message}</span> : null;
}

export function BriefHistoryPage() {
  const historyQuery = useQuery({ queryKey: ["briefs"], queryFn: api.briefs });
  if (historyQuery.isLoading) return <LoadingState label="Loading saved briefings…" />;
  if (historyQuery.isError) return <ErrorState error={getErrorMessage(historyQuery.error)} onRetry={() => void historyQuery.refetch()} />;
  const briefs = historyQuery.data ?? [];
  return <div className="page-stack"><PageHeading eyebrow="YOUR COMMUNICATIONS" title="Brief history." description="Previously generated content returned by your workspace API." />
    {briefs.length === 0 ? <EmptyState title="No saved briefings" body="Once a briefing or citizen advisory is generated, it will appear here." action={<Link to="/analysis" className="button button-primary button-small">Go to analysis <ArrowRight size={15} /></Link>} /> :
      <Card className="history-card"><div className="history-header"><span>TYPE</span><span>BRIEF</span><span>AUDIENCE</span><span>CREATED</span><span /></div>{briefs.map((brief) => <Link className="history-row" to={`/briefs/${brief.id}`} key={brief.id}><span className={`brief-type-icon ${brief.type === "INTERNAL" ? "icon-internal" : "icon-advisory"}`}>{brief.type === "INTERNAL" ? <FileText size={16} /> : <Sparkles size={16} />}</span><span className="history-title"><strong>{brief.type === "INTERNAL" ? "Internal briefing" : "Citizen advisory"}</strong><small>{brief.language.toUpperCase()} · {brief.readingLevel.toLowerCase()}</small></span><span className="history-audience">{brief.audience.replaceAll("_", " ")}</span><time>{formatDate(brief.createdAt)}</time><ArrowRight size={16} /></Link>)}</Card>}
  </div>;
}

export function BriefDetailPage() {
  const { briefId = "" } = useParams();
  const query = useQuery({ queryKey: ["brief", briefId], queryFn: () => api.brief(briefId), enabled: Boolean(briefId) });
  if (query.isLoading) return <LoadingState label="Loading saved briefing…" />;
  if (query.isError) return <ErrorState error={getErrorMessage(query.error)} onRetry={() => void query.refetch()} />;
  const brief = query.data!;
  return <div className="page-stack"><PageHeading eyebrow="SAVED COMMUNICATION" title={brief.type === "INTERNAL" ? "Internal briefing." : "Citizen advisory."} description={`${brief.audience.replaceAll("_", " ")} · ${brief.language.toUpperCase()} · ${brief.readingLevel.toLowerCase()} · ${formatDate(brief.createdAt)}`} actions={<Link to="/history" className="button button-outline"><ArrowLeft size={15} /> Brief history</Link>} />
    <Card className="brief-detail"><div className="verified-chip"><Check size={14} /> Retrieved from workspace API</div><div className="generated-copy">{brief.content}</div><div className="generated-evidence"><strong>Evidence references</strong><EvidenceList ids={brief.evidenceIds} /></div>{brief.limitations.length > 0 && <div className="limitations-block"><strong>Limitations</strong><ul>{brief.limitations.map((limitation) => <li key={limitation}>{limitation}</li>)}</ul></div>}<Link className="text-link" to={`/analysis/${brief.analysisId}`}>View source analysis <ArrowRight size={14} /></Link></Card>
  </div>;
}

export function ComparisonPage() {
  const [currentId, setCurrentId] = useState("");
  const [previousId, setPreviousId] = useState("");
  const [result, setResult] = useState<Awaited<ReturnType<typeof api.compare>> | null>(null);
  const [error, setError] = useState("");
  const history = getTrackedAnalyses();
  const mutation = useMutation({
    mutationFn: () => api.compare(currentId, previousId),
    onSuccess: (comparison) => { setResult(comparison); setError(""); },
    onError: (cause) => { setResult(null); setError(getErrorMessage(cause)); },
  });
  const onCompare = () => {
    setError("");
    setResult(null);
    mutation.mutate();
  };
  return <div className="page-stack"><PageHeading eyebrow="PERIOD-TO-PERIOD" title="Compare compatible analyses." description="Compare results only when location, standard, and declared window duration match." />
    {history.length < 2 ? <EmptyState title="More analyses needed" body="Run analyses on at least two datasets in this browser session. Comparison is performed by the backend and requires compatible declared time windows." action={<Link to="/quality" className="button button-primary button-small">Choose a dataset <ArrowRight size={15} /></Link>} /> :
      <Card className="compare-form-card"><div className="form-grid-two"><div><label className="field-label" htmlFor="current-analysis">Current analysis</label><select id="current-analysis" className="text-input" value={currentId} onChange={(event) => setCurrentId(event.target.value)}><option value="">Choose an analysis…</option>{history.map((item) => <option value={item.id} key={`current-${item.id}`}>{item.datasetName} · {item.locationName}</option>)}</select></div><div><label className="field-label" htmlFor="previous-analysis">Previous analysis</label><select id="previous-analysis" className="text-input" value={previousId} onChange={(event) => setPreviousId(event.target.value)}><option value="">Choose an analysis…</option>{history.filter((item) => item.id !== currentId).map((item) => <option value={item.id} key={`previous-${item.id}`}>{item.datasetName} · {item.locationName}</option>)}</select></div></div><div className="compare-submit"><span>Compatibility is verified by the backend. Equal period lengths and matching locations are required.</span><button className="button button-primary" disabled={!currentId || !previousId || mutation.isPending} onClick={onCompare}>{mutation.isPending ? <><LoaderCircle className="spin" size={16} /> Comparing…</> : <>Compare analyses <ArrowRight size={15} /></>}</button></div>{error && <div role="alert" className="form-alert">{error}</div>}</Card>}
    {result && <div className="page-stack"><div className="comparison-title"><div><div className="eyebrow">BACKEND COMPARISON</div><h2>Measurement changes</h2></div><span className={`confidence-change ${result.confidenceChange >= 0 ? "change-good" : "change-bad"}`}>{result.confidenceChange >= 0 ? "+" : ""}{(result.confidenceChange * 100).toFixed(1)}% completeness change</span></div>
      {result.changes.length === 0 ? <EmptyState title="No comparison rows returned" body="The backend found no compatible pollutant measurements to display." /> : <Card className="comparison-card"><div className="comparison-table-scroll"><table className="data-table comparison-table"><thead><tr><th>Pollutant</th><th>Previous</th><th>Current</th><th>Change</th><th>Evidence</th></tr></thead><tbody>{result.changes.map((item) => <tr key={item.pollutant}><td><strong>{item.pollutant}</strong></td><td>{item.previousValue == null ? "—" : `${item.previousValue.toFixed(2)} ${item.unit ?? ""}`}</td><td>{item.currentValue == null ? "—" : `${item.currentValue.toFixed(2)} ${item.unit ?? ""}`}</td><td><span className={`change-pill change-${item.classification.toLowerCase()}`}>{item.classification.replaceAll("_", " ")}</span></td><td><EvidenceList ids={item.evidenceIds} /></td></tr>)}</tbody></table></div></Card>}
      {result.limitations.length > 0 && <Card className="comparison-limitations"><strong>Limitations</strong><ul>{result.limitations.map((item) => <li key={item}>{item}</li>)}</ul></Card>}
    </div>}
    <div className="notice-banner"><Info size={18} /><p><strong>Changes are descriptive.</strong> “Improved” and “worsened” follow the backend’s numeric comparison and do not imply safety, causation, or regulatory compliance.</p></div>
  </div>;
}
