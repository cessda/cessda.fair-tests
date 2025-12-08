# CESSDA FAIR Tests

[![SQAaaS badge](https://github.com/EOSC-synergy/SQAaaS/raw/master/badges/badges_150x116/badge_software_silver.png)](https://api.eu.badgr.io/public/assertions/SGiodTQYQPGTwKuZbpUiXA "SQAaaS silver badge achieved")

This repository contains the source code for CESSDA community-specific FAIR
tests that validate data catalogue records against FAIR data principles.

## Overview

The FairTests utility provides four validation tests for CESSDA data catalogue
records:

### 1. Access Rights Validation

Checks whether records contain approved Access Rights terms from the CESSDA
vocabulary (e.g., "Open", "Restricted").

### 2. PID Schema Validation

Validates that records use approved Persistent Identifier schemas from the
CESSDA vocabulary (e.g., DOI, Handle, URN, ARK).

### 3. ELSST Keyword Validation

Verifies that records contain keywords from the ELSST (European Language Social
Science Thesaurus) controlled vocabulary.

The ELSST test implements strict validation requiring keywords to meet
**ALL three conditions** simultaneously:

1. The DDI keyword element has `vocab="ELSST"`
1. The DDI keyword element has a `vocabURI` attribute containing
`"elsst.cessda.eu"`
1. The keyword text matches a label from the ELSST Topics API

A record passes if at least one keyword meets all the specified validation
criteria.

### 4. DDI Recommended Vocabularies

Verifies that the record uses the following recommended DDI vocabularies in the
appropriate attributes:

1. DDI Analysis Unit
1. DDI Time Method
1. DDI Mode of Collection

A record passes if it contains at least one recommended DDI controlled
vocabulary.

### 5. DDI Optional Vocabulary (Sampling Procedure)

Verifies that the record uses the DDI Sampling Procedure vocabulary in the
appropriate attribute.
A record passes if it contains at least one term from the vocabulary.

### 6. CESSDA Topic Classification Vocabulary

Verifies that the record uses the Topic Classification vocabulary in the
appropriate attribute.
A record passes if it contains at least one term from the vocabulary.

## Prerequisites

Java 21 or greater is required to build and run this application.

## Quick Start

1. Check prerequisites and install any required software.
1. Clone the repository to your local workspace.
1. Build the application using `mvn clean verify`.
1. Run the application using one of the following methods:

### Using Maven Exec Plugin

```bash
mvn -Dexec.mainClass=cessda.fairtests.FairTests \
    -Dexec.args="<test-type> <CDC URL>" \
    org.codehaus.mojo:exec-maven-plugin:3.1.0:java
```

### Using Executable JAR

```bash
mvn clean package
java -jar target/fair-tests-1.0.0-jar-with-dependencies.jar <test-type> \
    <CDC URL>
```

## Example Usage

### Test Access Rights

```bash
java -jar target/fair-tests-1.0.0-jar-with-dependencies.jar access-rights \
    "https://datacatalogue.cessda.eu/detail/abc123?lang=en"
```

### Test PID Schema

```bash
java -jar target/fair-tests-1.0.0-jar-with-dependencies.jar pid \
    "https://datacatalogue.cessda.eu/detail/abc123?lang=en"
```

### Test ELSST Keywords

```bash
java -jar target/fair-tests-1.0.0-jar-with-dependencies.jar elsst-keywords \
    "https://datacatalogue.cessda.eu/detail/abc123?lang=en"
```

### Test recommended DDI vocabularies

```bash
java -jar target/fair-tests-1.0.0-jar-with-dependencies.jar ddi-vocabs \
    "https://datacatalogue.cessda.eu/detail/abc123?lang=en"
```

### Test optional DDI Sampling Procedure vocabulary

```bash
java -jar target/fair-tests-1.0.0-jar-with-dependencies.jar ddi-sampleproc \
    "https://datacatalogue.cessda.eu/detail/abc123?lang=en"
```

### Test CESSDA Topic Classification vocabulary

```bash
java -jar target/fair-tests-1.0.0-jar-with-dependencies.jar topic-class \
    "https://datacatalogue.cessda.eu/detail/abc123?lang=en"
```

### Test Type Options

- `access-rights` - Validate Access Rights terms
- `pid` - Validate Persistent Identifier schemas
- `elsst-keywords` - Validate ELSST controlled vocabulary keywords
- `ddi-vocabs` - Validate recommended DDI vocabularies
- `ddi-sampleproc` - Validate optional DDI Sampling Procedure vocabulary
- `topic-class` - Validate CESSDA Topic Classification vocabulary

### URL Requirements

The CDC URL must:

- Include the `/detail/{identifier}` path segment
- Optionally include a `lang` query parameter (e.g., `?lang=en`) for ELSST API
validation

### Return Values

- **Exit code 0** ("pass"): Record meets the validation criteria
- **Exit code 1** ("fail" or "indeterminate"): Record does not meet criteria,
or an error occurred

## Project Structure

This project uses the standard Maven project structure.

```text
<ROOT>
├── pom.xml
├── Dockerfile
├── Description.md      # Detailed technical documentation
├── README.md          # This file
├── src                # Contains all source code and assets for the application.
|   ├── main
|   |   ├── java       # Contains release source code of the application.
|   |   └── resources  # Contains release resources assets.
|   └── test
|       ├── java       # Contains test source code.
|       └── resources  # Contains test resource assets.
└── target             # The output directory for the build.
```

## How It Works

### Access Rights Validation

The test:

1. Fetches DDI metadata via OAI-PMH endpoint
1. Extracts values from `typeOfAccess` elements using XPath
1. Retrieves list of terms from CESSDA vocabulary API
1. Compares each attribute value against list of terms
1. Returns "pass" if any approved term is found

### PID Schema Validation

The test:

1. Fetches DDI metadata via OAI-PMH endpoint
1. Extracts `IDNo` elements with `agency` attributes using XPath
1. Retrieves list of PID terms from CESSDA vocabulary API
1. Compares each attribute value against list of terms
1. Returns "pass" if any term matches any value

### ELSST Keyword Validation

The test uses a two-phase validation approach:

#### Phase 1: Attribute Validation

The test examines all `<keyword>` elements in the DDI metadata and identifies
candidates that have:

- `vocab` attribute equal to `"ELSST"` **AND**
- `vocabURI` attribute containing `"elsst.cessda.eu"`

#### Phase 2: API Validation

For candidate keywords from Phase 1, the test:

- Queries the ELSST Topics API with the keyword text and language code
- Compares the keyword text (case-insensitive) against returned labels
- Returns "pass" immediately when a match is found

Keywords missing either required attribute are excluded from validation,
even if they might match ELSST API labels.

### Approved DDI Vocabularies Validation

The test:

1. Fetches DDI metadata via OAI-PMH endpoint
1. For each of Analysis Unit, Time Method, Mode of Collection:
    1. Extracts relevant attributes using XPath
    1. Retrieves list of terms from CESSDA vocabulary API
    1. Compares each attribute value against the list of terms
1. Returns "pass" if any term matches any value

### DDI Sampling Procedure Vocabulary Validation

The test:

1. Fetches DDI metadata via OAI-PMH endpoint
1. Extracts `sampProc` attribute values using XPath
1. Retrieves list of Sampling Procedure terms from CESSDA vocabulary API
1. Compares each attribute value against list of terms
1. Returns "pass" if any term matches any value

### CESSDA Topic Classification Vocabulary Validation

The test:

1. Fetches DDI metadata via OAI-PMH endpoint
1. Extracts `topcClas` attribute values using XPath
1. Retrieves list of Topic Classification terms from CESSDA vocabulary API
1. Compares each attribute value against list of terms
1. Returns "pass" if any term matches any value

## Technical Details

- **Language**: Java 21
- **Dependencies**: Jackson (JSON parsing), Java HTTP Client, javax.xml
    (XML/XPath processing)
- **Concurrency**: Uses virtual threads for parallel ELSST API queries
- **Timeouts**: 10-second connect timeout, 30-second request timeout
- **Standards**: DDI 2.5 metadata via OAI-PMH, CESSDA controlled vocabularies,
    DDI controlled vocabularies
- **Caching**: Vocabulary terms are cached to reduce API calls

### Shared Components

The consolidated FairTests class eliminates code duplication by sharing:

- HTTP client and request handling
- XML parsing and XPath evaluation
- Document fetching from OAI-PMH endpoint
- URL parsing and record identifier extraction
- Vocabulary API integration
- Logging utilities

## API Endpoints

The application integrates with the following services and hosted vocabularies:

- **OAI-PMH Endpoint**: `https://datacatalogue.cessda.eu/oai-pmh/v0/oai`
- **ELSST Topics API**: `https://skg-if-openapi.cessda.eu/api/topics`
- **Access Rights Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/CessdaAccessRights/1.0.0`
- **Topic Classification Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/TopicClassification/4.0.0?languageVersion=en-4.0.0&format=json`
- **Analysis Unit Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/AnalysisUnit/1.2.0?languageVersion=en-1.2.0&format=json`
- **Time Method Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/TimeMethod/1.2.1?languageVersion=en-1.2.1&format=json`
- **Colection Mode Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/ModeOfCollection/4.0.0?languageVersion=en-4.0.0&format=json`
- **PID Types Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/CessdaPersistentIdentifierTypes/1.0.0`
- **Sampling Procedure Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/SamplingProcedure/2.0.0?languageVersion=en-2.0.0&format=json`

## Description

See the [Description](Description.md) file for comprehensive technical
documentation including:

- Detailed API integration information
- XML processing and XPath expressions
- Error handling and logging
- Thread safety considerations
- Complete input/output specifications
- Vocabulary caching strategies

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

- `fair-tests-1.0.0.jar` - Standard JAR
- `fair-tests-1.0.0-jar-with-dependencies.jar` - Executable JAR with all
    dependencies

### Generate documentation

```bash
mvn javadoc:javadoc
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
