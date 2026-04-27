# FairTests

## Overview

`FairTests` is the top-level orchestrator for FAIR-data compliance checks.
It is responsible for three things only:

1. Fetching the raw HTTP response from a supplied URL.
2. Sniffing the response format using `FormatSniffer`.
3. Delegating the requested `TestType` to the appropriate `FormatParser`
   implementation.

All format-specific logic lives in the parser classes. `FairTests` itself
contains no vocabulary-matching or field-extraction code.

## Supported formats

| Format | Parser |
|--------|--------|
| XML (DDI Codebook 2.5, optionally OAI-PMH wrapped) | `XmlParser` |
| JSON object (`application/json`) | `CdcJsonParser` |
| HTML containing a JSON-LD `<script>` block | `HtmlParser` |

Any other format detected by `FormatSniffer` causes the method to return
`Result.INDETERMINATE` and log a warning.

## Supported tests

The following `TestType` values may be passed to `runTest`:

- `ACCESS_RIGHTS` — checks for an approved access rights term.
- `PID` — validates the persistent identifier schema.
- `ELSST_KEYWORDS` — checks for ELSST controlled vocabulary keywords.
- `TOPIC_CLASS` — checks for CESSDA topic classification terms.
- `DDI_ANALYSIS_UNIT` — checks for DDI analysis unit vocabulary usage.
- `DDI_COLLECTION_MODE` — checks for DDI collection mode vocabulary
  usage.
- `DDI_TIME_METHOD` — checks for DDI time method vocabulary usage.
- `DDI_SAMPLEPROC` — checks for DDI sampling procedure terms.
- `PROVENANCE` — checks for the presence of provenance information.

## Entry points

### Programmatic API

```java
Result runTest(TestType test, URI url)
```

Fetches the resource at `url`, detects its format, selects the
appropriate parser, and returns one of `PASS`, `FAIL`, or
`INDETERMINATE`. An `IOException` during fetch or parse is caught
internally and causes `INDETERMINATE` to be returned.

A set of convenience methods delegates to `runTest` and preserves the
original API surface:

```java
Result containsApprovedAccessRights(URI url)
Result containsApprovedPid(URI url)
Result containsElsstKeywords(URI url)
Result containsCessdaTopicClassificationTerms(URI url)
Result containsDdiAnalysisUnit(URI url)
Result containsDdiCollectionMode(URI url)
Result containsDdiTimeMethod(URI url)
Result containsDdiSamplingProcedureTerms(URI url)
Result containsProvenanceInformation(URI url)
```

### Command-line interface

```text
FairTests <test-type> <url>
```

`<test-type>` must be the `getTestName()` string of one of the
`TestType` enum constants. If the argument list is too short or the test
type is unrecognised, usage help is printed and the process exits with
code `1`.

On completion the result is written to standard output and the exit code
is `0` for `PASS` or `1` for any other result.

## HTTP behaviour

The HTTP request is built with the following characteristics:

- `Accept` header: `application/xml, application/json, text/xml, */*`
- Timeout: 30 seconds
- Method: `GET`

A non-200 status code causes the method to return `INDETERMINATE` and
log the status at `SEVERE` level. An `InterruptedException` during the
send is re-thrown as an `IllegalStateException` after restoring the
interrupt flag.

## Return values

| Value | Meaning |
|-------|---------|
| `PASS` | The record meets the criteria for the requested test. |
| `FAIL` | The record does not meet the criteria for the requested test. |
| `INDETERMINATE` | An error occurred, the HTTP response was not 200, or the format is unsupported. |

## Dependencies

- `XmlParser`, `CdcJsonParser`, `HtmlParser` — format-specific parsers,
  each held as a private final field.
- `FormatSniffer` — detects the response format and provides a rewound
  stream safe to pass to any parser.
- `VocabularyService` — shared service instance injected into each parser
  call.
- `java.net.http.HttpClient` — used for all HTTP communication.
- `org.apache.commons.cli` — parses the command-line arguments.

## Notes

- `FairTests` instances are not thread-safe by default because the
  `DocumentBuilder` inside `XmlParser` is not thread-safe. Create a
  separate `FairTests` instance per thread, or add external
  synchronisation if sharing is required.
- The `JSON_ARRAY` format detected by `FormatSniffer` is not currently
  mapped to a parser and will result in `INDETERMINATE`. This can be
  addressed by adding a `JSON_ARRAY` case to the `switch` expression in
  `runTest`.
- The `Accept` header requests XML and JSON in preference to other
  content types, but the final format decision is made by `FormatSniffer`
  based on the actual response body, not the `Content-Type` header.
