package pl.sca.fix.uibackend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.sca.fix.uibackend.workspace.WorkspaceRepository;

@ExtendWith(MockitoExtension.class)
class WorkspaceConfigurationTest {
    @Mock WorkspaceConfigurationRepository configurations;
    @Mock WorkspaceRepository workspaces;
    @Mock com.fasterxml.jackson.databind.ObjectMapper mapper;
    @InjectMocks WorkspaceConfigurationService service;

    @Test
    void returnsHelpfulDefaultsForNewWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        when(workspaces.existsById(workspaceId)).thenReturn(true);
        when(configurations.findById(workspaceId)).thenReturn(Optional.empty());

        WorkspaceConfiguration result = service.get(workspaceId);

        assertEquals("MINOR", result.updateLevel());
        assertEquals("PATCH", result.cveOverrideUpdateLevel());
        assertEquals(java.util.List.of("*"), result.analysisProjects());
    }

    @Test
    void exportsConfigurationAsJson() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(workspaces.existsById(workspaceId)).thenReturn(true);
        when(configurations.findById(workspaceId)).thenReturn(Optional.empty());
        ObjectWriter writer = org.mockito.Mockito.mock(ObjectWriter.class);
        when(mapper.writerWithDefaultPrettyPrinter()).thenReturn(writer);
        when(writer.writeValueAsBytes(org.mockito.ArgumentMatchers.any(WorkspaceConfiguration.class)))
                .thenReturn("{\"schemaVersion\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        byte[] exported = service.export(workspaceId);

        assertTrue(new String(exported, java.nio.charset.StandardCharsets.UTF_8).contains("schemaVersion"));
    }

    @Test
    void rejectsUnsupportedConfigurationSchema() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(workspaces.existsById(workspaceId)).thenReturn(true);
        when(mapper.readValue(org.mockito.ArgumentMatchers.any(byte[].class),
                org.mockito.ArgumentMatchers.eq(WorkspaceConfiguration.class)))
                .thenReturn(new WorkspaceConfiguration(2, java.util.List.of("*"), java.util.List.of("*"),
                        "MINOR", "PATCH", "SHALLOWEST", java.util.List.of(), java.util.List.of(),
                        null, null, java.util.List.of(), false));

        var exception = org.junit.jupiter.api.Assertions.assertThrows(
                pl.sca.fix.uibackend.workspace.WorkspaceException.class,
                () -> service.importConfiguration(workspaceId, "{}".getBytes()));

        assertEquals("CONFIGURATION_SCHEMA_UNSUPPORTED", exception.code());
    }
}

