package pl.sca.fix.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.List;

/**
 * Small diagnostic entry point for inspecting parser output without running the
 * pipeline.
 */
public final class CliConfigurationPreview {

    private static final int HELP_OPTION_WIDTH = 52;

    private CliConfigurationPreview() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || contains(args, "--help") || contains(args, "-h")) {
            printHelp();
            return;
        }
        if (contains(args, "--version")) {
            System.out.println("sca-fix-cli 0.1.1-SNAPSHOT");
            return;
        }
        try {
            CliConfiguration configuration = new CliConfigurationParser().parse(args);
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            System.out.println(mapper.writeValueAsString(configuration));
        } catch (CliConfigException exception) {
            System.err.println(exception.code() + " [" + exception.parameter() + "]: " + exception.getMessage());
            System.exit(2);
        }
    }

    private static boolean contains(String[] args, String expected) {
        for (String arg : args) {
            if (expected.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void printHelp() {
        System.out.println("sca-fix-cli 0.1.0-SNAPSHOT");
        System.out.println();
        System.out.println("Użycie:");
        System.out.println("  sca-fix-cli <komenda> [opcje]");
        System.out.println();
        System.out.println("Komendy:");
        commands(List.of(
                new String[]{"scan", "Analiza repozytorium, raportu i przygotowanie planu."},
                new String[]{"override-review", "Analiza override'ów Maven bez raportu podatności."},
                new String[]{"apply", "Zastosowanie planu w repozytorium."}));
        System.out.println();
        System.out.println("Opcje wspólne:");
        options(List.of(
                new String[]{"--config <plik.json>", "Dodatkowy plik konfiguracji."},
                new String[]{"--repository <ścieżka|url>", "Checkout lokalny albo adres repozytorium Git."},
                new String[]{"--projects <lista>", "Projekty groupId:artifactId albo '*', rozdzielone przecinkami."},
                new String[]{"--workspace-root <ścieżka>", "Katalog workspace'ów (domyślnie target/scafix/workspaces)."},
                new String[]{"--poms-root <ścieżka>", "Współdzielony cache POM-ów (domyślnie target/scafix/poms)."},
                new String[]{"--ignore-local-changes", "Zezwala na pracę z lokalnymi zmianami; blokuje push."},
                new String[]{"--verbose", "Włącza szczegółowe komunikaty."},
                new String[]{"--proxy-host <host>", "Host proxy HTTP dla repozytoriów Maven."},
                new String[]{"--proxy-port <port>", "Port proxy HTTP (wymagany razem z hostem)."},
                new String[]{"--proxy-username <user>", "Opcjonalny użytkownik proxy."},
                new String[]{"--proxy-password <pass>", "Opcjonalne hasło proxy."},
                new String[]{"--help, -h", "Wyświetla pomoc."},
                new String[]{"--version", "Wyświetla wersję programu."}));
        System.out.println();
        System.out.println("scan:");
        options(List.of(
                new String[]{"--report-source file|generate", "Źródło raportu; wymagane dla scan."},
                new String[]{"--report-input <plik>", "Wejściowy raport dla source=file."},
                new String[]{"--report-output <plik>", "Opcjonalny zapis raportu kanonicznego."},
                new String[]{"--update-level MAJOR|MINOR|PATCH", "Poziom aktualizacji zależności."},
                new String[]{"--cve-override-update-level MAJOR|MINOR|PATCH", "Poziom tylko dla CVE_OVERRIDE; domyślnie PATCH."},
                new String[]{"--ambiguous-strategy SKIP|COMMON_SOURCE|SHALLOWEST|ALL", "Strategia obsługi niejednoznacznych wystąpień."},
                new String[]{"--version-info-ttl <R1d,S10m>", "TTL cache wersji release/snapshot."},
                new String[]{"--override-review", "Dodaje etap OverrideReview do pełnego scan."},
                new String[]{"--apply", "Po planowaniu automatycznie stosuje plan; scan obsługuje też --commit i --push."},
                new String[]{"--disable-strategy <lista>", "Wyłącza strategie podczas apply, np. CVE_OVERRIDE,DIRECT_DEPENDENCY."},
                new String[]{"--skip <lista>", "Pomija biblioteki podczas apply, np. com.google.guava,org.apache:commons4,org.wso2:test-unit:3."},
                new String[]{"--continue <workspace-id>", "Wznawia workspace z config.json."},
                new String[]{"--restart <workspace-id>", "Tworzy nowy workspace na podstawie istniejącego."}));
        System.out.println();
        System.out.println("override-review:");
        System.out.println("  Uruchamia discovery, analizę Maven i OverrideReview bez raportu.");
        System.out.println("  Nie przyjmuje --report-source ani --report-input.");
        System.out.println();
        System.out.println("apply:");
        options(List.of(
                new String[]{"--plan <plik.json>", "Zastosowanie wskazanego planu."},
                new String[]{"--workspace <workspace-id>", "Zastosowanie planu z workspace."},
                new String[]{"--commit", "Wykonuje commit zmian."},
                new String[]{"--push <nazwa-brancha>", "Commit i push na wskazany branch."},
                new String[]{"(wymagane)", "Dokładnie jedno z --plan albo --workspace."},
                new String[]{"(ograniczenie)", "--push nie działa z --ignore-local-changes i wymaga --commit."}));
        System.out.println();
        System.out.println("Priorytet konfiguracji:");
        System.out.println("  wartości domyślne < config.json workspace < --config < parametry CLI");
        System.out.println("  settings.xml < workspace config.json < --config < parametry CLI (proxy).");
        System.out.println("  Sekrety nie są prezentowane w logu.");
        System.out.println();
        System.out.println("Przykłady:");
        System.out.println("  sca-fix-cli scan --repository ./checkout --report-source file --report-input scan.json");
        System.out.println("  sca-fix-cli scan --continue 20261122-120000");
        System.out.println("  sca-fix-cli scan --restart 20261122-120000 --config config.json");
        System.out.println("  sca-fix-cli override-review --repository ./checkout");
        System.out.println("  sca-fix-cli apply --workspace 20261122-120000 --commit");
        System.out.println("  sca-fix-cli apply --plan ./plan.json --repository ./checkout");
    }

    private static void commands(List<String[]> values) {
        for (String[] value : values) {
            System.out.printf("  %-18s%s%n", value[0], value[1]);
        }
    }

    private static void options(List<String[]> values) {
        for (String[] value : values) {
            System.out.printf("  %-" + HELP_OPTION_WIDTH + "s%s%n", value[0], value[1]);
        }
    }

    private static void printLegacyHelp() {
        System.out.println("sca-fix-cli 0.1.0-SNAPSHOT");
        System.out.println();
        System.out.println("Użycie:");
        System.out.println("  sca-fix-cli <komenda> [opcje]");
        System.out.println();
        System.out.println("Komendy:");
        System.out.println("  scan             Analiza repozytorium, raportu i przygotowanie planu.");
        System.out.println("  override-review  Analiza override'ów Maven bez raportu podatności.");
        System.out.println("  apply            Zastosowanie planu w repozytorium.");
        System.out.println();
        System.out.println("Opcje wspólne:");
        System.out.println("  --config <plik.json>       Dodatkowy plik konfiguracji.");
        System.out.println("  --repository <ścieżka|url>  Checkout lokalny albo adres repozytorium Git.");
        System.out.println("  --projects <lista>         Projekty groupId:artifactId albo '*', rozdzielone przecinkami.");
        System.out.println("  --workspace-root <ścieżka> Katalog workspace'ów (domyślnie target/scafix/workspaces).");
        System.out.println("  --poms-root <ścieżka>      Współdzielony cache POM-ów (domyślnie target/scafix/poms).");
        System.out.println("  --ignore-local-changes     Zezwala na pracę z lokalnymi zmianami; blokuje push.");
        System.out.println("  --verbose                  Włącza szczegółowe komunikaty.");
        System.out.println("  --proxy-host <host>        Host proxy HTTP dla repozytoriów Maven.");
        System.out.println("  --proxy-port <port>        Port proxy HTTP (wymagany razem z hostem).");
        System.out.println("  --proxy-username <user>    Opcjonalny użytkownik proxy.");
        System.out.println("  --proxy-password <pass>    Opcjonalne hasło proxy.");
        System.out.println("  --help, -h                 Wyświetla pomoc.");
        System.out.println("  --version                  Wyświetla wersję programu.");
        System.out.println();
        System.out.println("scan:");
        System.out.println("  --report-source file|generate  Źródło raportu; wymagane dla scan.");
        System.out.println("  --report-input <plik>          Wejściowy raport dla source=file.");
        System.out.println("  --report-output <plik>         Opcjonalny zapis raportu kanonicznego.");
        System.out.println("  --update-level MAJOR|MINOR|PATCH");
        System.out.println("  --cve-override-update-level MAJOR|MINOR|PATCH  Poziom tylko dla strategii CVE_OVERRIDE; domyslnie PATCH.");
        System.out.println("  --ambiguous-strategy SKIP|COMMON_SOURCE|SHALLOWEST|ALL");
        System.out.println("  --version-info-ttl <R1d,S10m>  TTL cache wersji release/snapshot.");
        System.out.println("  --override-review              Dodaje etap OverrideReview do pełnego scan.");
        System.out.println("  --apply                        Po planowaniu automatycznie stosuje plan; scan obsługuje też --commit i --push.");
        System.out.println("  --disable-strategy <lista>    Wyłącza strategie podczas apply, np. CVE_OVERRIDE,DIRECT_DEPENDENCY.");
        System.out.println("  --skip <lista>                Pomija biblioteki podczas apply, np. com.google.guava,org.apache:commons4,org.wso2:test-unit:3.");
        System.out.println("  --continue <workspace-id>      Wznawia workspace z config.json.");
        System.out.println("  --restart <workspace-id>       Tworzy nowy workspace na podstawie istniejącego.");
        System.out.println();
        System.out.println("override-review:");
        System.out.println("  Uruchamia discovery, analizę Maven i OverrideReview bez raportu.");
        System.out.println("  Nie przyjmuje --report-source ani --report-input.");
        System.out.println();
        System.out.println("apply:");
        System.out.println("  --plan <plik.json>             Zastosowanie wskazanego planu.");
        System.out.println("  --workspace <workspace-id>     Zastosowanie planu z workspace.");
        System.out.println("  --commit                       Wykonuje commit zmian.");
        System.out.println("  --push <nazwa-brancha>         Commit i push na wskazany branch.");
        System.out.println("  Wymagane jest dokładnie jedno z --plan albo --workspace.");
        System.out.println("  --push nie działa z --ignore-local-changes i wymaga --commit.");
        System.out.println();
        System.out.println("Priorytet konfiguracji:");
        System.out.println("  wartości domyślne < config.json workspace < --config < parametry CLI");
        System.out.println("  settings.xml < workspace config.json < --config < parametry CLI (proxy).");
        System.out.println("  Sekrety nie są prezentowane w logu.");
        System.out.println();
        System.out.println("Przykłady:");
        System.out.println("  sca-fix-cli scan --repository ./checkout --report-source file --report-input scan.json");
        System.out.println("  sca-fix-cli scan --continue 20261122-120000");
        System.out.println("  sca-fix-cli scan --restart 20261122-120000 --config config.json");
        System.out.println("  sca-fix-cli override-review --repository ./checkout");
        System.out.println("  sca-fix-cli apply --workspace 20261122-120000 --commit");
        System.out.println("  sca-fix-cli apply --plan ./plan.json --repository ./checkout");
    }
}
