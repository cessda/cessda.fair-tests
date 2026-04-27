# CESSDA FAIR Tests

[![SQAaaS badge](https://github.com/EOSC-synergy/SQAaaS/raw/master/badges/badges_150x116/badge_software_silver.png)](https://api.eu.badgr.io/public/assertions/SGiodTQYQPGTwKuZbpUiXA "SQAaaS silver badge achieved")

This repository contains the source code for CESSDA community-specific FAIR
(Findable, Accessible, Interoperable, Reusable) tests. Given a URL that
returns a metadata record, it fetches the response, detects its format,
and evaluates one or more predefined compliance tests, returning a
three-valued result: `PASS`, `FAIL`, or `INDETERMINATE`.

## Overview

The codebase follows a clean separation of concerns:

- One **orchestrator** class fetches the URL, sniffs the format, and
  routes to the correct parser.
- Three **parser** classes contain all format-specific logic.
- One **vocabulary service** (not documented here) supplies approved
  term sets and validates ELSST keywords.

```text
FairTests (orchestrator)
│
├── FormatSniffer          — detects XML / JSON / HTML
│
├── XmlParser              — DDI Codebook 2.5 XML
├── CdcJsonParser          — CDC-schema JSON objects
└── HtmlParser             — HTML pages with a JSON-LD block
         └── (delegates to CdcJsonParser)
```

All three parsers implement the `FormatParser` interface:

```java
Result runTest(TestType test, InputStream inputStream,
               VocabularyService vocabulary) throws IOException;
```

## Supported formats

| Format | Notes |
| -------- | ------- |
| DDI Codebook 2.5 XML | OAI-PMH envelope is handled transparently. |
| CDC-schema JSON | Root object must contain an `id` field. |
| HTML with JSON-LD | Must embed a `<script id="json-ld" type="application/ld+json">` block. |

## Supported tests

| `TestType` constant | What is checked |
| ---------------------- | ---------------------------------------------------------------- |
| `ACCESS_RIGHTS` | Approved access rights term is present. |
| `PID` | Persistent identifier schema is from an approved list. |
| `ELSST_KEYWORDS` | At least one ELSST controlled vocabulary keyword is present and valid. |
| `TOPIC_CLASS` | A CESSDA topic classification term is present. |
| `DDI_ANALYSIS_UNIT` | A DDI analysis unit vocabulary term is present. |
| `DDI_COLLECTION_MODE` | A DDI collection mode vocabulary term is present. |
| `DDI_TIME_METHOD` | A DDI time method vocabulary term is present. |
| `DDI_SAMPLEPROC` | A DDI sampling procedure term is present. |
| `PROVENANCE` | Publisher, creator, or funding information is present. |

## Prerequisites

Java 17 or greater is required to build and run this application.

## Quick start

### As a library

```java
FairTests fairTests = new FairTests();
URI url = new URI("https://example.org/api/studies/12345");

Result result = fairTests.containsApprovedAccessRights(url);
// or
Result result = fairTests.runTest(TestType.PID, url);
```

### From the command line

```text
java -cp fairtests.jar eu.cessda.fairtests.FairTests <test-type> <url>
```

`<test-type>` must match the `getTestName()` of a `TestType` constant.
The process exits with code `0` for `PASS` and `1` for any other result.

## Result values

| Value | Meaning |
| ------- | --------- |
| `PASS` | The record satisfies the test criteria. |
| `FAIL` | The record does not satisfy the test criteria. |
| `INDETERMINATE` | The test could not be completed (network error, unsupported format, parse error, missing required field). |

## Adding new tests

As well as adding rules for the new test(s) to
[CdcJsonParser..java](api/src/main/java/eu/cessda/fairtests/CdcJsonParser.java)
and [XmlParser.java](api/src/main/java/eu/cessda/fairtests/XmlParser.java)
you need to:

- extend the runTest switch statement
- extend the [TestType](api/src/main/java/eu/cessda/fairtests/TestType.java) enumeration
- add Unit tests in [FairTestsTests](api/src/test/java/eu/cessda/fairtests/FairTestsTest.java)
- add an API descriptor file in the `resources/static directory`

### JSON rules

In many cases, a new test just requires the creation of a rule to define which
fields to inspect:

```java
TestType.NEW_TEST,
new ValidationRule(
    "someArray",
    "someField",
    VocabularyService::getSomething,
    MatchType.EXACT,
    "Some Label"
)
```

### XML rules

In many cases, a new test just requires the creation of a rule to define which
fields to inspect:

```java
TestType.X,
new ValidationRule("//ddi:somePath", false, null, vocab::getX, EXACT, "X")
```

#### Request Format

- **Endpoint**: `POST /assess/test/{test_identifier}`
- **Path Parameter**: `test_identifier` - The identifier of the test to run
- **Content-Type**: `application/json`
- **Request Body**:

```json
  {
    "resource_identifier": "<https:example>"
  }
```

### Return Values

The result is returned in JSON-LD format.
See FAIR Testing Resource Vocabulary,
[Example: Describing a single test result](https://ostrails.github.io/FAIR_testing_resource_vocabulary/release/1.2.0/index-en.html#desc) for details.

## Project Structure

This project uses a Spring Boot multi-module structure.

```text
cessda.fair-tests
│── .mvn
    ├── wrapper
├── api                                         # API module
│   ├── src
│   │   ├── main
│   │   │   └── java/eu/cessda/fairtests        # Contains release source code
│   │   └── test
│   │       └── java/eu/cessda/fairtests        # Contains test source code
│   └── target                                  # Build output directory
│   ├── Dockerfile
│   ├── pom.xml
├── server             # Server implementation module
│   ├── src
│   │   └── main
│   │      ├── java/eu/cessda/fairtests        # Contains release source code
│   │      └── resources/static                # Contains API descriptions
│   └── target                                  # Build output directory
│   ├── Dockerfile
├── CITATION.cff
├── CODE_OF_CONDUCT.md
├── CONTRIBUTING.md
├── CONTRIBUTORS.md
├── Dockerfile
├── Jenkinsfile
├── LICENSE.txt
├── mvnw
├── mvnw.cmd
├── pom.xml                                     # Parent POM
├── README.md
├── README-CdcParser.md
├── README-FairTests.md
├── README-HtmlParser.md
├── README-TestLogic.md
└── README-XmlParser.md                         
```

## How It Works

See [Test Logic](README-TestLogic.md) for details.

## Dependencies

### Runtime Dependencies

- **Spring Boot Starter** - Core Spring Boot framework
- **Spring Boot Starter Web** - Web application support with embedded Tomcat
- **CESSDA FAIR Tests** (`eu.cessda.fairtests:fair-tests:1.0.0-SNAPSHOT`)
- Core FAIR testing library

### Test Dependencies

- **Spring Boot Starter Test** - Testing framework including JUnit, Mockito,
    and Spring Test utilities

### Build Requirements

- **Java 17**
- **Maven 3.6+** (recommended)
- **Spring Boot 4.0.1**

### Maven Dependency Management

This project uses Spring Boot's dependency management to ensure compatible
versions of all Spring dependencies. The parent POM coordinates are:

```xml
<parent>
    <groupId>eu.cessda.fairtests</groupId>
    <artifactId>parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</parent>
```

### Build Plugins

- **Spring Boot Maven Plugin** (4.0.1) - Packages the application as an
    executable JAR with embedded dependencies

## API Endpoints

The application integrates with the following services and hosted vocabularies:

- **OAI-PMH Endpoint**: `https://datacatalogue.cessda.eu/oai-pmh/v0/oai`
- **ELSST Topics API**: `https://skg-if-openapi.cessda.eu/api/topics`
- **Access Rights Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/CessdaAccessRights/1.0.0`
- **Topic Classification Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/TopicClassification/4.2.3?languageVersion=en-4.2.3&format=json`
- **Analysis Unit Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/AnalysisUnit/2.1.3?languageVersion=en-2.1.3&format=json`
- **Time Method Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/TimeMethod/1.2.3?languageVersion=en-1.2.3&format=json`
- **Colection Mode Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/ModeOfCollection/5.0.0?languageVersion=en-5.0.0&format=json`
- **PID Types Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/CessdaPersistentIdentifierTypes/1.0.0`
- **Sampling Procedure Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/SamplingProcedure/2.0.1?languageVersion=en-2.0.1&format=json`

## Building from Source

### Compile only

```bash
mvn clean compile
```

### Run tests

```bash
mvn clean test
```

### Create JAR with dependencies

```bash
mvn clean package
```

This creates two JAR files in the `target/` directory:

- `fair-tests-1.0.0-SNAPSHOT.jar` - Standard JAR
- `fair-tests-1.0.0-SNAPSHOT-jar-with-dependencies.jar`
    Executable JAR with all dependencies

### Generate documentation

```bash
mvn clean install javadoc:javadoc
```

Documentation will be available in `target/site/apidocs/`

## Component documentation

For detailed information about each class, see the individual README
files:

- [README-FairTests.md](README-FairTests.md) — orchestrator, HTTP
  fetching, CLI entry point, format routing.
- [README-XmlParser.md](README-XmlParser.md) — DDI Codebook 2.5 XML
  parsing, XPath-based rule engine, extraction strategies.
- [README-CdcJsonParser.md](README-CdcJsonParser.md) — CDC-schema JSON
  parsing, data-driven rule engine, flexible JSON extraction.
- [README-HtmlParser.md](README-HtmlParser.md) — HTML pre-processor,
  JSON-LD block extraction, delegation to `CdcJsonParser`.

## Contributing

Please read [CONTRIBUTING](CONTRIBUTING.md) for details on our code of conduct,
and the process for submitting pull requests to us.

## Versioning

See [Semantic Versioning](https://semver.org/) for guidance.

## Contributors

You can find the list of contributors in the [CONTRIBUTORS](CONTRIBUTORS.md) file.

## License

See the [LICENSE](LICENSE.txt) file.

## CITING

See the [CITATION](CITATION.cff) file.
