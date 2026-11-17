package pl.sca.fix.report.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionTemplate;
import pl.sca.fix.artifact.domain.Artifact;
import pl.sca.fix.artifact.domain.ArtifactVersion;
import pl.sca.fix.artifact.persistence.ArtifactRepository;
import pl.sca.fix.artifact.persistence.ArtifactVersionRepository;
import pl.sca.fix.dependency.domain.ArtifactTreeNode;
import pl.sca.fix.dependency.domain.DependencyRelation;
import pl.sca.fix.dependency.persistence.ArtifactTreeNodeRepository;
import pl.sca.fix.job.application.JobService;
import pl.sca.fix.job.domain.Job;
import pl.sca.fix.report.canonical.AffectedArtifact;
import pl.sca.fix.report.canonical.VulnerabilityFinding;
import pl.sca.fix.report.domain.DependencyTreeOccurrence;
import pl.sca.fix.report.domain.FindingStatus;
import pl.sca.fix.report.domain.SecurityScanReport;
import pl.sca.fix.report.persistence.DependencyTreeOccurrenceRepository;
import pl.sca.fix.report.persistence.SecurityScanReportRepository;
import pl.sca.fix.report.persistence.VulnerabilityFindingRepository;
import pl.sca.fix.project.domain.Project;
import pl.sca.fix.project.persistence.ProjectRepository;

class ReportCorrelationServiceTest {
    @Test
    void marksFindingMatchedWhenOneGraphOccurrenceExists() {
        Fixture fixture = fixture("1.0", List.of(version("1.0")), true);
        fixture.service.correlate(fixture.jobId, fixture.reportId);
        assertThat(fixture.finding.getStatus()).isEqualTo(FindingStatus.MATCHED);
        assertThat(fixture.occurrencesSaved()).isEqualTo(1);
    }

    @Test
    void marksFindingAmbiguousWhenMultipleVersionsMatch() {
        Fixture fixture = fixture(null, List.of(version("1.0"), version("1.1")), true);
        fixture.service.correlate(fixture.jobId, fixture.reportId);
        assertThat(fixture.finding.getStatus()).isEqualTo(FindingStatus.AMBIGUOUS);
        assertThat(fixture.occurrencesSaved()).isEqualTo(2);
    }

    @Test
    void marksFindingOrphanWhenArtifactIsAbsentFromGraph() {
        Fixture fixture = fixture("1.0", List.of(), false);
        fixture.service.correlate(fixture.jobId, fixture.reportId);
        assertThat(fixture.finding.getStatus()).isEqualTo(FindingStatus.ORPHAN);
        assertThat(fixture.occurrencesSaved()).isEqualTo(0);
    }

    private Fixture fixture(String reportedVersion, List<ArtifactVersion> graphVersions, boolean artifactPresent) {
        UUID reportId = UUID.randomUUID(); UUID jobId = UUID.randomUUID(); UUID projectId = UUID.randomUUID();
        UUID repositoryId = UUID.randomUUID();
        SecurityScanReport report = new SecurityScanReport(reportId, repositoryId, "report.json", "application/json", 1, "hash", java.time.Instant.now());
        Artifact artifact = new Artifact(UUID.randomUUID(), "org.example", "library", projectId);
        graphVersions = graphVersions.stream().map(item -> new ArtifactVersion(item.getId(), artifact.getId(), item.getVersion())).toList();
        final List<ArtifactVersion> preparedVersions = graphVersions;
        VulnerabilityFinding canonical = new VulnerabilityFinding("CVE-2026-0001", "CVE", "TEST", "CVE-2026-0001", "Issue", "HIGH", null, null, null, null, null, List.of(), List.of(), List.of(new AffectedArtifact("pkg:maven/org.example/library@1.0", "org.example", "library", reportedVersion, null, null, null, null)));
        pl.sca.fix.report.domain.VulnerabilityFinding finding = new pl.sca.fix.report.domain.VulnerabilityFinding(reportId, canonical);
        SecurityScanReportRepository reports = Mockito.mock(SecurityScanReportRepository.class);
        VulnerabilityFindingRepository findings = Mockito.mock(VulnerabilityFindingRepository.class);
        DependencyTreeOccurrenceRepository occurrences = Mockito.mock(DependencyTreeOccurrenceRepository.class);
        ArtifactRepository artifacts = Mockito.mock(ArtifactRepository.class);
        ArtifactVersionRepository versions = Mockito.mock(ArtifactVersionRepository.class);
        ArtifactTreeNodeRepository nodes = Mockito.mock(ArtifactTreeNodeRepository.class);
        JobService jobs = Mockito.mock(JobService.class);
        ProjectRepository projectRepository = Mockito.mock(ProjectRepository.class);
        Job job = new Job(jobId, "REPORT_CORRELATION");
        when(reports.findById(reportId)).thenReturn(Optional.of(report));
        when(findings.findByReportId(reportId)).thenReturn(List.of(finding));
        when(occurrences.save(any(DependencyTreeOccurrence.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jobs.get(jobId)).thenReturn(job);
        when(jobs.start(any(UUID.class), anyString())).thenReturn(job);
        when(jobs.finish(any(UUID.class), any(), any(), any(), any())).thenReturn(job);
        when(artifacts.findByGroupIdAndArtifactId("org.example", "library")).thenReturn(artifactPresent ? Optional.of(artifact) : Optional.empty());
        when(artifacts.findById(artifact.getId())).thenReturn(Optional.of(artifact));
        Project project = Mockito.mock(Project.class);
        when(project.getRepositoryId()).thenReturn(repositoryId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(versions.findByArtifactIdAndVersion(artifact.getId(), "1.0")).thenReturn(Optional.ofNullable(preparedVersions.isEmpty() ? null : preparedVersions.getFirst()));
        when(versions.findByArtifactIdIn(anyList())).thenReturn(preparedVersions);
        when(versions.findById(any(UUID.class))).thenAnswer(invocation -> preparedVersions.stream().filter(item -> item.getId().equals(invocation.getArgument(0))).findFirst());
        when(nodes.findByArtifactVersionIdIn(anyList())).thenAnswer(invocation -> preparedVersions.stream()
                .map(version -> new ArtifactTreeNode(projectId, version.getId(), null, DependencyRelation.DEPENDENCY, null, 0, true, false)).toList());
        ReportCorrelationService service = new ReportCorrelationService(reports, findings, occurrences, artifacts, versions, nodes, projectRepository, jobs,
                new TransactionTemplate(Mockito.mock(org.springframework.transaction.PlatformTransactionManager.class)));
        return new Fixture(service, finding, reportId, jobId, preparedVersions.size());
    }

    private ArtifactVersion version(String value) { return new ArtifactVersion(UUID.randomUUID(), UUID.randomUUID(), value); }
    private record Fixture(ReportCorrelationService service, pl.sca.fix.report.domain.VulnerabilityFinding finding, UUID reportId, UUID jobId, int expectedOccurrences) {
        long occurrencesSaved() { return expectedOccurrences; }
    }
}
