# CdcJsonParser

## Overview

`CdcJsonParser` implements the `FormatParser` interface and is responsible
for running FAIR-data compliance tests against metadata records expressed
as CDC-schema JSON. It accepts an `InputStream` containing a JSON object,
parses it using Jackson, and evaluates one `TestType` at a time, returning
`PASS`, `FAIL`, or `INDETERMINATE`.

The class is designed around a data-driven rule engine. Most tests are
expressed as `ValidationRule` records held in an immutable `Map`. Five
tests (`ELSST_KEYWORDS`, `FAIR_VOCABULARY`, `GROUNDED_METADATA`,
`RETRIEVABLE_PROTOCOL`, and `SEARCHABLE`) require bespoke logic and are
handled by dedicated private methods.

## Supported tests

The following `TestType` values are handled:

- `ACCESS_RIGHTS` — checks the `dataAccess` field for an approved access
  rights term (CONTAINS match).
- `DDI_ANALYSIS_UNIT` — checks the `unitTypes` field (EXACT match).
- `DDI_COLLECTION_MODE` — checks the `typeOfModeOfCollections` field
  (EXACT match).
- `DDI_SAMPLEPROC` — checks `samplingProcedureFreeTexts` for an approved
  sampling procedure term (CONTAINS match, because this field holds free
  text rather than controlled vocabulary terms).
- `DDI_TIME_METHOD` — checks the `typeOfTimeMethods` field (EXACT match).
- `ELSST_KEYWORDS` — checks the `keywords` array for entries where
  `vocab` equals `"ELSST"` and `vocabUri` contains `"elsst"`, then
  validates the collected terms against the ELSST vocabulary via
  `VocabularyService`. Validation is attempted for each language code in
  `langAvailableIn`; the first successful match returns `PASS`.
- `FAIR_VOCABULARY` — scans the `classifications`, `keywords`,
  `unitTypes`, `typeOfModeOfCollections`, and `typeOfTimeMethods` arrays
  for entries where both `vocab` and `vocabUri` are non-blank. The first
  `vocabUri` that resolves successfully over HTTP returns `PASS`.
- `FORMAL_KR_LANGUAGE` — checks for the presence of `studyXmlSourceUrl`,
  which indicates that the record originates from a formal DDI XML source
  accessible via OAI-PMH (PRESENCE_ANY).
- `GROUNDED_METADATA` — attempts to resolve the `studyUrl` field over
  HTTP. Returns `PASS` if the URL responds with HTTP 2xx or 3xx.
- `PID` — checks the `pidStudies` field for an approved PID schema
  (EXACT match against the `agency` value).
- `PROVENANCE` — checks for the presence of any non-blank value across
  `publisher.publisher`, `creators[].name`, or `funding[].agency`
  (PRESENCE_ANY).
- `RETRIEVABLE_PROTOCOL` — iterates `pidStudies`, constructs a resolution
  URL from the `agency` and `pid` fields (DOI → `https://doi.org/`,
  Handle → `https://hdl.handle.net/`, ARK → `https://n2t.net/`), and
  returns `PASS` on the first URL that resolves over HTTP. Scheme
  prefixes (e.g. `"doi:"`) are stripped from `pid` values before the URL
  is built.
- `SEARCHABLE` — checks that `studyXmlSourceUrl` is non-blank and
  contains both `"oai"` and `"GetRecord"`, indicating the record is
  exposed via an OAI-PMH endpoint and is discoverable by harvesters.
- `STRUCTURED_METADATA` — checks for the presence of both `titleStudy`
  and `abstract`, indicating a structurally complete CDC record
  (PRESENCE_ANY).
- `TOPIC_CLASS` — checks the `classifications` field for an approved
  CESSDA topic classification term (EXACT match).

Expected field locations:

| Test                 | JSON location                                              |
| -------------------- | ---------------------------------------------------------- |
| `ACCESS_RIGHTS`      | `dataAccess`                                               |
| `DDI_ANALYSIS_UNIT`  | `unitTypes[*].term`                                        |
| `DDI_COLLECTION_MODE`| `typeOfModeOfCollections[*].term`                          |
| `DDI_SAMPLEPROC`     | `samplingProcedureFreeTexts`                               |
| `DDI_TIME_METHOD`    | `typeOfTimeMethods[*].term`                                |
| `ELSST_KEYWORDS`     | `keywords[*].term`, `keywords[*].vocab`,                   |
|                      | `keywords[*].vocabUri`, `langAvailableIn[*]`               |
| `FAIR_VOCABULARY`    | `classifications[*].vocabUri`, `keywords[*].vocabUri`,     |
|                      | `unitTypes[*].vocabUri`, `typeOfModeOfCollections[*].vocabUri`, |
|                      | `typeOfTimeMethods[*].vocabUri`                            |
| `FORMAL_KR_LANGUAGE` | `studyXmlSourceUrl`                                        |
| `GROUNDED_METADATA`  | `studyUrl`                                                 |
| `PID`                | `pidStudies[*].agency`                                     |
| `PROVENANCE`         | `publisher.publisher`, `creators[*].name`,                 |
|                      | `funding[*].agency`                                        |
| `RETRIEVABLE_PROTOCOL` | `pidStudies[*].agency`, `pidStudies[*].pid`              |
| `SEARCHABLE`         | `studyXmlSourceUrl`                                        |
| `STRUCTURED_METADATA`| `titleStudy`, `abstract`                                   |
| `TOPIC_CLASS`        | `classifications[*].term`                                  |

## Internal design

### Enumerations

| Enum | Values | Purpose |
|------|--------|---------|
| `MatchType` | `EXACT`, `CONTAINS` | Controls how extracted values are compared against approved vocabulary terms. `CONTAINS` allows for values that embed a controlled term alongside free text (e.g. `"Restricted — see documentation"`). |
| `RuleType` | `VOCAB_MATCH`, `PRESENCE_ANY` | Distinguishes rules that validate against a vocabulary from rules that simply confirm a value is present. |

### `ValidationRule` record

Each entry in the `rules` map is a `ValidationRule` with the following
fields:

- `type` — `VOCAB_MATCH` or `PRESENCE_ANY`.
- `fields` — one or more dot-notation JSON paths to extract values from.
- `vocabSupplier` — a `Function<VocabularyService, Set<String>>` that
  retrieves the approved terms for this test; `null` for
  `PRESENCE_ANY` rules.
- `matchType` — `EXACT` or `CONTAINS`; `null` for `PRESENCE_ANY` rules.
- `label` — a human-readable string used in log messages.

### Rule evaluation engine

`evaluateRule(JsonNode, ValidationRule, VocabularyService)` is the core
engine:

1. Calls `extractMulti` to gather all candidate strings from the paths
   listed in the rule.
2. For `PRESENCE_ANY` rules, returns `PASS` if any non-blank value is
   found.
3. For `VOCAB_MATCH` rules, normalises both the candidates and the
   approved set, then tests each candidate with `matches()`.
4. Returns `PASS` on the first match; `FAIL` if the list is exhausted
   without a match.

### Extraction strategy

Three private methods work together to extract values from arbitrarily
structured JSON:

- `extractMulti` — iterates over the list of paths in a rule and
  aggregates results.
- `extractPath` — splits a dot-notation path into parts and delegates
  to `extractRecursive`.
- `extractRecursive` — traverses the JSON tree, transparently descending
  into arrays at any level without consuming a path segment.
- `extractValue` — leaf extractor that handles plain text nodes, arrays
  (by flattening), and objects (by probing for common keys: `value`,
  `term`, `label`, `name`, `agency`, `publisher`).

### Normalisation

Both candidate values and approved terms are normalised before comparison
by `normalise(String)`:

1. `null` inputs are converted to an empty string.
2. The string is trimmed.
3. It is converted to lowercase.
4. Internal runs of whitespace are collapsed to a single space.

`normaliseSet(Set<String>)` applies `normalise` to every member of a set.

## Entry point

```java
Result runTest(TestType test, InputStream inputStream,
               VocabularyService vocabulary) throws IOException
```

The method:

1. Parses the stream into a `JsonNode` using Jackson's `ObjectMapper`.
2. Returns `INDETERMINATE` immediately if the root object has no `id`
   field.
3. Dispatches bespoke test types via a `switch` statement:
   `ELSST_KEYWORDS`, `FAIR_VOCABULARY`, `GROUNDED_METADATA`,
   `RETRIEVABLE_PROTOCOL`, and `SEARCHABLE` each delegate to a
   dedicated private method.
4. Looks up the `ValidationRule` for all other test types; returns
   `INDETERMINATE` if no rule is defined.
5. Calls `evaluateRule` and returns the result.

## Return values

| Value | Meaning |
|-------|---------|
| `PASS` | At least one approved term, non-blank value, or resolvable URL was found. |
| `FAIL` | The relevant field(s) were present but no approved term matched, or no URL resolved. |
| `INDETERMINATE` | The JSON could not be parsed, lacked an `id` field, no candidates were found, or no rule exists for the requested test type. |

## Dependencies

- `com.fasterxml.jackson.databind` — JSON parsing (`ObjectMapper`,
  `JsonNode`).
- `VocabularyService` — supplies approved term sets and validates ELSST
  keywords.
- `FormatParser`, `TestType`, `Result` — shared interfaces and types
  within the `eu.cessda.fairtests` package.

## Notes

- The `RULES` map is a `static final Map.ofEntries(…)`; the class is
  therefore effectively stateless after construction and safe to share
  across threads.
- The `samplingProcedureFreeTexts` field uses a `CONTAINS` match because
  the spec field (`typeOfSamplingProcedures`) is not consistently used in
  practice and the actual field holds free text rather than controlled
  vocabulary terms.
- The `FAIR_VOCABULARY`, `GROUNDED_METADATA`, and
  `RETRIEVABLE_PROTOCOL` tests make outbound HTTP requests at runtime.
  Both connect and read timeouts are set to 5 seconds per URL.
- `RETRIEVABLE_PROTOCOL` strips scheme prefixes (e.g. `"doi:"`) from
  `pid` values before building the resolution URL. URNs and unrecognised
  agency values are silently skipped.
- Adding a new vocabulary test requires only a new `ValidationRule` entry
  in `RULES`; no changes to `runTest` or `evaluateRule` are needed.
  