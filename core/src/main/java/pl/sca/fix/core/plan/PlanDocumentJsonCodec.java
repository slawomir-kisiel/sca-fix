package pl.sca.fix.core.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import pl.sca.fix.core.port.PortException;

/** UTF-8 JSON codec for the portable plan document. */
public final class PlanDocumentJsonCodec {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final PlanDocumentValidator validator = new PlanDocumentValidator();

    public byte[] write(PlanDocument plan) {
        validator.validate(plan);
        try { return mapper.writeValueAsString(plan).getBytes(StandardCharsets.UTF_8); }
        catch (Exception exception) { throw new PortException(PortException.Kind.IO, "Unable to serialize plan", exception); }
    }

    public PlanDocument read(byte[] content) {
        try {
            PlanDocument plan = mapper.readValue(content, PlanDocument.class);
            validator.validate(plan);
            return plan;
        } catch (PlanValidationException exception) { throw exception; }
        catch (Exception exception) { throw new PlanValidationException("Invalid plan JSON: " + exception.getMessage()); }
    }
}
