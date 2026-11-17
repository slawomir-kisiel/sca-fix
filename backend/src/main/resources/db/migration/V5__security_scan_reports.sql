create table security_scan_reports (
    id uuid primary key,
    repository_id uuid,
    original_filename varchar(500) not null,
    content_type varchar(255),
    size_bytes bigint not null,
    sha256 varchar(64) not null,
    source varchar(80),
    format varchar(120),
    format_version varchar(80),
    imported_at timestamp with time zone not null,
    scan_date timestamp with time zone,
    status varchar(40) not null,
    constraint fk_security_scan_reports_repository foreign key (repository_id) references repositories(id)
);

create table security_scan_report_contents (
    report_id uuid primary key,
    content bytea not null,
    constraint fk_security_scan_report_contents_report foreign key (report_id) references security_scan_reports(id) on delete cascade
);

create index ix_security_scan_reports_repository on security_scan_reports(repository_id);
create index ix_security_scan_reports_sha256 on security_scan_reports(sha256);
