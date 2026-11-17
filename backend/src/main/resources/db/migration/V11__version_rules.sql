create table version_rules (
    id uuid primary key,
    artifact_id uuid not null unique,
    pattern varchar(1000),
    allow_snapshots boolean not null,
    updated_at timestamp with time zone not null,
    constraint fk_version_rules_artifact foreign key (artifact_id) references artifacts(id) on delete cascade
);
