package pl.sca.fix.uibackend.config;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface WorkspaceConfigurationRepository extends JpaRepository<WorkspaceConfigurationEntity, UUID> {}

