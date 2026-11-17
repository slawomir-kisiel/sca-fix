create table dependency_tree_occurrences (
    id uuid primary key,
    finding_id uuid not null,
    affected_artifact_id uuid not null,
    project_id uuid not null,
    vulnerable_artifact_tree_node_id uuid not null,
    resolved_version varchar(300) not null,
    definition_source varchar(80) not null,
    sources_available boolean not null,
    constraint fk_occurrences_finding foreign key (finding_id) references vulnerability_findings(id) on delete cascade,
    constraint fk_occurrences_affected_artifact foreign key (affected_artifact_id) references affected_artifacts(id) on delete cascade,
    constraint fk_occurrences_project foreign key (project_id) references projects(id),
    constraint fk_occurrences_tree_node foreign key (vulnerable_artifact_tree_node_id) references artifact_tree_nodes(id) on delete cascade
);
create index ix_occurrences_finding on dependency_tree_occurrences(finding_id);
create index ix_occurrences_project on dependency_tree_occurrences(project_id);
create index ix_occurrences_tree_node on dependency_tree_occurrences(vulnerable_artifact_tree_node_id);
