import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { api } from "../api/client";
import { ComparisonPage } from "../pages/BriefPages";
import { renderWithProviders } from "./render";

describe("historical comparison", () => {
  it("sends selected analyses to the API and renders its returned classifications", async () => {
    localStorage.setItem("civic-air-analysis-history", JSON.stringify([
      { id: "analysis-current", datasetId: "dataset-current", datasetName: "Current run", locationName: "Ward 4", createdAt: "2026-04-04T10:00:00Z" },
      { id: "analysis-previous", datasetId: "dataset-previous", datasetName: "Previous run", locationName: "Ward 4", createdAt: "2026-03-04T10:00:00Z" },
    ]));
    const compare = vi.spyOn(api, "compare").mockResolvedValue({
      currentAnalysisId: "analysis-current",
      previousAnalysisId: "analysis-previous",
      changes: [{ pollutant: "PM2.5", classification: "IMPROVED", currentValue: 12, previousValue: 18, unit: "ug/m3", evidenceIds: ["finding-1"] }],
      confidenceChange: 0.1,
      limitations: ["Descriptive comparison only."],
    });
    const user = userEvent.setup();
    renderWithProviders(<ComparisonPage />, ["/comparison"]);
    await user.selectOptions(screen.getByLabelText("Current analysis"), "analysis-current");
    await user.selectOptions(screen.getByLabelText("Previous analysis"), "analysis-previous");
    await user.click(screen.getByRole("button", { name: /compare analyses/i }));
    await waitFor(() => expect(compare).toHaveBeenCalledWith("analysis-current", "analysis-previous"));
    expect(await screen.findByText("IMPROVED")).toBeInTheDocument();
    expect(screen.getByText("PM2.5")).toBeInTheDocument();
  });
});
