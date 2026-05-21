# XmlParser

## Overview

`XmlParser` implements the `FormatParser` interface and runs FAIR-data
compliance tests against metadata records in DDI Codebook 2.5 XML format,
including records wrapped in an OAI-PMH envelope. It parses the incoming
`InputStream` into a DOM `Document`, evaluates one `TestType` at a time
using precompiled XPath expressions, and returns `PASS`, `FAIL`, or
`INDETERMINATE`.

The class is data-driven: most tests are expressed as `ValidationRule`
records in a static immutable `Map`. Only `ELSST_KEYWORDS` and
`PROVENANCE` require bespoke logic and are handled by dedicated
private methods.

## Supported tests

The following `TestType` values are handled:

- `ACCESS_RIGHTS` — evaluates `//ddi:conditions` using full text content;
  CONTAINS match against approved access rights terms.
- `DDI_ANALYSIS_UNIT` — evaluates `//ddi:anlyUnit` using direct text
  only; EXACT match.
- `DDI_COLLECTION_MODE` — evaluates `//ddi:collMode` using direct text
  only; EXACT match.
- `DDI_SAMPLEPROC` — evaluates `//ddi:sampProc` using full text content;
  CONTAINS match against approved sampling procedure terms.
- `DDI_TIME_METHOD` — evaluates `//ddi:timeMeth` using direct text only;
  EXACT match.
- `ELSST_KEYWORDS` — finds all `//ddi:keyword` elements, filters those
  with `vocab="ELSST"` and a `vocabURI` containing `"elsst"`, then
  validates the collected terms via `VocabularyService`.
- `FAIR_VOCABULARY` — finds all elements carrying a `vocabURI` attribute
  and checks that both `vocab` and `vocabURI` are present and
  non-blank. The first `vocabURI` that resolves successfully over HTTP
  returns `PASS`. Returns `FAIL` if candidates are present but none
  resolve; returns `INDETERMINATE` if no candidates are found or an
  error occurs.
- `FORMAL_KR_LANGUAGE` — checks that the document carries a recognised
  DDI namespace URI (one of the values in `SUPPORTED_DDI_NAMESPACES`).
  Returns `PASS` when the namespace is present, with or without an
  `xsi:schemaLocation` attribute; returns `FAIL` if no recognised DDI
  namespace is detected.
- `GROUNDED_METADATA` — collects all namespace URIs and
  `xsi:schemaLocation` URLs from the DOM, discards common
  infrastructure namespaces (W3C, OAI, LoC, Dublin Core, and any URI
  containing `"xml"`), then attempts HTTP resolution of each remaining
  candidate. Returns `PASS` on the first successful resolution;
  `FAIL` if candidates are found but none resolve; `INDETERMINATE` if
  no candidates remain or an error occurs.
- `PID` — evaluates `//ddi:IDNo`, extracts the `agency` attribute; EXACT
  match against approved PID schemas.
- `PROVENANCE` — checks for the presence of `//ddi:distrbtr`,
  `//ddi:AuthEnty`, or `//ddi:grantNo`; returns `PASS` if any exist.
- `RETRIEVABLE_PROTOCOL` — evaluates `//ddi:IDNo`, constructs a
  resolution URL from the `agency` attribute and element text content
  (DOI → `https://doi.org/`, Handle → `https://hdl.handle.net/`,
  ARK → `https://n2t.net/`), then tests whether the URL uses an open
  protocol and resolves successfully over HTTP. Returns `PASS` on the
  first resolvable identifier; `FAIL` if identifiers are present but
  none resolve; `INDETERMINATE` if no identifiers are found or an
  error occurs.
- `SEARCHABLE` — checks for the simultaneous presence of `//oai:record`,
  `//oai:header`, and `//oai:header/oai:identifier` elements, which
  indicate that the record is wrapped in an OAI-PMH envelope and is
  therefore discoverable. Returns `PASS` if all three are present;
  `FAIL` otherwise.
- `STRUCTURED_METADATA` — checks that the document root element declares
  a supported DDI Codebook namespace URI. Returns `PASS` if a
  supported namespace is detected; `FAIL` otherwise.
- `TOPIC_CLASS` — evaluates `//ddi:topcClas` using full text content;
  EXACT match against approved CESSDA topic classification terms.

Expected field locations:

| Test | DDI-C 2.5 XML location |
| :---: | :---: |
| `ACCESS_RIGHTS` | `/ddi:codeBook/ddi:stdyDscr/ddi:dataAccs/ddi:useStmt/ddi:conditions` |
| `DDI_ANALYSIS_UNIT` | `/ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:sumDscr/ddi:anlyUnit` |
| `DDI_COLLECTION_MODE` | `/ddi:codeBook/ddi:stdyDscr/ddi:method/ddi:dataColl/ddi:collMode` |
| `DDI_SAMPLEPROC` | `/ddi:codeBook/ddi:stdyDscr/ddi:method/ddi:dataColl/ddi:sampProc` |
| `DDI_TIME_METHOD` | `/ddi:codeBook/ddi:stdyDscr/ddi:method/ddi:dataColl/ddi:timeMeth` |
| `ELSST_KEYWORDS` | `/ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:keyword` |
| `ELSST_KEYWORDS` | `/ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:keyword/@vocab` |
| `ELSST_KEYWORDS` | `/ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:keyword/@vocabURI` |
| `ELSST_KEYWORDS` | `/ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:keyword/@xml:lang` |
| `FAIR_VOCABULARY` | `/ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:topcClas/@vocab` |
| `FAIR_VOCABULARY` | `/ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:keyword/@vocab` |
| `FAIR_VOCABULARY` | `/ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:sumDscr/ddi:anlyUnit/ddi:concept/@vocab` |
| `FAIR_VOCABULARY` | `/ddi:codeBook/ddi:stdyDscr/ddi:method/ddi:dataColl/ddi:collMode/ddi:concept/@vocab` |
| `FAIR_VOCABULARY` | `/ddi:codeBook/ddi:stdyDscr/ddi:method/ddi:dataColl/ddi:timeMeth/ddi:concept/@vocab` |
| `FAIR_VOCABULARY` | `/ddi:codeBook/ddi:stdyDscr/ddi:method/ddi:dataColl/ddi:sampProc/ddi:concept/@vocab` |
| `FORMAL_KR_LANGUAGE` | `codeBook/@sourceURL` |
| `GROUNDED_METADATA` | `/ddi:codeBook/ddi:stdyDscr/ddi:citation/ddi:holdings/@URI` |
| `PID` | `/ddi:codeBook/ddi:stdyDscr/ddi:othrStdyMat/ddi:relPubl/ddi:citation/ddi:titlStmt/ddi:IDNo/@agency` |
| `PROVENANCE` | `/ddi:codeBook/ddi:stdyDscr/ddi:citation/ddi:distStmt/ddi:distrbtr` |
| `PROVENANCE` | `/ddi:codeBook/ddi:stdyDscr/ddi:citation/ddi:rspStmt/ddi:AuthEnty` |
| `PROVENANCE` | `/ddi:codeBook/ddi:stdyDscr/ddi:citation/ddi:prodStmt/ddi:grantNo/@agency` |
| `RETRIEVABLE_PROTOCOL` | `/ddi:codeBook/ddi:stdyDscr/ddi:othrStdyMat/ddi:relPubl/ddi:citation/ddi:titlStmt/ddi:IDNo` |
| `RETRIEVABLE_PROTOCOL` | `/ddi:codeBook/ddi:stdyDscr/ddi:othrStdyMat/ddi:relPubl/ddi:citation/ddi:titlStmt/ddi:IDNo/@agency` |
| `SEARCHABLE` | `codeBook/@sourceURL` |
| `STRUCTURED_METADATA` | `/ddi:codeBook/ddi:stdyDscr/ddi:othrStdyMat/ddi:relPubl/ddi:citation/ddi:titlStmt/ddi:titl` |
| `STRUCTURED_METADATA` | `/ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:abstract` |
| `TOPIC_CLASS` | `/ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:topcClas` |

## Internal design

### Enumerations

| Enum | Values | Purpose |
| ---- | ------ | ------- |
| `ExtractionStrategy` | `DIRECT_TEXT`, `FULL_TEXT`, `ATTRIBUTE` | Determines which part of an XML node yields the candidate string. `DIRECT_TEXT` concatenates only immediate text-node children, ignoring nested elements. `FULL_TEXT` uses `getTextContent()` to include all descendant text. `ATTRIBUTE` reads a named attribute from the element. |
| `MatchType` | `EXACT`, `CONTAINS` | Controls how normalised candidate strings are compared against the approved term set. |

### `ValidationRule` record

Each entry in `RULES` is a `ValidationRule` with the following fields:

- `xpath` — the XPath expression used to locate candidate nodes.
- `strategy` — `DIRECT_TEXT`, `FULL_TEXT`, or `ATTRIBUTE`.
- `attribute` — the attribute name to read; only meaningful when
  `strategy` is `ATTRIBUTE`, `null` otherwise.
- `vocabSupplier` — a `Function<VocabularyService, Set<String>>` that
  retrieves the approved terms for this test.
- `matchType` — `EXACT` or `CONTAINS`.
- `label` — a human-readable string used in log messages.

### Rule evaluation engine

`evaluate(ValidationRule, Document, VocabularyService)` is the core
engine:

1. Evaluates the rule's XPath expression to obtain a `NodeList`.
2. Returns `FAIL` immediately if the list is empty.
3. Normalises the approved term set returned by `vocabSupplier`.
4. For each node, calls `extract` to obtain a candidate string, then
   normalises it and tests it with `matches`.
5. Returns `PASS` on the first match; `FAIL` if the list is exhausted.
6. Returns `INDETERMINATE` if an `XPathExpressionException` is thrown.

### Namespace handling

The `XPath` instance is configured at construction time with a
`NamespaceContext` that maps the prefix `ddi` to the DDI Codebook 2.5
namespace URI:

```text
ddi:codebook:2_5
```

All XPath expressions in `RULES` use this `ddi:` prefix.

### Extraction

`extract(Node, ValidationRule)` dispatches on `ExtractionStrategy`:

- `FULL_TEXT` — returns `node.getTextContent().trim()`.
- `DIRECT_TEXT` — calls `directText(Element)`, which iterates child
  nodes and concatenates only those of type `TEXT_NODE`.
- `ATTRIBUTE` — casts the node to `Element` and calls `getAttribute`
  with the rule's attribute name; returns `null` if the node is not an
  `Element`.

### Normalisation

Both candidate values and approved terms are normalised before comparison
by `normalise(String)`:

1. `null` inputs are converted to an empty string.
2. The string is converted to lowercase.
3. It is trimmed.
4. Internal runs of whitespace are collapsed to a single space.

`normaliseSet(Set<String>)` applies `normalise` to every non-null member
of a set.

## Entry point

```java
Result runTest(TestType test, InputStream inputStream,
               VocabularyService vocabulary) throws IOException
```

The method:

1. Parses the stream into a DOM `Document` via `parse(InputStream)`,
   which wraps any `SAXException` as an `IOException`.
2. Dispatches bespoke test types via a `switch` statement:
   `ELSST_KEYWORDS`, `FAIR_VOCABULARY`, `FORMAL_KR_LANGUAGE`,
   `GROUNDED_METADATA`, `PROVENANCE`, `RETRIEVABLE_PROTOCOL`,
   `SEARCHABLE`, and `STRUCTURED_METADATA` each delegate to a
   dedicated private method.
3. Looks up the `ValidationRule` for all other test types; returns
   `INDETERMINATE` if none is found.
4. Calls `evaluate` and returns the result.

## Return values

| Value | Meaning |
| ------- | --------- |
| `PASS` | At least one node contained an approved term; or a required element or resolvable URL was found. |
| `FAIL` | Relevant nodes were found but no approved term matched; or required elements or resolvable URLs were absent. |
| `INDETERMINATE` | Parsing failed, an XPath error occurred, no candidates were found, or no rule exists for the requested test type. |

## Dependencies

- `javax.xml.parsers` and `org.w3c.dom` — DOM parsing.
- `javax.xml.xpath` — XPath evaluation.
- `VocabularyService` — supplies approved term sets and validates ELSST
  keywords.
- `FormatParser`, `TestType`, `Result` — shared interfaces and types
  within the `eu.cessda.fairtests` package.

## Thread safety

The `DocumentBuilder` and `XPath` instances are created once at
construction and reused. Neither class is guaranteed to be thread-safe by
the Java specification; if `XmlParser` instances are shared across
threads, external synchronisation is required. The `RULES` map is
immutable and therefore safe to share.

## Notes

- `DIRECT_TEXT` extraction is preferred for controlled-vocabulary fields
  (e.g. `anlyUnit`, `collMode`) to avoid picking up text from nested
  `p` or `txt` child elements that might contain free-text descriptions.
- The `PROVENANCE` check uses a heuristic: it returns `PASS` if any of
  distributor, author entity, or grant number elements are present,
  without validating their content.
- The `GROUNDED_METADATA` and `FAIR_VOCABULARY` checks make outbound
  HTTP requests at runtime. Network timeouts (connect and read) are
  set to 5 seconds per URL.
- `RETRIEVABLE_PROTOCOL` supports DOI, Handle, and ARK agency values;
  URNs and unrecognised agency values are silently skipped.
- Adding a new vocabulary test requires only a new `ValidationRule`
  entry in `RULES`; no changes to `runTest` or `evaluate` are needed.
  