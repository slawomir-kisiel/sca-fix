package pl.sca.fix.core.maven;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Path;
import pl.sca.fix.core.port.FileSystemPort;

/** JSON codec for the persisted effective-POM model. */
public final class EffectivePomJsonCodec {
    private final FileSystemPort fileSystem;
    private final ObjectMapper mapper;

    public EffectivePomJsonCodec(FileSystemPort fileSystem) {
        this(fileSystem, new ObjectMapper().findAndRegisterModules()
                .enable(SerializationFeature.INDENT_OUTPUT));
    }

    public EffectivePomJsonCodec(FileSystemPort fileSystem, ObjectMapper mapper) {
        this.fileSystem = fileSystem;
        this.mapper = mapper;
    }

    public EffectivePom read(Path path) {
        try {
            return mapper.readValue(fileSystem.readBytes(path), EffectivePom.class);
        } catch (IOException | RuntimeException exception) {
            throw new EffectivePomParseException("Unable to read effective-POM JSON " + path, exception);
        }
    }

    public byte[] writeBytes(EffectivePom model) {
        try {
            return mapper.writeValueAsBytes(model);
        } catch (IOException exception) {
            throw new EffectivePomParseException("Unable to serialize effective-POM JSON", exception);
        }
    }

    public void writeAtomically(Path path, EffectivePom model) {
        fileSystem.writeAtomically(path, writeBytes(model));
    }
}
