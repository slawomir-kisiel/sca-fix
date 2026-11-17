create table repositories (
    id uuid primary key,
    provider varchar(40) not null,
    repository_url varchar(2000),
    clone_path varchar(2000) not null,
    credentials_ref varchar(255),
    default_branch varchar(255),
    base_commit_sha varchar(128),
    status varchar(20) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_repositories_clone_path unique (clone_path)
);

create index ix_repositories_url on repositories(repository_url);
