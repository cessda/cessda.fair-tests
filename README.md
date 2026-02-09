# CESSDA FAIR Tests

[![SQAaaS badge](https://github.com/EOSC-synergy/SQAaaS/raw/master/badges/badges_150x116/badge_software_silver.png)](https://api.eu.badgr.io/public/assertions/SGiodTQYQPGTwKuZbpUiXA "SQAaaS silver badge achieved")

This repository contains the source code for CESSDA community-specific FAIR
tests that validate DDI2.5 XML records against FAIR data principles.

## Overview

- The FairTests utility provides validation tests for DDI2.5 records
- Controlled vocabulary terms are fetched from the CESSDA vocabulary service
- Terms are cached in memory to improve performance and reduce API calls
- If a vocabulary service is unavailable, some tests fall back to default values
- Empty or whitespace-only values are treated as absent
- All text content is trimmed of leading and trailing whitespace before
    comparison

### 1. Access Rights Validation

Checks whether records contain approved Access Rights terms from the CESSDA
vocabulary (e.g., "Open", "Restricted").

### 2. PID Schema Validation

Validates that records use approved Persistent Identifier schemas from the
CESSDA vocabulary (e.g., DOI, Handle, URN, ARK).

### 3. ELSST Keyword Validation

Verifies that records contain keywords from the ELSST
(European Language Social Science Thesaurus) controlled vocabulary.

### 4. CESSDA Topic Classification Vocabulary

Verifies that the record uses the Topic Classification vocabulary in the
appropriate attribute.

### 5. DDI Analysis Unit

Verifies that the record uses DDI Analysis Unit vocabulary in the
appropriate attributes.

### 6. DDI Collection Mode

Verifies that the record uses the DDI Collection Mode vocabulary in the
appropriate attribute.

### 7. DDI Time Method

Verifies that the record uses the DDI Time Method vocabulary in the
appropriate attribute.

### 8. DDI Sampling Procedure

Verifies that the record uses the DDI Sampling Procedure vocabulary in the
appropriate attribute.

### 9. Provenance Information

Verifies that the record contains provenance metadata elements.

## Prerequisites

Java 21 or greater is required to build and run this application.

## Quick Start

1. Check prerequisites and install any required software.
1. Clone the repository to your local workspace.
1. Build the application using `mvn clean verify`.
1. Run the application using one of the following methods:

### Using Maven Exec Plugin

```bash
mvn -Dexec.mainClass=eu.cessda.fairtests.FairTests \
    -Dexec.args="<test-type> <CDC URL>" \
    org.codehaus.mojo:exec-maven-plugin:3.1.0:java
```

### Using Executable JAR

Run a FAIR test by making a POST request to the `/assess/test` endpoint:

```bash
curl -X POST https://fair-tests.cessda.eu/assess/test/<test-name> \
  -H "Content-Type: application/json" \
  -d '{
    "resource_identifier":"<https://example>"}'
```

## Example Usage

### Test Access Rights

```bash
curl -X POST https://fair-tests.cessda.eu/assess/test/access-rights \
  -H "Content-Type: application/json" \
  -d '{
    "resource_identifier":"https://datacatalogue.cessda.eu/oai-pmh/v0/oai?verb=GetRecord&metadataPrefix=oai_ddi25&identifier=1234567890"}'
```

### Test PID Schema

```bash
curl -X POST https://fair-tests.cessda.eu/assess/test/pid \
  -H "Content-Type: application/json" \
  -d '{
    "resource_identifier":"https://datacatalogue.cessda.eu/oai-pmh/v0/oai?verb=GetRecord&metadataPrefix=oai_ddi25&identifier=1234567890"}'
```

### Test ELSST Keywords

```bash
curl -X POST https://fair-tests.cessda.eu/assess/test/elsst-keywords \
  -H "Content-Type: application/json" \
  -d '{
    "resource_identifier":"https://datacatalogue.cessda.eu/oai-pmh/v0/oai?verb=GetRecord&metadataPrefix=oai_ddi25&identifier=1234567890"}'
```

### Test CESSDA Topic Classification vocabulary

```bash
curl -X POST https://fair-tests.cessda.eu/assess/test/topic-class \
  -H "Content-Type: application/json" \
  -d '{
    "resource_identifier":"https://datacatalogue.cessda.eu/oai-pmh/v0/oai?verb=GetRecord&metadataPrefix=oai_ddi25&identifier=1234567890"}'
```

### Test DDI Analysis Unit vocabulary

```bash
curl -X POST https://fair-tests.cessda.eu/assess/test/ddi-analysis-unit \
  -H "Content-Type: application/json" \
  -d '{
    "resource_identifier":"https://datacatalogue.cessda.eu/oai-pmh/v0/oai?verb=GetRecord&metadataPrefix=oai_ddi25&identifier=1234567890"}'
```

### Test DDI Collection Mode vocabulary

```bash
curl -X POST https://fair-tests.cessda.eu/assess/test/ddi-collection-mode \
  -H "Content-Type: application/json" \
  -d '{
    "resource_identifier":"https://datacatalogue.cessda.eu/oai-pmh/v0/oai?verb=GetRecord&metadataPrefix=oai_ddi25&identifier=1234567890"}'
```

### Test DDI Time Method vocabulary

```bash
curl -X POST https://fair-tests.cessda.eu/assess/test/ddi-time-method \
  -H "Content-Type: application/json" \
  -d '{
    "resource_identifier":"https://datacatalogue.cessda.eu/oai-pmh/v0/oai?verb=GetRecord&metadataPrefix=oai_ddi25&identifier=1234567890"}'
```

### Test DDI Sampling Procedure vocabulary

```bash
curl -X POST https://fair-tests.cessda.eu/assess/test/ddi-sampleproc \
  -H "Content-Type: application/json" \
  -d '{
    "resource_identifier":"https://datacatalogue.cessda.eu/oai-pmh/v0/oai?verb=GetRecord&metadataPrefix=oai_ddi25&identifier=1234567890"}'
```

### Test Provenance information

```bash
curl -X POST https://fair-tests.cessda.eu/assess/test/provenance \
  -H "Content-Type: application/json" \
  -d '{
    "resource_identifier":"https://datacatalogue.cessda.eu/oai-pmh/v0/oai?verb=GetRecord&metadataPrefix=oai_ddi25&identifier=1234567890"}'
```

### Test Type Options

- `access-rights` - Validate Access Rights terms
- `pid` - Validate Persistent Identifier schemas
- `elsst-keywords` - Validate ELSST use of controlled vocabulary keywords
- `topic-class` - Validate use of CESSDA Topic Classification vocabulary terms
- `ddi-analysis-unit` - Validate use of DDI Analysis Unit vocabulary terms
- `ddi-collection-mode` - Validate use of DDI Collection Mode vocabulary terms
- `ddi-time-method` - Validate use of DDI Time Method vocabulary terms
- `ddi-sampleproc` - Validate use of DDI Sampling Procedure vocabulary terms
- `provenance` - Validate Provenance

### URL Requirements

The URL must return DDI2.5 XML

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

The result is returned in JSON-LD format:

```text
{"value":"pass"}: Record meets the validation criteria
{"value":"fail"}: Record does not meet the validation criteria
{"value":"indeterminate"}: An error occured and the record could not be assessed
```

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
└── Test_Logic.md                               # Contains detailled description of how the tests work
```

## How It Works

See [Test Logic](Test_Logic.md) for details.

## Technical Details

- **Language**: Java 17
- **Concurrency**: Uses virtual threads for parallel ELSST API queries
- **Timeouts**: 10-second connect timeout, 30-second request timeout
- **Standards**: DDI 2.5 metadata via OAI-PMH, CESSDA controlled vocabularies,
    DDI controlled vocabularies
- **Caching**: Vocabulary terms are cached to reduce API calls

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

### Shared Components

The consolidated FairTests class eliminates code duplication by sharing:

- HTTP client and request handling
- XML parsing and XPath evaluation
- Document fetching from OAI-PMH endpoint
- URL parsing and record identifier extraction
- Vocabulary API integration

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

## Adding a new test

As well as adding the methods to run the new test to
[FairTests.java](api/src/main/java/eu/cessda/fairtests/FairTests.java) you need to:

- extend the runTest switch statement
- extend the [TestType](api/src/main/java/eu/cessda/fairtests/TestType.java) enumeration
- add Unit tests in [FairTestsTests](api/src/test/java/eu/cessda/fairtests/FairTestsTest.java)
- add an API descriptor file in the resources/static directory

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
