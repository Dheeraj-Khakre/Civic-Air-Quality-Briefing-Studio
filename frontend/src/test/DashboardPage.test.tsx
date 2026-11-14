import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../api/client";
import type { Dataset } from "../api/types";
import DashboardPage from "../pages/DashboardPage";
import { renderWithProviders } from "./render";

describe("DashboardPage", () => {
  beforeEach(() => {
    vi.spyOn(api, "datasets").mockResolvedValue([]);
    vi.spyOn(api, "briefs").mockResolvedValue([]);
  });

  it("shows a real-data empty state instead of fabricated metrics", async () => {
    renderWithProviders(<DashboardPage />);
    expect(await screen.findByText("Your workspace is ready")).toBeInTheDocument();
    expect(screen.getByText(/No sample statistics are shown here/)).toBeInTheDocument();
    const datasetMetric = screen.getByText("Datasets").parentElement;
    expect(datasetMetric?.querySelector(".metric-value")).toHaveTextContent("0");
  });

  it("renders dataset records supplied by the API", async () => {
    const dataset: Dataset = {
      id: "dataset-1",
      name: "April monitors",
      sourceType: "CSV",
      status: "READY",
      locationName: "Ward 4",
      rowCount: 18,
      createdAt: "2026-04-04T10:00:00Z",
    };
    vi.mocked(api.datasets).mockResolvedValue([dataset]);
    vi.spyOn(api, "quality").mockResolvedValue({
      datasetId: dataset.id, status: "COMPLETE", rowCount: 18, issueCount: 0, issues: [], normalizationDecisions: [],
    });
    renderWithProviders(<DashboardPage />);
    expect(await screen.findByText("April monitors")).toBeInTheDocument();
    expect(screen.getByText("Ward 4 · 18 records")).toBeInTheDocument();
  });
});
