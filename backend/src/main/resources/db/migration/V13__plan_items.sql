create table plan_items (
    id uuid primary key,
    finding_id uuid not null,
    occurrence_id uuid not null,
    target_project_id uuid not null,
    change_point_tree_node_id uuid not null,
    strategy varchar(40) not null,
    target_artifact_version_id uuid not null,
    selected_at timestamp with time zone not null,
    constraint uq_plan_item_occurrence unique (occurrence_id),
    constraint fk_plan_item_finding foreign key (finding_id) references vulnerability_findings(id) on delete cascade,
    constraint fk_plan_item_occurrence foreign key (occurrence_id) references dependency_tree_occurrences(id) on delete cascade,
    constraint fk_plan_item_project foreign key (target_project_id) references projects(id),
    constraint fk_plan_item_node foreign key (change_point_tree_node_id) references artifact_tree_nodes(id),
    constraint fk_plan_item_target foreign key (target_artifact_version_id) references artifact_versions(id)
);
