# XmlParser

## Overview

`XmlParser` implements the `FormatParser` interface and runs FAIR-data
compliance tests against metadata records in DDI Codebook 2.5 XML format,
including records wrapped in an OAI-PMH envelope. It parses the incoming
`InputStream` into a DOM `Document`, evaluates one `TestType` at a time
using precompiled XPath expressions, and returns `PASS`, `FAIL`, or
`INDETERMINATE`.

The class is data-driven: most tests are expressed as `ValidationRule`
records in a static immutable `Map`. Only `ELSST_KEYWORDS` and `PROVENANCE` require bespoke logic and are handled by dedicated
private methods.

## Supported tests

The following `TestType` values are handled:

- `ACCESS_RIGHTS` — evaluates `//ddi:conditions` using full text content;
  CONTAINS match against approved access rights terms.
- `PID` — evaluates `//ddi:IDNo`, extracts the `agency` attribute; EXACT
  match against approved PID schemas.
- `TOPIC_CLASS` — evaluates `//ddi:topcClas` using full text content;
  EXACT match against approved CESSDA topic classification terms.
- `DDI_ANALYSIS_UNIT` — evaluates `//ddi:anlyUnit` using direct text
  only; EXACT match.
- `DDI_COLLECTION_MODE` — evaluates `//ddi:collMode` using direct text
  only; EXACT match.
- `DDI_TIME_METHOD` — evaluates `//ddi:timeMeth` using direct text only;
  EXACT match.
- `DDI_SAMPLEPROC` — evaluates `//ddi:sampProc` using full text content;
  CONTAINS match against approved sampling procedure terms.
- `PROVENANCE` — checks for the presence of `//ddi:distrbtr`,
  `//ddi:AuthEnty`, or `//ddi:grantNo`; returns `PASS` if any exist.
- `ELSST_KEYWORDS` — finds all `//ddi:keyword` elements, filters those
  with `vocab="ELSST"` and a `vocabURI` containing `"elsst"`, then
  validates the collected terms via `VocabularyService`.

## Internal design

### Enumerations

| Enum | Values | Purpose |
|------|--------|---------|
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
2. Handles `PROVENANCE` by calling `checkProvenance(Document)`.
3. Handles `ELSST_KEYWORDS` by calling
   `checkElsstKeywords(Document, VocabularyService)`.
4. Looks up the `ValidationRule` for all other test types; returns
   `INDETERMINATE` if none is found.
5. Calls `evaluate` and returns the result.

## Return values

| Value | Meaning |
|-------|---------|
| `PASS` | At least one node contained an approved term (or a provenance/ELSST element was found). |
| `FAIL` | Relevant nodes were found but no approved term matched (or no provenance/ELSST elements were present). |
| `INDETERMINATE` | Parsing failed, an XPath error occurred, or no rule exists for the requested test type. |

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
- Adding a new vocabulary test requires only a new `ValidationRule` entry
  in `RULES`; no changes to `runTest` or `evaluate` are needed.
