export type Audience = "WARD_OFFICER" | "HOUSING_SOCIETY" | "SCHOOL";
export type UserRole = Audience | "ADMIN";
export type ReadingLevel = "SIMPLE" | "DETAILED";
export type BriefType = "INTERNAL" | "CITIZEN_ADVISORY";

export interface UserSummary {
  id: string;
  email: string;
  role: UserRole;
}

export interface TokenResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserSummary;
}

export interface Dataset {
  id: string;
  name: string;
  sourceType: "CSV" | "XLSX" | "PASTED";
  status: "READY";
  locationName: string;
  periodStart?: string | null;
  periodEnd?: string | null;
  rowCount: number;
  createdAt: string;
}

export interface QualityIssue {
  id: string;
  rowNumber?: number | null;
  code: string;
  severity: "INFO" | "WARNING" | "ERROR";
  message: string;
}

export interface NormalizationDecision {
  rowNumber: number;
  field: string;
  originalValue: string;
  normalizedValue?: string | null;
  decision: string;
}

export interface QualityReport {
  datasetId: string;
  status: "PENDING" | "COMPLETE" | "FAILED";
  rowCount: number;
  issueCount: number;
  issues: QualityIssue[];
  normalizationDecisions: NormalizationDecision[];
}

export interface PollutantMetric {
  pollutant: string;
  average: number;
  minimum: number;
  maximum: number;
  unit: string;
  observationCount: number;
  evidenceIds: string[];
}

export interface LocationMetric {
  location: string;
  pollutant: string;
  average: number;
  unit: string;
  observationCount: number;
}

export interface AnalysisResult {
  id: string;
  datasetId: string;
  status: "COMPLETE";
  standard: string;
  createdAt: string;
  dataCompleteness: number;
  metrics: PollutantMetric[];
  locations: LocationMetric[];
}

export interface Finding {
  id: string;
  category: string;
  summary: string;
  pollutant?: string | null;
  value?: number | null;
  unit?: string | null;
  evidenceIds: string[];
  confidence: number;
  limitations: string[];
}

export interface Recommendation {
  id: string;
  action: string;
  priority: "LOW" | "MEDIUM" | "HIGH";
  audience: Audience;
  reason: string;
  evidenceIds: string[];
  limitations: string[];
}

export interface Brief {
  id: string;
  analysisId: string;
  type: BriefType;
  audience: Audience;
  language: "en" | "hi";
  readingLevel: ReadingLevel;
  content: string;
  evidenceIds: string[];
  limitations: string[];
  createdAt: string;
}

export interface ComparisonChange {
  pollutant: string;
  classification: "IMPROVED" | "WORSENED" | "UNCHANGED" | "MISSING_DATA";
  currentValue?: number | null;
  previousValue?: number | null;
  unit?: string | null;
  evidenceIds: string[];
}

export interface Comparison {
  currentAnalysisId: string;
  previousAnalysisId: string;
  changes: ComparisonChange[];
  confidenceChange: number;
  limitations: string[];
}

export interface ApiProblem {
  title?: string;
  detail?: string;
  status?: number;
  errorCode?: string;
  fieldErrors?: { field: string; message: string }[];
}
