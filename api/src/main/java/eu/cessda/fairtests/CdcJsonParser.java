/*
 * SPDX-FileCopyrightText: 2026 CESSDA ERIC (support@cessda.eu)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package eu.cessda.fairtests;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CdcJsonParser implements FormatParser {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Defines how to compare extracted values against approved vocabulary
     * terms.
     * <ul>
     * <li>EXACT: the extracted value must exactly match one of the approved
     * terms.</li>
     * <li>CONTAINS: the extracted value must contain (as a substring) one of
     * the approved terms. Useful for cases where the extracted value includes
     * additional context around the approved term, as is common in real-world
     * datasets.</li>
     * </ul>
     */
    private enum MatchType {
        EXACT,
        CONTAINS
    }

    /**
     * Defines the type of validation rule, determining how it is evaluated.
     * <ul>
     * <li>VOCAB_MATCH: checks extracted values against an approved
     * vocabulary.</li>
     * <li>PRESENCE_ANY: checks only that a non-blank value is present in the
     * specified field(s), without comparing against a vocabulary. Used for
     * tests such as PROVENANCE and STRUCTURED_METADATA where existence of a
     * value is sufficient.</li>
     * </ul>
     */
    private enum RuleType {
        VOCAB_MATCH,
        PRESENCE_ANY
    }

    /**
     * Encapsulates all information needed to evaluate a specific test type.
     *
     * @param type          determines how the rule is evaluated (VOCAB_MATCH or
     *                      PRESENCE_ANY).
     * @param fields        JSON path(s) from which candidate values are
     *                      extracted. Multiple paths are supported to handle
     *                      varying JSON structures across datasets.
     * @param vocabSupplier retrieves the approved term set from the
     *                      VocabularyService; {@code null} for PRESENCE_ANY
     *                      rules.
     * @param matchType     EXACT or CONTAINS comparison; {@code null} for
     *                      PRESENCE_ANY rules.
     * @param label         human-readable name used in log messages.
     */
    private record ValidationRule(
            RuleType type,
            List<String> fields,
            Function<VocabularyService, Set<String>> vocabSupplier,
            MatchType matchType,
            String label) {
    }

    /**
     * Normalises the input document to a single entity node, handling both
     * the CDC-schema structure (fields at document root) and the SKG-IF
     * structure (entity fields nested under the first @graph element).
     *
     * @param document the root JSON document as parsed from the source
     * @return the JsonNode representing the entity to be validated
     */
    private JsonNode normaliseEntity(JsonNode document) {

        JsonNode graphNode = document.path("@graph");

        if (graphNode.isArray() && graphNode.size() > 0) {
            return graphNode.get(0);
        }

        return document;
    }

    /**
     * Maps each rules-based {@link TestType} to a {@link ValidationRule}.
     * Tests that require bespoke logic ({@code ELSST_KEYWORDS},
     * {@code FAIR_VOCABULARY}, {@code GROUNDED_METADATA},
     * {@code RETRIEVABLE_PROTOCOL}, and {@code SEARCHABLE}) are not listed
     * here; they are dispatched directly in {@link #runTest}.
     *
     * <p>
     * Notes on specific rules:
     * </p>
     * <ul>
     * <li>{@code PID}: extracts the {@code agency} field from each entry in
     * {@code pidStudies} (e.g. {@code "DOI"}) and checks it against approved
     * PID schemas.</li>
     * <li>{@code DDI_SAMPLEPROC}: uses {@code typeOfSamplingProcedures[*].term};
     * EXACT match is used accordingly.</li>
     * <li>{@code PROVENANCE}: PRESENCE_ANY across publisher name, creator
     * names, and funding agency — any non-blank value returns PASS.</li>
     * <li>{@code STRUCTURED_METADATA}: PRESENCE_ANY on {@code titleStudy} and
     * {@code abstract} — their presence indicates a structured CDC record.</li>
     * <li>{@code FORMAL_KR_LANGUAGE}: PRESENCE_ANY on
     * {@code studyXmlSourceUrl} — its presence indicates the record originates
     * from a formal DDI XML source accessible via OAI-PMH.</li>
     * </ul>
     */
    private static final Map<TestType, ValidationRule> RULES = Map.ofEntries(

            Map.entry(TestType.ACCESS_RIGHTS,
                    new ValidationRule(
                            RuleType.VOCAB_MATCH,
                            List.of("dataAccess"),
                            VocabularyService::getApprovedAccessRightsTerms,
                            MatchType.CONTAINS,
                            "Access Rights")),

            Map.entry(TestType.PID,
                    new ValidationRule(
                            RuleType.VOCAB_MATCH,
                            List.of("pidStudies"),
                            VocabularyService::getApprovedPidSchemas,
                            MatchType.EXACT,
                            "PID schema")),

            Map.entry(TestType.TOPIC_CLASS,
                    new ValidationRule(
                            RuleType.VOCAB_MATCH,
                            List.of("classifications"),
                            VocabularyService::getApprovedTopicClassTerms,
                            MatchType.EXACT,
                            "Topic Classification")),

            Map.entry(TestType.DDI_ANALYSIS_UNIT,
                    new ValidationRule(
                            RuleType.VOCAB_MATCH,
                            List.of("unitTypes"),
                            VocabularyService::getApprovedAnalysisUnitTerms,
                            MatchType.EXACT,
                            "Analysis Unit")),

            Map.entry(TestType.DDI_COLLECTION_MODE,
                    new ValidationRule(
                            RuleType.VOCAB_MATCH,
                            List.of("typeOfModeOfCollections"),
                            VocabularyService::getApprovedCollectionModeTerms,
                            MatchType.EXACT,
                            "Collection Mode")),

            Map.entry(TestType.DDI_TIME_METHOD,
                    new ValidationRule(
                            RuleType.VOCAB_MATCH,
                            List.of("typeOfTimeMethods"),
                            VocabularyService::getApprovedTimeMethodTerms,
                            MatchType.EXACT,
                            "Time Method")),

            Map.entry(TestType.DDI_SAMPLEPROC,
                    new ValidationRule(
                            RuleType.VOCAB_MATCH,
                            List.of("typeOfSamplingProcedures"),
                            VocabularyService::getApprovedSamplingProcTerms,
                            MatchType.EXACT,
                            "Sampling Procedure")),

            Map.entry(TestType.PROVENANCE,
                    new ValidationRule(
                            RuleType.PRESENCE_ANY,
                            List.of(
                                    "publisher.publisher",
                                    "creators[].name",
                                    "funding[].agency"),
                            null,
                            null,
                            "Provenance")),

            Map.entry(TestType.STRUCTURED_METADATA,
                    new ValidationRule(
                            RuleType.PRESENCE_ANY,
                            List.of("titleStudy", "abstract"),
                            null,
                            null,
                            "Structured Metadata")),

            Map.entry(TestType.FORMAL_KR_LANGUAGE,
                    new ValidationRule(
                            RuleType.PRESENCE_ANY,
                            List.of("studyXmlSourceUrl"),
                            null,
                            null,
                            "Formal KR Language")));

    /**
     * Runs the specified test against the JSON dataset read from the input
     * stream.
     *
     * <p>
     * The method first parses the stream and verifies that an {@code id}
     * field is present (returning {@link Result#INDETERMINATE} if not). It
     * then dispatches to a bespoke method for test types that cannot be
     * expressed as a simple rule ({@code ELSST_KEYWORDS},
     * {@code FAIR_VOCABULARY}, {@code GROUNDED_METADATA},
     * {@code RETRIEVABLE_PROTOCOL}, {@code SEARCHABLE}), and falls back to
     * the rules-based engine for all remaining types. Returns
     * {@link Result#INDETERMINATE} if no rule is defined for the test type.
     * </p>
     *
     * @param test        the type of test to run
     * @param inputStream the input stream containing the JSON dataset
     * @param vocabulary  the vocabulary service for retrieving approved terms
     * @return {@link Result#PASS}, {@link Result#FAIL}, or
     *         {@link Result#INDETERMINATE}
     * @throws IOException if the input stream cannot be read or parsed
     */
    @Override
    public Result runTest(TestType test, InputStream inputStream,
            VocabularyService vocabulary) throws IOException {

        //JsonNode dataset = mapper.readTree(inputStream);
        JsonNode dataset = normaliseEntity(mapper.readTree(inputStream));

        // Ensure that the dataset has an ID or local_identifier for logging
        // This allows JSON returned by CESSDA SKG-IF to be validated,
        // even though it does not have an "id" field
        if (dataset.path("id").isMissingNode()
                && dataset.path("local_identifier").isMissingNode()) {
            FairTests.logWarning(
                    "Dataset ID is required for validation but was not "
                            + "found. Cannot perform test.");
            return Result.INDETERMINATE;
        }

        switch (test) {
            case ELSST_KEYWORDS -> {
                return checkElsstKeywords(dataset, vocabulary);
            }
            case FAIR_VOCABULARY -> {
                return checkFairVocabulary(dataset);
            }
            case GROUNDED_METADATA -> {
                return checkGroundedMetadata(dataset);
            }
            case RETRIEVABLE_PROTOCOL -> {
                return checkRetrievableProtocol(dataset);
            }
            case SEARCHABLE -> {
                return checkSearchable(dataset);
            }
            default -> {
                /* fall through to rules-based evaluation */ }
        }

        ValidationRule rule = RULES.get(test);
        if (rule == null) {
            FairTests.logWarning("No rule defined for %s", test);
            return Result.INDETERMINATE;
        }

        return evaluateRule(dataset, rule, vocabulary);
    }

    // -------------------------------------------------------------------------
    // Core engine
    // -------------------------------------------------------------------------

    /**
     * Evaluates a {@link ValidationRule} against the dataset.
     *
     * <p>
     * Extracts candidate values from the fields named in the rule. If no
     * values are found, returns {@link Result#FAIL}. For
     * {@link RuleType#PRESENCE_ANY} rules, returns {@link Result#PASS} if any
     * non-blank value is present. For {@link RuleType#VOCAB_MATCH} rules,
     * normalises both candidates and approved terms and returns
     * {@link Result#PASS} on the first match.
     * </p>
     *
     * @param dataset    the JSON dataset to validate
     * @param rule       the validation rule to apply
     * @param vocabulary the vocabulary service for retrieving approved terms
     * @return {@link Result#PASS} or {@link Result#FAIL}
     */
    private Result evaluateRule(JsonNode dataset,
            ValidationRule rule,
            VocabularyService vocabulary) {

        List<String> values = extractMulti(dataset, rule.fields());

        if (values.isEmpty()) {
            FairTests.logInfo("No %s values found", rule.label());
            return Result.FAIL;
        }

        if (rule.type() == RuleType.PRESENCE_ANY) {
            boolean hasValue = values.stream()
                    .map(this::normalise)
                    .anyMatch(v -> !v.isBlank());

            if (hasValue) {
                FairTests.logInfo("%s information found", rule.label());
                return Result.PASS;
            }

            return Result.FAIL;
        }

        // VOCAB_MATCH
        Set<String> approved = normaliseSet(rule.vocabSupplier().apply(vocabulary));

        for (String val : values) {
            String norm = normalise(val);

            if (matches(norm, approved, rule.matchType())) {
                FairTests.logInfo("Approved %s found: %s", rule.label(), val);
                return Result.PASS;
            }
        }

        FairTests.logInfo("No approved %s found", rule.label());
        return Result.FAIL;
    }

    // -------------------------------------------------------------------------
    // Bespoke test methods
    // -------------------------------------------------------------------------

    /**
     * Validates ELSST keywords in the dataset against the ELSST vocabulary.
     *
     * <p>
     * Candidate keywords are those whose {@code vocab} field equals
     * {@code "ELSST"} (case-sensitive) and whose {@code vocabUri} field
     * contains the substring {@code "elsst"}.
     * </p>
     *
     * <p>
     * The language codes to validate against are read from the dataset's
     * top-level {@code langAvailableIn} array. Validation is attempted for
     * each language code in turn; the method returns {@link Result#PASS} as
     * soon as any language produces a successful match. If
     * {@code langAvailableIn} is absent, empty, or not an array, the method
     * returns {@link Result#FAIL} because no language code is available to
     * validate against.
     * </p>
     *
     * @param dataset    the JSON dataset to validate
     * @param vocabulary the vocabulary service to use for validation
     * @return {@link Result#PASS} if at least one candidate keyword matches an
     *         ELSST term in any of the declared languages;
     *         {@link Result#FAIL} otherwise
     */
    private Result checkElsstKeywords(JsonNode dataset,
            VocabularyService vocabulary) {

        JsonNode keywords = dataset.path("keywords");

        if (!keywords.isArray())
            return Result.FAIL;

        List<String> terms = new ArrayList<>();
        for (JsonNode k : keywords) {
            if ("ELSST".equals(k.path("vocab").asText())
                    && k.path("vocabUri").asText().contains("elsst")) {
                terms.add(k.path("term").asText());
            }
        }

        JsonNode langAvailableIn = dataset.path("langAvailableIn");

        if (!langAvailableIn.isArray() || langAvailableIn.isEmpty()) {
            FairTests.logWarning(
                    "No langAvailableIn codes found — cannot validate "
                            + "ELSST keywords");
            return Result.FAIL;
        }

        for (JsonNode langNode : langAvailableIn) {
            String lang = langNode.asText().trim();
            if (lang.isEmpty())
                continue;
            if (vocabulary.validateElsstKeywords(terms, lang) == Result.PASS) {
                FairTests.logInfo(
                        "ELSST keywords validated successfully for "
                                + "language %s",
                        lang);
                return Result.PASS;
            }
        }

        return Result.FAIL;
    }

    /**
     * Checks whether the dataset references at least one resolvable FAIR
     * vocabulary by scanning all vocabulary-bearing arrays for a non-blank
     * {@code vocabUri} value that can be reached over HTTP.
     *
     * <p>
     * The following top-level arrays are inspected:
     * {@code classifications}, {@code keywords}, {@code unitTypes},
     * {@code typeOfModeOfCollections}, and {@code typeOfTimeMethods}. For
     * each entry, both a non-blank {@code vocab} name and a non-blank
     * {@code vocabUri} must be present. The first {@code vocabUri} that
     * resolves successfully returns {@link Result#PASS}.
     * </p>
     *
     * @param dataset the JSON dataset to inspect
     * @return {@link Result#PASS} if a resolvable vocabulary URI is found;
     *         {@link Result#FAIL} if candidates are present but none resolve
     *         or none have both required attributes;
     *         {@link Result#INDETERMINATE} if an unexpected error occurs
     */
    private Result checkFairVocabulary(JsonNode dataset) {

        try {
            List<String> vocabArrays = List.of(
                    "classifications",
                    "keywords",
                    "unitTypes",
                    "typeOfModeOfCollections",
                    "typeOfTimeMethods");

            for (String field : vocabArrays) {
                JsonNode array = dataset.path(field);
                if (!array.isArray())
                    continue;

                for (JsonNode entry : array) {
                    String vocab = entry.path("vocab").asText("").trim();
                    String vocabUri = entry.path("vocabUri").asText("").trim();

                    if (vocab.isEmpty() || vocabUri.isEmpty())
                        continue;

                    if (!looksResolvable(vocabUri))
                        continue;

                    FairTests.logInfo(
                            "Testing FAIR vocabulary %s at %s",
                            vocab, vocabUri);

                    if (resolves(vocabUri)) {
                        FairTests.logInfo(
                                "Resolvable FAIR vocabulary found: %s",
                                vocabUri);
                        return Result.PASS;
                    }
                }
            }

            FairTests.logInfo("No resolvable FAIR vocabularies found");
            return Result.FAIL;

        } catch (Exception e) {
            FairTests.logWarning(
                    "FAIR vocabulary check failed: %s", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks whether the dataset's metadata is grounded by attempting to
     * resolve the {@code studyUrl} field over HTTP.
     *
     * <p>
     * {@code studyUrl} is expected to be a persistent, resolvable URL
     * (typically a DOI landing page) that grounds the metadata record in a
     * retrievable resource. If the field is absent or blank, the method
     * returns {@link Result#FAIL}. If the URL resolves successfully,
     * {@link Result#PASS} is returned.
     * </p>
     *
     * @param dataset the JSON dataset to inspect
     * @return {@link Result#PASS} if {@code studyUrl} resolves;
     *         {@link Result#FAIL} if the field is absent, blank, or does not
     *         resolve; {@link Result#INDETERMINATE} if an unexpected error
     *         occurs
     */
    private Result checkGroundedMetadata(JsonNode dataset) {

        try {
            String studyUrl = dataset.path("studyUrl").asText("").trim();

            if (studyUrl.isEmpty()) {
                FairTests.logInfo("No studyUrl found for grounded metadata check");
                return Result.FAIL;
            }

            if (!looksResolvable(studyUrl)) {
                FairTests.logInfo(
                        "studyUrl does not use an open protocol: %s",
                        studyUrl);
                return Result.FAIL;
            }

            FairTests.logInfo(
                    "Testing grounded metadata URL: %s", studyUrl);

            if (resolves(studyUrl)) {
                FairTests.logInfo(
                        "Resolvable grounded metadata URL found: %s",
                        studyUrl);
                return Result.PASS;
            }

            FairTests.logInfo(
                    "studyUrl did not resolve: %s", studyUrl);
            return Result.FAIL;

        } catch (Exception e) {
            FairTests.logWarning(
                    "Grounded metadata check failed: %s", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks whether any PID in the dataset's {@code pidStudies} array can be
     * resolved via an open HTTP protocol.
     *
     * <p>
     * For each entry in {@code pidStudies}, the {@code agency} and
     * {@code pid} fields are read. A resolution URL is constructed using
     * {@link #buildResolutionUrl(String, String)} (supporting DOI, Handle,
     * and ARK agencies). The first URL that uses an open protocol and
     * resolves successfully returns {@link Result#PASS}.
     * </p>
     *
     * <p>
     * Note: the {@code pid} values in the CDC JSON may include a
     * scheme prefix (e.g. {@code "doi:10.17903/FK2/BVFEYX"}). The prefix
     * is stripped before constructing the resolution URL.
     * </p>
     *
     * @param dataset the JSON dataset to inspect
     * @return {@link Result#PASS} if a resolvable PID is found;
     *         {@link Result#FAIL} if no resolvable PID is found;
     *         {@link Result#INDETERMINATE} if an unexpected error occurs
     */
    private Result checkRetrievableProtocol(JsonNode dataset) {

        try {
            JsonNode pidStudies = dataset.path("pidStudies");

            if (!pidStudies.isArray() || pidStudies.isEmpty()) {
                FairTests.logInfo("No pidStudies found");
                return Result.FAIL;
            }

            for (JsonNode entry : pidStudies) {
                String agency = entry.path("agency").asText("").trim();
                String pid = entry.path("pid").asText("").trim();

                // Strip any scheme prefix, e.g. "doi:10.x/y" -> "10.x/y"
                String value = stripPidPrefix(pid);

                String resolved = buildResolutionUrl(agency, value);
                if (resolved == null)
                    continue;

                FairTests.logInfo(
                        "Testing PID resolution URL: %s", resolved);

                if (looksResolvable(resolved) && resolves(resolved)) {
                    FairTests.logInfo(
                            "Resolvable open protocol found: %s", resolved);
                    return Result.PASS;
                }
            }

            FairTests.logInfo("No retrievable open protocol found");
            return Result.FAIL;

        } catch (Exception e) {
            FairTests.logWarning(
                    "Retrievable protocol check failed: %s", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks whether the dataset is searchable by verifying that
     * {@code studyXmlSourceUrl} contains an OAI-PMH {@code GetRecord}
     * request, indicating the record is exposed via OAI-PMH and is therefore
     * discoverable by harvesters.
     *
     * <p>
     * A URL is accepted if it is non-blank and contains both
     * {@code "oai"} and {@code "GetRecord"} as substrings. This matches the
     * canonical OAI-PMH endpoint pattern used in CDC records (e.g.
     * {@code .../oai?verb=GetRecord&identifier=...}).
     * </p>
     *
     * @param dataset the JSON dataset to inspect
     * @return {@link Result#PASS} if a valid OAI-PMH source URL is found;
     *         {@link Result#FAIL} otherwise;
     *         {@link Result#INDETERMINATE} if an unexpected error occurs
     */
    private Result checkSearchable(JsonNode dataset) {

        try {
            String sourceUrl = dataset.path("studyXmlSourceUrl")
                    .asText("").trim();

            if (sourceUrl.isEmpty()) {
                FairTests.logInfo("No studyXmlSourceUrl found");
                return Result.FAIL;
            }

            if (sourceUrl.contains("oai") && sourceUrl.contains("GetRecord")) {
                FairTests.logInfo(
                        "OAI-PMH source URL found: %s", sourceUrl);
                return Result.PASS;
            }

            FairTests.logInfo(
                    "studyXmlSourceUrl is not an OAI-PMH GetRecord URL: %s",
                    sourceUrl);
            return Result.FAIL;

        } catch (Exception e) {
            FairTests.logWarning(
                    "Searchable check failed: %s", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    // -------------------------------------------------------------------------
    // Shared HTTP helpers
    // -------------------------------------------------------------------------

    /**
     * Strips a scheme prefix from a PID value, returning the bare identifier.
     *
     * <p>
     * For example, {@code "doi:10.17903/FK2/BVFEYX"} becomes
     * {@code "10.17903/FK2/BVFEYX"}. If no colon-delimited prefix is found,
     * or if the value is blank, the value is returned unchanged.
     * </p>
     *
     * @param pid the raw PID value, possibly prefixed with a scheme
     * @return the PID value with any leading scheme prefix removed
     */
    private String stripPidPrefix(String pid) {
        if (pid == null || pid.isEmpty())
            return pid;
        int colon = pid.indexOf(':');
        if (colon > 0 && colon < pid.length() - 1) {
            String candidate = pid.substring(colon + 1);
            // Only strip if the remainder doesn't start with "//"
            // (which would indicate a full URI like "https://")
            if (!candidate.startsWith("//"))
                return candidate;
        }
        return pid;
    }

    /**
     * Constructs a resolution URL for the given PID agency and identifier
     * value. Supports DOI, Handle, and ARK agencies. Returns {@code null}
     * for URNs, unrecognised agencies, or {@code null} inputs.
     *
     * @param agency the PID agency (e.g. {@code "DOI"}, {@code "Handle"},
     *               {@code "ARK"}); case-insensitive
     * @param value  the bare PID value, with any scheme prefix already
     *               stripped
     * @return the resolution URL, or {@code null} if not constructable
     */
    private String buildResolutionUrl(String agency, String value) {
        if (agency == null || value == null)
            return null;

        return switch (agency.trim().toLowerCase()) {
            case "doi" -> "https://doi.org/" + value.trim();
            case "handle" -> "https://hdl.handle.net/" + value.trim();
            case "ark" -> "https://n2t.net/" + value.trim();
            default -> null;
        };
    }

    /**
     * Returns {@code true} if the value is a non-blank string starting with
     * {@code "http://"} or {@code "https://"}.
     *
     * @param value the string to check
     * @return {@code true} if the value looks resolvable over HTTP
     */
    private boolean looksResolvable(String value) {
        return value != null && !value.isBlank()
                && (value.startsWith("http://")
                        || value.startsWith("https://"));
    }

    /**
     * Attempts an HTTP GET to the given URL and returns {@code true} if the
     * response code is in the 200–399 range. Redirects are followed. Both
     * connect and read timeouts are set to 5 seconds. Any exception causes
     * the method to return {@code false}.
     *
     * @param url the URL to test
     * @return {@code true} if the URL resolves successfully
     */
    private boolean resolves(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int code = conn.getResponseCode();
            FairTests.logInfo(
                    "Resolution check %s returned HTTP %d", url, code);
            return code >= 200 && code < 400;

        } catch (Exception e) {
            FairTests.logInfo(
                    "Resolution failed for %s: %s", url, e.getMessage());
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Extraction helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts candidate values from multiple JSON paths and returns them as
     * a single flat list.
     *
     * @param root  the root JSON node
     * @param paths the paths to extract from
     * @return all extracted string values
     */
    private List<String> extractMulti(JsonNode root, List<String> paths) {
        List<String> results = new ArrayList<>();
        for (String path : paths) {
            results.addAll(extractPath(root, path));
        }
        return results;
    }

    /**
     * Extracts values from a single dot-notation JSON path.
     * Array segments (e.g. {@code "creators[].name"}) are handled by
     * iterating over every element of the array and continuing the
     * traversal on each.
     *
     * @param node the node to start from
     * @param path the dot-notation path (array brackets are stripped and
     *             handled implicitly)
     * @return the list of extracted string values
     */
    private List<String> extractPath(JsonNode node, String path) {
        List<String> results = new ArrayList<>();
        String[] parts = path.split("\\.");
        extractRecursive(node, parts, 0, results);
        return results;
    }

    /**
     * Recursively traverses the JSON structure following the given path
     * parts. When an array is encountered the traversal descends into each
     * element at the same path depth. Leaf values are collected via
     * {@link #extractValue(JsonNode, List)}.
     *
     * @param current the current node
     * @param parts   the path segments
     * @param index   the current segment index
     * @param results the accumulator for extracted values
     */
    private void extractRecursive(JsonNode current, String[] parts,
            int index, List<String> results) {

        if (current == null || current.isNull())
            return;

        if (index == parts.length) {
            extractValue(current, results);
            return;
        }

        String part = parts[index];

        if (current.isArray()) {
            for (JsonNode item : current) {
                extractRecursive(item, parts, index, results);
            }
            return;
        }

        if (current.isObject()) {
            JsonNode next = current.path(part);
            if (!next.isMissingNode()) {
                extractRecursive(next, parts, index + 1, results);
            }
        }
    }

    /**
     * Extracts a string value from a leaf node. Handles three cases:
     * <ul>
     * <li>Textual node: adds the text directly.</li>
     * <li>Array node: recursively extracts from each element.</li>
     * <li>Object node: checks for the first present key among
     * {@code value}, {@code term}, {@code label}, {@code name},
     * {@code agency}, and {@code publisher}, and adds its text.</li>
     * </ul>
     *
     * @param node    the node to extract from
     * @param results the accumulator for extracted values
     */
    private void extractValue(JsonNode node, List<String> results) {
        if (node.isTextual()) {
            String val = node.asText().trim();
            if (!val.isEmpty())
                results.add(val);
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                extractValue(item, results);
            }
            return;
        }

        if (node.isObject()) {
            for (String key : List.of(
                    "value", "term", "label", "name", "agency", "publisher")) {
                if (node.has(key)) {
                    String val = node.path(key).asText("").trim();
                    if (!val.isEmpty()) {
                        results.add(val);
                        return;
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Normalisation and matching
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the candidate matches the approved set using
     * the given strategy.
     *
     * @param candidate the normalised candidate string
     * @param approved  the set of normalised approved terms
     * @param type      {@link MatchType#EXACT} or {@link MatchType#CONTAINS}
     * @return {@code true} if a match is found
     */
    private boolean matches(String candidate, Set<String> approved,
            MatchType type) {
        return switch (type) {
            case EXACT -> approved.contains(candidate);
            case CONTAINS -> approved.stream().anyMatch(candidate::contains);
        };
    }

    /**
     * Normalises a string by trimming whitespace, converting to lowercase,
     * and collapsing internal runs of whitespace to a single space. Returns
     * an empty string for {@code null} input.
     *
     * @param s the string to normalise
     * @return the normalised string
     */
    private String normalise(String s) {
        if (s == null)
            return "";
        return s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * Applies {@link #normalise(String)} to every member of a set. Returns
     * an empty set for {@code null} input.
     *
     * @param set the set to normalise
     * @return a new set of normalised strings
     */
    private Set<String> normaliseSet(Set<String> set) {
        if (set == null)
            return Set.of();
        return set.stream().map(this::normalise).collect(Collectors.toSet());
    }
}