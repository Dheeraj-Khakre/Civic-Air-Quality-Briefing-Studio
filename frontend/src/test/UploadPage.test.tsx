import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { vi } from "vitest";
import { api } from "../api/client";
import UploadPage from "../pages/UploadPage";
import { renderWithProviders } from "./render";

describe("UploadPage", () => {
  it("rejects unsupported file types with an accessible validation message", async () => {
    const user = userEvent.setup({ applyAccept: false });
    renderWithProviders(<UploadPage />, ["/upload"]);
    const input = screen.getByLabelText("Choose dataset file");
    const unsupported = new File(["not a dataset"], "notes.txt", { type: "text/plain" });
    await user.upload(input, unsupported);
    await user.type(screen.getByRole("textbox", { name: /Location or ward name/ }), "Ward 4");
    await user.click(screen.getByRole("button", { name: /upload and review quality/i }));
    expect(await screen.findByText("Use a .csv or .xlsx file.")).toBeInTheDocument();
  });

  it("submits a valid CSV to the authenticated API", async () => {
    const user = userEvent.setup();
    const upload = vi.spyOn(api, "upload").mockResolvedValue({
      id: "dataset-1", name: "readings.csv", sourceType: "CSV", status: "READY",
      locationName: "Ward 4", rowCount: 1, createdAt: "2026-04-04T10:00:00Z",
    });
    renderWithProviders(<UploadPage />, ["/upload"]);
    await user.upload(screen.getByLabelText("Choose dataset file"), new File(["timestamp,value"], "readings.csv", { type: "text/csv" }));
    await user.type(screen.getByRole("textbox", { name: /Location or ward name/ }), "Ward 4");
    await user.click(screen.getByRole("button", { name: /upload and review quality/i }));
    await waitFor(() => expect(upload).toHaveBeenCalled());
    expect(upload.mock.calls[0][1]).toBe("Ward 4");
    expect(localStorage.getItem("civic-air-selected-dataset")).toBe("dataset-1");
  });
});
