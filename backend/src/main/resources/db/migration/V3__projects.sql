create table projects (
    id uuid primary key,
    repository_id uuid not null,
    name varchar(255) not null,
    pom_path varchar(2000) not null,
    group_id varchar(255),
    artifact_id varchar(255),
    version varchar(255),
    packaging varchar(80),
    parent_group_id varchar(255),
    parent_artifact_id varchar(255),
    parent_version varchar(255),
    base_branch varchar(255),
    base_commit_sha varchar(128),
    status varchar(30) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_projects_repository foreign key (repository_id) references repositories(id),
    constraint uk_projects_repository_pom unique (repository_id, pom_path)
);

create index ix_projects_repository on projects(repository_id);
create index ix_projects_coordinates on projects(group_id, artifact_id);
