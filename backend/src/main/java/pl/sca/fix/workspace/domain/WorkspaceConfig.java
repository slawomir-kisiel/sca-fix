package pl.sca.fix.workspace.domain;

import java.nio.file.Path;

public record WorkspaceConfig(String rootPath, Path effectivePath, boolean exists,
                              boolean writable, boolean defaultValue) {
}
