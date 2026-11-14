import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ArrowDownToLine, ArrowRight, CheckCircle2, FileSpreadsheet, FileUp, FileWarning, LoaderCircle, UploadCloud } from "lucide-react";
import { useRef, useState, type DragEvent } from "react";
import { useForm } from "react-hook-form";
import { useNavigate } from "react-router-dom";
import { z } from "zod";
import { api, getErrorMessage } from "../api/client";
import { Card, FieldError, PageHeading } from "../components/ui";

const fileSchema = z.object({
  locationName: z.string().trim().min(1, "Enter a location or ward name.").max(160),
  file: z.instanceof(File, { message: "Choose a CSV or XLSX file." })
    .refine((file) => /\.(csv|xlsx)$/i.test(file.name), "Use a .csv or .xlsx file.")
    .refine((file) => file.size <= 20 * 1024 * 1024, "File size must be 20 MB or less."),
  periodStart: z.string().optional(),
  periodEnd: z.string().optional(),
}).refine((values) => !values.periodStart || !values.periodEnd || new Date(values.periodStart) < new Date(values.periodEnd), {
  path: ["periodEnd"], message: "The end of the declared period must be after its start.",
});

const pastedSchema = z.object({
  name: z.string().trim().min(1, "Add a name for this dataset.").max(160),
  locationName: z.string().trim().min(1, "Enter a location or ward name.").max(160),
  delimiter: z.enum([",", ";", "\t"]),
  content: z.string().trim().min(1, "Paste a header row and at least one data row.").max(2_000_000, "Pasted data must be 2 MB or less."),
  periodStart: z.string().optional(),
  periodEnd: z.string().optional(),
});

type FileFields = z.infer<typeof fileSchema>;
type PasteFields = z.infer<typeof pastedSchema>;
type UploadMode = "file" | "paste";

export default function UploadPage() {
  const [mode, setMode] = useState<UploadMode>("file");
  const [fileProgress, setFileProgress] = useState(0);
  const [submitError, setSubmitError] = useState("");
  const fileInput = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const fileForm = useForm<FileFields>({ resolver: zodResolver(fileSchema), defaultValues: { locationName: "", periodStart: "", periodEnd: "" } });
  const pasteForm = useForm<PasteFields>({ resolver: zodResolver(pastedSchema), defaultValues: { name: "", locationName: "", delimiter: ",", content: "", periodStart: "", periodEnd: "" } });
  const file = fileForm.watch("file");

  const completeUpload = (dataset: Awaited<ReturnType<typeof api.upload>>) => {
    localStorage.setItem("civic-air-selected-dataset", dataset.id);
    queryClient.setQueryData(["datasets"], (current: typeof dataset[] | undefined) => current ? [dataset, ...current] : [dataset]);
    void queryClient.invalidateQueries({ queryKey: ["datasets"] });
    void queryClient.invalidateQueries({ queryKey: ["briefs"] });
    navigate(`/datasets/${dataset.id}/quality`);
  };

  const fileMutation = useMutation({
    mutationFn: (values: FileFields) => api.upload(values.file, values.locationName, values.periodStart, values.periodEnd, setFileProgress),
    onSuccess: completeUpload,
    onError: (error) => setSubmitError(getErrorMessage(error)),
  });
  const pasteMutation = useMutation({
    mutationFn: (values: PasteFields) => api.pastedDataset(values),
    onSuccess: completeUpload,
    onError: (error) => setSubmitError(getErrorMessage(error)),
  });

  function chooseFile(next?: File) {
    if (next) fileForm.setValue("file", next, { shouldValidate: true, shouldDirty: true });
  }
  function onDrop(event: DragEvent<HTMLButtonElement>) {
    event.preventDefault();
    chooseFile(event.dataTransfer.files[0]);
  }

  return <div className="page-stack">
    <PageHeading eyebrow="DATA INTAKE" title="Bring your air-quality data." description="Submit your source file or paste tabular records. Original values are preserved and data quality is reported before analysis." />
    <div className="upload-layout">
      <Card className="upload-main">
        <div className="tab-switch" role="tablist" aria-label="Dataset input type">
          <button role="tab" aria-selected={mode === "file"} className={mode === "file" ? "tab-active" : ""} onClick={() => { setMode("file"); setSubmitError(""); }}><FileUp size={16} /> Upload a file</button>
          <button role="tab" aria-selected={mode === "paste"} className={mode === "paste" ? "tab-active" : ""} onClick={() => { setMode("paste"); setSubmitError(""); }}><ArrowDownToLine size={16} /> Paste data</button>
        </div>
        {mode === "file" ? <form className="form-stack" onSubmit={fileForm.handleSubmit((values) => { setSubmitError(""); fileMutation.mutate(values); })} noValidate>
          <button type="button" className={`drop-zone ${file ? "drop-zone-selected" : ""}`} onClick={() => fileInput.current?.click()} onDragOver={(event) => event.preventDefault()} onDrop={onDrop} aria-label="Choose or drop a CSV or XLSX file">
            <input ref={fileInput} className="visually-hidden" type="file" accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" onChange={(event) => chooseFile(event.target.files?.[0])} aria-label="Choose dataset file" />
            <span className="drop-icon">{file ? <FileSpreadsheet size={22} /> : <UploadCloud size={24} />}</span>
            <strong>{file ? file.name : "Drop a CSV or Excel file here"}</strong>
            <span>{file ? `${(file.size / 1024).toFixed(0)} KB · Ready to upload` : "or click to browse your files"}</span>
            <small>CSV or XLSX · maximum 20 MB</small>
          </button>
          <FieldError>{fileForm.formState.errors.file?.message}</FieldError>
          <label className="field-label" htmlFor="file-location">Location or ward name <span className="required">*</span></label>
          <input id="file-location" className="text-input" placeholder="e.g. Ward 12, Central District" {...fileForm.register("locationName")} />
          <FieldError>{fileForm.formState.errors.locationName?.message}</FieldError>
          <div className="form-grid-two"><div><label className="field-label" htmlFor="file-start">Period start <span className="optional">Optional</span></label><input id="file-start" className="text-input" type="datetime-local" {...fileForm.register("periodStart")} /></div><div><label className="field-label" htmlFor="file-end">Period end <span className="optional">Optional</span></label><input id="file-end" className="text-input" type="datetime-local" {...fileForm.register("periodEnd")} /><FieldError>{fileForm.formState.errors.periodEnd?.message}</FieldError></div></div>
          {submitError && <UploadError message={submitError} />}
          {fileMutation.isPending && <div className="progress-wrap"><div className="progress-meta"><span>Uploading source file</span><strong>{fileProgress}%</strong></div><div className="progress-track"><div style={{ width: `${fileProgress}%` }} /></div></div>}
          <button className="button button-primary submit-button" type="submit" disabled={fileMutation.isPending}>{fileMutation.isPending ? <><LoaderCircle className="spin" size={17} /> Uploading…</> : <><UploadCloud size={17} /> Upload and review quality</>}</button>
        </form> : <form className="form-stack" onSubmit={pasteForm.handleSubmit((values) => { setSubmitError(""); pasteMutation.mutate(values); })} noValidate>
          <div className="form-grid-two"><div><label htmlFor="paste-name" className="field-label">Dataset name <span className="required">*</span></label><input id="paste-name" className="text-input" placeholder="Morning monitoring run" {...pasteForm.register("name")} /><FieldError>{pasteForm.formState.errors.name?.message}</FieldError></div><div><label htmlFor="paste-location" className="field-label">Location or ward <span className="required">*</span></label><input id="paste-location" className="text-input" placeholder="Ward 12" {...pasteForm.register("locationName")} /><FieldError>{pasteForm.formState.errors.locationName?.message}</FieldError></div></div>
          <div className="form-grid-two"><div><label htmlFor="paste-delimiter" className="field-label">Column delimiter</label><select id="paste-delimiter" className="text-input" {...pasteForm.register("delimiter")}><option value=",">Comma</option><option value=";">Semicolon</option><option value={"\t"}>Tab</option></select></div><div><div className="field-label">Declared period <span className="optional">Optional</span></div><div className="date-pair"><input aria-label="Period start" className="text-input" type="datetime-local" {...pasteForm.register("periodStart")} /><input aria-label="Period end" className="text-input" type="datetime-local" {...pasteForm.register("periodEnd")} /></div></div></div>
          <label htmlFor="pasted-content" className="field-label">Tabular data <span className="required">*</span></label>
          <textarea id="pasted-content" className="text-input code-textarea" rows={10} placeholder={"timestamp,location,pollutant,value,unit\n2026-03-01T10:00:00Z,Ward 12,PM2.5,18,ug/m3"} {...pasteForm.register("content")} aria-describedby="paste-help" />
          <div id="paste-help" className="field-hint">Use a header row. Values stay as submitted; missing readings and unknown units will be reported, not guessed.</div>
          <FieldError>{pasteForm.formState.errors.content?.message}</FieldError>
          {submitError && <UploadError message={submitError} />}
          <button className="button button-primary submit-button" type="submit" disabled={pasteMutation.isPending}>{pasteMutation.isPending ? <><LoaderCircle className="spin" size={17} /> Submitting…</> : <><ArrowRight size={17} /> Submit and review quality</>}</button>
        </form>}
      </Card>
      <aside className="upload-aside">
        <Card className="aside-card"><div className="aside-card-title"><span className="aside-icon"><CheckCircle2 size={17} /></span><strong>What happens next</strong></div><ol className="step-list"><li><span>1</span><div><strong>Source is preserved</strong><p>Submitted rows remain available as evidence.</p></div></li><li><span>2</span><div><strong>Quality is checked</strong><p>Duplicates, gaps, and unsupported values are surfaced.</p></div></li><li><span>3</span><div><strong>You choose to analyze</strong><p>No metrics are generated until you start an analysis.</p></div></li></ol></Card>
        <Card className="aside-card soft-card"><div className="aside-card-title"><span className="aside-icon warning-icon"><FileWarning size={17} /></span><strong>Data handling</strong></div><p>Unknown units are not converted. Missing values are not replaced with zero. AQI is not calculated.</p><div className="aside-footnote">All analysis is descriptive and links back to your source rows.</div></Card>
      </aside>
    </div>
  </div>;
}

function UploadError({ message }: { message: string }) {
  return <div className="form-alert" role="alert"><FileWarning size={17} />{message}</div>;
}
