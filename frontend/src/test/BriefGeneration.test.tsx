import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { api } from "../api/client";
import { BriefGeneratorPage } from "../pages/BriefPages";
import { renderWithProviders } from "./render";

describe("brief generation", () => {
  it("does not display generated content before a verified analysis exists", async () => {
    renderWithProviders(<BriefGeneratorPage type="CITIZEN_ADVISORY" />, ["/brief/advisory"]);
    expect(await screen.findByText("No active analysis")).toBeInTheDocument();
    expect(screen.queryByText(/air quality is/i)).not.toBeInTheDocument();
  });

  it("allows a local admin to select every audience", async () => {
    localStorage.setItem("civic-air-user", JSON.stringify({
      id: "admin-1", email: "admin@civic-air.local", role: "ADMIN",
    }));
    localStorage.setItem("civic-air-active-analysis", "analysis-1");
    vi.spyOn(api, "analysis").mockResolvedValue({
      id: "analysis-1", datasetId: "dataset-1", status: "COMPLETE", standard: "DESCRIPTIVE_V1",
      createdAt: "2026-04-04T10:00:00Z", dataCompleteness: 1, metrics: [], locations: [],
    });

    renderWithProviders(<BriefGeneratorPage type="INTERNAL" />, ["/brief/internal"]);
    const audience = await screen.findByLabelText("Target audience");
    expect(audience).toHaveValue("SCHOOL");
    expect(screen.getByRole("option", { name: "Ward Officer" })).not.toBeDisabled();
    expect(screen.getByRole("option", { name: "Housing Society" })).not.toBeDisabled();
  });

  it("renders only the API-returned brief and its evidence IDs", async () => {
    localStorage.setItem("civic-air-active-analysis", "analysis-1");
    vi.spyOn(api, "analysis").mockResolvedValue({
      id: "analysis-1", datasetId: "dataset-1", status: "COMPLETE", standard: "DESCRIPTIVE_V1",
      createdAt: "2026-04-04T10:00:00Z", dataCompleteness: 0.9, metrics: [], locations: [],
    });
    const generateBrief = vi.spyOn(api, "generateBrief").mockResolvedValue({
      id: "brief-1", analysisId: "analysis-1", type: "CITIZEN_ADVISORY", audience: "SCHOOL",
      language: "en", readingLevel: "SIMPLE", content: "API-verified advisory content.",
      evidenceIds: ["finding-12345678"], limitations: ["Descriptive data only."], createdAt: "2026-04-04T10:00:00Z",
    });
    const user = userEvent.setup();
    renderWithProviders(<BriefGeneratorPage type="CITIZEN_ADVISORY" />, ["/brief/advisory"]);
    await user.click(await screen.findByRole("button", { name: /generate advisory/i }));
    expect(await screen.findByText("API-verified advisory content.")).toBeInTheDocument();
    expect(screen.getByText("finding-…")).toBeInTheDocument();
    expect(generateBrief).toHaveBeenCalledWith("analysis-1", "citizen-advisories", {
      audience: "SCHOOL", language: "en", readingLevel: "SIMPLE",
    });
  });
});
