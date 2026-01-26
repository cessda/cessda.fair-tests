# FAIR Test Implementation Logic

## Access Rights Validation (access-rights)

**Purpose:** Verifies that a record contains approved access rights
terminology.

**Metadata Location:** The test examines the conditions element within the data
access section (`/codeBook/stdyDscr/dataAccs/useStmt/conditions`) of DDI 2.5
metadata.

**Validation Logic:**

- The system retrieves the DDI 2.5 metadata record
- All conditions elements are located using XPath queries
- The text content of each element is extracted and trimmed of whitespace
- A case-insensitive comparison is performed between each value and the
    approved terms from the CESSDA Access Rights controlled vocabulary
- If the vocabulary service is unavailable, the system falls back to default
    terms: "open" and "restricted"

**Pass Criteria:** At least one access rights value matches an approved term
(case-insensitive match)

**Fail Criteria:** No conditions elements are found, or none match approved
terms

**Indeterminate Result:** An error occurred whilst fetching or processing the
metadata

**Key Implementation Note:** The comparison is case-insensitive substring match,
meaning "Open", "open", and "OPEN" are all treated as equivalent and "Open Access" will match "open".

---

## Persistent Identifier Schema Validation (pid)

**Purpose:** Confirms that a record uses an approved persistent identifier
scheme.

**Metadata Location:** The test examines IDNo elements with agency attributes
in the citation's title statement section (`/codeBook/stdyDscr/citation/titlStmt/IDNo`).

**Validation Logic:**

- The system retrieves the DDI 2.5 metadata record
- All IDNo elements are located using XPath queries
- For each IDNo element, the agency attribute value is extracted
- A case-insensitive comparison is performed between each agency value and
    approved PID schemes from the CESSDA Persistent Identifier Types vocabulary
- If the vocabulary service is unavailable, the system falls back to default
    schemes: DOI, Handle, URN, and ARK

**Pass Criteria:** At least one IDNo element has an agency attribute that
matches an approved scheme (case-insensitive match)

**Fail Criteria:** No IDNo elements are found, or none have agency attributes
matching approved schemes

**Indeterminate Result:** An error occurred whilst fetching or processing the
metadata

**Key Implementation Note:** The comparison is case-insensitive, so "DOI",
"doi", and "Doi" are all treated as equivalent.

---

## ELSST Keywords Validation (elsst-keywords)

**Purpose:** Validates that a record contains properly attributed ELSST
(European Language Social Science Thesaurus) keywords.

**Metadata Location:** The test examines keyword elements in the study
information subject section (`/codeBook/stdyDscr/stdyInfo/subject/keyword`).

**Validation Logic:**

The test uses a two-phase validation approach:

**Phase 1 - Attribute Validation:**

- The system retrieves the DDI 2.5 metadata record
- The language code is extracted from the metadata record (e.g., `xml:lang=en`)
- All keyword elements are located using XPath queries
- For each keyword element, the system checks if it meets both of these
    criteria:
  - The vocab attribute equals "ELSST" (exact match, case-sensitive)
  - The vocabURI attribute contains "elsst.cessda.eu" (substring match)
- Keywords meeting both criteria become "candidates" for Phase 2

**Phase 2 - API Validation:**

- For each candidate keyword, the system queries the ELSST Topics API with:
  - The keyword text
  - The language code extracted from the URL
- The API returns labels for matching ELSST topics in the specified language
- A case-insensitive comparison is performed between the candidate keyword text
    and the API-returned labels
- The system returns "pass" immediately upon finding the first match

**Pass Criteria:** At least one keyword meets all three conditions:

- vocab attribute equals "ELSST"
- vocabURI attribute contains "elsst.cessda.eu"
- Keyword text matches an ELSST API label (case-insensitive)

**Fail Criteria:**

- No keywords are found, OR
- No keywords have both required attributes, OR
- No candidate keywords match ELSST API labels

**Indeterminate Result:**

- An error occurred whilst fetching metadata or querying the ELSST API, OR
- No language code is available in the URL

**Key Implementation Notes:**

- Keywords missing either the vocab or vocabURI attribute are excluded from
    validation entirely, even if their text might match ELSST terms
- The API comparison is case-insensitive
- The test requires all three conditions to be met simultaneously

---

## CESSDA Topic Classification Vocabulary (topic-class)

**Purpose:** Checks whether a record uses CESSDA Topic Classification
    controlled vocabulary.

**Metadata Location:** The test examines topcClas (topic classification)
    elements in the study information subject section
    (`/codeBook/stdyDscr/stdyInfo/subject/topcClas`).

**Validation Logic:**

- The system retrieves the DDI 2.5 metadata record
- All topcClas elements are located using XPath queries
- For each element, the system verifies:
  - The vocab attribute equals "CESSDA Topic Classification" (exact match)
  - The text content is non-empty after trimming whitespace
  - The text matches a term from the CESSDA Topic Classification vocabulary
    (version 4.2.3)
- Vocabulary terms are retrieved from the CESSDA vocabulary service and cached

**Pass Criteria:** At least one topic classification element meets all three
criteria (correct vocab attribute, non-empty text, and exact match with vocabulary term)

**Fail Criteria:** No topcClas elements are found, or none meet all the
criteria

**Indeterminate Result:** An error occurred whilst fetching or processing the
metadata

**Key Implementation Note:** Both the vocab attribute and the text content must
match exactly (case-sensitive comparison).

---

## DDI Analysis Unit Vocabulary (ddi-anunit)

**Purpose:** Verifies that a record uses DDI Analysis Unit controlled
vocabulary.

**Metadata Location:** The test examines anlyUnit (analysis unit) elements in
the study description's summary description section
(`/codeBook/stdyDscr/stdyInfo/sumDscr/anlyUnit`).

**Validation Logic:**

- The system retrieves the DDI 2.5 metadata record
- All anlyUnit elements are located using XPath queries
- The text content of each element is extracted and trimmed of whitespace
- Each value is compared against approved terms from the CESSDA Analysis Unit
vocabulary (version 2.1.3)
- Vocabulary terms are retrieved from the CESSDA vocabulary service and cached

**Pass Criteria:** At least one analysis unit value matches an approved term
(exact match, case-sensitive)

**Fail Criteria:** No anlyUnit elements are found, or none match approved terms

**Indeterminate Result:** An error occurred whilst fetching or processing the
metadata

---

## DDI Sampling Procedure Vocabulary (ddi-sampleproc)

**Purpose:** Checks whether a record uses DDI Sampling Procedure controlled
vocabulary.

**Metadata Location:** The test examines sampProc (sampling procedure) elements
in the data collection method section
(`/codeBook/stdyDscr/method/dataColl/sampProc`).

**Validation Logic:**

- The system retrieves the DDI 2.5 metadata record
- All sampProc elements are located using XPath queries
- The text content of each element is extracted and trimmed of whitespace
- Each value is compared against approved terms from the CESSDA Sampling
Procedure vocabulary (version 2.0.1)
- Vocabulary terms are retrieved from the CESSDA vocabulary service and cached

**Pass Criteria:** At least one sampling procedure value matches an approved
term (exact match, case-sensitive)

**Fail Criteria:** No sampProc elements are found, or none match approved terms

**Indeterminate Result:** An error occurred whilst fetching or processing the
metadata

---

## DDI Mode of Collection Vocabulary (ddi-colmod)

**Purpose:** Confirms that a record uses DDI Mode of Collection controlled
vocabulary.

**Metadata Location:** The test examines collMode (collection mode) elements in
the data collection method section (`/codeBook/stdyDscr/method/dataColl/collMode`).

**Validation Logic:**

- The system retrieves the DDI 2.5 metadata record
- All collMode elements are located using XPath queries
- The text content of each element is extracted and trimmed of whitespace
- Each value is compared against approved terms from the CESSDA Mode of
    Collection vocabulary (version 5.0.0)
- Vocabulary terms are retrieved from the CESSDA vocabulary service and cached

**Pass Criteria:** At least one collection mode value matches an approved term
(exact match, case-sensitive)

**Fail Criteria:** No collMode elements are found, or none match approved terms

**Indeterminate Result:** An error occurred whilst fetching or processing the
metadata

---

## DDI Time Method Vocabulary (ddi-timeth)

**Purpose:** Validates that a record uses DDI Time Method controlled vocabulary.

**Metadata Location:** The test examines timeMeth (time method) elements in the
data collection method section (`/codeBook/stdyDscr/method/dataColl/timeMeth`).

**Validation Logic:**

- The system retrieves the DDI 2.5 metadata record
- All timeMeth elements are located using XPath queries
- The text content of each element is extracted and trimmed of whitespace
- Each value is compared against approved terms from the CESSDA Time Method
    vocabulary (version 1.2.3)
- Vocabulary terms are retrieved from the CESSDA vocabulary service and cached

**Pass Criteria:** At least one time method value matches an approved term
(exact match, case-sensitive)

**Fail Criteria:** No timeMeth elements are found, or none match approved terms

**Indeterminate Result:** An error occurred whilst fetching or processing the
metadata

---

## Provenance Information Validation (provenance)

**Purpose:** Verifies that a record contains provenance metadata elements.

**Metadata Location:** The test examines three elements:

- distrbtr (distributor/publisher) in the distribution statement
    (`/codeBook/stdyDscr/citation/distStmt/distrbtr`)
- AuthEnty (author/authoring entity) in the responsibility statement
    (`/codeBook/stdyDscr/citation/rspStmt/AuthEnty`)
- grantNo (grant number) in the production statement
    (`/codeBook/stdyDscr/citation/prodStmt/grantNo`)

**Validation Logic:**

- The system retrieves the DDI 2.5 metadata record
- The presence of each of the three elements is checked using XPath queries:
  - Publisher (distrbtr)
  - Author (AuthEnty)
  - Grant Number (grantNo)
- For each element, the system checks if at least one instance exists
    (regardless of content)

**Pass Criteria:** At least one of the three elements (publisher, author, or
grant number) is present in the metadata

**Fail Criteria:** None of the three elements are present

**Indeterminate Result:** An error occurred whilst fetching or processing the
metadata

**Key Implementation Notes:**

- Only the presence of elements is checked; the content is not validated
- Empty elements count as present
- This is a more lenient test than the original specification, which required
    both author and publisher to be mandatory
- At least one provenance element must be present for the record to pass
