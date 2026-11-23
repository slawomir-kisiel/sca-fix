package pl.sca.fix.core.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import pl.sca.fix.core.port.FileSystemPort;

/** Writes the text tree and its JSON index as one UTF-8 file. */
public final class DependencyTreeFileWriter {
    private final FileSystemPort fileSystem;
    private final ObjectMapper mapper;
    private final DependencyTreeTextWriter textWriter;

    public DependencyTreeFileWriter(FileSystemPort fileSystem) {
        this(fileSystem, new ObjectMapper());
    }

    public DependencyTreeFileWriter(FileSystemPort fileSystem, ObjectMapper mapper) {
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem must not be null");
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.textWriter = new DependencyTreeTextWriter();
    }

    public void write(Path target, DependencyTree tree) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(tree, "tree must not be null");
        try {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String content = textWriter.render(tree) + "----\n"
                    + mapper.writeValueAsString(tree.references()) + "\n";
            fileSystem.writeAtomically(target, content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new DependencyTreeParseException("Unable to write dependency tree: " + target, exception);
        }
    }
}
