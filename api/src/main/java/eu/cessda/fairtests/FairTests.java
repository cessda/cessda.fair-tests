/*
 * SPDX-FileCopyrightText: 2025 CESSDA ERIC (support@cessda.eu)
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
 *
 */

package eu.cessda.fairtests;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * <H2>FairTests</H2>
 * <P>
 * Consolidated utility class for checking DDI2.5 records against
 * various FAIR data criteria:
 * <UL>
 * <LI>Access Rights compliance</LI>
 * <LI>Persistent Identifier (PID) schema validation</LI>
 * <LI>ELSST controlled vocabulary keyword validation</LI>
 * <LI>CESSDA Topic Classification vocabulary usage</LI>
 * <LI>DDI Analysis Unit vocabulary usage</LI>
 * <LI>DDI Collection Mode vocabulary usage</LI>
 * <LI>DDI Time Method vocabulary usage</LI>
 * <LI>DDI Sampling Procedure vocabulary usage</LI>
 * <LI>Provenance information presence</LI>
 * </UL>
 * <P>
 * All tests fetch DDI 2.5 metadata via the specified URL.
 * <P>
 * Return values for all tests:
 * <UL>
 * <LI>"pass": the record meets the criteria</LI>
 * <LI>"fail": the record does not meet the criteria</LI>
 * <LI>"indeterminate": an error occurred preventing definitive
 * determination</LI>
 * </UL>
 */
public class FairTests {

    // Logger
    private static final Logger logger = Logger.getLogger(FairTests.class.getName());

    // Namespace and URL constants
    private static final String DDI_NAMESPACE = "ddi:codebook:2_5";
    // Logging messages
    private static final String ERROR = "Error: ";
    // Logging messages
    private static final String FETCHED = "Fetched ";

    // ELSST API base URL
    private static final String ELSST_API_BASE = "https://skg-if-staging.cessda.eu/api/topics";

    // Access Rights vocabulary URL
    private static final String ACCESS_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/CessdaAccessRights/1.0.0?languageVersion=en-1.0.0&format=json";
    // PID vocabulary URL
    private static final String PID_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/CessdaPersistentIdentifierTypes/1.0.0?languageVersion=en-1.0.0&format=json";
    // Topic Classification vocabulary URL
    private static final String TOPIC_CLASS_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/TopicClassification/4.2.3?languageVersion=en-4.2.3&format=json";
    // Recommended DDI vocabularies URLs
    private static final String ANALYSIS_UNIT_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/AnalysisUnit/2.1.3?languageVersion=en-2.1.3&format=json";
    // Time Method vocabulary URL
    private static final String TIME_METHOD_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/TimeMethod/1.2.3?languageVersion=en-1.2.3&format=json";
    // Sampling Procedure vocabulary URL
    private static final String SAMPLING_PROC_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/SamplingProcedure/2.0.1?languageVersion=en-2.0.1&format=json";
    // Mode of Collection vocabulary URL
    private static final String COLLECTION_MODE_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/ModeOfCollection/5.0.0?languageVersion=en-5.0.0&format=json";

    // ELSST constants
    private static final String ELSST_VOCAB_NAME = "ELSST";
    private static final String ELSST_URI_SUBSTRING = "elsst.cessda.eu";
    private static final String HTTP_HEADER_ACCEPT = "Accept";

    // Cache for ELSST keywords by language code and keyword list (to avoid repeated
    // API calls for the same language and keywords)
    private final ConcurrentMap<String, Set<String>> cachedElsstKeywordsByLang = new ConcurrentHashMap<>();

    // Topic Classification constant
    private static final String TOPIC_CLASS_VOCAB_NAME = "CESSDA Topic Classification";
    // Cached vocabularies
    final ConcurrentSkipListSet<String> cachedAccessRightsTerms = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedAnalysisUnitTerms = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedCollectionModeTerms = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedElsstKeywords = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedPidSchemas = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedSamplingProcTerms = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedTimeMethodTerms = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedTopicClassTerms = new ConcurrentSkipListSet<>();
    // Shared components
    private final DocumentBuilder documentBuilder;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    // XPath expressions
    private final XPathExpression ddiCodebookXPath;
    private final XPathExpression accessRightsXPath;
    private final XPathExpression analysisUnitXPath;
    private final XPathExpression collectionModeXPath;
    private final XPathExpression keywordXPath;
    private final XPathExpression pidXPath;
    private final XPathExpression samplingProcXPath;
    private final XPathExpression timeMethodXPath;
    private final XPathExpression topicClassXPath;
    private final XPathExpression publisherXPath;
    private final XPathExpression authorXPath;
    private final XPathExpression grantNoXPath;

    /**
     * Create a new instance of {@link FairTests}
     */
    public FairTests() {
        var documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        try {
            this.documentBuilder = documentBuilderFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }

        // Set XPath namespace context
        XPath xPath = XPathFactory.newInstance().newXPath();
        xPath.setNamespaceContext(new NamespaceContext() {
            public String getNamespaceURI(String prefix) {
                return "ddi".equals(prefix) ? DDI_NAMESPACE : null;
            }

            public String getPrefix(String namespaceURI) {
                return null;
            }

            public Iterator<String> getPrefixes(String namespaceURI) {
                return null;
            }
        });

        // Compile XPaths
        try {
            ddiCodebookXPath = xPath.compile("//ddi:codeBook");
            // for DDI2.5 use conditions element as typeOfAccess is not available until
            // DDI2.6
            accessRightsXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:dataAccs/ddi:useStmt/ddi:conditions");
            analysisUnitXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:sumDscr/ddi:anlyUnit");
            collectionModeXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:method/ddi:dataColl/ddi:collMode");
            keywordXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:keyword");
            pidXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:citation/ddi:titlStmt/ddi:IDNo");
            samplingProcXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:method/ddi:dataColl/ddi:sampProc");
            timeMethodXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:method/ddi:dataColl/ddi:timeMeth");
            topicClassXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:topcClas");
            // Provenance paths
            publisherXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:citation/ddi:distStmt/ddi:distrbtr");
            authorXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:citation/ddi:rspStmt/ddi:AuthEnty");
            grantNoXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:citation/ddi:prodStmt/ddi:grantNo");
        } catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Get a comma-separated list of valid test types.
     * 
     * @return String of valid test types
     */
    private static String getValidTestTypes() {
        return Arrays.stream(TestType.values())
                .map(TestType::getTestName)
                .collect(Collectors.joining(", "));
    }

    /**
     * Main method for command-line execution.
     *
     * @param args args[0]: test type (e.g. "access-rights", "pid",
     *             "elsst-keywords", etc.)
     *             args[1]: A URL that should return DDI2.5 metadata
     * @throws ParseException if the command line is invalid.
     */
    @SuppressWarnings("java:S106")
    public static void main(String[] args) throws ParseException, URISyntaxException {

        // Set logger level
        logger.setLevel(Level.INFO);

        // Command line options
        @SuppressWarnings("deprecation")
        HelpFormatter formatter = new HelpFormatter();
        var options = new Options();
        var commandLine = new DefaultParser().parse(options, args);

        var testMap = new HashMap<String, TestType>();
        for (TestType testType : EnumSet.allOf(TestType.class)) {
            testMap.put(testType.getTestName(), testType);
        }

        if (commandLine.getArgList().size() < 2 || !testMap.containsKey(commandLine.getArgList().get(0))) {
            formatter.printHelp("FairTests <test-type> <url>\ntest types: " + getValidTestTypes(), null, options, null,
                    false);
            System.exit(1);
        }

        String testName = commandLine.getArgList().get(0);
        TestType test = testMap.get(testName);

        String urlString = commandLine.getArgList().get(1);
        URI url = new URI(urlString);

        // Instance tests
        FairTests tests = new FairTests();

        // Run tests and get result
        Result result = tests.runTest(test, url);

        logger.log(Level.INFO, "Result: {0}", result);
        System.out.println(result);
        System.exit(Result.PASS == result ? 0 : 1);
    }

    /**
     * Fetch the default approved Access Rights terms.
     * 
     * @return Set of approved Access Rights terms
     */
    private static Set<String> defaultAccessRightsTerms() {
        return Set.of("open", "restricted");
    }

    /**
     * Fetch the default approved PID schemas.
     * 
     * @return Set of approved PID schema names
     */
    private static Set<String> defaultPidSchemas() {
        return Set.of("DOI", "Handle", "URN", "ARK");
    }

    // ============================================================================
    // PUBLIC API METHODS
    // ============================================================================

    /**
     * Run the specified test against the given URL.
     * 
     * @param test The test to run
     * @param url  A URL that should return DDI2.5 metadata
     * @return Result of the test: "pass", "fail", or "indeterminate"
     */
    public Result runTest(TestType test, URI url) {
        return switch (test) {
            case ACCESS_RIGHTS -> containsApprovedAccessRights(url);
            case PID -> containsApprovedPid(url);
            case ELSST_KEYWORDS -> containsElsstKeywords(url);
            case TOPIC_CLASS -> containsCessdaTopicClassificationTerms(url);
            case DDI_ANALYSIS_UNIT -> containsDdiAnalysisUnit(url);
            case DDI_COLLECTION_MODE -> containsDdiCollectionMode(url);
            case DDI_TIME_METHOD -> containsDdiTimeMethod(url);
            case DDI_SAMPLEPROC -> containsDdiSamplingProcedureTerms(url);
            case PROVENANCE -> containsProvenanceInformation(url);
        };
    }

    /**
     * Checks whether an DDI2.5 record contains an approved Access Rights term.
     *
     * @param url A URL that should return DDI2.5 metadata
     * @return "pass", "fail", or "indeterminate"
     */
    public Result containsApprovedAccessRights(URI url) {
        try {
            Document doc = fetchAndParseDocument(url);
            return checkAccessRights(doc);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Couldn't check access rights", e);
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks whether a DDI2.5 record contains an approved PID schema.
     *
     * @param url A URL that should return DDI2.5 metadata
     * @return "pass", "fail", or "indeterminate"
     */
    public Result containsApprovedPid(URI url) {
        try {
            Document doc = fetchAndParseDocument(url);
            return checkPidSchemas(doc);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Couldn't check approved PIDs", e);
        } catch (IllegalArgumentException e) {
            logger.log(Level.SEVERE, "Invalid argument: ", e);
        }
        return Result.INDETERMINATE;
    }

    /**
     * Checks whether a DDI2.5 record contains ELSST keywords that meet ALL three
     * criteria:
     * The vocab attribute equals "ELSST" (exact match, case-sensitive)
     * The vocabURI attribute contains "elsst.cessda.eu" (substring match)
     * The keyword value exists in the ELSST vocabulary (case-insensitive match)
     *
     * @param url A URL that should return DDI2.5 metadata
     * @return "pass", "fail", or "indeterminate"
     */
    public Result containsElsstKeywords(URI url) {
        try {
            Document doc = fetchAndParseDocument(url);
            return validateElsstKeywords(doc);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to check if a record contains ELSST keywords: ", e);
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks whether a DDI2.5 record uses Topic Classification vocabulary
     * terms.
     *
     * @param url A URL that should return DDI2.5 metadata
     * @return "pass", "fail", or "indeterminate"
     */
    public Result containsCessdaTopicClassificationTerms(URI url) {
        try {
            Document doc = fetchAndParseDocument(url);
            return checkCessdaTopicClassification(doc);
        } catch (IOException e) {
            logger.log(Level.SEVERE, ERROR, e);
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks whether a DDI2.5 record uses the DDI Analysis Unit vocabulary.
     *
     * @param url A URL that should return DDI2.5 metadata
     * @return "pass", "fail", or "indeterminate"
     */
    public Result containsDdiAnalysisUnit(URI url) {
        try {
            Document doc = fetchAndParseDocument(url);
            return checkAnalysisUnit(doc);
        } catch (IOException e) {
            logger.log(Level.SEVERE, ERROR, e);
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks whether a DDI2.5 record uses the DDI Collection Mode vocabulary.
     *
     * @param url A URL that should return DDI2.5 metadata
     * @return "pass", "fail", or "indeterminate"
     */
    public Result containsDdiCollectionMode(URI url) {
        try {
            Document doc = fetchAndParseDocument(url);
            return checkCollectionMode(doc);
        } catch (IOException e) {
            logger.log(Level.SEVERE, ERROR, e);
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks whether a DDI2.5 record uses the DDI Time Method vocabulary.
     *
     * @param url A URL that should return DDI2.5 metadata
     * @return "pass", "fail", or "indeterminate"
     */
    public Result containsDdiTimeMethod(URI url) {
        try {
            Document doc = fetchAndParseDocument(url);
            return checkTimeMethod(doc);
        } catch (IOException e) {
            logger.log(Level.SEVERE, ERROR, e);
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks whether a DDI2.5 record uses DDI Sampling Procedure vocabulary terms.
     *
     * @param url A URL that should return DDI2.5 metadata
     * @return "pass", "fail", or "indeterminate"
     */
    public Result containsDdiSamplingProcedureTerms(URI url) {
        try {
            Document doc = fetchAndParseDocument(url);
            return checkDdiSamplingProcedure(doc);
        } catch (IOException e) {
            logger.log(Level.SEVERE, ERROR, e);
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks whether a DDI2.5 record contains provenance information.
     *
     * @param url A URL that should return DDI2.5 metadata
     * @return "pass", "fail", or "indeterminate"
     */
    public Result containsProvenanceInformation(URI url) {
        try {
            Document doc = fetchAndParseDocument(url);
            return checkProvenance(doc);
        } catch (IOException e) {
            logger.log(Level.SEVERE, ERROR, e);
            return Result.INDETERMINATE;
        }
    }

    /**
     * Fetch the OAI-PMH GetRecord XML and parse to extract the DDI codeBook
     * element.
     *
     * @param url The OAI-PMH GetRecord URL
     * @return The DDI codeBook Document
     * @throws IOException - if an I/O error occurs
     */
    public Document fetchAndParseDocument(URI url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(url)
                .header(HTTP_HEADER_ACCEPT, "application/xml, text/xml, */*")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        var response = getHTTPResponse(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200)
            throw new IOException("Failed to fetch document: HTTP " + response.statusCode());

        try (var body = response.body()) {
            logger.log(Level.INFO, "Parsing XML response from OAI-PMH endpoint at: {0}", url);

            Document oaiDoc;
            synchronized (documentBuilder) {
                oaiDoc = documentBuilder.parse(body);
            }

            synchronized (ddiCodebookXPath) {
                Node codeBookNode = (Node) ddiCodebookXPath.evaluate(oaiDoc, XPathConstants.NODE);
                if (codeBookNode == null) {
                    logger.log(Level.WARNING, "No DDI codeBook found in OAI-PMH response from: {0}", url);
                    throw new IOException(
                            "Failed to extract DDI codeBook from OAI-PMH response: No DDI codeBook found");
                }

                Document ddiDoc = documentBuilder.newDocument();
                ddiDoc.appendChild(ddiDoc.importNode(codeBookNode, true));
                return ddiDoc;
            }

        } catch (IOException | SAXException | XPathExpressionException e) {
            throw new IOException("Failed to parse XML response", e);
        }
    }

    // ============================================================================
    // ACCESS RIGHTS VALIDATION
    // ============================================================================

    /**
     * Check the access rights in the DDI document.
     *
     * @param ddiDoc The DDI document to check
     * @return Result of the check
     */
    private Result checkAccessRights(Document ddiDoc) {
        Set<String> approvedValues = getApprovedAccessRights();

        try {
            synchronized (accessRightsXPath) {
                NodeList nodes = (NodeList) accessRightsXPath.evaluate(ddiDoc, XPathConstants.NODESET);

                logger.log(Level.INFO, "NodeList length: {0}", nodes.getLength());

                if (nodes.getLength() == 0) {
                    logger.log(Level.INFO, "No Access Rights element found in DDI document");
                    return Result.FAIL;
                }

                for (int i = 0; i < nodes.getLength(); i++) {
                    String val = nodes.item(i).getTextContent();
                    logger.log(Level.INFO, "Raw value: [{0}]", val);

                    String trimmedVal = val.trim().toLowerCase();
                    logger.log(Level.INFO, "Trimmed value: [{0}]", trimmedVal);

                    // check if any approved value is contained within the trimmed value
                    // (case-insensitive substring match)
                    boolean match = approvedValues.stream()
                            .map(String::toLowerCase)
                            .anyMatch(trimmedVal::contains);

                    if (match) {
                        logger.log(Level.INFO, "Approved Access Rights found: {0}", trimmedVal);
                        return Result.PASS;
                    }
                }

                logger.log(Level.INFO, "No approved Access Rights found in record");
                return Result.FAIL;
            }
        } catch (XPathExpressionException e) {
            logger.log(Level.SEVERE, "Error checking document for approved Access Rights: {0}", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Checks whether a DDI2.5 record contains provenance information.
     * Only one of publisher, author, or grant number is required.
     *
     * @param ddiDoc The DDI2.5 document to check
     * @return "pass", "fail", or "indeterminate"
     */
    public Result checkProvenance(Document ddiDoc) {
        try {
            NodeList publisherNodes;
            NodeList authorNodes;
            NodeList grantNoNodes;
            synchronized (publisherXPath) {
                publisherNodes = (NodeList) publisherXPath.evaluate(ddiDoc, XPathConstants.NODESET);
            }
            synchronized (authorXPath) {
                authorNodes = (NodeList) authorXPath.evaluate(ddiDoc, XPathConstants.NODESET);
            }
            synchronized (grantNoXPath) {
                grantNoNodes = (NodeList) grantNoXPath.evaluate(ddiDoc, XPathConstants.NODESET);
            }

            boolean hasPublisher = publisherNodes != null && publisherNodes.getLength() > 0;
            boolean hasAuthor = authorNodes != null && authorNodes.getLength() > 0;
            boolean hasGrantNo = grantNoNodes != null && grantNoNodes.getLength() > 0;

            if (hasPublisher || hasAuthor || hasGrantNo) {
                if (hasPublisher) {
                    logger.log(Level.INFO, "Publisher information found {0}", publisherNodes.item(0).getTextContent());
                }
                if (hasAuthor) {
                    logger.log(Level.INFO, "Author information found {0}", authorNodes.item(0).getTextContent());
                }
                if (hasGrantNo) {
                    logger.log(Level.INFO, "Grant number information found {0}", grantNoNodes.item(0).getTextContent());
                }
                return Result.PASS;
            } else {
                logger.log(Level.INFO, "Provenance information missing in record");
                return Result.FAIL;
            }
        } catch (XPathExpressionException e) {
            logger.log(Level.SEVERE, "Error checking provenance information: {0}", e.getMessage());
            return Result.INDETERMINATE;
        }

    }

    // ============================================================================
    // PID SCHEMA VALIDATION
    // ============================================================================

    private Result checkPidSchemas(Document ddiDoc) {
        try {
            NodeList idNoNodes;
            synchronized (pidXPath) {
                idNoNodes = (NodeList) pidXPath.evaluate(ddiDoc, XPathConstants.NODESET);
            }

            if (idNoNodes == null || idNoNodes.getLength() == 0) {
                logger.log(Level.INFO, "No IDNo elements found in DDI document");
                return Result.FAIL;
            }

            Set<String> approvedSchemas = getApprovedPidSchemas();
            for (int i = 0; i < idNoNodes.getLength(); i++) {
                Node idNoNode = idNoNodes.item(i);
                Node agencyAttr = idNoNode.getAttributes().getNamedItem("agency");
                if (agencyAttr != null) {
                    // make case insensitive comparison
                    String agency = agencyAttr.getNodeValue();
                    if (approvedSchemas.stream().anyMatch(approved -> approved.equalsIgnoreCase(agency))) {
                        logger.log(Level.INFO, "Approved PID schema found: {0}", agency);
                        return Result.PASS;
                    }
                }
            }
            logger.log(Level.INFO, "No approved PID schemas found in record");
            return Result.FAIL;
        } catch (XPathExpressionException e) {
            logger.log(Level.SEVERE, "Error checking document for approved PID: {0}", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Fetch the approved PID schemas from the CESSDA vocabulary service.
     *
     * @return Set of approved PID schema names
     */
    private Set<String> getApprovedAccessRights() {
        if (!cachedAccessRightsTerms.isEmpty()) {
            return cachedAccessRightsTerms;
        }

        logger.info("Fetching approved Access Rights schemas from CESSDA vocabulary");

        try {
            Set<String> schemas = fetchVocabularyTerms(ACCESS_VOCAB_URL, "AccessRights");
            if (schemas.isEmpty()) {
                logger.info("Using default Access Rights terms due to empty vocabulary");
                return defaultAccessRightsTerms();
            }

            cachedAccessRightsTerms.addAll(schemas);
            logger.log(Level.INFO, "Fetched approved Access Rights schemas: {0}", cachedAccessRightsTerms);
            return cachedAccessRightsTerms;

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to fetch AccessRights vocabulary: {0}", e.getMessage());
            return defaultAccessRightsTerms();
        }
    }

    /**
     * Validate ELSST keywords in the DDI document.
     * Checks for keywords with vocab="ELSST" and vocabURI containing "elsst.cessda.eu",
     * then verifies if any of those keywords match the ELSST API results for the given language code.
     * 
     * @param doc The DDI document to check
     * @return Result of the validation
     */
    @SuppressWarnings("java:S2259")
    private Result validateElsstKeywords(Document doc) {
        try {

            NodeList nodes;
            synchronized (keywordXPath) {
                nodes = (NodeList) keywordXPath.evaluate(doc, XPathConstants.NODESET);
            }

            if (nodes.getLength() == 0) {
                logger.info("No keywords found");
                return Result.FAIL;
            }

            List<KeywordCandidate> candidates = new ArrayList<>();
            String languageCode = null;

            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);

                if (!(node instanceof Element element)) {
                    continue;
                }

                String vocabAttr = element.getAttribute("vocab");
                String vocabURI = element.getAttribute("vocabURI");

                // correct XML namespace-safe extraction
                String lang = element.getAttributeNS(
                        "http://www.w3.org/XML/1998/namespace",
                        "lang");

                if (languageCode == null && lang != null && !lang.isBlank()) {
                    languageCode = lang;
                }

                String text = element.getTextContent().trim();

                logger.log(Level.INFO,
                        "Processing keyword node: text='{0}', vocab='{1}', vocabURI='{2}', lang='{3}'",
                        new Object[] { text, vocabAttr, vocabURI, lang });

                if (text.isEmpty()) {
                    continue;
                }

                boolean hasVocab = ELSST_VOCAB_NAME.equals(vocabAttr);
                boolean hasVocabURI = vocabURI != null && vocabURI.contains(ELSST_URI_SUBSTRING);

                if (hasVocab && hasVocabURI) {
                    candidates.add(new KeywordCandidate(text.trim(), true, true));

                    logger.log(Level.INFO,
                            "Candidate keyword found: '{0}'",
                            text);
                }
            }

            if (candidates.isEmpty()) {
                logger.info("No valid ELSST candidates found");
                return Result.FAIL;
            }

            if (languageCode == null || languageCode.isBlank()) {
                logger.info("No language code available for ELSST API validation");
                return Result.INDETERMINATE;
            }

            logger.log(Level.INFO,
                    "Checking {0} candidate keyword(s) via ELSST API (lang={1})",
                    new Object[] { candidates.size(), languageCode });

            return validateCandidatesAgainstElsstApi(candidates, languageCode);

        } catch (XPathExpressionException e) {
            logger.severe("XPath evaluation error: " + e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Fetch the approved PID schemas from the CESSDA vocabulary service.
     *
     * @return Set of approved PID schema names
     */
    private Set<String> getApprovedPidSchemas() {
        if (!cachedPidSchemas.isEmpty()) {
            return cachedPidSchemas;
        }

        logger.info("Fetching approved PID schemas from CESSDA vocabulary...");
        try {
            Set<String> schemas = fetchVocabularyTerms(PID_VOCAB_URL, "PID");
            if (schemas.isEmpty()) {
                return defaultPidSchemas();
            }

            cachedPidSchemas.addAll(schemas);

            logger.log(Level.INFO, "Fetched {0} approved PID schemas: {1}",
                    new Object[] { schemas.size(), cachedPidSchemas });
            return cachedPidSchemas;

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to fetch PID vocabulary: {0}", e.getMessage());
            return defaultPidSchemas();
        }
    }

    /**
     * Validate ELSST keywords in the DDI document.
     * 
     * @param candidates   List of candidate keywords with vocab and vocabURI
     *                     attributes
     * @param languageCode Language code for ELSST API query
     * @return Result of the validation
     */
    private Result validateCandidatesAgainstElsstApi(List<KeywordCandidate> candidates, String languageCode) {
        try {

            List<String> candidateTexts = candidates.stream()
                    .map(c -> c.text().trim().toUpperCase())
                    .toList();

            Set<String> elsstKeywords = fetchElsstKeywords(candidateTexts, languageCode);

            logger.info("ELSST keywords fetched from API: " + elsstKeywords);

            // Normalize API results (safe defensive normalization)
            Set<String> normalisedElsst = elsstKeywords.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());

            logger.info("ELSST keywords normalised for comparison: " + normalisedElsst);

            for (KeywordCandidate candidate : candidates) {

                String normalisedCandidate = candidate.text().trim().toUpperCase();

                logger.log(Level.INFO,
                        "Checking candidate keyword against ELSST API results: ''{0}''",
                        normalisedCandidate);

                if (normalisedElsst.contains(normalisedCandidate)) {
                    logger.info(Result.PASS
                            + ": Keyword '" + candidate.text()
                            + "' matches ELSST API result");

                    return Result.PASS;
                }
            }

            logger.info(Result.FAIL + ": No keywords match ELSST API results");
            return Result.FAIL;

        } catch (IOException e) {
            logger.severe("Failed to fetch ELSST keywords: " + e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Fetch ELSST keywords from the ELSST API for the given list of keywords and
     * language code.
     * 
     * @param keywords List of keyword texts to search for
     * @param langCode Language code for the API query (e.g. "en", "fr", etc.)
     * @return a set of ELSST keywords
     */
    private Set<String> fetchElsstKeywords(List<String> keywords, String langCode) throws IOException {

        String cacheKey = langCode + "|" + String.join(",", keywords);

        // Return cached result if available
        Set<String> cached = cachedElsstKeywordsByLang.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Set<String> result = ConcurrentHashMap.newKeySet();
        String encodedLangCode = URLEncoder.encode(langCode, StandardCharsets.UTF_8);

        for (String k : keywords) {

            String url = ELSST_API_BASE
                    + "?filter=cf.search.labels:" + URLEncoder.encode(k, StandardCharsets.UTF_8)
                    + ",cf.search.language:" + encodedLangCode;

            logger.log(Level.INFO, "Fetching ELSST keywords from API URL: {0}", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header(HTTP_HEADER_ACCEPT, "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = getHTTPResponse(
                    request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new IOException("ELSST API returned " + response.statusCode() + " for: " + url);
            }

            try (InputStream body = response.body()) {

                JsonNode root = mapper.readTree(body);
                JsonNode graph = root.path("@graph");

                if (!graph.isArray()) {
                    throw new IOException("Invalid API response: '@graph' is missing or not an array");
                }

                for (JsonNode topicNode : graph) {

                    JsonNode labels = topicNode.path("labels");

                    if (!labels.isObject()) {
                        continue;
                    }

                    JsonNode langLabel = labels.get(langCode);

                    if (langLabel != null && !langLabel.isNull()) {
                        String value = langLabel.asText();
                        if (!value.isBlank()) {
                            result.add(value);
                        }
                    }
                }
            }
        }

        cachedElsstKeywordsByLang.put(cacheKey, result);

        logger.log(Level.INFO, "Cached ELSST keywords for key {0}: {1} entries",
                new Object[] { cacheKey, result.size() });

        return result;
    }

    // ============================================================================
    // VOCABULARIES VALIDATION
    // ============================================================================

    /**
     * Check for CESSDA Topic Classification in the DDI document.
     *
     * @param ddiDoc The DDI document
     * @return "pass", "fail", or "indeterminate"
     */
    private Result checkCessdaTopicClassification(Document ddiDoc) {
        try {
            NodeList nodes;
            synchronized (topicClassXPath) {
                nodes = (NodeList) topicClassXPath.evaluate(ddiDoc, XPathConstants.NODESET);
            }

            if (nodes == null || nodes.getLength() == 0) {
                logger.info("No Topic Classification elements found");
                return Result.FAIL;
            }

            Set<String> approvedTerms = getApprovedTopicClassTerms();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                String vocabAttr = element.getAttribute("vocab");
                String text = element.getTextContent().trim();

                if (TOPIC_CLASS_VOCAB_NAME.equals(vocabAttr) && !text.isEmpty() &&
                        approvedTerms.contains(text)) {
                    logger.log(Level.INFO, "Found CESSDA Topic Classification : {0}", text);
                    return Result.PASS;
                }

            }
            logger.log(Level.INFO, "No approved Topic Classification found in record");
            return Result.FAIL;
        } catch (XPathExpressionException e) {
            logger.log(Level.SEVERE, "Error checking Topic Classification: {0}", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Check for DDI Analysis Unit in the DDI document.
     *
     * @param ddiDoc The DDI document
     * @return "pass", "fail", or "indeterminate"
     */
    private Result checkAnalysisUnit(Document ddiDoc) {
        try {
            synchronized (analysisUnitXPath) {
                NodeList nodes = (NodeList) analysisUnitXPath.evaluate(ddiDoc, XPathConstants.NODESET);
                if (nodes == null || nodes.getLength() == 0) {
                    logger.info("No Analysis Unit terms found");
                    return Result.FAIL;
                }

                Set<String> approvedTerms = getApprovedAnalysisUnitTerms();

                for (int i = 0; i < nodes.getLength(); i++) {
                    // Get only the direct text content, excluding child elements
                    StringBuilder description = new StringBuilder();
                    Element anlyUnit = (Element) nodes.item(i);
                    for (Node child = anlyUnit.getFirstChild(); child != null; child = child.getNextSibling()) {
                        if (child.getNodeType() == Node.TEXT_NODE) {
                            description.append(child.getTextContent());
                        }
                    }
                    // Trim and check the description text
                    String descriptionText = description.toString().trim();
                    if (!descriptionText.isEmpty() && approvedTerms.contains(descriptionText)) {
                        logger.log(Level.INFO, "Found Analysis Unit term: {0}", descriptionText);
                        return Result.PASS;
                    }
                }
                logger.log(Level.INFO, "No approved Analysis Unit term found in record");
                return Result.FAIL;
            }
        } catch (XPathExpressionException e) {
            logger.log(Level.SEVERE, "Error checking Analysis Unit {0}", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Check for DDI Time Method in the DDI document.
     *
     * @param ddiDoc The DDI document
     * @return "pass", "fail", or "indeterminate"
     */
    private Result checkTimeMethod(Document ddiDoc) {
        try {
            NodeList nodes;
            synchronized (timeMethodXPath) {
                nodes = (NodeList) timeMethodXPath.evaluate(ddiDoc, XPathConstants.NODESET);
            }

            if (nodes == null || nodes.getLength() == 0) {
                logger.info("No Time Method elements found");
                return Result.FAIL;
            }

            Set<String> approvedTerms = getApprovedTimeMethodTerms();
            logger.log(Level.INFO, "Approved Time Method terms: {0}", approvedTerms);

            for (int i = 0; i < nodes.getLength(); i++) {
                // Get only the direct text content, excluding child elements
                StringBuilder description = new StringBuilder();
                Element timeMeth = (Element) nodes.item(i);
                for (Node child = timeMeth.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child.getNodeType() == Node.TEXT_NODE) {
                        description.append(child.getTextContent());
                    }
                }
                // Trim and check the description text
                String descriptionText = description.toString().trim();
                logger.log(Level.INFO, "Time Method description text: {0}", descriptionText);
                if (!descriptionText.isEmpty() && approvedTerms.contains(descriptionText)) {
                    logger.log(Level.INFO, "Found Time Method term: {0}", descriptionText);
                    return Result.PASS;
                }
            }

            logger.log(Level.INFO, "No approved Time Method found in record");
            return Result.FAIL;
        } catch (XPathExpressionException e) {
            logger.log(Level.SEVERE, "Error checking Time Method {0}", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Check for DDI Sampling Procedure in the DDI document.
     *
     * @param ddiDoc The DDI document
     * @return "pass", "fail", or "indeterminate"
     */
    private Result checkDdiSamplingProcedure(Document ddiDoc) {
        try {
            NodeList nodes;
            synchronized (samplingProcXPath) {
                nodes = (NodeList) samplingProcXPath.evaluate(ddiDoc, XPathConstants.NODESET);
                if (nodes == null || nodes.getLength() == 0) {
                    logger.info("No Sampling Procedure terms found");
                    return Result.FAIL;
                }

                Set<String> approvedTerms = getApprovedSamplingProcTerms();
                for (int i = 0; i < nodes.getLength(); i++) {
                    String text = nodes.item(i).getTextContent().trim();
                    if (!text.isEmpty() && approvedTerms.contains(text)) {
                        logger.log(Level.INFO, "Found DDI Sampling Procedure term: {0}", text);
                        return Result.PASS;
                    }
                }
                logger.log(Level.INFO, "No Sampling Procedure terms found in record");
                return Result.FAIL;
            }
        } catch (XPathExpressionException e) {
            logger.log(Level.SEVERE, "Error checking for Sampling Procedure terms {0}", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    /**
     * Check for DDI Mode of Collection in the DDI document.
     *
     * @param ddiDoc The DDI document
     * @return "pass", "fail", or "indeterminate"
     */
    private Result checkCollectionMode(Document ddiDoc) {
        try {
            NodeList nodes;
            synchronized (collectionModeXPath) {
                nodes = (NodeList) collectionModeXPath.evaluate(ddiDoc, XPathConstants.NODESET);
                if (nodes == null || nodes.getLength() == 0) {
                    logger.info("No Mode of Collection elements found");
                    return Result.FAIL;
                }

                Set<String> approvedTerms = getApprovedCollectionModeTerms();

                for (int i = 0; i < nodes.getLength(); i++) {
                    Element collMode = (Element) nodes.item(i);

                    // Get only the direct text content, excluding child elements
                    StringBuilder description = new StringBuilder();
                    for (Node child = collMode.getFirstChild(); child != null; child = child.getNextSibling()) {
                        if (child.getNodeType() == Node.TEXT_NODE) {
                            description.append(child.getTextContent());
                        }
                    }
                    // Trim and check the description text
                    String descriptionText = description.toString().trim();

                    if (!descriptionText.isEmpty() && approvedTerms.contains(descriptionText)) {
                        logger.log(Level.INFO, "Found Mode of Collection term: {0}", descriptionText);
                        return Result.PASS;
                    }
                }

                logger.log(Level.INFO, "No approved Mode of Collection found in record");
                return Result.FAIL;
            }
        } catch (XPathExpressionException e) {
            logger.log(Level.SEVERE, "Error checking Mode of Collection", e);
            return Result.INDETERMINATE;
        }
    }

    /**
     * Fetch and cache approved Topic Classification terms from CESSDA vocabulary.
     *
     * @return Set of approved Topic Classification terms
     */
    private Set<String> getApprovedTopicClassTerms() {
        if (!cachedTopicClassTerms.isEmpty()) {
            return cachedTopicClassTerms;
        }

        logger.info("Fetching approved Topic Classification terms from CESSDA vocabulary...");
        try {
            Set<String> terms = fetchVocabularyTerms(TOPIC_CLASS_VOCAB_URL, "TopicClassification");
            if (terms.isEmpty()) {
                logger.info("Using default Topic Classification terms");
                return Collections.emptySet();
            }
            cachedTopicClassTerms.addAll(terms);
            logger.log(Level.INFO, FETCHED + "{0} approved Topic Classification terms", terms.size());
            return cachedTopicClassTerms;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to fetch Topic Classification vocabulary", e);
            return Collections.emptySet();
        }
    }

    /**
     * Fetch and cache approved Analysis Unit terms from CESSDA vocabulary.
     *
     * @return Set of approved Analysis Unit terms
     */
    private Set<String> getApprovedAnalysisUnitTerms() {
        if (!cachedAnalysisUnitTerms.isEmpty()) {
            return cachedAnalysisUnitTerms;
        }

        logger.info("Fetching approved Analysis Unit terms from CESSDA vocabulary");
        try {
            Set<String> terms = fetchVocabularyTerms(ANALYSIS_UNIT_VOCAB_URL, "AnalysisUnit");
            if (terms.isEmpty()) {
                return Collections.emptySet();
            }

            cachedAnalysisUnitTerms.addAll(terms);
            logger.log(Level.INFO, FETCHED + "{0} approved Analysis Unit terms", terms.size());
            return cachedAnalysisUnitTerms;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to fetch Analysis Unit vocabulary", e);
            return Collections.emptySet();
        }
    }

    /**
     * Fetch and cache approved Time Method terms from CESSDA vocabulary.
     *
     * @return Set of approved Time Method terms
     */
    private Set<String> getApprovedTimeMethodTerms() {
        if (!cachedTimeMethodTerms.isEmpty()) {
            return cachedTimeMethodTerms;
        }

        logger.info("Fetching approved Time Method terms from CESSDA vocabulary");
        try {
            Set<String> terms = fetchVocabularyTerms(TIME_METHOD_VOCAB_URL, "TimeMethod");
            if (terms.isEmpty()) {
                return Collections.emptySet();
            }

            cachedTimeMethodTerms.addAll(terms);
            logger.log(Level.INFO, FETCHED + "{0} approved Time Method terms", terms.size());
            return cachedTimeMethodTerms;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to fetch Time Method vocabulary", e);
            return Collections.emptySet();
        }
    }

    /**
     * Fetch and cache approved Sampling Procedure terms from DDI vocabulary.
     *
     * @return Set of approved Sampling Procedure terms
     */
    private Set<String> getApprovedSamplingProcTerms() {
        if (!cachedSamplingProcTerms.isEmpty()) {
            return cachedSamplingProcTerms;
        }

        logger.info("Fetching Sampling Procedure terms from CESSDA vocabulary");
        try {
            Set<String> terms = fetchVocabularyTerms(SAMPLING_PROC_VOCAB_URL, "SamplingProcedure");
            if (terms.isEmpty()) {
                return Collections.emptySet();
            }

            cachedSamplingProcTerms.addAll(terms);
            logger.log(Level.INFO, FETCHED + "{0} approved Sampling Procedure terms", terms.size());
            return cachedSamplingProcTerms;
        } catch (IOException e) {
            Thread.currentThread().interrupt(); // Restore interrupt status
            logger.log(Level.SEVERE, "Failed to fetch Sampling Procedure vocabulary", e);
            return Collections.emptySet();
        }
    }

    /**
     * Fetch and cache approved Mode of Collection terms from CESSDA vocabulary.
     *
     * @return Set of approved Mode of Collection terms
     */
    private Set<String> getApprovedCollectionModeTerms() {
        if (!cachedCollectionModeTerms.isEmpty()) {
            return cachedCollectionModeTerms;
        }

        logger.info("Fetching approved Mode of Collection terms from CESSDA vocabulary");
        try {
            Set<String> terms = fetchVocabularyTerms(COLLECTION_MODE_VOCAB_URL, "ModeOfCollection");
            if (terms.isEmpty()) {
                return Collections.emptySet();
            }

            cachedCollectionModeTerms.addAll(terms);
            logger.log(Level.INFO, FETCHED + "{0} approved Mode of Collection terms", terms.size());
            return cachedCollectionModeTerms;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to fetch Mode of Collection vocabulary", e);
            return Collections.emptySet();
        }
    }

    // ============================================================================
    // SHARED RESOURCE METHODS
    // ============================================================================

    /**
     * Get HTTP response for the given request and body handler.
     */
    private <T> HttpResponse<T> getHTTPResponse(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException {
        HttpResponse<T> response;
        try {
            response = httpClient.send(request, bodyHandler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return response;
    }

    // ============================================================================
    // SHARED VOCABULARY FETCHING
    // ============================================================================

    /**
     * Fetch vocabulary terms from the given vocabulary URL.
     */
    private Set<String> fetchVocabularyTerms(String vocabUrl, String vocabType) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vocabUrl))
                .header(HTTP_HEADER_ACCEPT, "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        var response = getHTTPResponse(request, HttpResponse.BodyHandlers.ofInputStream());

        // don't want an exception for 404 here, just return empty set
        if (response.statusCode() != 200) {
            logger.log(Level.WARNING, "Vocabulary API returned {0} for {1}",
                    new Object[] { response.statusCode(), vocabType });
            return Collections.emptySet();
        }

        JsonNode root = mapper.readTree(response.body());
        Set<String> terms = new HashSet<>();

        JsonNode versions = root.path("versions");
        if (versions.isArray() && !versions.isEmpty()) {
            JsonNode firstVersion = versions.get(0);
            JsonNode concepts = firstVersion.path("concepts");

            if (concepts.isArray()) {
                for (JsonNode titleNode : concepts) {
                    String value = titleNode.path("title").asText(null);
                    if (value != null && !value.isBlank()) {
                        value = value.trim();
                        terms.add(value);
                    }
                }
            }
        }

        if (terms.isEmpty()) {
            logger.log(Level.SEVERE, "{0} terms found in vocabulary response", vocabType);
        }

        return terms;
    }

}
