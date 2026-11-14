# Civic Air Quality Briefing Studio

Java 21 / Spring Boot backend for privately ingesting civic air-quality datasets, preserving source rows, reporting quality issues, calculating deterministic descriptive analytics, and producing evidence-linked recommendations and briefs.

## What works

- Account registration/login with BCrypt password hashes and signed bearer JWTs. Public registration creates `SCHOOL` accounts only; provision `WARD_OFFICER` and `HOUSING_SOCIETY` roles through an authorized database operator. Role-scoped actions and all dataset, analysis and brief reads are owner-scoped.
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

Copy `.env.example` to `.env`, replace the database password and set `JWT_SECRET` to a secure Base64-encoded random key, then run:

```shell
docker compose up --build
```

The compose stack runs PostgreSQL with a persistent named volume, applies schema migration `V1` at backend startup, and waits for database/backend health checks. The backend image is a non-root Java 21 runtime image. The default AI configuration is off and has no provider credentials. For Gemini, set `CIVIC_AI_ENABLED=true`, `AI_PROVIDER=google-genai`, `GEMINI_API_KEY`, and `GEMINI_MODEL` in `.env`; for OpenAI set `CIVIC_AI_ENABLED=true`, `AI_PROVIDER=openai`, `SPRING_AI_OPENAI_API_KEY`, and `SPRING_AI_OPENAI_CHAT_OPTIONS_MODEL`; for Ollama set `CIVIC_AI_ENABLED=true`, `AI_PROVIDER=ollama`, `SPRING_AI_OLLAMA_CHAT_OPTIONS_MODEL`, and the Ollama base URL. Provider credentials remain server-side.

## API outline

| Capability | Endpoint |
|---|---|
| Register school account / login | `POST /api/v1/auth/register`, `POST /api/v1/auth/login` |
| Upload CSV/XLSX or pasted tabular data | `POST /api/v1/datasets`, `POST /api/v1/datasets/pasted` |
| List/detail/quality report | `GET /api/v1/datasets`, `GET /api/v1/datasets/{datasetId}`, `GET /api/v1/datasets/{datasetId}/quality-report` |
| Analyze and inspect findings | `POST /api/v1/datasets/{datasetId}/analyses`, `GET /api/v1/analyses/{analysisId}`, `GET /api/v1/analyses/{analysisId}/findings` |
| Audience recommendations | `GET /api/v1/analyses/{analysisId}/recommendations?audience=SCHOOL` |
| Create/read briefs and advisories | `POST /api/v1/analyses/{analysisId}/briefs`, `POST /api/v1/analyses/{analysisId}/citizen-advisories`, `GET /api/v1/briefs`, `GET /api/v1/briefs/{briefId}` |
| Compare analyses | `POST /api/v1/briefs/compare` |

Every API route except registration/login, health checks and docs requires `Authorization: Bearer <accessToken>`. To provision another role, an authorized operator can update the account’s `role` column to `WARD_OFFICER` or `HOUSING_SOCIETY`; public registration cannot grant these roles. Never expose JWT secrets, model credentials, or database credentials to the frontend.

## Architecture and data

The modular monolith uses `config`, `security`, `user`, `ingestion`, `normalization`, `validation`, `analytics`, `ai`, `recommendation`, `briefing`, `comparison`, `audit`, and `common` packages. Controllers own HTTP DTO boundaries; services own use cases; repositories isolate persistence. Constructor injection is used throughout.

Flyway creates:

- `app_user`, `dataset`, `dataset_row`, and `quality_issue` for accounts, provenance, observations, and validation.
- `analysis`, `finding`, and `brief` for deterministic analysis and stored briefs.
- `audit_event` for non-sensitive actor/action/resource history.

Dataset source rows are stored as JSON text and associated with the parsed/canonical representation; only known supported pollutant/unit mappings normalize. Findings contain evidence IDs referencing dataset-row UUIDs. Brief evidence IDs reference persisted finding UUIDs, which in turn link to the supporting dataset rows. Hibernate validates the Flyway schema and never creates production tables.

## Tests and remaining scope

`mvn test` includes the application context, normalization/validation unit tests, and an HTTP integration flow that registers, uploads, analyzes, generates a sourced advisory, and reads it back. Testcontainers PostgreSQL is included for repository verification where Docker is available.

AQI calculation and authoritative breakpoint seed data remain deliberately unimplemented. Uploaded-file binary archival, distributed rate limiting, non-school public registration, and infrastructure-specific production role provisioning are also outside this release; the API does not claim these capabilities.
