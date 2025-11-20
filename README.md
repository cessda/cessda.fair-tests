# CESSDA FAIR Tests

This repository contains the source code for CESSDA community-specific FAIR tests that validate data catalogue records against FAIR data principles.

## Overview

The FairTests utility provides three validation tests for CESSDA data catalogue records:

### 1. Access Rights Validation

Checks whether records contain approved Access Rights terms from the CESSDA vocabulary (e.g., "Open", "Restricted").

### 2. PID Schema Validation

Validates that records use approved Persistent Identifier schemas from the CESSDA vocabulary (e.g., DOI, Handle, URN, ARK).

### 3. ELSST Keyword Validation

Verifies that records contain keywords from the ELSST (European Language Social Science Thesaurus) controlled vocabulary.

The ELSST test implements strict validation requiring keywords to meet **ALL three conditions** simultaneously:

1. The DDI keyword element has `vocab="ELSST"`
2. The DDI keyword element has a `vocabURI` attribute containing `"elsst.cessda.eu"`
3. The keyword text matches a label from the ELSST Topics API

A record passes if at least one keyword meets all the specified validation criteria.

## Prerequisites

Java 21 or greater is required to build and run this application.

## Quick Start

1. Check prerequisites and install any required software.
2. Clone the repository to your local workspace.
3. Build the application using `mvn clean verify`.
4. Run the application using one of the following methods:

### Using Maven Exec Plugin

```bash
mvn -Dexec.mainClass=cessda.fairtests.FairTests \
    -Dexec.args="<test-type> <CDC URL>" \
    org.codehaus.mojo:exec-maven-plugin:3.1.0:java
```

### Using Executable JAR

```bash
mvn clean package
java -jar target/fair-tests-1.0.0-jar-with-dependencies.jar <test-type> <CDC URL>
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

### Test Type Options

- `access-rights` - Validate Access Rights terms
- `pid` - Validate Persistent Identifier schemas
- `elsst-keywords` - Validate ELSST controlled vocabulary keywords

### URL Requirements

The CDC URL must:

- Include the `/detail/{identifier}` path segment
- Optionally include a `lang` query parameter (e.g., `?lang=en`) for ELSST API validation

### Return Values

- **Exit code 0** ("pass"): Record meets the validation criteria
- **Exit code 1** ("fail" or "indeterminate"): Record does not meet criteria, or an error occurred

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
2. Extracts values from `typeOfAccess` elements using XPath
3. Retrieves approved terms from CESSDA vocabulary API
4. Compares extracted values against approved list
5. Returns "pass" if any approved term is found

### PID Schema Validation

The test:

1. Fetches DDI metadata via OAI-PMH endpoint
2. Extracts `IDNo` elements with `agency` attributes using XPath
3. Retrieves approved PID schemas from CESSDA vocabulary API
4. Compares agency values against approved schemas
5. Returns "pass" if any approved schema is found

### ELSST Keyword Validation

The test uses a two-phase validation approach:

#### Phase 1: Attribute Validation

The test examines all `<keyword>` elements in the DDI metadata and identifies candidates that have:

- `vocab` attribute equal to `"ELSST"` **AND**
- `vocabURI` attribute containing `"elsst.cessda.eu"`

#### Phase 2: API Validation

For candidate keywords from Phase 1, the test:

- Queries the ELSST Topics API with the keyword text and language code
- Compares the keyword text (case-insensitive) against returned labels
- Returns "pass" immediately when a match is found

Keywords missing either required attribute are excluded from validation, even if they might match ELSST API labels.

## Technical Details

- **Language**: Java 21
- **Dependencies**: Jackson (JSON parsing), Java HTTP Client, javax.xml (XML/XPath processing)
- **Concurrency**: Uses virtual threads for parallel ELSST API queries
- **Timeouts**: 10-second connect timeout, 30-second request timeout
- **Standards**: DDI 2.5 metadata via OAI-PMH, CESSDA controlled vocabularies
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

The application integrates with the following CESSDA services:

- **OAI-PMH Endpoint**: `https://datacatalogue.cessda.eu/oai-pmh/v0/oai`
- **Access Rights Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/CessdaAccessRights/1.0.0`
- **PID Types Vocabulary**: `https://vocabularies.cessda.eu/v2/vocabularies/CessdaPersistentIdentifierTypes/1.0.0`
- **ELSST Topics API**: `https://skg-if-openapi.cessda.eu/api/topics`

## Description

See the [Description](Description.md) file for comprehensive technical documentation including:

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
- `fair-tests-1.0.0-jar-with-dependencies.jar` - Executable JAR with all dependencies

### Generate documentation

```bash
mvn javadoc:javadoc
```

Documentation will be available in `target/site/apidocs/`

## Contributing

Please read [CONTRIBUTING](CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## Versioning

See [Semantic Versioning](https://semver.org/) for guidance.

## Contributors

You can find the list of contributors in the [CONTRIBUTORS](CONTRIBUTORS.md) file.

## License

See the [LICENSE](LICENSE.txt) file.

## CITING

See the [CITATION](CITATION.cff) file.
