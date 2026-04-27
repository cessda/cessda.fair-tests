# HtmlParser

## Overview

`HtmlParser` implements the `FormatParser` interface and handles HTTP
responses that return an HTML page embedding a JSON-LD metadata block
rather than a bare JSON or XML document. It locates a `<script>` element
with `id="json-ld"` and `type="application/ld+json"`, extracts the text
content between that opening tag and the following `</script>` closing
tag, and delegates all test logic to an inner `CdcJsonParser` instance.

The class contains no vocabulary-matching logic of its own. It acts
purely as a pre-processor: once the JSON-LD block has been extracted, the
full set of `TestType` evaluations is handled by `CdcJsonParser`.

## Expected HTML structure

The parser expects the metadata to be embedded in the following form:

```html
<script id="json-ld" type="application/ld+json">
{ ... }
</script>
```

The search for the opening tag is case-insensitive, so variant
capitalisations such as `<SCRIPT ID="JSON-LD" ...>` are also matched.

## Internal design

### Constants

| Constant | Value |
|----------|-------|
| `JSON_LD_OPEN_TAG` | `<script id="json-ld" type="application/ld+json">` |
| `JSON_LD_CLOSE_TAG` | `</script>` |

Both constants are lowercased at runtime for the case-insensitive search.

### Extraction logic

`extractJsonLdBlock(String html)` performs the following steps:

1. Returns `null` (logging a warning) if the HTML string is `null` or
   blank.
2. Converts the entire HTML string to lowercase and searches for the
   lowercased form of `JSON_LD_OPEN_TAG`.
3. If the opening tag is not found, returns `null` and logs a warning.
4. Computes `contentStart` as the index immediately following the closing
   `>` of the opening tag.
5. Searches for the lowercased form of `JSON_LD_CLOSE_TAG` starting from
   `contentStart`.
6. If the closing tag is not found, returns `null` and logs a warning.
7. Extracts the substring between `contentStart` and `closeTagStart`,
   trims it, and returns `null` (with a warning) if the result is empty.
8. Otherwise returns the trimmed JSON text.

The index arithmetic is performed on the lowercase copy of the HTML, but
the actual substring is taken from the original (mixed-case) string so
that the extracted JSON is not inadvertently lowercased.

### Stream helpers

| Method | Purpose |
|--------|---------|
| `readStream(InputStream)` | Reads all bytes from the stream and decodes them as UTF-8. |
| `toStream(String)` | Wraps a JSON string in a `ByteArrayInputStream` using UTF-8 encoding. |

## Entry point

```java
Result runTest(TestType test, InputStream inputStream,
               VocabularyService vocabulary) throws IOException
```

The method:

1. Reads the entire `InputStream` into a `String` via `readStream`.
2. Calls `extractJsonLdBlock` to locate and extract the JSON-LD text.
3. Returns `Result.INDETERMINATE` if extraction fails (the reason has
   already been logged).
4. Wraps the extracted JSON in a new `InputStream` and passes it to
   `CdcJsonParser#runTest`, returning whatever result that produces.

## Return values

| Value | Meaning |
|-------|---------|
| `PASS` | The JSON-LD block was found and `CdcJsonParser` determined the record passes the test. |
| `FAIL` | The JSON-LD block was found but `CdcJsonParser` determined the record fails the test. |
| `INDETERMINATE` | The stream could not be read, no JSON-LD `<script>` block was found, or `CdcJsonParser` returned `INDETERMINATE`. |

## Dependencies

- `CdcJsonParser` — handles all JSON test logic once the block is
  extracted; held as a private final field.
- `VocabularyService` — passed through unchanged to `CdcJsonParser`.
- `FormatParser`, `TestType`, `Result` — shared interfaces and types
  within the `eu.cessda.fairtests` package.

## Notes

- The class is intentionally thin. Any future HTML-specific test (for
  example, checking for a canonical `<link>` element) can be added as
  a new `switch` case in `runTest` without altering the JSON-LD
  extraction path.
- Only the first occurrence of the JSON-LD `<script>` block is
  extracted. If a page embeds more than one such block, all blocks after
  the first are ignored.
- The parser does not validate or sanitise the extracted JSON before
  passing it to `CdcJsonParser`; malformed JSON will result in an
  `IOException` being thrown from `CdcJsonParser#runTest`.
