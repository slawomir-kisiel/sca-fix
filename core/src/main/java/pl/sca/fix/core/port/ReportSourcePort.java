package pl.sca.fix.core.port;

/** Loads a report as bytes; format detection and adaptation are separate concerns.
 * Missing or unreadable inputs are reported as {@link PortException}. */
public interface ReportSourcePort {
    ReportDocument load(ReportSourceRequest request) throws PortException;
}
