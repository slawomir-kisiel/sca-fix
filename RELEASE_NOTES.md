# Informacje o wydaniach

## 0.1.2 — 2026-11-26

### Dodane funkcjonalności

- Rozszerzono `override-review` o automatyczne wyszukiwanie bezpośrednich zależności prowadzących do biblioteki objętej override'em.
- Dla znalezionych bezpośrednich zależności obliczane są dostępne aktualizacje zgodnie z `update-level`.
- Wszystkie możliwe aktualizacje bezpośrednich zależności są stosowane tymczasowo jednocześnie przed wygenerowaniem jednego effective POM-u.
- Dostępne aktualizacje są dodawane do planu i zgłaszane jako warningi.
- Dodano obsługę `--report-source owasp`.
- Etap `REPORT` może wygenerować agregowany raport OWASP przez:

  ```text
  org.owasp:dependency-check-maven:12.1.9:aggregate
  ```

- Raport OWASP jest zapisywany w workspace jako `reports/owasp/dependency-check-report.json`, a następnie importowany do kanonicznego `report.json`.
- Dodano opcję `--nvd-api-key` oraz obsługę zmiennej środowiskowej `NVD_API_KEY`.
- Dodano szczegółowy tryb `--verbose`, który pokazuje polecenia, katalog roboczy, stdout, stderr, kod zakończenia i wyjątki.

### Poprawione działanie

- Planowanie aktualizacji korzysta z wersji zależności z lokalnego `dependencies/dependency` przed wersją z `dependencyManagement/dependency`.
- Wersja używana w rekomendacji i warningach odpowiada wersji wynikającej z lokalnej deklaracji zależności.
- Polecenia Maven dla raportu OWASP używają `-Dodc.outputDirectory` i oczekują stałej nazwy `dependency-check-report.json`.
- Raport agregowany jest uruchamiany bez `-N`, aby obejmował moduły projektu wielomodułowego.
- Wartość klucza NVD jest maskowana w logach verbose, logach Maven i komunikatach błędów.
- Przy braku klucza NVD zapisywana jest tylko informacja, że `nvdApiKey` nie został podany, co może spowolnić pobieranie danych z rejestru NVD.
- Podniesiono wersję projektu do `0.1.2` w głównym POM-ie, modułach oraz `ui-backend`.

### Testy

- Dodano i zaktualizowano testy dla rozszerzonego `override-review`.
- Dodano testy parsera CLI dla źródła `owasp` i opcji NVD.
- Dodano testy budowania polecenia Maven Dependency-Check, w tym agregacji, katalogu wyjściowego i maskowania klucza.
- Zweryfikowano moduły `core` i `cli`: 102 testy core oraz 27 testów CLI zakończyły się pomyślnie.

