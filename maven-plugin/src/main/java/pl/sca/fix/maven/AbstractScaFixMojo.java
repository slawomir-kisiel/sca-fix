package pl.sca.fix.maven;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sca.fix.core.report.UpdateLevel;

abstract class AbstractScaFixMojo extends AbstractMojo {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Parameter(defaultValue = "${project.baseDir}", readonly = true, required = true)
    protected File projectBaseDir;
    @Parameter(property = "scaFix.repository") protected String repository;
    @Parameter(property = "scaFix.projects") protected String projects;
    @Parameter(property = "scaFix.workspaceRoot", defaultValue = "${project.build.directory}/scafix/workspaces")
    protected File workspaceRoot;
    @Parameter(property = "scaFix.pomsRoot", defaultValue = "${project.build.directory}/scafix/poms")
    protected File pomsRoot;
    @Parameter(property = "scaFix.reportSource") protected String reportSource;
    @Parameter(property = "scaFix.reportInput") protected File reportInput;
    @Parameter(property = "scaFix.reportOutput") protected File reportOutput;
    @Parameter(property = "scaFix.updateLevel", defaultValue = "MINOR") protected String updateLevel;
    @Parameter(property = "scaFix.ambiguousStrategy", defaultValue = "COMMON_SOURCE") protected String ambiguousStrategy;
    @Parameter(property = "scaFix.versionInfoTtl", defaultValue = "R1d,S10m") protected String versionInfoTtl;
    @Parameter(property = "scaFix.ignoreLocalChanges", defaultValue = "false") protected boolean ignoreLocalChanges;
    @Parameter(property = "scaFix.apply", defaultValue = "false") protected boolean apply;
    @Parameter(property = "scaFix.commit", defaultValue = "false") protected boolean commit;
    @Parameter(property = "scaFix.push") protected String pushBranch;
    @Parameter(property = "scaFix.overrideReview", defaultValue = "false") protected boolean overrideReview;
    @Parameter(property = "scaFix.verbose", defaultValue = "false") protected boolean verbose;

    protected MavenPluginConfiguration configuration() {
        Path repo = (repository == null || repository.isBlank() ? projectBaseDir.toPath() : Path.of(repository));
        List<String> selected = projects == null || projects.isBlank() ? List.of("*")
                : Arrays.stream(projects.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
        UpdateLevel level;
        try { level = UpdateLevel.valueOf(updateLevel.toUpperCase()); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("INVALID_UPDATE_LEVEL: " + updateLevel, exception); }
        return new MavenPluginConfiguration(repo, workspaceRoot.toPath(), pomsRoot.toPath(), selected, reportSource,
                reportInput == null ? null : reportInput.toPath(), reportOutput == null ? null : reportOutput.toPath(),
                level, ambiguousStrategy, versionInfoTtl, ignoreLocalChanges, apply, commit, pushBranch,
                overrideReview, verbose);
    }

    protected void execute(MavenPluginExecution execution) throws MojoExecutionException {
        MavenPluginConfiguration config;
        try {
            config = configuration();
            new MavenPluginConfigurationValidator().validate(config);
            log.info("SCA-fix configuration repository={} projects={} apply={} overrideReview={}",
                    config.repository(), config.projects(), config.apply(), config.overrideReview());
            if (execution == null) throw new MojoExecutionException("MAVEN_PIPELINE_NOT_CONFIGURED");
            execution.execute(config);
        } catch (MojoExecutionException exception) { throw exception;
        } catch (Exception exception) { throw new MojoExecutionException("SCA-fix pipeline failed", exception); }
    }
}
