package eu.cessda.fairtests;

import java.io.IOException;
import java.io.InputStream;
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
     * MATCH TYPE
     * Defines how to compare extracted values against the approved vocabulary
     * terms.
     * - EXACT: the extracted value must exactly match one of the approved terms.
     * - CONTAINS: the extracted value must contain (as a substring) one of the
     * approved terms.
     * This is useful for cases where the extracted value may include additional
     * context or
     * qualifiers around the approved term, which is common in real-world datasets.
     */
    private enum MatchType {
        EXACT,
        CONTAINS
    }

    /**
     * RULE TYPE
     * Defines the type of validation rule, which can be used to determine how to
     * evaluate the rule. For example, VOCAB_MATCH rules require checking against an
     * approved vocabulary, while PRESENCE_ANY rules simply check for the presence
     * of
     * any value in the specified field(s) without needing to compare against a
     * vocabulary.
     * This allows us to have different evaluation logic for different types of
     * rules,
     * while still using the same underlying structure for defining the rules and
     * the core evaluation engine.
     * For example, the PROVENANCE test would be a PRESENCE_ANY rule, while the
     * other tests that check
     * against approved vocabularies would be VOCAB_MATCH rules.
     * This design allows us to easily add new types of rules in the future if
     * needed,
     * without affecting the core logic of the runTest method or the generic
     * evaluation engine.
     */
    private enum RuleType {
        VOCAB_MATCH,
        PRESENCE_ANY
    }

    /**
     * VALIDATION RULE
     * This record encapsulates all the information needed to validate a specific
     * test type:
     * - type: the type of rule (e.g. VOCAB_MATCH, PRESENCE_ANY) which determines
     * how the rule is evaluated.
     * - fields: the JSON field(s) to extract values from for this test. This can
     * support multiple paths if needed, allowing for flexibility in handling
     * different JSON structures across datasets.
     * - extractionMode: the strategy to use for extracting values from that field.
     * - vocabSupplier: a function that takes a VocabularyService and returns the
     * set of approved terms for that test.
     * - matchType: the type of match to perform when comparing extracted values
     * against the approved terms.
     * - label: a human-readable label for logging purposes.
     */
    private record ValidationRule(
            RuleType type,
            List<String> fields, // supports multiple paths
            Function<VocabularyService, Set<String>> vocabSupplier,
            MatchType matchType,
            String label) {
    }

    /**
     * VALIDATIONRULE DEFINITIONS
     * This map defines the validation rules for each test type. Each rule
     * specifies:
     * - The JSON field to extract from the dataset.
     * - The extraction mode to use for that field.
     * - A function to retrieve the approved vocabulary terms for that test.
     * - The type of match to perform (exact or contains).
     * - A human-readable label for logging purposes.
     */
    private final Map<TestType, ValidationRule> rules = Map.of(

            /**
             * For Access Rights, we look for a scalar field 'dataAccess' and check if it
             * contains
             * any of the approved access rights terms. The 'CONTAINS' match type allows for
             * values
             * like "Restricted - see documentation" to pass if "Restricted" is an approved
             * term.
             */
            TestType.ACCESS_RIGHTS,
            new ValidationRule(
                    RuleType.VOCAB_MATCH,
                    List.of("dataAccess"),
                    VocabularyService::getApprovedAccessRightsTerms,
                    MatchType.CONTAINS,
                    "Access Rights"),

            /**
             * For PID, we use the generic extraction mode to handle various possible
             * structures
             * (e.g. a simple string, an object with a 'pid' field, etc.).
             * We check for an exact match against approved PID schemas.
             */
            TestType.PID,
            new ValidationRule(
                    RuleType.VOCAB_MATCH,
                    List.of("pidStudies"),
                    VocabularyService::getApprovedPidSchemas,
                    MatchType.EXACT,
                    "PID schema"),

            /**
             * For Topic Classification, we also use the generic extraction mode to handle
             * various structures,
             * but we check for an exact match against approved topic classification terms.
             * The spec suggests 'classifications[*].term' but in practice we find
             * 'classifications'
             * with various structures, so we use the generic extractor to be flexible.
             * The match is exact because we expect the values to be controlled vocabulary
             * terms,
             * not free text.
             */
            TestType.TOPIC_CLASS,
            new ValidationRule(
                    RuleType.VOCAB_MATCH,
                    List.of("classifications"),
                    VocabularyService::getApprovedTopicClassTerms,
                    MatchType.EXACT,
                    "Topic Classification"),

            /**
             * For DDI Analysis Unit, we also use the generic extraction mode to handle
             * various structures,
             * but we check for an exact match against approved analysis unit terms.
             * The spec suggests 'unitTypes[*].term' but in practice we find 'unitTypes'
             * with various structures, so we use the generic extractor to be flexible.
             * The match is exact because we expect the values to be controlled vocabulary
             * terms, not free text.
             */
            TestType.DDI_ANALYSIS_UNIT,
            new ValidationRule(
                    RuleType.VOCAB_MATCH,
                    List.of("unitTypes"),
                    VocabularyService::getApprovedAnalysisUnitTerms,
                    MatchType.EXACT,
                    "Analysis Unit"),

            /**
             * For DDI Collection Mode, we also use the generic extraction mode to handle
             * various structures,
             * but we check for an exact match against approved collection mode terms.
             * The spec suggests 'typeOfModeOfCollections[*].term' but in practice we find
             * 'typeOfModeOfCollections'
             * with various structures, so we use the generic extractor to be flexible.
             * The match is exact because we expect the values to be controlled vocabulary
             * terms, not free text.
             */
            TestType.DDI_COLLECTION_MODE,
            new ValidationRule(
                    RuleType.VOCAB_MATCH,
                    List.of("typeOfModeOfCollections"),
                    VocabularyService::getApprovedCollectionModeTerms,
                    MatchType.EXACT,
                    "Collection Mode"),

            /**
             * For DDI Time Method, we also use the generic extraction mode to handle
             * various structures,
             * but we check for an exact match against approved time method terms.
             * The spec suggests 'typeOfTimeMethods[*].term' but in practice we find
             * 'typeOfTimeMethods'
             * with various structures, so we use the generic extractor to be flexible.
             * The match is exact because we expect the values to be controlled vocabulary
             * terms, not free text.
             */
            TestType.DDI_TIME_METHOD,
            new ValidationRule(
                    RuleType.VOCAB_MATCH,
                    List.of("typeOfTimeMethods"),
                    VocabularyService::getApprovedTimeMethodTerms,
                    MatchType.EXACT,
                    "Time Method"),

            /**
             * Note there is a bug in the spec: rather than
             * 'typeOfSamplingProcedures[*].term'
             * 'samplingProcedureFreeTexts' is used, and the values are free text rather
             * than controlled vocabulary terms. Hence we use CONTAINS rather than EXACT,
             * and we normalise the approved terms to allow for partial matches.
             */
            TestType.DDI_SAMPLEPROC,
            new ValidationRule(
                    RuleType.VOCAB_MATCH,
                    List.of("samplingProcedureFreeTexts"),
                    VocabularyService::getApprovedSamplingProcTerms,
                    MatchType.CONTAINS,
                    "Sampling Procedure"),

            /**
             * For Provenance, we check for the presence of either a 'publisher' or
             * 'creators' field with a 'name' subfield, or a 'funding' field with an
             * 'agency'
             * subfield. We use the PRESENCE_ANY rule type because we just want to check
             * for the presence of any value in those fields, without needing to compare
             * against an approved vocabulary. The generic extraction mode allows us to
             * handle the various ways in which provenance information might be structured
             * in
             * the JSON datasets, based on our analysis of real-world datasets and the
             * flexibility needed to accommodate the spec's suggestions and the reality of
             * what we find in the wild. The presence of any value in those fields would
             * indicate that provenance information is present, which is what this test is
             * designed to check for.
             * 
             */
            TestType.PROVENANCE,
            new ValidationRule(
                    RuleType.PRESENCE_ANY,
                    List.of(
                            "publisher.publisher",
                            "creators[].name",
                            "funding[].agency"),
                    null,
                    null,
                    "Provenance"));

    @Override
    /**
     * The main entry point for running a test on a dataset. It reads the JSON from
     * the input stream,
     * checks for the presence of a dataset ID, and then dispatches to the
     * appropriate validation
     * logic based on the test type. For most tests, it looks up the corresponding
     * ValidationRule
     * and evaluates it against the dataset. For special cases like PROVENANCE and
     * ELSST_KEYWORDS,
     * it calls dedicated methods to handle the specific logic for those tests.
     * 
     * The method returns PASS, FAIL, or INDETERMINATE based on the outcome of the
     * validation.
     * The INDETERMINATE result is used when the test cannot be performed due to
     * missing information
     * (e.g. no dataset ID) or when there is no defined rule for the given test
     * type.
     * The method also logs relevant information at each step to aid in debugging
     * and understanding the validation process.
     * Note that the method assumes that the input JSON structure is based on the
     * CDC schema,
     * and the extraction logic in the ValidationRules is designed to handle the
     * variations
     * observed in real-world datasets following that schema.
     * 
     * @param test        the type of test to run
     * @param inputStream the input stream containing the JSON dataset
     * @param vocabulary  the vocabulary service to use for retrieving approved
     *                    terms
     * @return the result of the test (PASS, FAIL, or INDETERMINATE)
     */
    public Result runTest(TestType test, InputStream inputStream, VocabularyService vocabulary)
            throws IOException {

        JsonNode dataset = mapper.readTree(inputStream);

        if (dataset.path("id").isMissingNode()) {
            FairTests.logWarning("Dataset ID is required for validation but was not found. Cannot perform test.");
            return Result.INDETERMINATE;
        }

        /**
         * Special case for ELSST_KEYWORDS, which also doesn't fit the standard
         * pattern and requires checking the 'keywords' array for specific vocab terms
         * with a 'vocab' of 'ELSST'. The validation logic for this test is specific
         * enough to warrant
         * its own method, rather than trying to shoehorn it into the generic rule
         * evaluation engine
         */
        if (test == TestType.ELSST_KEYWORDS) {
            return checkElsstKeywords(dataset, vocabulary);
        }

        /**
         * For all other tests, we look up the corresponding ValidationRule from
         * the 'rules' map and evaluate it against the dataset.
         * If there is no defined rule for the given test type, we log a warning
         * and return INDETERMINATE.
         * This allows us to easily add new tests in the future by simply defining
         * a new ValidationRule and adding it to the map, without needing to change
         * the core logic of the runTest method or the evaluation engine.
         * The design of the ValidationRule and the rules map allows for a clean
         * separation of the test definitions from the core evaluation logic,
         * making the code more maintainable and extensible.
         */
        ValidationRule rule = rules.get(test);
        if (rule == null) {
            FairTests.logWarning("No rule defined for  %s", test);
            return Result.INDETERMINATE;
        }

        return evaluateRule(dataset, rule, vocabulary);
    }

    /**
     * CORE ENGINE
     * The core engine for evaluating a validation rule against a dataset. It
     * extracts the relevant values
     * from the dataset based on the rule's field and extraction mode, and then
     * checks if any of the extracted values match the approved vocabulary terms
     * according to the specified match type.
     * The method returns PASS if at least one approved term is found, FAIL if no
     * approved terms are found, and logs relevant information at each step to aid
     * in understanding the validation process.
     * 
     * @param dataset    the JSON dataset to validate
     * @param rule       the validation rule to apply
     * @param vocabulary the vocabulary service to use for retrieving approved terms
     * @return the result of the validation
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

        // VOCAB MATCH
        Set<String> approved = normaliseSet(rule.vocabSupplier().apply(vocabulary));

        for (String val : values) {
            String norm = normalise(val);

            if (matches(norm, approved, rule.matchType())) {
                FairTests.logInfo("Approved %s found: %s" ,
                        rule.label(), val);
                return Result.PASS;
            }
        }

        FairTests.logInfo("No approved %s found", rule.label());
        return Result.FAIL;
    }

    /**
     * Checks if a candidate string matches any of the approved strings based on the
     * specified match type.
     * The method supports two match types:
     * - EXACT: Checks for an exact match
     * - CONTAINS: Checks if the candidate contains any of the approved strings as a
     * substring
     * This allows for flexibility in handling cases where the candidate string may
     * include additional context or qualifiers around the approved term, which is
     * common in real-world datasets. For example, a candidate value of "Restricted
     * - see documentation" would match an approved term of "Restricted" when using
     * the CONTAINS match type.
     * 
     * @param candidate the string to check for a match against the approved terms
     * @param approved  the set of approved strings
     * @param type      the match type to use for the comparison (EXACT or CONTAINS)
     * @return boolean true if a match is found based on the specified match type,
     *         false otherwise
     */
    private boolean matches(String candidate, Set<String> approved, MatchType type) {
        return switch (type) {
            case EXACT -> approved.contains(candidate);
            case CONTAINS -> approved.stream().anyMatch(candidate::contains);
        };
    }

    /**
     * Normalises a string by trimming whitespace, converting to lowercase, and
     * collapsing multiple spaces into a single space. This is useful for improving
     * the chances of matching candidate strings against approved terms in cases
     * where there may be variations in formatting, such as extra spaces or
     * differences in capitalization. By normalising both the candidate strings and
     * the approved terms before comparison, we can achieve more robust matching
     * that is less sensitive to minor formatting differences.
     *
     * @param s the string to normalise
     * @return String the normalised string, or an empty string if the input is null
     */
    private String normalise(String s) {
        if (s == null)
            return "";
        return s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * Normalises a set of strings by applying the normalise method to each string.
     * This is useful for preparing the approved vocabulary terms for comparison
     * against candidate strings, ensuring that both the approved terms and the
     * candidate strings are in a consistent format for matching. By normalising the
     * approved terms, we can improve the chances of successful matches even when
     * there are variations in formatting in the original dataset.
     * The method takes a set of strings as input and returns a new set containing
     * the normalised versions of those strings. If the input set is null, it
     * returns an empty set to avoid null pointer exceptions in the matching logic.
     * 
     * @param set the set of strings to normalise
     * @return Set<String> the set of normalised strings, or an empty set if the
     *         input is null
     */
    private Set<String> normaliseSet(Set<String> set) {
        if (set == null)
            return Set.of();
        return set.stream().map(this::normalise).collect(Collectors.toSet());
    }

    /**
     * SPECIAL CASES
     * These methods handle specific test types that do not fit the standard pattern
     * of extracting values from
     * a specific field and comparing them against an approved vocabulary. 
     */
    

    /**
     * Validates ELSST keywords in the dataset against the ELSST vocabulary.
     *
     * <p>Candidate keywords are those whose {@code vocab} field equals
     * {@code "ELSST"} (case-sensitive) and whose {@code vocabUri} field
     * contains the substring {@code "elsst"}.</p>
     *
     * <p>The language codes to validate against are read from the dataset's
     * top-level {@code langAvailableIn} array (e.g.
     * {@code ["en", "sv"]}). Validation is attempted for each language code
     * in turn; the method returns {@link Result#PASS} as soon as any language
     * produces a successful match. If {@code langAvailableIn} is absent,
     * empty, or not an array, the method returns {@link Result#FAIL} because
     * no language code is available to validate against.</p>
     *
     * @param dataset    the JSON dataset to validate
     * @param vocabulary the vocabulary service to use for validation
     * @return {@link Result#PASS} if at least one candidate keyword matches an
     *         ELSST term in any of the declared languages;
     *         {@link Result#FAIL} otherwise
     */
    private Result checkElsstKeywords(JsonNode dataset, VocabularyService vocabulary) {
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
            FairTests.logWarning("No langAvailableIn codes found — cannot validate ELSST keywords");
            return Result.FAIL;
        }

        for (JsonNode langNode : langAvailableIn) {
            String lang = langNode.asText().trim();
            if (lang.isEmpty()) {
                continue;
            }
            if (vocabulary.validateElsstKeywords(terms, lang) == Result.PASS) {
                FairTests.logInfo("ELSST keywords validated successfully for language %s", lang);
                return Result.PASS;
            }
        }

        return Result.FAIL;
    }

    /**
     * EXTRACTION STRATEGIES
     * These methods implement the different strategies for extracting values from
     * the JSON dataset based on the specified extraction mode in the
     * ValidationRule. The
     * extractMulti method takes a list of JSON paths and applies the appropriate
     * extraction logic for each path based on the specified extraction mode. The
     * extractPath method handles the extraction of values from a single JSON path,
     * supporting both scalar
     * values and arrays, and the extractRecursive method performs the actual
     * traversal of the JSON structure based on the path parts, handling array
     * syntax (e.g. 'field[]') to extract values from arrays of objects. The
     * extraction logic is designed to be flexible and robust, allowing us to handle
     * the various ways in which relevant information might be structured in the
     * JSON datasets, based on our analysis of real-world datasets and the
     * flexibility needed to accommodate the spec's suggestions and the reality of
     * what we find in the wild.
     * 
     * @param root  the root JSON node to extract values from
     * @param paths the list of JSON paths to extract values from, which can support
     *              multiple paths for flexibility in handling different JSON
     *              structures across datasets
     * @return List<String> the list of extracted values from the specified paths in
     *         the JSON dataset, which will be used for validation against the
     *         approved vocabularies
     */
    private List<String> extractMulti(JsonNode root, List<String> paths) {
        List<String> results = new ArrayList<>();

        for (String path : paths) {
            results.addAll(extractPath(root, path));
        }

        return results;
    }

    /**
     * Extracts values from a single JSON path. The path can include dot notation
     * for nested fields and array syntax (e.g. 'field[]') to indicate that the
     * field is an array of objects. The method traverses the JSON structure based
     * on the path parts, extracting values according to the specified extraction
     * mode. For scalar fields, it extracts the text value directly. For arrays of
     * strings, it extracts each string value. For arrays of objects, it looks for
     * common keys like 'value', 'term', 'label', 'name', 'agency', or 'pid' to
     * extract the relevant text value from each object in the array. This method is
     * designed to be flexible and robust, allowing us to handle the various ways in
     * which relevant information might be structured in the JSON datasets, based on
     * our analysis of real-world datasets and the flexibility needed to accommodate
     * the spec's suggestions and the reality of what we find in the wild.
     * 
     * @param node the JSON node to extract values from
     * @param path the JSON path to extract values from
     * @return List<String> the list of extracted values from the specified JSON
     *         path, which will be used for validation against the approved
     *         vocabularies
     */
    private List<String> extractPath(JsonNode node, String path) {
        List<String> results = new ArrayList<>();

        String[] parts = path.split("\\.");

        extractRecursive(node, parts, 0, results);

        return results;
    }

    /**
     * Recursively extracts values from the JSON structure based on the path parts.
     * 
     * @param current the current JSON node in the traversal
     * @param parts   the array of path parts
     * @param index   the current index in the path parts array
     * @param results the list to which extracted values are added
     */
    private void extractRecursive(JsonNode current,
                              String[] parts,
                              int index,
                              List<String> results) {

    if (current == null || current.isNull()) return;

    // FINAL STEP: extract values
    if (index == parts.length) {
        extractValue(current, results);
        return;
    }

    String part = parts[index];

    if (current.isArray()) {
        // stay on same index, iterate elements
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
 * Extracts a string value from a JSON node, checking for common patterns such as:
 * - If the node is a textual value, return it directly.
 * - If the node is an object, check for common keys like 'value', 'term',
 * 'label', 'name', 'agency', or 'pid' and return the corresponding value if
 * found.
 * This method is used by the generic extraction strategy to handle the various
 * ways in which relevant information might be structured in the JSON datasets,
 * allowing us to extract the necessary values for validation against the approved
 * vocabularies even when the structure is not consistent across datasets.
 * The method adds any extracted values to the results list, which allows it to
 * handle cases where the node is an array of objects, extracting values from each
 * object in the array and adding them to the results list. This design allows for
 * flexibility in handling different JSON structures while still extracting the
 * relevant values needed for validation.
 * 
 * @param node the JSON node to extract the text from
 * @param results the list to which extracted values are added
 */
private void extractValue(JsonNode node, List<String> results) {
    if (node.isTextual()) {
        String val = node.asText().trim();
        if (!val.isEmpty()) results.add(val);
        return;
    }

    if (node.isArray()) {
        for (JsonNode item : node) {
            extractValue(item, results); // flatten
        }
        return;
    }

    if (node.isObject()) {
        for (String key : List.of("value", "term", "label", "name", "agency", "publisher")) {
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

}
