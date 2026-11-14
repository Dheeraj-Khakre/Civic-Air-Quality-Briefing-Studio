# Civic Air Quality Briefing Studio

Java 21 / Spring Boot backend for privately ingesting civic air-quality datasets, preserving source rows, reporting quality issues, calculating deterministic descriptive analytics, and producing evidence-linked recommendations and briefs.

## What works

- Account registration/login with BCrypt password hashes and signed bearer JWTs. Public registration creates `SCHOOL` accounts only; `WARD_OFFICER` and `HOUSING_SOCIETY` roles require authorized provisioning. Role-scoped actions and all dataset, analysis and brief reads are owner-scoped. An optional, local-only `ADMIN` bootstrap supports testing all audience flows and is disabled by default.
- CSV, XLSX (first worksheet), and pasted delimited-data intake. Upload content is parsed/validated rather than trusted by extension. The parser accepts common timestamp/location/pollutant/value/unit header aliases and long or known-pollutant-wide tabular layouts.
- Original row fields are retained in PostgreSQL with a SHA-256 digest of the submitted content. Quality reports preserve missing/invalid readings, unknown pollutants/units, invalid timestamps, duplicate records, and 1.5-IQR potential outliers. Outliers are flagged, not removed; missing readings are never filled with zero; unsupported units are never converted.
- Normalized mass concentrations are stored as `ug/m3`; `mg/m3` converts by the mass-unit factor 1000. `ppm` and other unsupported units remain unnormalized. Source rows remain available as evidence.
- Deterministic pollutant average/minimum/maximum, location aggregation, data completeness, and relative potential hotspots. Results cite persisted observation UUIDs. Confidence is the fraction of dataset rows that yielded valid measurements, not statistical uncertainty.
- Role-specific recommendations, stored English/Hindi simple/detailed internal briefs and citizen advisories, evidence-ID validation, historical comparison, audit records, per-user upload/brief limits, and OpenAPI/Swagger documentation.
- Spring AI integration supports Gemini, Ollama, or OpenAI chat providers. AI is disabled by default; when enabled, structured output is checked for non-empty valid evidence references, unsupported numeric claims, and disallowed health/clinical language. Failed or rejected generation returns an error; it never falls back to invented output. Deterministic, evidence-based templates are used when AI is disabled.

## Important analytical limitation

`DESCRIPTIVE_V1` is the only supported analysis standard. This release intentionally does **not** calculate AQI: no AQI breakpoint table and averaging-time implementation has been seeded/configured. Requests for an AQI standard are rejected rather than using undocumented thresholds. A relative potential hotspot means only the highest average among submitted locations for a pollutant; it does not mean an exceedance or health concern.

Comparison requires analyses owned by the caller, the same standard and location, and declared time windows of equal duration. A pollutant present in only one analysis is reported as `MISSING_DATA`; the backend does not infer a newly emerging pollution issue. Changes do not establish causation.

The database retains original submitted row values and the content digest, but not the complete upload byte stream. CSV data must have one header row; XLSX reads the first worksheet. The in-memory rate limiter is per application instance and should be replaced with a shared store if deploying multiple replicas.

## Build and run locally

Requirements: Java 21, Maven 3.9+, PostgreSQL 15+. `mvn test` runs deterministic tests against H2 and Flyway; an optional Testcontainers PostgreSQL repository test is skipped when Docker is unavailable.

Set environment variables before starting the application:

```powershell
$env:DATABASE_URL = "jdbc:postgresql://localhost:5432/civic_air"
$env:DATABASE_USERNAME = "civic_air"
$env:DATABASE_PASSWORD = "<database-password>"
$env:JWT_SECRET = "<Base64-encoded random key of at least 32 bytes>"
mvn spring-boot:run
```

For example, generate a JWT key using a secure password manager or secret store. If `JWT_SECRET` is omitted in local development, a random process-only key is generated, invalidating tokens after restart. Never rely on that behavior in a deployed environment.

The OpenAPI contract is `src/main/resources/static/openapi.yaml`; Swagger UI is at `/swagger-ui/index.html`. API routes use `/api/v1`, UUID IDs, UTC/ISO-8601 timestamps, and RFC 9457 `application/problem+json` errors. CORS allows `http://localhost:5173` by default; configure `CORS_ALLOWED_ORIGIN` for the frontend deployment. CSV/XLSX upload maximum is 20 MB; pasted content is limited to 2 MB. `POST /datasets` and `POST /datasets/pasted` return `202`, analysis returns `201` and is completed synchronously.

## Docker Compose

On Windows with Docker Desktop running, start the complete stack with:

```powershell
.\start-local.ps1
```

The script creates or repairs only placeholder local credentials in the ignored `.env`, using random values, then starts the stack in the background. If you start Compose directly (including from an IDE), create `.env` first; Compose requires non-empty `POSTGRES_PASSWORD` and `JWT_SECRET`, so running `docker compose up` against only `.env.example` will fail. On Windows, run `.\start-local.ps1` once to generate the ignored `.env`. On other platforms, copy `.env.example` to `.env` and replace the database password and JWT secret with unique secure values before running:

```shell
docker compose up --build
```

Open the frontend at `http://localhost:3000`; Swagger remains at `http://localhost:8080/swagger-ui/index.html`. Register a School account, upload a dataset, review its quality report, run analysis, and generate evidence-linked briefs. The database and user data persist in the `postgres_data` volume. Use `docker compose down -v` only when you intentionally want to erase the local database.

The Windows local startup script also generates a local-only administrator. Sign in with `admin@civic-air.local`; retrieve its generated password from the ignored `.env` as `CIVIC_LOCAL_ADMIN_PASSWORD`. This is not a shared or production credential. The Compose default keeps admin bootstrap disabled; `start-local.ps1` enables it only in the local `.env`. Do not commit `.env` or use this bootstrap in a deployed environment.

The backend's supported persistent database is PostgreSQL; the current migration and entity mappings are PostgreSQL-oriented, so this one-click stack intentionally uses PostgreSQL rather than substituting SQLite/H2 for an end-to-end run.

## End-to-end testing

The end-to-end flow runs the real frontend, backend, and PostgreSQL services together. It does not seed fake environmental readings or require an AI provider.

1. On Windows, start Docker Desktop, open PowerShell in the repository root, and run:

   ```powershell
   .\start-local.ps1
   ```

   This creates or repairs the ignored local `.env` with local-only secrets and starts the Compose services. Wait until all services are healthy:

   ```powershell
   docker compose ps
   ```

   Check backend health at `http://localhost:8080/actuator/health` and frontend health at `http://localhost:3000/`. Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`.

2. Open `http://localhost:3000` and sign in with the local administrator account:

   - Email: `admin@civic-air.local`
   - Password: read `CIVIC_LOCAL_ADMIN_PASSWORD` from the ignored `.env`; the script generates a local password rather than using a shared default.

   Alternatively, register an account through the application.

3. Use a small CSV or XLSX file with real or clearly identified test readings, or enter tabular data in the upload form. Submit it and verify that the application shows the dataset, quality report, and any validation issues. Missing readings should remain missing; do not interpret test values as live environmental measurements.

4. Run analysis for the uploaded dataset. Verify the returned summaries and evidence-linked findings, then open recommendations and generate an internal brief or citizen advisory. Try historical comparison and brief history after creating a second analysis/brief. The default local AI configuration is disabled; the application must report that AI is unavailable or use its documented non-AI behavior rather than inventing an AI response. To exercise a configured provider, set its credentials in the ignored `.env` only, restart the backend, and never put keys in frontend configuration.

5. If a step fails, inspect the service logs and status:

   ```powershell
   docker compose ps
   docker compose logs --tail 100 backend
   docker compose logs --tail 100 frontend
   docker compose logs --tail 100 postgres
   ```

   Stop the services without deleting test data with `docker compose down`. The named `postgres_data` volume persists across restarts. Only run `docker compose down -v` when you intentionally want to erase the local database and all test records.

For automated checks before the manual UI flow, run these from the repository root:

```powershell
mvn --batch-mode --no-transfer-progress verify
Push-Location frontend
npm ci
npm test -- --run
npm run build
Pop-Location
docker compose build
```

The Maven verification includes the backend unit and integration tests; the frontend commands run its test suite and production build. GitHub Actions additionally builds and scans both Docker images. A successful local test/build does not by itself verify a Railway deployment or an external AI provider.

## Frontend development

The React/TypeScript application is under `frontend/`. It uses Vite, React Router, TanStack Query, Axios, React Hook Form/Zod, Recharts, and Tailwind CSS. Its API client targets `/api/v1`; Vite proxies API and health requests to `http://localhost:8080`, while the Compose Nginx service proxies to the backend container.

```shell
cd frontend
npm ci
npm run dev
npm test
npm run build
```

The frontend includes the dashboard, authenticated CSV/XLSX/pasted-data intake, quality reports, deterministic analysis and evidence-linked findings, role-authorized recommendations and briefs, comparison of analyses tracked in the current browser, and brief history. Empty states reflect API data and do not use sample environmental measurements. The backend currently has no API to list all prior analyses, return pollutant time series, or preview raw source rows; comparison choices therefore include analyses created in that browser, dashboard trends are limited to the analysis's returned summary metrics, and upload preview is limited to the API's dataset metadata and quality report until those endpoints are added.

The compose stack runs PostgreSQL with a persistent named volume, applies schema migration `V1` at backend startup, and waits for database/backend health checks. The backend image is a non-root Java 21 runtime image. The default AI configuration is off and has no provider credentials. For Gemini, set `CIVIC_AI_ENABLED=true`, `AI_PROVIDER=google-genai`, `GEMINI_API_KEY`, and `GEMINI_MODEL` in `.env`; for OpenAI set `CIVIC_AI_ENABLED=true`, `AI_PROVIDER=openai`, `SPRING_AI_OPENAI_API_KEY`, and `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL`; for Ollama set `CIVIC_AI_ENABLED=true`, `AI_PROVIDER=ollama`, `SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL`, and the Ollama base URL. Provider credentials remain server-side.

## Google Cloud deployment (Cloud Run & Cloud SQL)

The application supports serverless multi-container deployment on Google Cloud using **Cloud Run** and managed **Cloud SQL for PostgreSQL**, with secrets managed by **Secret Manager** and container builds handled by **Cloud Build**.

For detailed setup instructions, automated scripts, and architecture details, see the [Google Cloud Deployment Guide](file:///C:/Dheeraj-workSpace/dhee.work/hack2skill-project/GCP_DEPLOYMENT_GUIDE.md):

- **PowerShell (Windows):** `.\deploy\gcp\deploy-gcp.ps1 -ProjectId "<YOUR_GCP_PROJECT_ID>"`
- **Bash / Cloud Shell:** `chmod +x deploy/gcp/deploy-gcp.sh && ./deploy/gcp/deploy-gcp.sh`
- **GitHub Actions:** Trigger the `.github/workflows/deploy-gcp.yml` workflow.
- **Teardown:** `.\deploy\gcp\destroy-gcp.ps1 -ProjectId "<YOUR_GCP_PROJECT_ID>"`

## CI/CD and Railway deployment

GitHub Actions runs on pushes to `main`, `develop`, and `dev`, and on pull requests targeting those deployment branches (including `main`). The CI workflow tests and compiles the Java 21 backend, runs the frontend tests and production build with Node 22, builds both Docker images, and scans source dependencies and images for fixable high/critical vulnerabilities and source secrets. The Docker job depends on successful backend and frontend jobs. GitHub Actions uses read-only repository permissions for CI; no Railway token or application credential is needed by these workflows. Dependabot checks Maven, npm, Docker base images, and workflow actions weekly.

### GitHub setup and deployment gate

1. In **Settings → Branches** (or repository rulesets), protect `main`, `develop`, and `dev`; require pull requests and the CI checks **Backend tests and compilation**, **Frontend tests and production build**, and **Docker builds and vulnerability scans**. Require the latest review and dismiss stale approvals according to your team policy. Do not allow direct pushes or force pushes to production `main`.
2. Connect the GitHub repository to Railway using Railway's native GitHub integration; do not add a Railway API token to GitHub Actions.
3. Configure a Railway project with separate `development`, `staging`, and `production` environments, each with its own PostgreSQL service and credentials. Configure the **development** backend/frontend services to deploy from `dev`, **staging** from `develop`, and **production** from `main`. Enable **Wait for CI** on every Railway application service and require the successful CI workflow/checks before Railway deploys. A pull request to `main` runs CI but does not deploy. Never connect feature branches to the production service.
4. Create two application services in each environment from the same repository:

   | Service | Root directory | Dockerfile | Watch paths | Railway health check |
   |---|---|---|---|---|
   | Backend | `/` (repository root) | `Dockerfile` | `Dockerfile`, `.dockerignore`, `pom.xml`, `src/**` | `/actuator/health` |
   | Frontend | `/frontend` | `Dockerfile` | `frontend/**` | `/` |

   Set health-check timeout to 300 seconds (or longer if startup/migrations need it). The backend health endpoint reports the application and datasource health. The frontend image health check tests the site itself; the deployment health monitor checks the frontend-to-backend proxy, and the local Compose health check waits for both services. Railway should wait for each service's health check before considering its deployment available.
5. Add a Railway PostgreSQL service in **each** environment. Keep it private; attach persistent storage using Railway's managed PostgreSQL volume. The application stores durable records in PostgreSQL. Uploaded source files are parsed and their rows/provenance are persisted in the database; the original upload bytes are not archived, so no application file volume is needed.
6. Configure the backend environment variables separately in each Railway environment:

   | Variable | Value |
   |---|---|
   | `DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` (use that environment's PostgreSQL service reference) |
   | `DATABASE_USERNAME` | `${{Postgres.PGUSER}}` |
   | `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
   | `JWT_SECRET` | Unique Base64-encoded random key of at least 32 bytes, generated/stored in Railway, never Git |
   | `CIVIC_LOCAL_ADMIN_ENABLED` | `false` |
   | `CIVIC_AI_ENABLED` | `false` unless a provider is configured |
   | `CORS_ALLOWED_ORIGIN` | The frontend's HTTPS Railway public domain |

   Railway injects `PORT`; the backend binds to it. Do not set `SERVER_PORT` in Railway. If using AI, set only the selected provider's server-side credential/model variables (`AI_PROVIDER`, and the Gemini, OpenAI, or Ollama settings described above) in Railway's **environment variables/secrets**. Never use the local admin bootstrap or local `.env` credentials in Railway.
7. Configure the frontend service with `PORT` supplied by Railway and `BACKEND_PRIVATE_HOST=${{Backend.RAILWAY_PRIVATE_DOMAIN}}:${{Backend.PORT}}`, replacing `Backend` with the exact Railway backend service name/reference. The frontend proxies `/api/` and `/actuator/` over Railway private networking; do not expose database credentials or AI secrets to it. Enable a public HTTPS domain for the frontend, and for the backend only if external health checks or direct Swagger access are required. If exposing the backend publicly, retain authentication and restrict/document actuator exposure appropriately.
8. Add GitHub repository **variables** `RAILWAY_BACKEND_URL` and `RAILWAY_FRONTEND_URL` with the public HTTPS service URLs. The `Deployment health monitor` workflow checks backend health (including database connectivity), frontend availability, and the frontend-to-backend proxy on a schedule and when manually dispatched. Railway's per-deployment health checks are the deployment-time gate. The optional AI provider is not probed by the generic health endpoint; verify it with an authenticated brief-generation smoke test after enabling a provider.

The service connections, Railway variables/private domains, persistent PostgreSQL instances, public domains, CI-wait setting, branch protections, and deployment history are platform-side resources and must be configured in the GitHub/Railway dashboards. This repository does not contain Railway credentials and cannot enable those account/project settings by itself.

### Environments, failures, and rollback

- **Development:** `dev` branch and development-only Railway services/database; use non-production test data and credentials.
- **Staging:** `develop` branch and a separate staging database; validate migrations, uploads, AI configuration, and the full UI/API flow before promotion.
- **Production:** `main` only, protected by required CI and Railway Wait for CI, with its own database and secrets. Promote through reviewed pull requests; do not deploy feature branches to this environment.
- **CI failure:** inspect the failing job and logs in **Actions**. Railway must not deploy that commit while required checks are failing. Fix the issue and push a new commit; avoid bypassing the required checks.
- **Railway cannot read the repository / Dockerfile validation fails without build logs:** reconnect the GitHub repository from Railway's project source settings, re-authorize the Railway GitHub App, and grant it access to this repository (including the selected organization, if applicable). Confirm the deployment branch and that the application services point to the repository root with `Dockerfile` for the backend and `/frontend` with `Dockerfile` for the frontend. Redeploy only after Railway can resolve the repository commit; this message occurs before Docker build logs are available.
- **Deployment failure after source checkout:** inspect the service's **Deployments** and **Logs** in Railway. Confirm the deployment commit, runtime variables, private host reference, database reachability, migration output, and health-check path/port. Use the `Deployment health monitor` workflow to distinguish backend, frontend, and proxy failures.
- **Application rollback:** use Railway **Deployments → previous successful deployment → Redeploy/Rollback** for the affected service; restore the corresponding compatible version for frontend/backend together if API contracts changed. Then confirm health and review logs. Do not roll back code alone if a migration has already removed/changed columns or data.
- **Database migration safety:** migrations run at backend startup. Make schema changes backward-compatible (additive/expand first), deploy code compatible with both schemas, migrate data, then remove old schema only in a later release. Take/verify a PostgreSQL backup before destructive migrations. Restoring a database backup is a separate recovery operation and can lose writes made after that backup.
- **Secrets:** use Railway environment-specific secret variables and GitHub's built-in `GITHUB_TOKEN` only for Actions. Rotate a secret if it is exposed; do not print it in workflow commands or logs. Local `.env` remains ignored and is not a deployment artifact.

## API reference

Base path: `/api/v1`. Except for registration, login, health checks, and API documentation, requests require `Authorization: Bearer <accessToken>`. Send and receive JSON unless an endpoint says otherwise. IDs are UUIDs; timestamps use ISO-8601/UTC. Errors use `application/problem+json` with an `errorCode` and, for validation failures, `fieldErrors`. Protected resources are always scoped to the authenticated owner, including for `ADMIN`.

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `POST` | `/auth/register` | Public | Create a School account; body: `{"email":"...","password":"at-least-12-chars"}`. Returns access token and user. |
| `POST` | `/auth/login` | Public | Authenticate with email/password; returns a one-hour bearer token and user. |
| `POST` | `/datasets` | Bearer | Multipart upload: `file` (CSV/XLSX), required `locationName`, optional `periodStart`/`periodEnd`. Max file size 20 MB. Returns `202`. |
| `POST` | `/datasets/pasted` | Bearer | Submit JSON `{ "name", "locationName", "delimiter", "content", "periodStart?", "periodEnd?" }`; content limit 2 MB. Returns `202`. |
| `GET` | `/datasets` | Bearer | List the caller's datasets. |
| `GET` | `/datasets/{datasetId}` | Bearer | Read an owned dataset. |
| `GET` | `/datasets/{datasetId}/quality-report` | Bearer | Retrieve validation issues and normalization decisions. |
| `POST` | `/datasets/{datasetId}/analyses` | Bearer | Run deterministic analysis; body `{"standard":"DESCRIPTIVE_V1","ward":"optional"}`. Returns `201`. |
| `GET` | `/analyses/{analysisId}` | Bearer | Retrieve an owned analysis and aggregate metrics. |
| `GET` | `/analyses/{analysisId}/findings` | Bearer | Retrieve evidence-linked findings. |
| `GET` | `/analyses/{analysisId}/recommendations?audience=SCHOOL` | Matching role or local Admin | Retrieve recommendations for `WARD_OFFICER`, `HOUSING_SOCIETY`, or `SCHOOL`. |
| `POST` | `/analyses/{analysisId}/briefs` | Matching role or local Admin | Generate an internal brief; JSON body `{"audience":"SCHOOL","language":"en","readingLevel":"SIMPLE"}`. |
| `POST` | `/analyses/{analysisId}/citizen-advisories` | Matching role or local Admin | Generate a citizen advisory using the same body shape as brief generation. |
| `GET` | `/briefs` | Bearer | List the caller's saved briefs and advisories. |
| `GET` | `/briefs/{briefId}` | Bearer | Read an owned brief. |
| `POST` | `/briefs/compare` | Bearer | Compare owned analyses; body `{"currentAnalysisId":"<uuid>","previousAnalysisId":"<uuid>"}`. |
| `GET` | `/actuator/health` | Public | Container health check. |

Swagger UI is available at `/swagger-ui/index.html` when the backend is running; the checked-in OpenAPI contract is `src/main/resources/static/openapi.yaml`. To provision a non-admin role outside local testing, use an authorized operator; public registration cannot grant elevated roles. Never expose JWT secrets, model credentials, or database credentials to the frontend.

## Architecture and data

The modular monolith uses `config`, `security`, `user`, `ingestion`, `normalization`, `validation`, `analytics`, `ai`, `recommendation`, `briefing`, `comparison`, `audit`, and `common` packages. Controllers own HTTP DTO boundaries; application services coordinate use cases; repositories isolate persistence. In ingestion, `DatasetService` handles upload orchestration, persistence, ownership, and audit events, while `DatasetObservationProcessor` independently maps source rows into normalized observations and validation issues. Constructor injection is used throughout.

Flyway creates:

- `app_user`, `dataset`, `dataset_row`, and `quality_issue` for accounts, provenance, observations, and validation.
- `analysis`, `finding`, and `brief` for deterministic analysis and stored briefs.
- `audit_event` for non-sensitive actor/action/resource history.

Dataset source rows are stored as JSON text and associated with the parsed/canonical representation; only known supported pollutant/unit mappings normalize. Findings contain evidence IDs referencing dataset-row UUIDs. Brief evidence IDs reference persisted finding UUIDs, which in turn link to the supporting dataset rows. Hibernate validates the Flyway schema and never creates production tables.

## Tests and remaining scope

`mvn test` runs backend unit and HTTP integration tests for normalization, validation, account registration/login, local-admin workflows, dataset ownership, upload, analysis, evidence-backed brief generation, and retrieval. The Testcontainers PostgreSQL repository test runs when Docker is available. In `frontend/`, `npm test -- --run` runs UI tests and `npm run build` type-checks and builds the production assets.

AQI calculation and authoritative breakpoint seed data remain deliberately unimplemented. Uploaded-file binary archival, distributed rate limiting, non-school public registration, and infrastructure-specific production role provisioning are also outside this release; the API does not claim these capabilities.
