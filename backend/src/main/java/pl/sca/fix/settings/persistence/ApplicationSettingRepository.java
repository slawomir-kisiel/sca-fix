package pl.sca.fix.settings.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.sca.fix.settings.domain.ApplicationSetting;

public interface ApplicationSettingRepository extends JpaRepository<ApplicationSetting, String> { }
