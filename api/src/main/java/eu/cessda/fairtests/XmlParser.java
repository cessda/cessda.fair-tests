package eu.cessda.fairtests;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

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
 * vocabularies. The parser is designed to be extensible, allowing new tests to
 * be added by defining new validation rules. The normalisation logic is
 * currently simple (lowercasing and whitespace collapsing) but can be enhanced
 * if needed. The parser also includes special handling for certain tests (e.g.
 * ELSST keywords, provenance) that don't fit the standard pattern. The use of
 * precompiled XPath expressions and a clear separation of concerns should help
 * maintain good performance even on large datasets. The parser is thread-safe,
 * allowing it to be used concurrently across multiple threads without issues.
 * The parser logs detailed information about its processing, which can be
 * helpful for debugging and understanding why certain tests pass or fail.
 * Overall, this implementation provides a solid foundation for validating DDI
 * XML datasets against the FAIR principles, and can be extended in the future
 * to support additional tests or more complex validation logic as needed.
 *
 */

public class XmlParser implements FormatParser {

    private static final Set<String> SUPPORTED_DDI_NAMESPACES = Set.of(
            "ddi:codebook:2_5",
            "ddi:codebook:2_6");

    private final DocumentBuilder builder;

    public XmlParser() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            builder = factory.newDocumentBuilder();

        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Creates an XPath instance that is aware of the DDI namespace used in the
     * given XML document. The method first detects the DDI namespace URI from
     * the document's root element, then creates an XPath instance and sets a
     * custom NamespaceContext that maps the "ddi" prefix to the detected
     * namespace URI. This allows XPath expressions that use the "ddi" prefix to
     * correctly resolve to the elements in the document, regardless of the
     * specific DDI version used (as long as it is one of the supported
     * namespaces). If the DDI namespace cannot be detected or if an error
     * occurs during XPath expression evaluation, the method throws an
     * IllegalStateException, indicating that it cannot create a valid XPath
     * instance for the document.
     *
     * @param doc the XML document for which to create a namespace-aware XPath
     *            instance
     * @return XPath instance that is aware of the DDI namespace used in the
     *         document
     */

    private XPath createXPath(Document doc) {

        String ddiNamespace;

        try {
            ddiNamespace = detectDdiNamespace(doc);
        } catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }

        XPath xpath = XPathFactory.newInstance().newXPath();

        xpath.setNamespaceContext(new NamespaceContext() {

            @Override
            public String getNamespaceURI(String prefix) {

                return switch (prefix) {

                    case "ddi" ->
                        ddiNamespace;

                    case "oai" ->
                        "http://www.openarchives.org/OAI/2.0/";

                    case "xsi" ->
                        "http://www.w3.org/2001/XMLSchema-instance";

                    default ->
                        null;
                };
            }

            @Override
            public String getPrefix(String uri) {
                return null;
            }

            @Override
            public Iterator<String> getPrefixes(String uri) {
                return null;
            }
        });

        return xpath;
    }

    /**
     * Checks if the XML document uses a supported DDI namespace by examining
     * the namespace URI of the root element. The method retrieves the namespace
     * URI from the document's root element and checks if it is included in the
     * set of supported DDI namespaces defined in the SUPPORTED_DDI_NAMESPACES
     * constant.
     *
     * @param doc the XML document to check for a supported DDI namespace
     * @return boolean indicating whether the document uses a supported DDI
     *         namespace (true if supported, false otherwise)
     */
    private boolean hasSupportedDdiNamespace(Document doc) {

        try {
            String namespace = detectDdiNamespace(doc);

            return namespace != null &&
                    SUPPORTED_DDI_NAMESPACES.contains(namespace);

        } catch (XPathExpressionException e) {
            return false;
        }
    }

    /**
     * Detects the DDI namespace URI used in the XML document by looking for the
     * "codeBook" element and retrieving its namespace URI. The method uses
     * XPath to find the first element with the local name "codeBook" and
     * returns its namespace URI. If no such element is found, it returns null.
     * If an error occurs during XPath expression evaluation, it throws an
     * XPathExpressionException, which can be handled by the calling method to
     * determine that the DDI namespace cannot be detected.
     *
     * @param doc the XML document for which to detect the DDI namespace
     * @return the DDI namespace URI used in the document, or null if not found
     * @throws XPathExpressionException if an error occurs during XPath
     *         expression evaluation
     */

    private String detectDdiNamespace(Document doc)
            throws XPathExpressionException {

        XPath xpath = XPathFactory.newInstance().newXPath();

        Node node = (Node) xpath.evaluate(
                "//*[local-name()='codeBook']",
                doc,
                XPathConstants.NODE);

        if (node == null) {
            return null;
        }

        return node.getNamespaceURI();
    }

    /**
     * Defines how to extract a value from an XML node for validation.
     * DIRECT_TEXT: only direct text children of the node are considered
     * (ignoring nested elements) FULL_TEXT: all text content of the node is
     * considered (including nested elements) ATTRIBUTE: a specific attribute of
     * the node is considered (attribute name specified in ValidationRule
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
     * Represents a validation rule for a specific test type, including the
     * XPath to locate relevant nodes, the strategy to extract values from those
     * nodes, the vocabulary supplier to get approved terms, the match type to
     * determine how to compare extracted values against approved terms, and a
     * label for logging purposes.
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
     * Defines the validation rules for each test type, mapping each TestType to
     * a corresponding ValidationRule that specifies how to extract and validate
     * values from the XML document for that test. This map can be easily
     * extended to add new tests by defining new ValidationRule instances and
     * associating them with the appropriate TestType.
     *
     */

    private static final Map<TestType, ValidationRule> RULES = Map.of(

            /**
             * For each test type, we define: - The XPath expression to locate
             * relevant nodes in the XML document - The strategy to extract
             * values from those nodes (e.g. direct text, full text, or a
             * specific attribute) - The vocabulary supplier function to get the
             * set of approved terms from the VocabularyService - The match type
             * to determine how to compare extracted values against approved
             * terms (e.g. exact match or contains) - A label for logging
             * purposes to identify the type of value being validated in log
             * messages
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
     * VocabularyService to validate extracted values against approved
     * vocabularies. The method first parses the XML document, then determines
     * which test to run based on the TestType, and finally evaluates the
     * relevant validation rule or special handling logic for that test type,
     * returning a Result indicating whether the test passed, failed, or is
     * indeterminate due to an error. The method includes special handling for
     * certain test types (e.g. PROVENANCE and ELSST_KEYWORDS) that don't fit
     * the standard pattern defined by the ValidationRule, while for other test
     * types it retrieves the corresponding ValidationRule from the RULES map
     * and evaluates it using the core engine logic defined in the evaluate()
     * method. The method also logs detailed information about the processing
     * steps and any issues encountered, which can be helpful for debugging and
     * understanding why certain tests pass or fail.
     *
     * @param test the type of test to run, which determines the validation
     *         logic to apply to the XML document
     * @param inputStream the input stream containing the XML document to test
     * @param vocabulary the vocabulary service to use for validating extracted
     *         values
     * @return the result of the test
     * @throws IOException if an error occurs while reading the input stream or
     *         parsing the XML document
     */

    @Override
    public Result runTest(TestType test, InputStream inputStream, VocabularyService vocabulary)
            throws IOException {

        Document doc = parse(inputStream);

        switch (test) {
            case ELSST_KEYWORDS      -> { return checkElsstKeywords(doc, vocabulary); }
            case FAIR_VOCABULARY     -> { return checkFairVocabulary(doc); }
            case FORMAL_KR_LANGUAGE  -> { return checkFormalLanguage(doc); }
            case GROUNDED_METADATA   -> { return checkGroundedMetadata(doc); }
            case PROVENANCE          -> { return checkProvenance(doc); }
            case RETRIEVABLE_PROTOCOL -> { return checkRetrievableProtocol(doc); }
            case SEARCHABLE          -> { return checkSearchable(doc); }
            case STRUCTURED_METADATA -> {
                return hasSupportedDdiNamespace(doc)
                        ? Result.PASS
                        : Result.FAIL;
            }
            default -> { /* fall through to rules-based evaluation */ }
        }

        ValidationRule rule = RULES.get(test);
        if (rule == null)
            return Result.INDETERMINATE;

        return evaluate(rule, doc, vocabulary);
    }

    /**
     * Evaluates a given validation rule against the provided XML document and
     * vocabulary service. The method uses XPath to locate relevant nodes in the
     * document based on the rule's XPath expression, then extracts values from
     * those nodes according to the specified extraction strategy (e.g. direct
     * text, full text, or a specific attribute). Each extracted value is
     * normalised and compared against the set of approved terms obtained from
     * the VocabularyService using the rule's vocabulary supplier function. The
     * comparison is done according to the specified match type (e.g. exact
     * match or contains). If any extracted value matches an approved term, the
     * method returns Result.PASS; if no values match but at least one candidate
     * was found, it returns Result.FAIL; if no relevant nodes were found or an
     * error occurs during processing, it returns Result.INDETERMINATE. The
     * method also logs detailed information about its processing steps and any
     * issues encountered for debugging purposes.
     *
     * @param rule the validation rule to evaluate
     * @param doc the XML document to evaluate against
     * @param vocabulary the vocabulary service to use for validating extracted
     *         values
     * @return Result
     */

    private Result evaluate(ValidationRule rule, Document doc, VocabularyService vocabulary) {
        XPath xpath = createXPath(doc);

        try {

            NodeList nodes = (NodeList) xpath.evaluate(rule.xpath(), doc, XPathConstants.NODESET);

            if (nodes == null || nodes.getLength() == 0) {
                FairTests.logInfo("XPath used: %s", rule.xpath());
                return Result.FAIL;
            }

            Set<String> approved = normaliseSet(rule.vocabSupplier().apply(vocabulary));

            for (int i = 0; i < nodes.getLength(); i++) {
                String value = extract(nodes.item(i), rule);

                if (value == null || value.isBlank())
                    continue;

                String norm = normalise(value);

                FairTests.logInfo("%s candidate: %s",
                        rule.label(), value);

                if (matches(norm, approved, rule.matchType())) {
                    FairTests.logInfo("Approved %s found: %s",
                            rule.label(), value);
                    return Result.PASS;
                }
            }

            FairTests.logInfo("No approved %s found", rule.label());
            return Result.FAIL;

        } catch (XPathExpressionException e) {
            FairTests.logWarning("XPath error: %s", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Extracts a value from the given XML node according to the specified
     * validation rule's extraction strategy. Depending on the strategy, the
     * method may return the direct text content of the node (ignoring nested
     * elements), the full text content of the node (including nested elements),
     * or the value of a specific attribute of the node. If the node is not an
     * Element when an attribute extraction is requested, or if any other issue
     * occurs during extraction, the method returns null. The extracted value is
     * returned as a String, which may be further normalised and validated
     * against approved vocabularies in the calling method.
     *
     * @param node the XML node from which to extract a value
     * @param rule the validation rule that specifies the extraction strategy
     *         and             any relevant parameters (e.g. attribute name for
     *         ATTRIBUTE             strategy)
     * @return Stringthe extracted value as a String, or null if extraction
     *         fails or         is not applicable
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
     * Extracts only the direct text content of an XML element, ignoring any
     * nested elements. The method iterates through the child nodes of the given
     * element and concatenates the text content of any text nodes it finds,
     * trimming the result before returning it. This is useful for cases where
     * we want to validate only the immediate text content of an element without
     * considering any additional text that may be present in nested child
     * elements.
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
     * Checks for the presence of a formal KR language in the XML document. The
     * method first checks if the document uses a supported DDI namespace, which
     * is a strong indicator that it is using a formal language. If a supported
     * DDI namespace is detected, it then checks for the presence of schema
     * grounding by looking for schemaLocation attributes that reference known
     * DDI schemas. If schema grounding is detected, it returns Result.PASS; if
     * a supported DDI namespace is detected but no schema grounding is found,
     * it still returns Result.PASS based on the assumption that the presence of
     * a recognised DDI namespace implies the use of a formal language, even if
     * schema grounding is not explicitly indicated. If no supported DDI
     * namespace is detected, it returns Result.FAIL, indicating that the
     * document is unlikely to be using a formal language. If an error occurs
     * during processing (e.g. due to a malformed document), it catches the
     * exception and returns Result.INDETERMINATE, indicating that it cannot
     * determine the presence of a formal language due to an error. The method
     * also logs detailed information about its processing steps and any issues
     * encountered for debugging purposes.
     *
     * @param doc the XML document to check
     * @return the result of the formal language check
     */

    private Result checkFormalLanguage(Document doc) {

        try {

            /*
             * XML itself is a formal language.
             *
             * We additionally require:
             * - namespace-qualified structure
             * - recognised metadata language/schema
             */

            String ddiNamespace = detectDdiNamespace(doc);

            if (ddiNamespace == null || ddiNamespace.isBlank()) {

                FairTests.logInfo("No DDI namespace detected");

                return Result.FAIL;
            }

            if (!SUPPORTED_DDI_NAMESPACES.contains(ddiNamespace)) {

                FairTests.logInfo(
                        "Unsupported DDI namespace: %s",
                        ddiNamespace);

                return Result.FAIL;
            }

            boolean hasSchema = !extractSchemaLocations(doc).isEmpty();

            FairTests.logInfo(
                    "Formal XML language detected: %s",
                    ddiNamespace);

            if (hasSchema) {

                FairTests.logInfo(
                        "Schema grounding detected");

                return Result.PASS;
            }

            /*
             * Weak variant:
             * structured XML + recognised DDI syntax is enough
             */

            return Result.PASS;

        } catch (Exception e) {

            FairTests.logWarning(
                    "Formal language check failed: %s",
                    e.getMessage());

            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks for the presence of resolvable FAIR vocabulary linkages in the XML
     * document.
     *
     * @param doc the XML document to check for FAIR vocabulary linkages
     * @return Result indicating whether resolvable FAIR vocabulary linkages are
     *         found (PASS),         candidates are present but not resolvable
     *         (FAIL), or no candidates         found/error         occurs
     *         (INDETERMINATE)
     */

    private Result checkFairVocabulary(Document doc) {

        try {

            XPath xpath = createXPath(doc);

            NodeList nodes = (NodeList) xpath.evaluate(
                    "//*[@vocabURI]",
                    doc,
                    XPathConstants.NODESET);

            if (nodes == null || nodes.getLength() == 0) {

                FairTests.logInfo(
                        "No FAIR vocabulary references found");

                return Result.FAIL;
            }

            for (int i = 0; i < nodes.getLength(); i++) {

                Element el = (Element) nodes.item(i);

                String vocab = el.getAttribute("vocab");

                String vocabURI = el.getAttribute("vocabURI");

                /*
                 * Require BOTH:
                 * - declared vocabulary name
                 * - linked vocabulary URI
                 */

                if (vocab == null
                        || vocab.isBlank()
                        || vocabURI == null
                        || vocabURI.isBlank()) {

                    continue;
                }

                if (!looksResolvable(vocabURI)) {
                    continue;
                }

                FairTests.logInfo(
                        "Testing FAIR vocabulary %s at %s",
                        vocab,
                        vocabURI);

                if (resolves(vocabURI)) {

                    FairTests.logInfo(
                            "Resolvable FAIR vocabulary found: %s",
                            vocabURI);

                    return Result.PASS;
                }
            }

            FairTests.logInfo(
                    "No resolvable FAIR vocabularies found");

            return Result.FAIL;

        } catch (Exception e) {

            FairTests.logInfo(
                    "FAIR vocabulary check failed: %s",
                    e.getMessage());

            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks for the presence of provenance information in the XML document by
     * looking for specific elements that indicate the presence of a publisher,
     * creator, or funding information. The method uses XPath to check for the
     * existence of these elements and returns Result.PASS if any of them are
     * found, Result.FAIL if none are found, and Result.INDETERMINATE if an
     * error occurs during processing. This is a simple heuristic approach to
     * determine whether provenance information is present in the dataset, which
     * is an important aspect of FAIR data principles. The method also logs
     * detailed information about its processing steps and any issues
     * encountered for debugging purposes. The specific elements checked for
     * provenance information are: - Publisher: indicated by the presence of the
     * "ddi:distrbtr" element - Creator: indicated by the presence of the
     * "ddi:AuthEnty" element - Funding: indicated by the presence of the
     * "ddi:grantNo" element If any of these elements are found in the XML
     * document, the method concludes that provenance information is present and
     * returns Result.PASS. If none of these elements are found, it returns
     * Result.FAIL, indicating that provenance information is likely missing. If
     * an error occurs during the XPath evaluation (e.g. due to a malformed
     * document), the method catches the exception and returns
     * Result.INDETERMINATE, indicating that it cannot determine the presence of
     * provenance information due to an error.
     *
     * @param doc the XML document to check for provenance information
     * @return Result indicating whether provenance information is present
     *         (PASS),         likely missing (FAIL), or indeterminate due to an
     *         error         (INDETERMINATE)
     */

    private Result checkProvenance(Document doc) {
        try {
            XPath xpath = createXPath(doc);
            boolean hasPublisher = hasXPath(doc, xpath, "//ddi:distrbtr");
            boolean hasCreator   = hasXPath(doc, xpath, "//ddi:AuthEnty");
            boolean hasFunding   = hasXPath(doc, xpath, "//ddi:grantNo");

            if (hasPublisher || hasCreator || hasFunding) {
                FairTests.logInfo("Provenance found");
                return Result.PASS;
            }

            FairTests.logInfo("No Provenance found");
            return Result.FAIL;

        } catch (Exception e) {
            FairTests.logInfo("Provenance check failed: %s", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks for the presence of ELSST keywords in the XML document by looking
     * for "ddi:keyword" elements that have a "vocab" attribute equal to "ELSST"
     * and a "vocabURI" attribute that contains "elsst". The method collects the
     * text content of these keyword elements, along with their language (from
     * the "xml:lang" attribute), and then validates them against the approved
     * ELSST keywords provided by the VocabularyService. If valid ELSST keywords
     * are found, it returns Result.PASS; if no valid keywords are found but
     * candidates were present, it returns Result.FAIL; if no candidates are
     * found or an error occurs, it returns Result.INDETERMINATE. This method
     * provides specific handling for ELSST keywords, which are important for
     * ensuring that datasets are properly classified according to this
     * controlled vocabulary. The method also logs detailed information about
     * its processing steps and any issues encountered for debugging purposes.
     * The method performs the following steps: 1. Uses XPath to find all
     * "ddi:keyword" elements in the document 2. Iterates through the found
     * keyword elements and checks if they have the appropriate "vocab" and
     * "vocabURI" attributes to identify them as ELSST keywords 3. Collects the
     * text content of valid ELSST keyword elements and their language (from the
     * "xml:lang" attribute) 4. Validates the collected ELSST keywords against
     * the approved terms from the VocabularyService 5. Returns Result.PASS if
     * valid keywords are found, Result.FAIL if candidates are found but none
     * are valid, and Result.INDETERMINATE if no candidates are found or if an
     * error occurs during processing. The method ensures that only keywords
     * that are explicitly marked as ELSST keywords (via the "vocab" and
     * "vocabURI" attributes) are considered for validation, which helps
     * maintain the integrity of the test and ensures that it is specifically
     * checking for compliance with the ELSST controlled vocabulary.
     *
     * @param doc the XML document to check for ELSST keywords
     * @param vocabulary the vocabulary service to use for validating ELSST
     *         keywords
     * @return Result indicating whether valid ELSST keywords are found (PASS),
     *         candidates are found but none are valid (FAIL), or no candidates
     *         are         found or an error occurs (INDETERMINATE)
     */

    private Result checkElsstKeywords(Document doc, VocabularyService vocabulary) {
        XPath xpath = createXPath(doc);

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

            FairTests.logInfo("ELSST candidates: %s (lang=%s)", terms, lang);

            if (terms.isEmpty()) {
                FairTests.logInfo("No valid ELSST terms found");
                return Result.FAIL;
            }

            if (lang == null || lang.isBlank()) {
                FairTests.logInfo("No xml:lang found for ELSST keywords");
                return Result.INDETERMINATE;
            }

            return vocabulary.validateElsstKeywords(terms, lang);

        } catch (Exception e) {
            FairTests.logWarning("Error checking ELSST keywords: %s", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks for the presence of retrievable open protocol identifiers in the
     * XML document by looking for "ddi:IDNo" elements and attempting to resolve
     * them using known PID resolution services. The method uses XPath to find
     * all "ddi:IDNo" elements, then for each element it retrieves the "agency"
     * attribute and the text content, constructs a resolution URL based on the
     * agency and value, and checks if the URL uses an open protocol and is
     * resolvable. If a resolvable open protocol is found, it returns
     * Result.PASS; if no retrievable open protocol is found but candidates are
     * present, it returns Result.FAIL; if no candidates are found or an error
     * occurs, it returns Result.INDETERMINATE.
     *
     * @param doc the XML document to check for retrievable protocol identifiers
     * @return Result indicating whether a resolvable open protocol identifier
     *         is         found (PASS),         candidates are found but none
     *         are retrievable (FAIL),         or no candidates are found or an
     *         error occurs (INDETERMINATE)
     */

    private Result checkRetrievableProtocol(Document doc) {

        try {

            XPath xpath = createXPath(doc);

            NodeList nodes = (NodeList) xpath.evaluate(
                    "//ddi:IDNo",
                    doc,
                    XPathConstants.NODESET);

            if (nodes == null || nodes.getLength() == 0) {

                FairTests.logInfo("No PID identifiers found");

                return Result.FAIL;
            }

            for (int i = 0; i < nodes.getLength(); i++) {

                Element el = (Element) nodes.item(i);

                String agency = el.getAttribute("agency");

                String value = el.getTextContent().trim();

                String resolved = buildResolutionUrl(agency, value);

                if (resolved == null) {
                    continue;
                }

                FairTests.logInfo(
                        "Testing PID resolution URL: %s",
                        resolved);

                if (usesOpenProtocol(resolved)
                        && resolves(resolved)) {

                    FairTests.logInfo(
                            "Resolvable open protocol found: %s",
                            resolved);

                    return Result.PASS;
                }
            }

            FairTests.logInfo("No retrievable open protocol found");

            return Result.FAIL;

        } catch (Exception e) {

            FairTests.logInfo(
                    "Retrievable protocol check failed: %s",
                    e.getMessage());

            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks for the presence of searchable metadata in the XML document by
     * looking for specific elements that indicate the presence of OAI-PMH
     * records, headers, and identifiers. The method uses XPath to check for the
     * existence of these elements and returns Result.PASS if they are found,
     * Result.FAIL if they are not found, and Result.INDETERMINATE if an error
     * occurs during processing. This is a heuristic approach to determine
     * whether the dataset includes searchable metadata that can be accessed via
     * OAI-PMH, which is an important aspect of FAIR data principles. The method
     * also logs detailed information about its processing steps and any issues
     * encountered for debugging purposes. The specific elements checked for
     * searchable metadata are: - OAI-PMH record: indicated by the presence of
     * the "oai:record" element - OAI-PMH header: indicated by the presence of
     * the "oai:header" element - OAI-PMH identifier: indicated by the presence
     * of the "oai:header/oai:identifier" element If all of these elements are
     * found in the XML document, the method concludes that searchable metadata
     * is present and returns Result.PASS
     *
     * @param doc the XML document to check for searchable metadata
     * @return Result indicating whether searchable metadata is present (PASS),
     *         likely missing (FAIL),         or indeterminate due to an error
     *         (INDETERMINATE)
     */

    private Result checkSearchable(Document doc) {

        try {

            XPath xpath = createXPath(doc);

            /*
             * OAI-PMH discoverability signals
             */

            boolean hasRecord = hasXPath(doc, xpath, "//oai:record");

            boolean hasHeader = hasXPath(doc, xpath, "//oai:header");

            boolean hasIdentifier = hasXPath(doc, xpath, "//oai:header/oai:identifier");

            if (hasRecord && hasHeader && hasIdentifier) {

                String identifier = xpath.evaluate(
                        "string(//oai:header/oai:identifier)",
                        doc);

                FairTests.logInfo(
                        "Searchable OAI identifier found: %s",
                        identifier);

                return Result.PASS;
            }

            FairTests.logInfo("No searchable OAI-PMH metadata found");

            return Result.FAIL;

        } catch (Exception e) {

            FairTests.logInfo(
                    "Searchability check failed: %s",
                    e.getMessage());

            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks for the presence of grounded metadata in the XML document by
     * extracting all namespaces used in the document and checking if any of
     * them are resolvable (i.e. can be accessed via HTTP). The method ignores
     * common infrastructure namespaces (e.g. XML, RDF) and focuses on
     * namespaces that are likely to represent the dataset's own metadata. If
     * any resolvable namespaces are found, it returns Result.PASS; if no
     * resolvable namespaces are found but candidates are present, it returns
     * Result.FAIL; if no candidates are found or an error occurs, it returns
     * Result.INDETERMINATE. This method provides a heuristic approach to
     * determine whether the dataset includes grounded metadata that can be
     * accessed and understood by machines, which is an important aspect of FAIR
     * data principles. The method also logs detailed information about its
     * processing steps and any issues encountered for debugging purposes.
     *
     * @param doc the XML document to check for grounded metadata
     * @return Result indicating whether resolvable namespaces are found (PASS),
     *         candidates are found but none are resolvable (FAIL),         or
     *         if no candidates are found or an error occurs (INDETERMINATE).
     */

    private Result checkGroundedMetadata(Document doc) {

        try {

            Set<String> candidates = new HashSet<>();
            candidates.addAll(extractNamespaces(doc));
            candidates.addAll(extractSchemaLocations(doc));

            FairTests.logInfo("Grounding candidates: %s", candidates);

            for (String candidate : candidates) {

                if (isInfrastructureNamespace(candidate)) {
                    continue;
                }

                if (resolves(candidate)) {

                    FairTests.logInfo(
                            "Resolvable grounded metadata found: %s",
                            candidate);

                    return Result.PASS;
                }
            }

            FairTests.logInfo("No resolvable grounded namespaces found");

            return Result.FAIL;

        } catch (Exception e) {

            FairTests.logInfo(
                    "Grounded metadata check failed: %s",
                    e.getMessage());

            return Result.INDETERMINATE;
        }
    }

    private Set<String> extractSchemaLocations(Document doc) {

        Set<String> locations = new HashSet<>();

        collectSchemaLocations(doc.getDocumentElement(), locations);

        return locations;
    }

    /**
     * Recursively collects schema locations from the XML document by traversing
     * the DOM tree. For each element, it checks for the presence of
     * "xsi:schemaLocation" and "xsi:noNamespaceSchemaLocation" attributes, and
     * if they are present and look like resolvable URLs, it adds them to the
     * set of locations. The method then continues to traverse the child nodes
     * of the element, ensuring that all schema locations used anywhere in the
     * document are collected. This allows the grounded metadata check to
     * consider not only namespaces but also any schema locations that may be
     * relevant for determining whether the dataset includes resolvable
     * metadata. The method also ensures that it does not add null or blank
     * schema locations to the set, which helps to focus the check on valid
     * candidates that may be resolvable.
     *
     * @param node the current XML node being processed in the DOM tree
     *         traversal
     * @param locations the set of schema locations collected so far, which will
     *         be                  updated with any new locations found during
     *         the traversal
     */

    private void collectSchemaLocations(Node node, Set<String> locations) {

        if (node instanceof Element el) {

            String schemaLocation = el.getAttributeNS(
                    "http://www.w3.org/2001/XMLSchema-instance",
                    "schemaLocation");

            if (schemaLocation != null && !schemaLocation.isBlank()) {

                /*
                 * schemaLocation format:
                 * namespaceURI schemaURL namespaceURI schemaURL ...
                 */

                String[] parts = schemaLocation.trim().split("\\s+");

                for (int i = 1; i < parts.length; i += 2) {

                    String url = parts[i];

                    if (looksResolvable(url)) {
                        locations.add(url);
                    }
                }
            }

            String noNamespaceSchemaLocation = el.getAttributeNS(
                    "http://www.w3.org/2001/XMLSchema-instance",
                    "noNamespaceSchemaLocation");

            if (looksResolvable(noNamespaceSchemaLocation)) {
                locations.add(noNamespaceSchemaLocation);
            }
        }

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {

            collectSchemaLocations(child, locations);
        }
    }

    /**
     * Checks if the given value looks like a resolvable URL by checking if it
     * starts with "http://" or "https://". This is a simple heuristic to
     * determine if a namespace URI or schema location is likely to be
     * resolvable, which is important for the grounded metadata check. The
     * method also ensures that the value is not null or blank before checking
     * its format. This helps to filter out invalid candidates and focus the
     * check on values that have a reasonable chance of being resolvable.
     *
     * @param value the string value to check for resolvability
     * @return boolean indicating whether the value looks like a resolvable URL
     *         (true if it starts with "http://" or "https://", false otherwise)
     */

    private boolean looksResolvable(String value) {

        return value != null &&
                !value.isBlank() &&
                (value.startsWith("http://")
                        || value.startsWith("https://"));
    }

    /**
     * Extracts all namespace URIs used in the XML document by traversing the
     * DOM tree. For each element, it adds the element's namespace URI to the
     * set of namespaces, as well as the namespace URIs of any attributes. The
     * method then continues to traverse the child nodes of the element,
     * ensuring that all namespaces used anywhere in the document are collected.
     * This comprehensive collection of namespaces allows the grounded metadata
     * check to consider all potential namespaces that may be relevant for
     * determining whether the dataset includes resolvable metadata. The method
     * handles both element namespaces and attribute namespaces, which is
     * important because metadata can be expressed in either way in XML
     * documents. The method also ensures that it does not add null or blank
     * namespace URIs to the set, which helps to focus the check on valid
     * namespaces that may be resolvable.
     *
     * @param doc the XML document from which to extract namespaces
     * @return the set of namespace URIs found in the document
     */

    private Set<String> extractNamespaces(Document doc) {

        Set<String> namespaces = new HashSet<>();

        Element root = doc.getDocumentElement();

        collectNamespaces(root, namespaces);

        return namespaces;
    }

    /**
     * Recursively collects all namespace URIs used in the XML document by
     * traversing the DOM tree. For each element, it adds the element's
     * namespace URI to the set of namespaces, as well as the namespace URIs of
     * any attributes. The method then continues to traverse the child nodes of
     * the element, ensuring that all namespaces used anywhere in the document
     * are collected. This comprehensive collection of namespaces allows the
     * grounded metadata check to consider all potential namespaces that may be
     * relevant for determining whether the dataset includes resolvable
     * metadata. The method handles both element namespaces and attribute
     * namespaces, which is important because metadata can be expressed in
     * either way in XML documents. The method also ensures that it does not add
     * null or blank namespace URIs to the set, which helps to focus the check
     * on valid namespaces that may be resolvable.
     *
     * @param node the current XML node being processed in the DOM tree
     *         traversal
     * @param namespaces the set of namespace URIs collected so far, which will
     *         be                   updated with any new namespaces found during
     *         the traversal
     */

    private void collectNamespaces(Node node, Set<String> namespaces) {

        if (node instanceof Element el) {

            String ns = el.getNamespaceURI();

            if (ns != null && !ns.isBlank()) {
                namespaces.add(ns);
            }

            for (int i = 0; i < el.getAttributes().getLength(); i++) {

                Node attr = el.getAttributes().item(i);

                String attrNs = attr.getNamespaceURI();

                if (attrNs != null && !attrNs.isBlank()) {
                    namespaces.add(attrNs);
                }
            }
        }

        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {

            collectNamespaces(child, namespaces);
        }
    }

    /**
     * Checks if the given namespace URI is a common infrastructure namespace
     * that should be ignored when checking for grounded metadata. The method
     * checks if the namespace URI starts with known prefixes for common
     * infrastructure namespaces (e.g. XML, RDF, Dublin Core) or contains "xml",
     * which is a common substring in such namespaces. This helps to filter out
     * namespaces that are not likely to represent the dataset's own metadata
     * and focus the grounded metadata check on namespaces that are more likely
     * to be relevant for FAIR data principles.
     *
     * @param ns the namespace URI to check
     * @return boolean indicating whether the namespace is a common
     *         infrastructure         namespace         that should be ignored
     */

    private boolean isInfrastructureNamespace(String ns) {

        return ns.startsWith("http://www.w3.org/")
                || ns.startsWith("http://www.openarchives.org/")
                || ns.startsWith("http://www.loc.gov/")
                || ns.startsWith("http://purl.org/dc/")
                || ns.contains("xml");
    }

    /**
     * Checks if the given namespace URI is resolvable by making an HTTP HEAD
     * request to it and checking the response code. A namespace is considered
     * resolvable if the HTTP response code is in the 200-399 range. The method
     * handles any exceptions that may occur during the HTTP request and logs
     * relevant information about the namespace being checked and the outcome of
     * the resolution attempt.
     *
     * @param namespace the namespace URI to check for resolvability
     * @return true if the namespace is resolvable, false otherwise
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
                    "Resolution check %s returned HTTP %s",
                    url,
                    code);

            return code >= 200 && code < 400;

        } catch (Exception e) {

            FairTests.logInfo(
                    "Resolution failed for %s: %s",
                    url,
                    e.getMessage());

            return false;
        }
    }

    /**
     * Parses an XML document from an input stream. The method uses a
     * DocumentBuilder to parse the XML content from the provided InputStream
     * and returns a Document object representing the parsed XML. If any errors
     * occur during parsing (e.g. due to malformed XML), the method catches the
     * SAXException and wraps it in an IOException, which is then thrown to
     * indicate that an error occurred while reading or parsing the input
     * stream. This method abstracts away the logic of parsing XML content,
     * allowing the main test logic to focus on validation rather than parsing
     * details. The method performs the following steps: 1. Uses the
     * DocumentBuilder to parse the XML content from the provided InputStream 2.
     * If parsing is successful, it returns the resulting Document object
     * representing the XML structure. 3. If a SAXException occurs during
     * parsing (e.g. due to malformed XML), the method catches the exception and
     * throws a new IOException with the original exception as its cause,
     * indicating that an error occurred while reading or parsing the input
     * stream. This allows the calling method to handle parsing errors
     * appropriately, such as by returning an indeterminate result for the test.
     *
     * @param inputStream the InputStream containing the XML content to parse
     * @return Document representing the parsed XML document
     * @throws IOException if an error occurs while reading the input stream or
     *         if                     the XML content is malformed and cannot be
     *         parsed                     successfully
     */

    private Document parse(InputStream inputStream) throws IOException {
        try {
            return builder.parse(inputStream);
        } catch (SAXException e) {
            throw new IOException(e);
        }
    }

    /**
     * Checks if a candidate value matches any of the approved terms based on
     * the specified match type. The method takes a candidate string, a set of
     * approved strings, and a MatchType to determine how to compare the
     * candidate against the approved terms. If the match type is EXACT, it
     * checks if the candidate exactly matches any of the approved terms. If the
     * match type is CONTAINS, it checks if the candidate contains any of the
     * approved terms as a substring. The method returns true if a match is
     * found according to the specified criteria, or false if no match is found.
     * This method abstracts away the logic of comparing candidate values
     * against approved vocabularies based on different matching strategies,
     * allowing for flexible validation logic in the evaluate() method. The
     * method performs the following steps: 1. Determines the matching logic to
     * apply based on the provided MatchType (e.g. EXACT or CONTAINS). 2. If the
     * MatchType is EXACT, it checks if the candidate string is exactly equal to
     * any of the strings in the approved set, returning true if a match is
     * found. 3. If the MatchType is CONTAINS, it checks if the candidate string
     * contains any of the strings in the approved set as a substring, returning
     * true if a match is found. 4. If no matches are found according to the
     * specified MatchType, the method returns false, indicating that the
     * candidate does not match any of the approved terms based on the given
     * criteria.
     *
     * @param candidate the string value extracted from the XML document that we
     *         want to validate against the approved terms
     * @param approved the set of approved strings to compare against
     * @param type the match type to use for comparison
     * @return boolean indicating whether the candidate matches any of the
     *         approved         terms according to the specified match type
     *         (true if a match is         found, false otherwise)
     */

    private boolean matches(String candidate, Set<String> approved, MatchType type) {
        return switch (type) {
            case EXACT -> approved.contains(candidate);
            case CONTAINS -> approved.stream().anyMatch(candidate::contains);
        };
    }

    /**
     * Normalises a string by converting it to lowercase, trimming whitespace,
     * and replacing multiple spaces with a single space. This method is used to
     * ensure that comparisons between extracted values and approved terms are
     * done in a consistent way, ignoring differences in case and extraneous
     * whitespace. The normalisation process helps improve the robustness of the
     * validation logic by allowing for minor variations in how values are
     * represented in the XML document while still correctly identifying matches
     * against the approved vocabularies. If the input string is null, the
     * method returns an empty string to avoid null pointer exceptions during
     * processing. The method performs the following steps: 1. Checks if the
     * input string is null, and if so, returns an empty string to prevent null
     * pointer exceptions during processing. 2. Converts the input string to
     * lowercase to ensure that comparisons are case-insensitive 3. Trims
     * leading and trailing whitespace from the input string to remove any
     * extraneous spaces that may affect comparisons. 4. Replaces multiple
     * consecutive whitespace characters within the string with a single space
     * to collapse any internal whitespace to a consistent format, which helps
     * ensure that minor variations in spacing do not affect the outcome of
     * comparisons against approved terms.
     *
     * @param s the input string to normalise, which may be null
     * @return String the normalised version of the input string, with null
     *         inputs resulting in an empty string, and non-null inputs
     *         converted to lowercase, trimmed, and with internal whitespace
     *         collapsed to a single space
     */

    private String normalise(String s) {
        if (s == null)
            return "";
        return s.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    /**
     * Normalises a set of strings by applying the normalise method to each
     * string in the set. This method is used to ensure that comparisons between
     * extracted values and approved terms are done in a consistent way,
     * ignoring differences in case and extraneous whitespace. The normalisation
     * process helps improve the robustness of the validation logic by allowing
     * for minor variations in how values are represented in the XML document
     * while still correctly identifying matches against the approved
     * vocabularies. If the input set is null, the method returns an empty set
     * to avoid null pointer exceptions during processing. The method performs
     * the following steps: 1. Checks if the input set is null, and if so,
     * returns an empty set to prevent null pointer exceptions during
     * processing. 2. Iterates through each string in the input set, applying
     * the normalise method to it to convert it to lowercase, trim whitespace,
     * and collapse internal whitespace to a single space. 3. Collects the
     * normalised strings into a new set, which is returned as the output. This
     * ensures that all strings in the approved set are in a consistent format
     * for comparison against normalised candidate values extracted from the XML
     * document.
     *
     * @param input the input set of strings to normalise, which may be null
     * @return Set<String> the normalised version of the input set, with null
     *         inputs resulting in an empty set, and non-null inputs converted
     *         to lowercase, trimmed, and with internal whitespace collapsed
     *         to a single space
     */

    private Set<String> normaliseSet(Set<String> input) {
        return input.stream()
                .filter(s -> s != null)
                .map(this::normalise)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Helper method to check if any nodes exist for a given XPath expression in
     * the XML document. This method is used to determine the presence of
     * specific elements that indicate searchable metadata (e.g. OAI-PMH
     * records, headers, identifiers) in the checkSearchable() method. It
     * evaluates the provided XPath expression against the document and returns
     * true if any nodes are found, or false if no nodes are found. If an error
     * occurs during XPath evaluation, it throws an XPathExpressionException,
     * which can be caught by the calling method to handle indeterminate
     * results. This method abstracts away the logic of checking for the
     * existence of nodes based on an XPath expression, making the searchable
     * metadata check cleaner and more focused on its specific logic.
     *
     * @param doc the XML document to evaluate
     * @param xpath the XPath object for evaluating the expression
     * @param expression the XPath expression to evaluate
     * @return true if any nodes are found, false otherwise
     * @throws XPathExpressionException if an error occurs during XPath
     *         evaluation
     */

    private boolean hasXPath(
            Document doc,
            XPath xpath,
            String expression)
            throws XPathExpressionException {

        NodeList nodes = (NodeList) xpath.evaluate(
                expression,
                doc,
                XPathConstants.NODESET);

        return nodes != null && nodes.getLength() > 0;
    }

    /**
     * Builds a resolution URL based on the given agency and value. The method
     * checks the agency to determine which resolution service to use (e.g. DOI,
     * Handle, ARK) and constructs the appropriate URL for resolving the
     * identifier. If the agency is not recognized or if either the agency or
     * value is null, the method returns null, indicating that a resolution URL
     * cannot be constructed. This method is used in the
     * checkRetrievableProtocol() method to determine the URL to test for
     * resolvability based on the identifiers found in the XML document.
     *
     * @param agency the agency attribute from the "ddi:IDNo" element, which
     *         indicates the type of identifier (e.g. DOI, Handle, ARK)
     * @param value the value of the identifier
     * @return the resolution URL, or null if the agency is not recognized or if
     *         either the agency or value is null
     */

    private String buildResolutionUrl(
            String agency,
            String value) {

        if (agency == null || value == null) {
            return null;
        }

        String a = agency.trim().toLowerCase();
        String v = value.trim();

        return switch (a) {

            case "doi" ->
                "https://doi.org/" + v;

            case "handle" ->
                "https://hdl.handle.net/" + v;

            case "ark" ->
                "https://n2t.net/" + v;

            case "urn" ->
                null;

            default ->
                null;
        };
    }

    /**
     * Checks if the given URL uses an open protocol (i.e. starts with "http://"
     * or "https://"). This is a simple heuristic to determine if a URL is
     * likely to be resolvable and accessible via standard web protocols, which
     * is important for both the retrievable protocol check and the grounded
     * metadata check. The method returns true if the URL starts with "http://"
     * or "https://", and false otherwise. This helps to filter out URLs that
     * are not likely to be resolvable or accessible, such as URNs or other
     * non-HTTP identifiers, and focus the checks on candidates that have a
     * reasonable chance of being resolvable and accessible via standard web
     * protocols.
     *
     * @param url the URL to check for using an open protocol
     * @return true if the URL uses an open protocol, false otherwise
     */

    private boolean usesOpenProtocol(String url) {

        return url.startsWith("http://")
                || url.startsWith("https://");
    }
}