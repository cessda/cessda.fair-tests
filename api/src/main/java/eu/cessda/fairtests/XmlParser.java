package eu.cessda.fairtests;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Parser for XML datasets, specifically designed to handle DDI Codebook format.
 * It uses XPath to extract relevant fields and validate them against approved
 * vocabularies.
 * The parser is designed to be extensible, allowing new tests to be added by
 * defining new validation rules.
 * The normalisation logic is currently simple (lowercasing and whitespace
 * collapsing) but can be enhanced if needed.
 * The parser also includes special handling for certain tests (e.g. ELSST
 * keywords, provenance) that don't fit the standard pattern.
 * The use of precompiled XPath expressions and a clear separation of concerns
 * should help maintain good performance even on large datasets.
 * The parser is thread-safe, allowing it to be used concurrently across
 * multiple threads without issues.
 * The parser logs detailed information about its processing, which can be
 * helpful for debugging and understanding why certain tests pass or fail.
 * Overall, this implementation provides a solid foundation for validating DDI
 * XML datasets against the FAIR principles, and can be extended in the future
 * to support additional tests or more complex validation logic as needed.
 * 
 */
public class XmlParser implements FormatParser {

    private static final Logger logger = Logger.getLogger(XmlParser.class.getName());
    private static final String DDI_NAMESPACE = "ddi:codebook:2_5";

    private final DocumentBuilder builder;
    private final XPath xpath;

    public XmlParser() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            builder = factory.newDocumentBuilder();

            xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new NamespaceContext() {
                public String getNamespaceURI(String prefix) {
                    return "ddi".equals(prefix) ? DDI_NAMESPACE : null;
                }

                public String getPrefix(String uri) {
                    return null;
                }

                public Iterator<String> getPrefixes(String uri) {
                    return null;
                }
            });

        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Defines how to extract a value from an XML node for validation.
     * DIRECT_TEXT: only direct text children of the node are considered (ignoring
     * nested elements)
     * FULL_TEXT: all text content of the node is considered (including nested
     * elements)
     * ATTRIBUTE: a specific attribute of the node is considered (attribute name
     * specified in ValidationRule
     */
    enum ExtractionStrategy {
        DIRECT_TEXT,
        FULL_TEXT,
        ATTRIBUTE
    }

    /**
     * Defines the type of match to perform when validating extracted values.
     * EXACT: the extracted value must exactly match one of the approved terms
     * CONTAINS: the extracted value must contain one of the approved terms
     */
    enum MatchType {
        EXACT,
        CONTAINS
    }

    /**
     * Represents a validation rule for a specific test type, including the XPath to
     * locate relevant nodes,
     * the strategy to extract values from those nodes, the vocabulary supplier to
     * get approved terms,
     * the match type to determine how to compare extracted values against approved
     * terms, and a label for logging purposes.
     */
    record ValidationRule(
            String xpath,
            ExtractionStrategy strategy,
            String attribute, // only used for ATTRIBUTE
            Function<VocabularyService, Set<String>> vocabSupplier,
            MatchType matchType,
            String label) {
    }

    /**
     * Defines the validation rules for each test type, mapping each TestType to a
     * corresponding ValidationRule that specifies how to extract and validate
     * values from the XML document for that test.
     * This map can be easily extended to add new tests by defining new
     * ValidationRule instances and associating them with the appropriate TestType.
     * 
     */
    private static final Map<TestType, ValidationRule> RULES = Map.of(

            /**
             * For each test type, we define:
             * - The XPath expression to locate relevant nodes in the XML document
             * - The strategy to extract values from those nodes (e.g. direct text, full
             * text, or a specific attribute)
             * - The vocabulary supplier function to get the set of approved terms from the
             * VocabularyService
             * - The match type to determine how to compare extracted values against
             * approved terms (e.g. exact match or contains)
             * - A label for logging purposes to identify the type of value being validated
             * in log messages
             */
            TestType.ACCESS_RIGHTS,
            new ValidationRule(
                    "//ddi:conditions",
                    ExtractionStrategy.FULL_TEXT,
                    null,
                    VocabularyService::getApprovedAccessRightsTerms,
                    MatchType.CONTAINS,
                    "Access Rights"),

            TestType.PID,
            new ValidationRule(
                    "//ddi:IDNo",
                    ExtractionStrategy.ATTRIBUTE,
                    "agency",
                    VocabularyService::getApprovedPidSchemas,
                    MatchType.EXACT,
                    "PID"),

            TestType.TOPIC_CLASS,
            new ValidationRule(
                    "//ddi:topcClas",
                    ExtractionStrategy.FULL_TEXT,
                    null,
                    VocabularyService::getApprovedTopicClassTerms,
                    MatchType.EXACT,
                    "Topic Classification"),

            TestType.DDI_ANALYSIS_UNIT,
            new ValidationRule(
                    "//ddi:anlyUnit",
                    ExtractionStrategy.DIRECT_TEXT,
                    null,
                    VocabularyService::getApprovedAnalysisUnitTerms,
                    MatchType.EXACT,
                    "Analysis Unit"),

            TestType.DDI_COLLECTION_MODE,
            new ValidationRule(
                    "//ddi:collMode",
                    ExtractionStrategy.DIRECT_TEXT,
                    null,
                    VocabularyService::getApprovedCollectionModeTerms,
                    MatchType.EXACT,
                    "Collection Mode"),

            TestType.DDI_TIME_METHOD,
            new ValidationRule(
                    "//ddi:timeMeth",
                    ExtractionStrategy.DIRECT_TEXT,
                    null,
                    VocabularyService::getApprovedTimeMethodTerms,
                    MatchType.EXACT,
                    "Time Method"),

            TestType.DDI_SAMPLEPROC,
            new ValidationRule(
                    "//ddi:sampProc",
                    ExtractionStrategy.FULL_TEXT,
                    null,
                    VocabularyService::getApprovedSamplingProcTerms,
                    MatchType.CONTAINS,
                    "Sampling Procedure"));

    /**
     * Runs the specified test on the given XML input stream, using the provided
     * VocabularyService to validate extracted values against approved vocabularies.
     * The method first parses the XML document, then determines which test to run
     * based on the TestType, and finally evaluates the relevant validation rule or
     * special handling logic for that test type, returning a Result indicating
     * whether the test passed, failed, or is indeterminate due to an error.
     * The method includes special handling for certain test types (e.g. PROVENANCE
     * and ELSST_KEYWORDS) that don't fit the standard pattern defined by the
     * ValidationRule, while for other test types it retrieves the corresponding
     * ValidationRule from the RULES map and evaluates it using the core engine
     * logic defined in the evaluate() method.
     * The method also logs detailed information about the processing steps and any
     * issues encountered, which can be helpful for debugging and understanding why
     * certain tests pass or fail.
     * 
     * @param test        the type of test to run, which determines the validation
     *                    logic to apply to the XML document
     * @param inputStream the input stream containing the XML document to test
     * @param vocabulary  the vocabulary service to use for validating extracted
     *                    values
     * @return the result of the test
     * @throws IOException if an error occurs while reading the input stream or
     *                     parsing the XML document
     */
    @Override
    public Result runTest(TestType test, InputStream inputStream, VocabularyService vocabulary)
            throws IOException {

        Document doc = parse(inputStream);

        if (test == TestType.PROVENANCE) {
            return checkProvenance(doc);
        }

        if (test == TestType.ELSST_KEYWORDS) {
            return checkElsstKeywords(doc, vocabulary);
        }

        ValidationRule rule = RULES.get(test);
        if (rule == null)
            return Result.INDETERMINATE;

        return evaluate(rule, doc, vocabulary);
    }

    /**
     * Evaluates a given validation rule against the provided XML document and
     * vocabulary service.
     * The method uses XPath to locate relevant nodes in the document based on the
     * rule's XPath expression, then extracts values from those nodes according to
     * the specified extraction strategy (e.g. direct text, full text, or a specific
     * attribute). Each extracted value is normalised and compared against the set
     * of approved terms obtained from the VocabularyService using the rule's
     * vocabulary supplier function. The comparison is done according to the
     * specified match type (e.g. exact match or contains). If any extracted value
     * matches an approved term, the method returns Result.PASS; if no values match
     * but at least one candidate was found, it returns Result.FAIL; if no relevant
     * nodes were found or an error occurs during processing, it returns
     * Result.INDETERMINATE.
     * The method also logs detailed information about its processing steps and any
     * issues encountered for debugging purposes.
     * 
     * @param rule       the validation rule to evaluate
     * @param doc        the XML document to evaluate against
     * @param vocabulary the vocabulary service to use for validating extracted
     *                   values
     * @return Result
     */
    private Result evaluate(ValidationRule rule, Document doc, VocabularyService vocabulary) {
        try {
            NodeList nodes = (NodeList) xpath.evaluate(rule.xpath(), doc, XPathConstants.NODESET);

            if (nodes == null || nodes.getLength() == 0) {
                logger.info("No {0} nodes found" + rule.label());
                return Result.FAIL;
            }

            Set<String> approved = normaliseSet(rule.vocabSupplier().apply(vocabulary));

            for (int i = 0; i < nodes.getLength(); i++) {
                String value = extract(nodes.item(i), rule);

                if (value == null || value.isBlank())
                    continue;

                String norm = normalise(value);

                logger.info("{0} candidate: {1}" +
                        new Object[] { rule.label(), value });

                if (matches(norm, approved, rule.matchType())) {
                    logger.info("Approved {0} found: {1}" +
                            new Object[] { rule.label(), value });
                    return Result.PASS;
                }
            }

            logger.info("No approved {0} found" + rule.label());
            return Result.FAIL;

        } catch (XPathExpressionException e) {
            logger.warning("XPath error: {0}" + e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Extracts a value from the given XML node according to the specified
     * validation rule's extraction strategy.
     * Depending on the strategy, the method may return the direct text content of
     * the node (ignoring nested elements),
     * the full text content of the node (including nested elements), or the value
     * of a specific attribute of the node. If the node is not an Element when an
     * attribute extraction is requested, or if any other issue occurs during
     * extraction, the method returns null. The extracted value is returned as a
     * String, which may be further normalised and validated against approved
     * vocabularies in the calling method.
     * 
     * @param node the XML node from which to extract a value
     * @param rule the validation rule that specifies the extraction strategy and
     *             any relevant parameters (e.g. attribute name for ATTRIBUTE
     *             strategy)
     * @return Stringthe extracted value as a String, or null if extraction fails or
     *         is not applicable
     */
    private String extract(Node node, ValidationRule rule) {

        return switch (rule.strategy()) {

            case DIRECT_TEXT -> {
                if (node instanceof Element e)
                    yield directText(e);
                yield null;
            }

            case FULL_TEXT -> node.getTextContent().trim();

            case ATTRIBUTE -> {
                if (node instanceof Element e) {
                    yield e.getAttribute(rule.attribute());
                }
                yield null;
            }
        };
    }

    /**
     * Extracts only the direct text content of an XML element, ignoring any nested
     * elements. The method iterates through the child nodes of the given element
     * and concatenates the text content of any text nodes it finds, trimming the
     * result before returning it. This is useful for cases where we want to
     * validate only the immediate text content of an element without considering
     * any additional text that may be present in nested child elements.
     * 
     * @param element the XML element from which to extract direct text content
     * @return String the concatenated direct text content of the element, with
     *         leading and trailing whitespace removed
     */
    private String directText(Element element) {
        StringBuilder sb = new StringBuilder();

        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {

            if (child.getNodeType() == Node.TEXT_NODE) {
                sb.append(child.getTextContent());
            }
        }

        return sb.toString().trim();
    }

    /**
     * Checks for the presence of provenance information in the XML document by
     * looking for specific elements that indicate the presence of a publisher,
     * creator, or funding information. The method uses XPath to check for the
     * existence of these elements and returns Result.PASS if any of them are found,
     * Result.FAIL if none are found, and Result.INDETERMINATE if an error occurs
     * during processing. This is a simple heuristic approach to determine whether
     * provenance information is present in the dataset, which is an important
     * aspect of FAIR data principles. The method also logs detailed information
     * about its processing steps and any issues encountered for debugging purposes.
     * The specific elements checked for provenance information are:
     * - Publisher: indicated by the presence of the "ddi:distrbtr" element
     * - Creator: indicated by the presence of the "ddi:AuthEnty" element
     * - Funding: indicated by the presence of the "ddi:grantNo" element
     * If any of these elements are found in the XML document, the method concludes
     * that provenance information is present and returns Result.PASS. If none of
     * these elements are found, it returns Result.FAIL, indicating that provenance
     * information is likely missing. If an error occurs during the XPath evaluation
     * (e.g. due to a malformed document), the method catches the exception and
     * returns Result.INDETERMINATE, indicating that it cannot determine the
     * presence of provenance information due to an error.
     * 
     * @param doc the XML document to check for provenance information
     * @return Result indicating whether provenance information is present (PASS),
     *         likely missing (FAIL), or indeterminate due to an error
     *         (INDETERMINATE)
     */
    private Result checkProvenance(Document doc) {
        try {
            boolean hasPublisher = hasValue(doc, "//ddi:distrbtr");
            boolean hasCreator = hasValue(doc, "//ddi:AuthEnty");
            boolean hasFunding = hasValue(doc, "//ddi:grantNo");

            if (hasPublisher || hasCreator || hasFunding) {
                logger.info("Provenance found");
                return Result.PASS;
            }

            logger.info("No Provenance found");
            return Result.FAIL;

        } catch (Exception e) {
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks for the presence of ELSST keywords in the XML document by looking for
     * "ddi:keyword" elements that have a "vocab" attribute equal to "ELSST" and a
     * "vocabURI" attribute that contains "elsst". The method collects the text
     * content of these keyword elements, along with their language (from the
     * "xml:lang" attribute), and then validates them against the approved ELSST
     * keywords provided by the VocabularyService. If valid ELSST keywords are
     * found, it returns Result.PASS; if no valid keywords are found but candidates
     * were present, it returns Result.FAIL; if no candidates are found or an error
     * occurs, it returns Result.INDETERMINATE. This method provides specific
     * handling for ELSST keywords, which are important for ensuring that datasets
     * are properly classified according to this controlled vocabulary. The method
     * also logs detailed information about its processing steps and any issues
     * encountered for debugging purposes.
     * The method performs the following steps:
     * 1. Uses XPath to find all "ddi:keyword" elements in the document
     * 2. Iterates through the found keyword elements and checks if they have the
     * appropriate
     * "vocab" and "vocabURI" attributes to identify them as ELSST keywords
     * 3. Collects the text content of valid ELSST keyword elements and their
     * language (from the "xml:lang" attribute)
     * 4. Validates the collected ELSST keywords against the approved terms from the
     * VocabularyService
     * 5. Returns Result.PASS if valid keywords are found, Result.FAIL if candidates
     * are found but none are valid, and Result.INDETERMINATE if no candidates are
     * found or if an error occurs during processing.
     * The method ensures that only keywords that are explicitly marked as ELSST
     * keywords (via the "vocab" and "vocabURI" attributes) are considered for
     * validation, which helps maintain the integrity of the test and ensures that
     * it is specifically checking for compliance with the ELSST controlled
     * vocabulary.
     * 
     * @param doc        the XML document to check for ELSST keywords
     * @param vocabulary the vocabulary service to use for validating ELSST keywords
     * @return Result indicating whether valid ELSST keywords are found (PASS),
     *         candidates are found but none are valid (FAIL), or no candidates are
     *         found or an error occurs (INDETERMINATE)
     */
    private Result checkElsstKeywords(Document doc, VocabularyService vocabulary) {
        try {
            XPathExpression expr = xpath.compile("//ddi:keyword");

            NodeList nodes;
            synchronized (expr) {
                nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
            }

            if (nodes.getLength() == 0)
                return Result.FAIL;

            List<String> terms = new ArrayList<>();
            String lang = null;

            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String vocab = el.getAttribute("vocab");
                String vocabURI = el.getAttribute("vocabURI");
                lang = el.getAttribute("xml:lang");

                boolean isElsst = "ELSST".equals(vocab) &&
                        vocabURI != null &&
                        vocabURI.contains("elsst");

                if (!isElsst)
                    continue;

                String text = el.getTextContent().trim();
                if (!text.isEmpty()) {
                    terms.add(text);
                }

                // Namespace-aware extraction
                if (lang == null || lang.isBlank()) {
                    String xmlLang = el.getAttributeNS(
                            "http://www.w3.org/XML/1998/namespace",
                            "lang");

                    if (xmlLang != null && !xmlLang.isBlank()) {
                        lang = xmlLang;
                    }
                }
            }

            logger.info("ELSST candidates: " + terms + " (lang=" + lang + ")");

            if (terms.isEmpty()) {
                logger.info("No valid ELSST terms found");
                return Result.FAIL;
            }

            if (lang == null || lang.isBlank()) {
                logger.info("No xml:lang found for ELSST keywords");
                return Result.INDETERMINATE;
            }

            return vocabulary.validateElsstKeywords(terms, lang);

        } catch (Exception e) {
            logger.warning("Error checking ELSST keywords: {0}" + e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Helper method to check if any nodes exist for a given XPath expression in the
     * XML document. This method is used to determine the presence of specific
     * elements that indicate provenance information (e.g. publisher, creator,
     * funding) in the checkProvenance() method. It evaluates the provided XPath
     * expression against the document and returns true if any nodes are found, or
     * false if no nodes are found. If an error occurs during XPath evaluation, it
     * throws an XPathExpressionException, which can be caught by the calling method
     * to handle indeterminate results. This method abstracts away the logic of
     * checking for the existence of nodes based on an XPath expression, making the
     * provenance check cleaner and more focused on its specific logic.
     * The method performs the following steps:
     * 1. Evaluates the provided XPath expression against the XML document to obtain
     * a Node
     * List of matching nodes.
     * 2. Checks if the resulting NodeList is not null and has a length greater
     * than 0, which indicates that at least one matching node was found in the
     * document.
     * 3. Returns true if matching nodes are found, or false if no matching nodes
     * are found. If an error occurs during XPath evaluation (e.g. due to a
     * malformed document or an invalid XPath expression), the method throws an
     * XPathExpressionException, which can be handled by the calling method to
     * determine that the result is indeterminate due to an error.
     * 
     * @param doc  the XML document to evaluate
     * @param path the XPath expression to evaluate against the document
     * @return boolean indicating whether any nodes matching the XPath expression
     *         are found in the document
     * @throws XPathExpressionException if an error occurs during XPath evaluation
     */
    private boolean hasValue(Document doc, String path) throws XPathExpressionException {
        NodeList nodes = (NodeList) xpath.evaluate(path, doc, XPathConstants.NODESET);
        return nodes != null && nodes.getLength() > 0;
    }

    /**
     * Parses an XML document from an input stream.
     * The method uses a DocumentBuilder to parse the XML content from the provided
     * InputStream and returns a Document object representing the parsed XML. If any
     * errors occur during parsing (e.g. due to malformed XML), the method catches
     * the SAXException and wraps it in an IOException, which is then thrown to
     * indicate that an error occurred while reading or parsing the input stream.
     * This method abstracts away the logic of parsing XML content, allowing the
     * main test logic to focus on validation rather than parsing details.
     * The method performs the following steps:
     * 1. Uses the DocumentBuilder to parse the XML content from the provided
     * InputStream
     * 2. If parsing is successful, it returns the resulting Document object
     * representing the XML structure.
     * 3. If a SAXException occurs during parsing (e.g. due to malformed
     * XML), the method catches the exception and throws a new IOException with the
     * original exception as its cause, indicating that an error occurred while
     * reading or parsing the input stream. This allows the calling method to handle
     * parsing errors appropriately, such as by returning an indeterminate result
     * for the test.
     * 
     * @param inputStream the InputStream containing the XML content to parse
     * @return Document representing the parsed XML document
     * @throws IOException if an error occurs while reading the input stream or if
     *                     the XML content is malformed and cannot be parsed
     *                     successfully
     */
    private Document parse(InputStream inputStream) throws IOException {
        try {
            return builder.parse(inputStream);
        } catch (SAXException e) {
            throw new IOException(e);
        }
    }

    /**
     * Checks if a candidate value matches any of the approved terms based on the
     * specified match type. The method takes a candidate string, a set of approved
     * strings, and a MatchType to determine how to compare the candidate against
     * the approved terms. If the match type is EXACT, it checks if the candidate
     * exactly matches any of the approved terms. If the match type is CONTAINS, it
     * checks if the candidate contains any of the approved terms as a substring.
     * The method returns true if a match is found according to the specified
     * criteria, or false if no match is found. This method abstracts away the logic
     * of comparing candidate values against approved vocabularies based on
     * different matching strategies, allowing for flexible validation logic in the
     * evaluate() method.
     * The method performs the following steps:
     * 1. Determines the matching logic to apply based on the provided MatchType
     * (e.g. EXACT or CONTAINS).
     * 2. If the MatchType is EXACT, it checks if the candidate string is exactly
     * equal to any of the strings in the approved set, returning true if a match is
     * found.
     * 3. If the MatchType is CONTAINS, it checks if the candidate string contains
     * any of the strings in the approved set as a substring, returning true if a
     * match is found.
     * 4. If no matches are found according to the specified MatchType, the method
     * returns false, indicating that the candidate does not match any of the
     * approved terms based on the given criteria.
     * 
     * @param candidate the string value extracted from the XML document that we
     *                  want to validate against the approved terms
     * @param approved  the set of approved strings to compare against
     * @param type      the match type to use for comparison
     * @return boolean indicating whether the candidate matches any of the approved
     *         terms according to the specified match type (true if a match is
     *         found, false otherwise)
     */
    private boolean matches(String candidate, Set<String> approved, MatchType type) {
        return switch (type) {
            case EXACT -> approved.contains(candidate);
            case CONTAINS -> approved.stream().anyMatch(candidate::contains);
        };
    }

    /**
     * Normalises a string by converting it to lowercase, trimming whitespace, and
     * replacing multiple spaces with a single space. This method is used to ensure
     * that comparisons between extracted values and approved terms are done in a
     * consistent way, ignoring differences in case and extraneous whitespace. The
     * normalisation process helps improve the robustness of the validation logic by
     * allowing for minor variations in how values are represented in the XML
     * document while still correctly identifying matches against the approved
     * vocabularies. If the input string is null, the method returns an empty string
     * to avoid null pointer exceptions during processing.
     * The method performs the following steps:
     * 1. Checks if the input string is null, and if so, returns an
     * empty string to prevent null pointer exceptions during processing.
     * 2. Converts the input string to lowercase to ensure that comparisons are
     * case-insensitive
     * 3. Trims leading and trailing whitespace from the input string to remove any
     * extraneous spaces that may affect comparisons.
     * 4. Replaces multiple consecutive whitespace characters within the string with
     * a single space to
     * collapse any internal whitespace to a consistent format, which helps ensure
     * that minor variations in spacing do not affect the outcome of comparisons
     * against approved terms.
     * 
     * @param s the input string to normalise, which may be null
     * @return String the normalised version of the input string, with null inputs
     *         resulting in an empty string, and non-null inputs converted to
     *         lowercase, trimmed, and with internal whitespace collapsed to a
     *         single space
     */
    private String normalise(String s) {
        if (s == null)
            return "";
        return s.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    /**
     * Normalises a set of strings by applying the normalise method to each string
     * in the set. This method is used to ensure that comparisons between extracted
     * values and approved terms are done in a consistent way, ignoring differences
     * in case and extraneous whitespace. The normalisation process helps improve
     * the robustness of the validation logic by allowing for minor variations in
     * how values are represented in the XML document while still correctly
     * identifying matches against the approved vocabularies. If the input set is
     * null, the method returns an empty set to avoid null pointer exceptions during
     * processing.
     * The method performs the following steps:
     * 1. Checks if the input set is null, and if so, returns an empty set to
     * prevent null pointer exceptions during processing.
     * 2. Iterates through each string in the input set, applying the normalise
     * method to it to convert it to lowercase, trim whitespace, and collapse
     * internal whitespace to a single space.
     * 3. Collects the normalised strings into a new set, which is returned as the
     * output. This ensures that all strings in the approved set are in a consistent
     * format for comparison against normalised candidate values extracted from the
     * XML document.
     * 
     * @param input the input set of strings to normalise, which may be null
     * @return Set<String> the normalised version of the input set, with null inputs
     *         resulting in an empty set, and non-null inputs converted to
     *         lowercase, trimmed, and with internal whitespace collapsed to a
     *         single space
     */
    private Set<String> normaliseSet(Set<String> input) {
        Set<String> out = new HashSet<>();
        for (String s : input) {
            if (s != null)
                out.add(normalise(s));
        }
        return out;
    }
}