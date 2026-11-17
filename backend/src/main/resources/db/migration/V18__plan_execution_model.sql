alter table update_plans add column repository_id uuid;
alter table update_plans add column branch_name varchar(255);
alter table update_plans add column base_commit_sha varchar(80);
alter table update_plans add column applied_at timestamp with time zone;
alter table update_plans add column committed_at timestamp with time zone;
alter table update_plans add constraint fk_update_plan_repository
    foreign key (repository_id) references repositories(id);
create index ix_update_plans_repository on update_plans(repository_id);

alter table plan_items add column applied_from_version varchar(255);
alter table plan_items drop column if exists execution_order;
