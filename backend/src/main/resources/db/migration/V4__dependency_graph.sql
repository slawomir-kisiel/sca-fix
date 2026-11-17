create table artifacts (
    id uuid primary key,
    group_id varchar(255) not null,
    artifact_id varchar(255) not null,
    display_name varchar(255),
    project_id uuid,
    constraint uk_artifacts_coordinates unique (group_id, artifact_id),
    constraint fk_artifacts_project foreign key (project_id) references projects(id)
);

create table artifact_versions (
    id uuid primary key,
    artifact_id uuid not null,
    version varchar(255) not null,
    parent_artifact_version_id uuid,
    analysis_status varchar(20) not null,
    analyzed_at timestamp with time zone,
    analyzed_commit_sha varchar(128),
    constraint uk_artifact_versions_version unique (artifact_id, version),
    constraint fk_artifact_versions_artifact foreign key (artifact_id) references artifacts(id),
    constraint fk_artifact_versions_parent foreign key (parent_artifact_version_id) references artifact_versions(id)
);

create index ix_artifact_versions_artifact on artifact_versions(artifact_id);
create table artifact_tree_nodes (
    id uuid primary key,
    project_id uuid not null,
    artifact_version_id uuid not null,
    previous_node_id uuid,
    relation_type varchar(20) not null,
    scope varchar(80),
    depth integer not null,
    direct boolean not null,
    managed boolean not null,
    constraint fk_tree_nodes_project foreign key (project_id) references projects(id) on delete cascade,
    constraint fk_tree_nodes_version foreign key (artifact_version_id) references artifact_versions(id),
    constraint fk_tree_nodes_previous foreign key (previous_node_id) references artifact_tree_nodes(id) on delete cascade
);

create index ix_tree_nodes_project on artifact_tree_nodes(project_id);
create index ix_tree_nodes_version on artifact_tree_nodes(artifact_version_id);
create index ix_tree_nodes_previous on artifact_tree_nodes(previous_node_id);
