# SCA-fix-tool

SCA-fix-tool to lokalna aplikacja webowa wspierająca analizę podatności w zależnościach Maven oraz przygotowanie kontrolowanych zmian w plikach `pom.xml`.

## Cel projektu

Aplikacja skraca drogę od raportu SCA do bezpiecznej i weryfikowalnej zmiany w kodzie źródłowym:

```text
repozytorium Git
    -> projekty Maven i dependency:tree
    -> import raportu Checkmarx lub OWASP
    -> korelacja podatności z grafem zależności
    -> wybór miejsca, strategii i wersji docelowej
    -> plan aktualizacji
    -> diff, wykonanie i walidacja Maven
```

Aplikacja nie zastępuje skanera bezpieczeństwa. Raport skanera pozostaje źródłem informacji o podatności, a graf zależności jest budowany niezależnie na podstawie Maven.

## Najważniejsze zasady

- raport jest przechowywany w oryginalnej postaci binarnej poza bazą danych;
- dane raportów są mapowane do kanonicznego modelu niezależnego od formatu;
- graf zależności jest tworzony wyłącznie z analizy Maven;
- finding może mieć wiele wystąpień w różnych projektach i ścieżkach;
- brak dopasowania do grafu jest widoczny jako `ORPHAN`;
- żadna zmiana w `pom.xml` nie jest wykonywana bez decyzji użytkownika;
- operacje Git i Maven działają jako asynchroniczne joby z `jobId` i podglądem konsoli;
- H2 jest domyślną bazą danych, a PostgreSQL może być użyty konfiguracyjnie.

## Funkcjonalność aktualna

### Repozytoria i projekty

- rejestracja istniejącego lokalnego checkoutu Git;
- klonowanie repozytorium z podanego URL-a do katalogu roboczego `./work`;
- automatyczne wykrywanie śledzonych plików `pom.xml`;
- rejestracja wielu projektów Maven w jednym repozytorium;
- filtrowanie projektów po nazwie, grupie, wersji, folderze i statusie analizy;
- uruchamianie analizy Maven dla wybranych projektów;
- prezentacja statusu analizy, daty oraz outputu joba;
- przechowywanie `effective-pom` w plikach `./data/effective-poms/{groupId}/{artifactId}/{version}/effective-pom.xml`;
- odczyt repozytoriów Maven projektu z sekcji `<repositories>` w `effective-pom`;
- filtrowanie repozytoriów według polityk `releases` i `snapshots`.

### Analiza Maven

Aplikacja uruchamia dla projektu `dependency:tree` z użyciem zdefiniowanej wersji pluginu, niezależnie od wersji zapisanej w projekcie. Wynik jest zapisywany jako struktura `Artifact`, `ArtifactVersion` i `ArtifactDependency`. Ścieżki zależności są utrwalane w węzłach drzewa i mogą być ładowane w UI na żądanie.

Po udanej analizie graf projektu jest aktualizowany, a poprzednia struktura pozostaje zachowana do chwili poprawnego odczytu nowego wyniku. Błąd analizy nie usuwa poprzedniego grafu.

### Import raportów SCA

Obsługiwane są:

- Checkmarx JSON;
- OWASP Dependency-Check JSON.

Raport jest rozpoznawany przez adapter, a następnie mapowany do wspólnych encji `SecurityScanReport`, `VulnerabilityFinding` i `AffectedArtifact`. Raport można usunąć wraz z jego findings i korelacją; graf Maven repozytorium pozostaje zachowany. Usunięcie raportu powiązanego z aktywnym planem wymaga dodatkowego potwierdzenia.

### Korelacja podatności

Findingi są płaskimi informacjami o podatności. Podczas korelacji wyszukiwane są odpowiadające im artefakty i wystąpienia w aktualnym grafie Maven. Użytkownik może zobaczyć:

- podatny artefakt i wersję z raportu;
- wersję wykrytą w projekcie;
- projekty, w których artefakt występuje;
- ścieżki prowadzące do podatnej biblioteki;
- status `MATCHED`, `AMBIGUOUS` albo `ORPHAN`;
- findings pogrupowane po `groupId:artifactId:version`, z filtrowaniem po tekście, severity i statusie planowania.

### Wybór zmiany

UI-12 prowadzi użytkownika przez sześć decyzji:

1. wybór occurrence context;
2. wybór projektu, w którego `pom.xml` ma zostać wykonana zmiana;
3. wybór artefaktu na ścieżce zależności;
4. wybór strategii aktualizacji;
5. wybór wersji docelowej;
6. zapis decyzji w aktywnym planie.

Dostępne strategie obejmują zmianę bezpośredniej zależności, `dependencyManagement`, property, CVE Override, odłożenie i odrzucenie. Decyzja może zostać zapisana wyłącznie w wybranym planie o statusie `PLANNED`.

Podczas ustalania miejsca zmiany aplikacja korzysta z komentarzy przy elemencie
`<version>` w `mvn help:effective-pom -Dverbose`. Dzięki temu może wskazać
projekt, w którym wersja jest faktycznie zdefiniowana, nawet jeśli zależność
została użyta tranzytywnie albo bez wersji w POM-ie projektu occurrence.

Proponowane wersje są pobierane z grafu lokalnego, repozytoriów Maven
zdefiniowanych dla projektu oraz repozytoriów globalnych. Niedostępne źródła
są czasowo blokowane, aby nie spowalniały kolejnych zapytań.

### Plany aktualizacji i PlanItem

`UpdatePlan` grupuje wiele niezależnych `PlanItem` dla jednego repozytorium. W aplikacji może być aktywny jeden plan dla repozytorium, a jego wybór jest dostępny globalnie w prawym górnym rogu layoutu.

Na formatce planu użytkownik może:

- przeglądać listę PlanItem i ich statusy;
- edytować decyzję przez przycisk `Edytuj`;
- usunąć pojedynczy PlanItem;
- usunąć cały plan wraz z jego PlanItem;
- zobaczyć komunikat błędu dla elementu, którego nie udało się zastosować.

Usuwanie wymaga potwierdzenia. Planów `APPLIED` i `COMMITTED` oraz zastosowanych PlanItem nie można usuwać.

### Diff i wykonanie

Plan może zostać zastosowany na branchu zależnym od `taskId`. Przed zmianą użytkownik otrzymuje diff plików `pom.xml`. Aplikacja może zastosować wszystkie PlanItem, nawet jeśli część z nich zakończy się błędem. Każdy element otrzymuje własny status i komunikat błędu, dzięki czemu można ponowić tylko nieudane zmiany.

Dostępne są również operacje commit, revert, ponowienie apply oraz podgląd konsoli joba.

### Joby i konsola

Długotrwałe operacje otrzymują `jobId` i zapisują status, etap, kod zakończenia, komunikat błędu oraz linie `STDOUT` i `STDERR`. Log zawiera również wykonywane polecenia w formie zbliżonej do terminala. Output można odtworzyć po ponownym wejściu do konsoli joba.

### Repozytoria Maven

Repozytoria projektu są odczytywane z utrwalonego `effective-pom` i używane
przed repozytoriami globalnymi skonfigurowanymi w `application.properties`.
Repozytoria są rozpoznawane po URL-u, a wynik wersji zawiera identyfikator
źródłowego repozytorium.

Po timeoutach, błędach połączenia oraz odpowiedziach HTTP 401/403 repozytorium
jest domyślnie blokowane na 10 minut. Powtarzające się błędy 5xx również
aktywują blokadę. Blokada jest współdzielona przez aplikację, ale nie wpływa na
pozostałe repozytoria. Credentials mogą być pobierane z `settings.xml`; nie są
zapisywane w bazie, logach ani odpowiedziach API.

## Automatyzacja

Automatyzacja obejmuje obecnie:

- klonowanie repozytoriów Git;
- wykrywanie projektów Maven;
- uruchamianie analizy `dependency:tree` dla pojedynczych i wybranych wielu projektów;
- budowanie i aktualizowanie grafu zależności;
- import i kanonizację raportów;
- korelację findings z wystąpieniami w grafie;
- pobieranie i walidację proponowanych wersji Maven;
- wyszukiwanie wersji w repozytoriach projektowych i globalnych z obsługą
  timeoutów oraz czasowej blokady niedostępnych źródeł;
- przygotowanie planów dla findings;
- przygotowanie diffu, zastosowanie planu i walidację zmian Maven;
- częściową obsługę błędów i ponawianie operacji.

Aplikacja może przygotować plan i diff, ale nie modyfikuje źródeł bez wyraźnej akcji użytkownika.

## Funkcjonalność planowana

Plan rozwoju obejmuje między innymi:

- hierarchiczny wybór wersji Maven z rekomendacją wersji domyślnej;
- obsługę bardziej złożonych definicji parent i BOM;
- pełniejszą historię jobów, planów, branchy i commitów;
- globalną formatkę ustawień aplikacji;
- dalsze testy komponentów i scenariuszy end-to-end.

Szczegółowa lista prac znajduje się w [todo.md](todo.md). Opisy architektury, wymagań i przypadków użycia są dostępne w katalogu [docs](docs/).

## Technologie

- backend: Java, Spring Boot, Spring Data JPA, Flyway;
- frontend: React, TypeScript, Vite;
- baza domyślna: plikowa H2;
- baza opcjonalna: PostgreSQL;
- integracje lokalne: Git CLI, Maven CLI;
- integracja z repozytoriami Maven przez HTTP(S), credentials z `settings.xml`
  i opcjonalny proxy;
- komunikacja: REST oraz SSE dla outputu jobów.

## Uruchomienie

Wymagane są Java, Maven, Node.js oraz dostępne w `PATH` polecenia Git i Maven.

```bat
run-app.bat
```

Po uruchomieniu interfejs jest dostępny pod adresem `http://localhost:8080`.

Do przeglądania struktury plikowej bazy H2 służy:

```bat
run-h2-console.bat
```

PostgreSQL można włączyć przez odpowiedni plik lub właściwości `application.properties`.

## Status projektu

Projekt jest rozwijanym prototypem/MVP. Najważniejsze przepływy domenowe są zaimplementowane, ale część funkcji pozostaje w trakcie dopracowania, przede wszystkim automatyzacja rekomendacji, obsługa złożonych definicji Maven oraz pełna historia operacji.
