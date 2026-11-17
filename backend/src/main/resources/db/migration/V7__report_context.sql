alter table security_scan_reports add column context_project_id uuid;
alter table security_scan_reports add column context_pom_path varchar(2000);
alter table security_scan_reports add column context_branch varchar(500);
alter table security_scan_reports add column context_commit_sha varchar(200);
alter table security_scan_reports add column context_source varchar(40);

alter table security_scan_reports add constraint fk_security_scan_reports_context_project
    foreign key (context_project_id) references projects(id);
