package pl.sca.fix.repository.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RepositoryRequestValidatorTest {
    @Test
    void acceptsHttpAndSshGitUrls() {
        assertThatCode(() -> RepositoryRequestValidator.validateRepositoryUrl("https://git.example.com/team/repo.git"))
                .doesNotThrowAnyException();
        assertThatCode(() -> RepositoryRequestValidator.validateRepositoryUrl("ssh://git@git.example.com/team/repo.git"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsUnsupportedRepositoryUrl() {
        assertThatThrownBy(() -> RepositoryRequestValidator.validateRepositoryUrl("ftp://example.com/repo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supported Git scheme");
    }

    @Test
    void rejectsBlankClonePath() {
        assertThatThrownBy(() -> RepositoryRequestValidator.validateClonePath(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("clonePath is required");
    }
}
