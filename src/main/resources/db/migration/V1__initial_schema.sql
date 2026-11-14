CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE dataset (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id),
    name VARCHAR(160) NOT NULL,
    source_type VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    location_name VARCHAR(160) NOT NULL,
    period_start TIMESTAMP WITH TIME ZONE,
    period_end TIMESTAMP WITH TIME ZONE,
    content_sha256 VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_dataset_owner_created ON dataset(owner_id, created_at DESC);

CREATE TABLE dataset_row (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES dataset(id) ON DELETE CASCADE,
    row_number INTEGER NOT NULL,
    original_data VARCHAR(20000) NOT NULL,
    pollutant VARCHAR(40),
    observed_at TIMESTAMP WITH TIME ZONE,
    location_name VARCHAR(160),
    original_value DOUBLE PRECISION,
    original_unit VARCHAR(40),
    normalized_value DOUBLE PRECISION,
    normalized_unit VARCHAR(40),
    duplicate BOOLEAN NOT NULL
);
CREATE INDEX idx_dataset_row_dataset ON dataset_row(dataset_id);

CREATE TABLE quality_issue (
    id UUID PRIMARY KEY,
    dataset_id UUID NOT NULL REFERENCES dataset(id) ON DELETE CASCADE,
    row_number INTEGER,
    code VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    message VARCHAR(500) NOT NULL
);
CREATE INDEX idx_quality_issue_dataset ON quality_issue(dataset_id, row_number);

CREATE TABLE analysis (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id),
    dataset_id UUID NOT NULL REFERENCES dataset(id),
    standard VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    location_name VARCHAR(160) NOT NULL,
    period_start TIMESTAMP WITH TIME ZONE,
    period_end TIMESTAMP WITH TIME ZONE,
    completeness DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_analysis_owner_created ON analysis(owner_id, created_at DESC);

CREATE TABLE finding (
    id UUID PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analysis(id) ON DELETE CASCADE,
    category VARCHAR(48) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    pollutant VARCHAR(40),
    measurement_value DOUBLE PRECISION,
    unit VARCHAR(32),
    evidence_ids VARCHAR(2000000) NOT NULL
);

CREATE TABLE brief (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES app_user(id),
    analysis_id UUID NOT NULL REFERENCES analysis(id),
    type VARCHAR(32) NOT NULL,
    audience VARCHAR(32) NOT NULL,
    language VARCHAR(8) NOT NULL,
    reading_level VARCHAR(16) NOT NULL,
    content VARCHAR(20000) NOT NULL,
    evidence_ids VARCHAR(20000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_brief_owner_created ON brief(owner_id, created_at DESC);

CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    actor_id UUID REFERENCES app_user(id),
    action VARCHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_audit_resource_time ON audit_event(resource_id, occurred_at DESC);
