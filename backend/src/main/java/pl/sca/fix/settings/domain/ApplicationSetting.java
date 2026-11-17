package pl.sca.fix.settings.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "application_settings")
public class ApplicationSetting {
    @Id private String settingKey;
    private String settingValue;
    private Instant updatedAt;

    protected ApplicationSetting() { }

    public ApplicationSetting(String settingKey, String settingValue) {
        this.settingKey = settingKey;
        this.settingValue = settingValue;
        this.updatedAt = Instant.now();
    }

    public String getSettingKey() { return settingKey; }
    public String getSettingValue() { return settingValue; }
    public Instant getUpdatedAt() { return updatedAt; }
}
