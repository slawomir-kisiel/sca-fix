create table finding_strategy_selections (
    id uuid primary key,
    finding_id uuid not null unique,
    occurrence_id uuid not null,
    target_artifact_version_id uuid,
    strategy varchar(40) not null,
    selected_at timestamp with time zone not null,
    constraint fk_strategy_selection_finding foreign key (finding_id) references vulnerability_findings(id) on delete cascade,
    constraint fk_strategy_selection_occurrence foreign key (occurrence_id) references dependency_tree_occurrences(id) on delete cascade,
    constraint fk_strategy_selection_version foreign key (target_artifact_version_id) references artifact_versions(id)
);
