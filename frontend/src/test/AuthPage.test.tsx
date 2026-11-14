import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import AuthPage from "../auth/AuthPage";
import { renderWithProviders } from "./render";

describe("AuthPage", () => {
  it("validates the password before making an authentication request", async () => {
    const user = userEvent.setup();
    renderWithProviders(<AuthPage />, ["/login"]);
    await user.type(screen.getByLabelText("Email address"), "citizen@example.org");
    await user.type(screen.getByLabelText("Password"), "short");
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Password must be at least 12 characters.")).toBeInTheDocument();
  });
});
