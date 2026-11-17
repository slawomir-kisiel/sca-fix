create table application_settings (
    setting_key varchar(200) primary key,
    setting_value varchar(2000) not null,
    updated_at timestamp with time zone not null
);

insert into application_settings(setting_key, setting_value, updated_at)
values ('maven.analysis.pool.size', '5', current_timestamp);

alter table projects add column maven_analysis_job_id uuid;
alter table projects add column maven_analysis_status varchar(30) not null default 'REGISTERED';
alter table projects add column maven_analysis_started_at timestamp with time zone;
alter table projects add column maven_analysis_finished_at timestamp with time zone;

alter table projects add constraint fk_projects_maven_analysis_job
    foreign key (maven_analysis_job_id) references jobs(id);
create index ix_projects_maven_analysis_job on projects(maven_analysis_job_id);
