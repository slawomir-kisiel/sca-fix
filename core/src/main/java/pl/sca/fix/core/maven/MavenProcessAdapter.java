package pl.sca.fix.core.maven;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.sca.fix.core.port.FileSystemPort;
import pl.sca.fix.core.port.MavenDependencyTreeRequest;
import pl.sca.fix.core.port.MavenEffectivePomRequest;
import pl.sca.fix.core.port.MavenExecutionResult;
import pl.sca.fix.core.port.MavenPort;
import pl.sca.fix.core.port.MavenOwaspReportRequest;
import pl.sca.fix.core.port.PortException;
import pl.sca.fix.core.port.ProcessRequest;
import pl.sca.fix.core.port.ProcessResult;
import pl.sca.fix.core.port.ProcessRunnerPort;

/** Maven adapter that executes pinned plugin goals through ProcessRunnerPort. */
public final class MavenProcessAdapter implements MavenPort {
    private static final Logger LOG = LoggerFactory.getLogger(MavenProcessAdapter.class);
    private final ProcessRunnerPort processRunner;
    private final FileSystemPort fileSystem;
    private final MavenToolConfiguration configuration;

    public MavenProcessAdapter(ProcessRunnerPort processRunner, FileSystemPort fileSystem,
                               MavenToolConfiguration configuration) {
        this.processRunner = Objects.requireNonNull(processRunner, "processRunner must not be null");
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
    }

    @Override
    public MavenExecutionResult generateEffectivePom(MavenEffectivePomRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> command = baseCommand(request.pomFile());
        command.add(pluginGoal("org.apache.maven.plugins", "maven-help-plugin",
                configuration.helpPluginVersion(), "effective-pom"));
        command.add("-Dverbose");
        command.add("-Doutput=" + absolute(request.outputFile()));
        return execute(command, request.outputFile());
    }

    @Override
    public MavenExecutionResult generateDependencyTree(MavenDependencyTreeRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        String pluginVersion = request.dependencyPluginVersion() == null
                ? configuration.dependencyPluginVersion() : request.dependencyPluginVersion();
        MavenPluginVersion parsedVersion = MavenPluginVersion.parse(pluginVersion);
        if (request.displayAncestors() && !parsedVersion.atLeast(2, 9)) {
            throw new PortException(PortException.Kind.UNSUPPORTED,
                    "dependency:display-ancestors requires maven-dependency-plugin >= 2.9, configured: " + pluginVersion);
        }
        List<String> treeCommand = baseCommand(request.pomFile());
        treeCommand.add(pluginGoal("org.apache.maven.plugins", "maven-dependency-plugin",
                pluginVersion, "tree"));
        treeCommand.add("-DoutputType=json");
        treeCommand.add("-Dverbose");
        treeCommand.add("-DoutputFile=" + absolute(request.outputFile()));
        MavenExecutionResult treeResult = execute(treeCommand, request.outputFile());
        if (!request.displayAncestors()) return treeResult;
        List<String> ancestorsCommand = baseCommand(request.pomFile());
        ancestorsCommand.add(pluginGoal("org.apache.maven.plugins", "maven-dependency-plugin",
                pluginVersion, "display-ancestors"));
        MavenExecutionResult ancestorsResult = executeWithoutOutput(ancestorsCommand, request.outputFile());
        return new MavenExecutionResult(combine(treeResult.processResult(), ancestorsResult.processResult()),
                treeResult.outputFile());
    }

    @Override
    public MavenExecutionResult generateOwaspReport(MavenOwaspReportRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<String> command = baseCommand(request.pomFile(), false);
        command.add(pluginGoal("org.owasp", "dependency-check-maven", request.pluginVersion(), "aggregate"));
        command.add("-Dformat=json");
        command.add("-Dodc.outputDirectory=" + absolute(request.outputDirectory()));
        command.add("-DnvdApiServerId=" + request.nvdApiServerId());
        command.add("-DossIndexServerId=" + request.ossIndexServerId());
        if (request.nvdApiKey() != null) command.add("-DnvdApiKey=" + request.nvdApiKey());
        return execute(command, request.outputFile());
    }

    private MavenExecutionResult execute(List<String> command, Path outputFile) {
        MavenExecutionResult result = executeWithoutOutput(command, outputFile);
        if (!fileSystem.exists(outputFile)) {
            throw new PortException(PortException.Kind.IO, "Maven did not create output file: " + outputFile);
        }
        return result;
    }

    private MavenExecutionResult executeWithoutOutput(List<String> command, Path outputFile) {
        Path outputParent = absolutePath(outputFile).getParent();
        if (outputParent != null) fileSystem.createDirectories(outputParent);
        ProcessRequest request = new ProcessRequest(command, workingDirectory(command), Map.of(), configuration.timeout());
        LOG.info("Executing Maven command: $ {}", safeDisplayCommand(request));
        ProcessResult result;
        try {
            result = processRunner.run(request);
        } catch (PortException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new PortException(PortException.Kind.IO,
                    "Unable to start Maven command: " + safeDisplayCommand(request), exception);
        }
        if (result.timedOut()) {
            throw new PortException(PortException.Kind.TIMEOUT,
                    "Maven command timed out: " + safeDisplayCommand(request));
        }
        if (result.exitCode() != 0) {
            throw new PortException(PortException.Kind.PROCESS_FAILED,
                    "Maven command failed exitCode=" + result.exitCode() + ": " + safeDisplayCommand(request));
        }
        return new MavenExecutionResult(result, outputFile);
    }

    private List<String> baseCommand(Path pomFile) {
        return baseCommand(pomFile, true);
    }

    private List<String> baseCommand(Path pomFile, boolean nonRecursive) {
        Path absolutePom = absolutePath(pomFile);
        List<String> command = new ArrayList<>();
        if (configuration.windows()) {
            command.add("cmd.exe");
            command.add("/c");
        }
        command.add(configuration.executable());
        if (nonRecursive) command.add("-N");
        command.add("-f");
        command.add(absolutePom.toString());
        return command;
    }

    private Path workingDirectory(List<String> command) {
        for (int index = 0; index < command.size() - 1; index++) {
            if ("-f".equals(command.get(index))) return Path.of(command.get(index + 1)).getParent();
        }
        throw new IllegalArgumentException("Maven command does not contain -f");
    }

    private static String pluginGoal(String groupId, String artifactId, String version, String goal) {
        return groupId + ":" + artifactId + ":" + version + ":" + goal;
    }

    private static String safeDisplayCommand(ProcessRequest request) {
        return request.displayCommand().replaceAll("(?i)(-DnvdApiKey=)[^\\s]+", "$1***");
    }

    private static String absolute(Path path) {
        return absolutePath(path).toString();
    }

    private static Path absolutePath(Path path) {
        return Objects.requireNonNull(path, "path must not be null").toAbsolutePath().normalize();
    }

    private static ProcessResult combine(ProcessResult first, ProcessResult second) {
        Duration duration = first.duration().plus(second.duration());
        return new ProcessResult(second.exitCode(), first.stdout() + second.stdout(),
                first.stderr() + second.stderr(), duration, first.timedOut() || second.timedOut());
    }
}
