create table plan_preparation_runs (
    id uuid primary key,
    report_id uuid not null,
    update_plan_id uuid not null,
    status varchar(30) not null,
    created_at timestamp with time zone not null,
    completed_at timestamp with time zone,
    constraint fk_plan_preparation_run_report foreign key (report_id) references security_scan_reports(id),
    constraint fk_plan_preparation_run_plan foreign key (update_plan_id) references update_plans(id)
);

create table plan_preparation_items (
    id uuid primary key,
    run_id uuid not null,
    group_id varchar(255),
    artifact_id varchar(255),
    version varchar(255),
    max_cvss_score double,
    job_id uuid,
    finding_id uuid,
    occurrence_id uuid,
    status varchar(30) not null,
    message varchar(2000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint fk_plan_preparation_item_run foreign key (run_id) references plan_preparation_runs(id)
);
create index ix_plan_preparation_items_run on plan_preparation_items(run_id);
