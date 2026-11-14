export interface TrackedAnalysis {
  id: string;
  datasetId: string;
  datasetName: string;
  locationName: string;
  createdAt: string;
}

const KEY = "civic-air-analysis-history";

export function trackAnalysis(value: TrackedAnalysis) {
  const history = getTrackedAnalyses().filter((item) => item.id !== value.id);
  localStorage.setItem(KEY, JSON.stringify([value, ...history].slice(0, 30)));
  localStorage.setItem("civic-air-active-analysis", value.id);
}

export function getTrackedAnalyses(): TrackedAnalysis[] {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(KEY) ?? "[]");
    if (!Array.isArray(stored)) return [];
    return stored.filter((item): item is TrackedAnalysis =>
      typeof item?.id === "string" && typeof item?.datasetId === "string"
      && typeof item?.datasetName === "string" && typeof item?.locationName === "string"
      && typeof item?.createdAt === "string");
  } catch {
    return [];
  }
}

export function getActiveAnalysisId() {
  return localStorage.getItem("civic-air-active-analysis") ?? "";
}
