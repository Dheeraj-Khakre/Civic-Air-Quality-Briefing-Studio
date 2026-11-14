import axios from "axios";
import type { ApiProblem, Audience, Brief, Comparison, Dataset, Finding, QualityReport, Recommendation, TokenResponse, AnalysisResult } from "./types";

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "/api/v1",
  timeout: 30_000,
  headers: { Accept: "application/json" },
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem("civic-air-token");
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError<ApiProblem>(error) && error.response?.status === 401) {
      window.dispatchEvent(new CustomEvent("civic-air:unauthorized"));
    }
    return Promise.reject(error);
  },
);

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiProblem>(error)) {
    const data = error.response?.data;
    if (data?.fieldErrors?.length) return data.fieldErrors.map(({ field, message }) => `${field}: ${message}`).join(" · ");
    if (data?.detail) return data.detail;
    if (data?.title) return data.title;
    if (!error.response) return "Cannot reach the API. Check the connection and try again.";
  }
  return error instanceof Error ? error.message : "Something went wrong. Please try again.";
}

export const api = {
  async register(email: string, password: string): Promise<TokenResponse> {
    return (await http.post<TokenResponse>("/auth/register", { email, password })).data;
  },
  async login(email: string, password: string): Promise<TokenResponse> {
    return (await http.post<TokenResponse>("/auth/login", { email, password })).data;
  },
  async datasets(): Promise<Dataset[]> {
    return (await http.get<Dataset[]>("/datasets")).data;
  },
  async dataset(id: string): Promise<Dataset> {
    return (await http.get<Dataset>(`/datasets/${id}`)).data;
  },
  async quality(id: string): Promise<QualityReport> {
    return (await http.get<QualityReport>(`/datasets/${id}/quality-report`)).data;
  },
  async upload(file: File, locationName: string, periodStart?: string, periodEnd?: string, onProgress?: (percent: number) => void): Promise<Dataset> {
    const body = new FormData();
    body.append("file", file);
    body.append("locationName", locationName);
    if (periodStart) body.append("periodStart", new Date(periodStart).toISOString());
    if (periodEnd) body.append("periodEnd", new Date(periodEnd).toISOString());
    return (await http.post<Dataset>("/datasets", body, {
      onUploadProgress: (event) => {
        if (event.total) onProgress?.(Math.round((event.loaded * 100) / event.total));
      },
    })).data;
  },
  async pastedDataset(input: { name: string; locationName: string; delimiter: string; content: string; periodStart?: string; periodEnd?: string }): Promise<Dataset> {
    return (await http.post<Dataset>("/datasets/pasted", {
      ...input,
      periodStart: input.periodStart ? new Date(input.periodStart).toISOString() : undefined,
      periodEnd: input.periodEnd ? new Date(input.periodEnd).toISOString() : undefined,
    })).data;
  },
  async analyze(datasetId: string, ward?: string): Promise<AnalysisResult> {
    return (await http.post<AnalysisResult>(`/datasets/${datasetId}/analyses`, { standard: "DESCRIPTIVE_V1", ward })).data;
  },
  async analysis(id: string): Promise<AnalysisResult> {
    return (await http.get<AnalysisResult>(`/analyses/${id}`)).data;
  },
  async findings(id: string): Promise<Finding[]> {
    return (await http.get<Finding[]>(`/analyses/${id}/findings`)).data;
  },
  async recommendations(id: string, audience: Audience): Promise<Recommendation[]> {
    return (await http.get<Recommendation[]>(`/analyses/${id}/recommendations`, { params: { audience } })).data;
  },
  async generateBrief(id: string, type: "briefs" | "citizen-advisories", input: { audience: Audience; language: "en" | "hi"; readingLevel: "SIMPLE" | "DETAILED" }): Promise<Brief> {
    return (await http.post<Brief>(`/analyses/${id}/${type}`, input)).data;
  },
  async briefs(): Promise<Brief[]> {
    return (await http.get<Brief[]>("/briefs")).data;
  },
  async brief(id: string): Promise<Brief> {
    return (await http.get<Brief>(`/briefs/${id}`)).data;
  },
  async compare(currentAnalysisId: string, previousAnalysisId: string): Promise<Comparison> {
    return (await http.post<Comparison>("/briefs/compare", { currentAnalysisId, previousAnalysisId })).data;
  },
};
