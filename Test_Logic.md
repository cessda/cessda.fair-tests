# FAIR Test Implementation Logic

DO NOT RENAME THIS FILE (from Test_Logic.md) as it is referenced by
many items in [FAIR Wizard](https://ostrails-fair.fair-wizard.com/wizard/projects?sort=updatedAt,desc&userUuids=f74122d1-1661-40ea-b6c9-92ee7a93bb6d&projectTags=CESSDA)

Each test supports three input formats:

- **DDI Codebook 2.5 XML** — handled by `XmlParser`, including records
  wrapped in an OAI-PMH envelope.
- **CDC-schema JSON** — handled by `CdcJsonParser`; the root object must
  contain an `id` field.
- **HTML with embedded JSON-LD** — handled by `HtmlParser`, which
  extracts the content of a
  `<script id="json-ld" type="application/ld+json">` block and then
  delegates to `CdcJsonParser`.

The format is detected automatically from the response body. The same
fifteen tests are available for all three formats; the metadata location
and field names differ per format as described in each section below.

## Access Rights Validation (access-rights)

**Purpose:** Verifies that a record contains approved access rights
terminology.

### XML (access-rights)

**Metadata location:**
`/codeBook/stdyDscr/dataAccs/useStmt/conditions`

**Validation logic:**

- All `conditions` elements are located using XPath
  (`//ddi:conditions`).
- The full text content of each element is extracted and trimmed of
  whitespace.
- A case-insensitive substring comparison is performed between each
  value and the approved terms from the CESSDA Access Rights controlled
  vocabulary.
- If the vocabulary service is unavailable, the system falls back to
  default terms: `open` and `restricted`.

### CDC JSON and HTML/JSON-LD (access-rights)

**Metadata location:** `dataAccess` (top-level field)

**Validation logic:**

- The `dataAccess` field is read as a scalar string value.
- A case-insensitive substring comparison is performed between the value
  and the approved terms.
- The CONTAINS match type is used, so a value such as
  `"Restricted — see documentation"` will match the approved term
  `"Restricted"`.

### Pass criteria (access-rights)

At least one access rights value matches an approved term
(case-insensitive substring match).

### Fail criteria (access-rights)

No matching element or field is found, or none match approved terms.

### Indeterminate result (access-rights)

An error occurred whilst fetching or processing the metadata.

**Key implementation note:** The comparison is a case-insensitive
substring match, meaning `"Open"`, `"open"`, and `"OPEN"` are all
treated as equivalent and `"Open Access"` will match `"open"`.

## Persistent Identifier Schema Validation (pid)

**Purpose:** Confirms that a record uses an approved persistent
identifier scheme.

### XML (pid)

**Metadata location:**
`/codeBook/stdyDscr/citation/titlStmt/IDNo`

**Validation logic:**

- All `IDNo` elements are located using XPath (`//ddi:IDNo`).
- For each element the `agency` attribute value is extracted.
- A case-insensitive comparison is performed between each agency value
  and approved PID schemes from the CESSDA Persistent Identifier Types
  vocabulary.
- If the vocabulary service is unavailable, the system falls back to
  default schemes: DOI, Handle, URN, and ARK.

### CDC JSON and HTML/JSON-LD (pid)

**Metadata location:** `pidStudies` (top-level field)

**Validation logic:**

- The `pidStudies` field is extracted; it may be a string, an object,
  or an array of objects.
- The generic extractor probes each object for common keys (`value`,
  `term`, `label`, `name`, `agency`, `publisher`) to obtain candidate
  strings.
- An exact, case-insensitive comparison is performed between each
  candidate and the approved PID schemas.

### Pass criteria (pid)

At least one identifier element or field has an agency or scheme value
that matches an approved scheme (case-insensitive match).

### Fail criteria (pid)

No identifier elements or fields are found, or none have values matching
approved schemes.

### Indeterminate result (pid)

An error occurred whilst fetching or processing the metadata.

**Key implementation note:** The comparison is case-insensitive, so
`"DOI"`, `"doi"`, and `"Doi"` are all treated as equivalent.

## ELSST Keywords Validation (elsst-keywords)

**Purpose:** Validates that a record contains properly attributed ELSST
(European Language Social Science Thesaurus) keywords.

### XML (elsst-keywords)

**Metadata location:**
`/codeBook/stdyDscr/stdyInfo/subject/keyword`

**Validation logic:**

The test uses a two-phase validation approach.

**Phase 1 — Attribute validation:**

- The language code is extracted from the metadata record (e.g.
  `xml:lang="en"`).
- All `keyword` elements are located using XPath (`//ddi:keyword`).
- For each element, the system checks that both of the following
  criteria are met:
  - The `vocab` attribute equals `"ELSST"` (exact, case-sensitive).
  - The `vocabURI` attribute contains `"elsst.cessda.eu"` (substring
    match).
- Keywords meeting both criteria become candidates for Phase 2.

**Phase 2 — API validation:**

- For each candidate keyword, the ELSST Topics API is queried with the
  keyword text and the language code.
- The API returns labels for matching ELSST topics in the specified
  language.
- A case-insensitive comparison is performed between the candidate text
  and the API-returned labels.
- The system returns `pass` immediately upon finding the first match.

### CDC JSON and HTML/JSON-LD (elsst-keywords)

**Metadata location:** `keywords` (top-level array)

**Validation logic:**

- The `keywords` field must be a JSON array.
- For each element in the array, the system checks that:
  - The `vocab` field equals `"ELSST"` (exact, case-sensitive).
  - The `vocabUri` field contains `"elsst"` (substring match).
- Candidate keyword texts are collected from the `term` field of
  matching elements.
- The language codes to validate against are read from the top-level
  `langAvailableIn` array. Validation is attempted for each code in
  turn; the test passes as soon as any language produces a match.
- If `langAvailableIn` is absent or empty, the test returns `FAIL`
  because no language code is available to validate against.

### Pass criteria (elsst-keywords)

At least one keyword meets all conditions: correct vocabulary
attribution, a URI containing the expected substring, and a keyword text
that matches an ELSST API label (case-insensitive).

### Fail criteria (elsst-keywords)

No keywords are found, no keywords have both required attributes, or no
candidate keywords match ELSST API labels.

### Indeterminate result (elsst-keywords)

An error occurred whilst fetching metadata or querying the ELSST API, or
no language code is available.

**Key implementation notes:**

- Keywords missing either the `vocab` or `vocabURI`/`vocabUri`
  attribute are excluded from validation entirely, even if their text
  might match ELSST terms.
- The API comparison is case-insensitive.
- All three conditions must be met simultaneously.

## CESSDA Topic Classification Vocabulary (topic-class)

**Purpose:** Checks whether a record uses CESSDA Topic Classification
controlled vocabulary.

### XML (topic-class)

**Metadata location:**
`/codeBook/stdyDscr/stdyInfo/subject/topcClas`

**Validation logic:**

- All `topcClas` elements are located using XPath (`//ddi:topcClas`).
- For each element the system verifies:
  - The `vocab` attribute equals `"CESSDA Topic Classification"` (exact
    match).
  - The text content is non-empty after trimming whitespace.
  - The text matches a term from the CESSDA Topic Classification
    vocabulary (version 4.2.3).
- Vocabulary terms are retrieved from the CESSDA vocabulary service and
  cached.

### CDC JSON and HTML/JSON-LD (topic-class)

**Metadata location:** `classifications` (top-level field)

**Validation logic:**

- The `classifications` field is extracted; it may be a string, an
  object, or an array of objects.
- The generic extractor probes each object for common keys (`value`,
  `term`, `label`, `name`) to obtain candidate strings.
- An exact, case-insensitive comparison is performed between each
  candidate and the approved topic classification terms.

### Pass criteria (topic-class)

At least one topic classification value matches an approved vocabulary
term.

### Fail criteria (topic-class)

No topic classification elements or fields are found, or none meet all
the criteria.

### Indeterminate result (topic-class)

An error occurred whilst fetching or processing the metadata.

**Key implementation note:** For XML, both the `vocab` attribute and the
text content must match exactly (case-sensitive). For JSON, only the
text content is compared, using an exact case-insensitive match against
the vocabulary.

## DDI Analysis Unit Vocabulary (ddi-analysis-unit)

**Purpose:** Verifies that a record uses DDI Analysis Unit controlled
vocabulary.

### XML (ddi-analysis-unit)

**Metadata location:**
`/codeBook/stdyDscr/stdyInfo/sumDscr/anlyUnit`

**Validation logic:**

- All `anlyUnit` elements are located using XPath (`//ddi:anlyUnit`).
- Only the direct text children of each element are extracted
  (nested elements are ignored).
- Each value is compared against approved terms from the CESSDA Analysis
  Unit vocabulary (version 2.1.3) using an exact, case-insensitive
  match.

### CDC JSON and HTML/JSON-LD (ddi-analysis-unit)

**Metadata location:** `unitTypes` (top-level field)

**Validation logic:**

- The `unitTypes` field is extracted; it may be a string, an object, or
  an array of objects.
- The generic extractor probes each object for common keys (`value`,
  `term`, `label`, `name`) to obtain candidate strings.
- An exact, case-insensitive comparison is performed between each
  candidate and the approved analysis unit terms.

### Pass criteria (ddi-analysis-unit)

At least one analysis unit value matches an approved term (exact
case-insensitive match).

### Fail criteria (ddi-analysis-unit)

No analysis unit elements or fields are found, or none match approved
terms.

### Indeterminate result (ddi-analysis-unit)

An error occurred whilst fetching or processing the metadata.

## DDI Sampling Procedure Vocabulary (ddi-sampleproc)

**Purpose:** Checks whether a record uses DDI Sampling Procedure
controlled vocabulary.

### XML (ddi-sampleproc)

**Metadata location:**
`/codeBook/stdyDscr/method/dataColl/sampProc`

**Validation logic:**

- All `sampProc` elements are located using XPath (`//ddi:sampProc`).
- The full text content of each element is extracted and trimmed.
- Each value is compared against approved terms from the CESSDA Sampling
  Procedure vocabulary (version 2.0.1) using a case-insensitive
  substring match.

### CDC JSON and HTML/JSON-LD (ddi-sampleproc)

**Metadata location:** `typeOfSamplingProcedures` (top-level field)

**Validation logic:**

- The `typeOfSamplingProcedures` field is extracted; it may be a
  string or an array of strings.
- A CONTAINS, case-insensitive comparison is performed between each
  value and the approved sampling procedure terms.
- CONTAINS matching is used because this field holds free text rather
  than controlled vocabulary terms, so approved terms may appear
  embedded within a longer phrase.

### Pass criteria (ddi-sampleproc)

At least one sampling procedure value contains an approved term
(case-insensitive substring match).

### Fail criteria (ddi-sampleproc)

No sampling procedure elements or fields are found, or none match
approved terms.

### Indeterminate result (ddi-sampleproc)

An error occurred whilst fetching or processing the metadata.

**Key implementation note:** Both formats use CONTAINS matching for this
test because the `typeOfSamplingProcedures` field (and the equivalent
`sampProc` element) commonly contains free text rather than a bare
controlled vocabulary term.

## DDI Mode of Collection Vocabulary (ddi-collection-mode)

**Purpose:** Confirms that a record uses DDI Mode of Collection
controlled vocabulary.

### XML (ddi-collection-mode)

**Metadata location:**
`/codeBook/stdyDscr/method/dataColl/collMode`

**Validation logic:**

- All `collMode` elements are located using XPath (`//ddi:collMode`).
- Only the direct text children of each element are extracted (nested
  elements are ignored).
- Each value is compared against approved terms from the CESSDA Mode of
  Collection vocabulary (version 5.0.0) using an exact,
  case-insensitive match.

### CDC JSON and HTML/JSON-LD (ddi-collection-mode)

**Metadata location:** `typeOfModeOfCollections` (top-level field)

**Validation logic:**

- The `typeOfModeOfCollections` field is extracted; it may be a string,
  an object, or an array of objects.
- The generic extractor probes each object for common keys (`value`,
  `term`, `label`, `name`) to obtain candidate strings.
- An exact, case-insensitive comparison is performed between each
  candidate and the approved collection mode terms.

### Pass criteria (ddi-collection-mode)

At least one collection mode value matches an approved term (exact
case-insensitive match).

### Fail criteria (ddi-collection-mode)

No collection mode elements or fields are found, or none match approved
terms.

### Indeterminate result (ddi-collection-mode)

An error occurred whilst fetching or processing the metadata.

## DDI Time Method Vocabulary (ddi-time-method)

**Purpose:** Validates that a record uses DDI Time Method controlled
vocabulary.

### XML (ddi-time-method)

**Metadata location:**
`/codeBook/stdyDscr/method/dataColl/timeMeth`

**Validation logic:**

- All `timeMeth` elements are located using XPath (`//ddi:timeMeth`).
- Only the direct text children of each element are extracted (nested
  elements are ignored).
- Each value is compared against approved terms from the CESSDA Time
  Method vocabulary (version 1.2.3) using an exact, case-insensitive
  match.

### CDC JSON and HTML/JSON-LD (ddi-time-method)

**Metadata location:** `typeOfTimeMethods` (top-level field)

**Validation logic:**

- The `typeOfTimeMethods` field is extracted; it may be a string, an
  object, or an array of objects.
- The generic extractor probes each object for common keys (`value`,
  `term`, `label`, `name`) to obtain candidate strings.
- An exact, case-insensitive comparison is performed between each
  candidate and the approved time method terms.

### Pass criteria (ddi-time-method)

At least one time method value matches an approved term (exact
case-insensitive match).

### Fail criteria (ddi-time-method)

No time method elements or fields are found, or none match approved
terms.

### Indeterminate result (ddi-time-method)

An error occurred whilst fetching or processing the metadata.

## Provenance Information Validation (provenance)

**Purpose:** Verifies that a record contains provenance metadata
elements.

### XML (provenance)

**Metadata locations:**

- `distrbtr` (distributor/publisher):
  `/codeBook/stdyDscr/citation/distStmt/distrbtr`
- `AuthEnty` (author/authoring entity):
  `/codeBook/stdyDscr/citation/rspStmt/AuthEnty`
- `grantNo` (grant number):
  `/codeBook/stdyDscr/citation/prodStmt/grantNo`

**Validation logic:**

- The presence of each of the three elements is checked using XPath
  (`//ddi:distrbtr`, `//ddi:AuthEnty`, `//ddi:grantNo`).
- At least one instance of any element must exist (regardless of
  content).

### CDC JSON and HTML/JSON-LD (provenance)

**Metadata locations** (any one of the following):

- `publisher.publisher` — publisher name nested within a publisher
  object.
- `creators[].name` — `name` field within any object in the `creators`
  array.
- `funding[].agency` — `agency` field within any object in the
  `funding` array.

**Validation logic:**

- A `PRESENCE_ANY` rule type is applied: the test passes as soon as any
  non-blank string value is extracted from any of the three paths.
- No comparison against a controlled vocabulary is made; only presence
  is checked.

### Pass criteria (provenance)

At least one of the relevant elements or fields is present and contains
a non-blank value.

### Fail criteria (provenance)

None of the relevant elements or fields are present, or all are blank.

### Indeterminate result (provenance)

An error occurred whilst fetching or processing the metadata.

**Key implementation notes:**

- For XML, only the presence of elements is checked; empty elements
  count as present.
- For JSON, the value must be non-blank; an empty string does not
  satisfy the test.
- This is a more lenient test than the original specification, which
  required both author and publisher to be mandatory; at least one
  provenance element must be present for the record to pass.

## FAIR Vocabulary Validation (fair-vocabulary)

**Purpose:** Checks whether a record references at least one resolvable
controlled vocabulary by a URI.

### XML (fair-vocabulary)

**Metadata location:** any element carrying a `vocabURI` attribute

**Validation logic:**

- All elements carrying a `vocabURI` attribute are located using XPath
  (`//*[@vocabURI]`).
- For each element, both the `vocab` attribute (vocabulary name) and
  `vocabURI` attribute (vocabulary URI) must be non-blank.
- Each qualifying URI is tested with an HTTP GET request. The first URI
  that returns a 2xx or 3xx response returns `PASS`.
- URIs that do not begin with `http://` or `https://` are skipped.

### CDC JSON and HTML/JSON-LD (fair-vocabulary)

**Metadata location:** `vocab` and `vocabUri` fields within the
`classifications`, `keywords`, `unitTypes`, `sampProc`,
`typeOfModeOfCollections`, and `typeOfTimeMethods` arrays

**Validation logic:**

- Each of the five arrays is scanned for entries where both the `vocab`
  field (vocabulary name) and `vocabUri` field (vocabulary URI) are
  non-blank.
- Each qualifying URI is tested with an HTTP GET request. The first URI
  that returns a 2xx or 3xx response returns `PASS`.
- URIs that do not begin with `http://` or `https://` are skipped.

### Pass criteria (fair-vocabulary)

At least one qualifying vocabulary URI resolves successfully over HTTP.

### Fail criteria (fair-vocabulary)

Qualifying entries are present but no vocabulary URI resolves, or all
entries are missing one of the required fields.

### Indeterminate result (fair-vocabulary)

No qualifying entries were found, or an unexpected error occurred.

**Key implementation note:** Both formats require a vocabulary name
(`vocab`/`vocab` attribute) alongside the URI. A bare URI with no
vocabulary name does not satisfy the test.

## Formal Knowledge Representation Language (formal-kr-language)

**Purpose:** Verifies that the metadata uses a recognised formal,
machine-readable language or schema.

### XML (formal-kr-language)

**Validation logic:**

- The namespace URI declared on the document root element is compared
  against the set of supported DDI Codebook namespace URIs held in
  `SUPPORTED_DDI_NAMESPACES`.
- If a supported namespace is found, the document is additionally
  checked for an `xsi:schemaLocation` attribute that reference known
  DDI schemas, including the `xsi:schemaLocation` attribute on the
  `/ddi:codeBook` root element for DDI Codebook 2.5. If schema grounding
  is detected, it returns `PASS`.
- If no supported DDI namespace is detected, `FAIL` is returned.

### CDC JSON and HTML/JSON-LD (formal-kr-language)

**Metadata location:** `studyXmlSourceUrl` (top-level field)

**Validation logic:**

- The `studyXmlSourceUrl` field is checked for a non-blank value using
  a `PRESENCE_ANY` rule.
- Its presence indicates that the record originates from a DDI XML
  source accessible via OAI-PMH, which constitutes a formal knowledge
  representation language.

### Pass criteria (formal-kr-language)

For XML: a supported DDI namespace URI is present on the document root.
For JSON: the `studyXmlSourceUrl` field is non-blank.

### Fail criteria (formal-kr-language)

For XML: no supported DDI namespace is found. For JSON: the
`studyXmlSourceUrl` field is absent or blank.

### Indeterminate result (formal-kr-language)

An unexpected error occurred whilst processing the metadata.

## Grounded Metadata Validation (grounded-metadata)

**Purpose:** Checks whether the metadata is grounded in at least one
resolvable, machine-readable resource.

### XML (grounded-metadata)

**Validation logic:**

- All namespace URIs used anywhere in the document are collected by
  traversing the DOM tree (both element and attribute namespaces).
- All URLs from `xsi:schemaLocation` and `xsi:noNamespaceSchemaLocation`
  attributes are collected.
- The study URL is collected from the `URI` attribute of
  `//ddi:codeBook/ddi:stdyDscr/ddi:citation/ddi:holdings`, matched
  wherever `ddi:codeBook` occurs in the document (e.g. nested inside an
  OAI-PMH envelope), as this is the information source of the study's own
  resolvable location.
- Common infrastructure namespaces are excluded from consideration: those
  beginning with `http://www.w3.org/`, `http://www.openarchives.org/`,
  `http://www.loc.gov/`, or `http://purl.org/dc/`, and any URI containing
  `"xml"`.
- Each remaining candidate URL is tested with an HTTP GET request. The
  first URL that returns a 2xx or 3xx response returns `PASS`.

### CDC JSON and HTML/JSON-LD (grounded-metadata)

**Metadata location:** `studyUrl` (top-level field)

**Validation logic:**

- The `studyUrl` field is read. If absent or blank, `FAIL` is returned.
- If the value does not begin with `http://` or `https://`, `FAIL` is
  returned.
- The URL is tested with an HTTP GET request. A 2xx or 3xx response
  returns `PASS`.

### Pass criteria (grounded-metadata)

At least one candidate URL resolves successfully over HTTP.

### Fail criteria (grounded-metadata)

Candidate URLs are present but none resolve, or no candidates are found
after filtering.

### Indeterminate result (grounded-metadata)

An unexpected error occurred whilst processing the metadata or making
HTTP requests.

**Key implementation note:** Connect and read timeouts for HTTP requests
are set to 5 seconds. Redirects are followed automatically.

## Retrievable Protocol Validation (retrievable-protocol)

**Purpose:** Verifies that the record's persistent identifier can be
retrieved via an open, standard internet protocol.

### XML (retrievable-protocol)

**Metadata location:**
`/codeBook/stdyDscr/citation/titlStmt/IDNo`

**Validation logic:**

- All `IDNo` elements are located using XPath (`//ddi:IDNo`).
- For each element, the `agency` attribute and the element text content
  (the identifier value) are read.
- A resolution URL is constructed from the agency and value:
  - `DOI` → `https://doi.org/<value>`
  - `Handle` → `https://hdl.handle.net/<value>`
  - `ARK` → `https://n2t.net/<value>`
  - `URN` and unrecognised agencies → skipped.
- The constructed URL is checked to confirm it uses an open protocol
  (`http://` or `https://`) and then tested with an HTTP GET request.
  The first URL that returns a 2xx or 3xx response returns `PASS`.

### CDC JSON and HTML/JSON-LD (retrievable-protocol)

**Metadata location:** `pidStudies` (top-level array)

**Validation logic:**

- Each entry in `pidStudies` is read for its `agency` and `pid` fields.
- CDC JSON `pid` values may include a scheme prefix (e.g.
  `"doi:10.17903/FK2/BVFEYX"`); this prefix is stripped before the
  resolution URL is constructed.
- Resolution URLs are constructed using the same agency mapping as for
  XML (DOI, Handle, ARK); URNs and unrecognised agencies are skipped.
- The first constructed URL that resolves successfully over HTTP returns
  `PASS`.

### Pass criteria (retrievable-protocol)

At least one identifier resolves successfully via an open HTTP protocol.

### Fail criteria (retrievable-protocol)

Identifiers are present but none resolve, or no supported agency values
are found.

### Indeterminate result (retrievable-protocol)

No identifiers were found, or an unexpected error occurred.

**Key implementation note:** Connect and read timeouts are set to
5 seconds. Redirects are followed automatically.

## Searchable Metadata Validation (searchable)

**Purpose:** Verifies that the record is discoverable via a standard
metadata harvesting protocol.

### XML (searchable)

**Validation logic:**

- The document is checked for the simultaneous presence of three
  OAI-PMH structural elements using XPath:
  - `//oai:record` — the OAI-PMH record wrapper.
  - `//oai:header` — the OAI-PMH record header.
  - `//oai:header/oai:identifier` — the OAI-PMH record identifier.
- All three must be present for `PASS` to be returned. This confirms
  the record is wrapped in a valid OAI-PMH envelope and is therefore
  discoverable by OAI-PMH harvesters.

### CDC JSON and HTML/JSON-LD (searchable)

**Metadata location:** `studyXmlSourceUrl` (top-level field)

**Validation logic:**

- The `studyXmlSourceUrl` field is read. If absent or blank, `FAIL` is
  returned.
- The value must contain both the substrings `"oai"` and `"GetRecord"`,
  which identifies it as an OAI-PMH `GetRecord` endpoint URL (e.g.
  `.../oai?verb=GetRecord&identifier=...`).
- If both substrings are present, `PASS` is returned.

### Pass criteria (searchable)

For XML: all three OAI-PMH structural elements are present. For JSON:
`studyXmlSourceUrl` contains a valid OAI-PMH `GetRecord` URL.

### Fail criteria (searchable)

For XML: one or more of the required OAI-PMH elements are absent. For
JSON: the field is absent, blank, or does not contain the expected
substrings.

### Indeterminate result (searchable)

An unexpected error occurred whilst processing the metadata.

## Structured Metadata Validation (structured-metadata)

**Purpose:** Verifies that the record uses a recognised, structured
metadata schema.

### XML (structured-metadata)

**Validation logic:**

- The namespace URI declared on the document root element is compared
  against the set of supported DDI Codebook namespace URIs held in
  `SUPPORTED_DDI_NAMESPACES`.
- If a supported DDI namespace is detected, `PASS` is returned.
- If no supported namespace is found, `FAIL` is returned.

### CDC JSON and HTML/JSON-LD (structured-metadata)

**Metadata locations:** `titleStudy` and `abstract` (top-level fields)

**Validation logic:**

- A `PRESENCE_ANY` rule is applied across the `titleStudy` and
  `abstract` fields.
- The presence of a non-blank value in either field indicates that the
  record follows the structured CDC schema.

### Pass criteria (structured-metadata)

For XML: a supported DDI namespace URI is declared on the document root.
For JSON: at least one of `titleStudy` or `abstract` is non-blank.

### Fail criteria (structured-metadata)

For XML: no supported DDI namespace is found. For JSON: both fields are
absent or blank.

### Indeterminate result (structured-metadata)

An unexpected error occurred whilst processing the metadata.

**Key implementation note:** `structured-metadata` and
`formal-kr-language` are closely related and will often agree. The
distinction is that `structured-metadata` is a lower-bar structural
check (is this a recognised schema?) whilst `formal-kr-language` is a
higher-bar language check (is the schema declared formally, with
namespace qualification and optional schema grounding?).
