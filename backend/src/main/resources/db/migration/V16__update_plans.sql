create table update_plans (
    id uuid primary key,
    task_id varchar(80) not null unique,
    name varchar(255) not null,
    status varchar(30) not null,
    active boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
-- H2 2.3 does not support PostgreSQL-style partial indexes. The application
-- service enforces that only one PLANNED plan is active at a time.
create index ix_update_plans_active on update_plans(active);
