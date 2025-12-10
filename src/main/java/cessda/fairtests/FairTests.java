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

package cessda.fairtests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * <h1>FairTests</h1>
 * <p>
 * Consolidated utility class for checking CESSDA Data Catalogue records against
 * various FAIR data criteria:
 * - Access Rights compliance
 * - Persistent Identifier (PID) schema validation
 * - ELSST controlled vocabulary keyword validation
 * <p>
 * All tests fetch DDI 2.5 metadata via the CESSDA OAI-PMH endpoint and validate
 * against approved vocabularies from the CESSDA vocabulary service.
 * <p>
 * Return values for all tests:
 * <ul>
 *     <li>"pass": the record meets the criteria</li>
 *     <li>"fail": the record does not meet the criteria</li>
 *     <li>"indeterminate": an error occurred preventing definitive determination</li>
 * </ul>
 */
public class FairTests {

    // Logger
    private static final Logger logger = Logger.getLogger(FairTests.class.getName());

    // Common constants
    private static final String DDI_NAMESPACE = "ddi:codebook:2_5";
    private static final String OAI_PMH_BASE = "https://datacatalogue.cessda.eu/oai-pmh/v0/oai?verb=GetRecord&metadataPrefix=oai_ddi25&identifier=";
    private static final String DETAIL_SEGMENT = "/detail/";

    // Vocabulary URLs
    private static final String ACCESS_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/CessdaAccessRights/1.0.0?languageVersion=en-1.0.0&format=json";
    private static final String PID_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/CessdaPersistentIdentifierTypes/1.0.0?languageVersion=en-1.0.0&format=json";
    private static final String ELSST_API_BASE = "https://skg-if-openapi.cessda.eu/api/topics";

    // ELSST constants
    private static final String ELSST_VOCAB_NAME = "ELSST";
    private static final String ELSST_URI_SUBSTRING = "elsst.cessda.eu";
    private static final String HTTP_HEADER_ACCEPT = "Accept";

    // Shared components
    private final DocumentBuilder documentBuilder;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final XPath xPath = XPathFactory.newInstance().newXPath();
    // XPath expressions
    private final XPathExpression accessRightsXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:dataAccs/ddi:typeOfAccess");
    private final XPathExpression ddiCodebookXPath = xPath.compile("//ddi:codeBook");
    private final XPathExpression pidXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:citation/ddi:titlStmt/ddi:IDNo");
    private final XPathExpression keywordXPath = xPath.compile("//ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:keyword");
    /**
     * Constructor initialises shared components.
     *
     */
    public FairTests() throws ParserConfigurationException, XPathExpressionException {
        var documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        this.documentBuilder = documentBuilderFactory.newDocumentBuilder();

        // Set XPath namespace context
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
    }

    // Cached vocabularies
    private final ConcurrentSkipListSet<String> cachedAccessRightsTerms = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<String> cachedPidSchemas = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<String> cachedElsstKeywords = new ConcurrentSkipListSet<>();

    /**
     * Main method for command-line execution.
     *
     * @param args args[0]: test type ("access-rights", "pid", "elsst-keywords")
     *             args[1]: CESSDA detail URL
     *
     */
    public static void main(String[] args) throws ParseException, ParserConfigurationException, XPathExpressionException {

        // Set logger level
        logger.setLevel(Level.INFO);

        // Command line options
        var options = new Options();
        var commandLine = new DefaultParser().parse(options, args);

        var testMap = new HashMap<String, TestTypes>();
        for (TestTypes testTypes : EnumSet.allOf(TestTypes.class)) {
            testMap.put(testTypes.testName(), testTypes);
        }

        if (commandLine.getArgList().size() < 2 || testMap.containsKey(commandLine.getArgList().getFirst())) {
            new HelpFormatter().printHelp("FairTests <test-type> <url>\ntest types: access-rights, pid, elsst-keywords", options);
            System.exit(1);
        }

        TestTypes test = testMap.get(commandLine.getArgList().getFirst());
        String url = commandLine.getArgList().get(1);

        // Instance tests
        FairTests tests = new FairTests();

        // Run tests and get result
        Result result = switch (test) {
            case ACCESS_RIGHTS -> tests.containsApprovedAccessRights(url);
            case PID -> tests.containsApprovedPid(url);
            case ELSST_KEYWORDS -> tests.containsElsstKeywords(url);
        };

        logger.log(Level.INFO, "Result: {0}", result);
        System.exit(Result.PASS == result ? 0 : 1);
    }

    // ============================================================================
    // PUBLIC API METHODS
    // ============================================================================

    /**
     * Checks whether a CESSDA record contains an approved Access Rights term.
     *
     * @param url The CESSDA detail URL
     * @return "pass", "fail", or "indeterminate"
     */
    public Result containsApprovedAccessRights(String url) {
        try {
            String recordId = extractRecordIdentifier(url);
            Document doc = fetchAndParseDocument(OAI_PMH_BASE + recordId);
            return checkAccessRights(doc, recordId);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Couldn't check access rights", e);
            return Result.INDETERMINATE;
        }
    }

    private static Set<String> defaultAccessRightsTerms() {
        return Set.of("Open", "Restricted");
    }

    private static Set<String> defaultPidSchemas() {
        return Set.of("DOI", "Handle", "URN", "ARK");
    }

    // ============================================================================
    // SHARED UTILITY METHODS
    // ============================================================================

    /**
     * Checks whether a CESSDA record contains an approved PID schema.
     *
     * @param url The CESSDA detail URL
     * @return "pass", "fail", or "indeterminate"
     */
    public Result containsApprovedPid(String url) {
        try {
            String recordId = extractRecordIdentifier(url);
            Document doc = fetchAndParseDocument(OAI_PMH_BASE + recordId);
            return checkPidSchemas(doc, recordId);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Couldn't check approved PIDs", e);
        }
        return Result.INDETERMINATE;
    }

    /**
     * Checks whether a CESSDA record contains ELSST keywords that meet ALL three criteria:
     * 1. vocab attribute equals "ELSST"
     * 2. vocabURI attribute contains "elsst.cessda.eu"
     * 3. Keyword text matches an ELSST API label
     *
     * @param url The CESSDA detail URL
     * @return "pass", "fail", or "indeterminate"
     */
    public Result containsElsstKeywords(String url) {
        try {
            String recordId = extractRecordIdentifier(url);
            logger.log(Level.INFO, "Extracted record identifier: {0}", recordId);

            var languageCode = extractLanguageCodeFromUrl(new URI(url));
            logger.log(Level.INFO, "Extracted language code: {0}", languageCode);

            Document doc = fetchAndParseDocument(OAI_PMH_BASE + recordId);
            return validateElsstKeywords(doc, languageCode);
        } catch (IOException | URISyntaxException e) {
            logger.log(Level.SEVERE, "Failed to check if a record contains ELSST keywords: ", e);
            return Result.INDETERMINATE;
        }
    }

    /**
     * Extract the record identifier from the CESSDA detail URL.
     */
    private String extractRecordIdentifier(String url) {
        if (!url.contains(DETAIL_SEGMENT)) {
            throw new IllegalArgumentException("URL must contain '" + DETAIL_SEGMENT + "': " + url);
        }
        String cleanUrl = url.split("\\?")[0];
        String id = cleanUrl.substring(cleanUrl.indexOf(DETAIL_SEGMENT) + DETAIL_SEGMENT.length());
        if (id.isEmpty()) {
            throw new IllegalArgumentException("No record identifier in URL: " + url);
        }
        return id;
    }

    /**
     * Fetch the OAI-PMH GetRecord XML and parse to extract the DDI codeBook element.
     * @param url The OAI-PMH GetRecord URL
     * @return The DDI codeBook Document
     * @throws IOException - if an I/O error occurs
    */
    public Document fetchAndParseDocument(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
            .header(HTTP_HEADER_ACCEPT, "application/xml, text/xml, */*")
            .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        var response = getHTTPResponse(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200)
            throw new IOException("Failed to fetch document: HTTP " + response.statusCode());

        try (var body = response.body()) {
            logger.log(Level.INFO, "Parsing XML response from OAI-PMH endpoint at: {0}", url);
            Document oaiDoc = documentBuilder.parse(body);

            Node codeBookNode = (Node) ddiCodebookXPath.evaluate(oaiDoc, XPathConstants.NODE);
            if (codeBookNode == null) {
                throw new IllegalArgumentException("No DDI codeBook found");
            }

            Document ddiDoc = documentBuilder.newDocument();
            ddiDoc.appendChild(ddiDoc.importNode(codeBookNode, true));
            return ddiDoc;

        } catch (XPathExpressionException e) {
            // parser and xpath expression should always be valid
            throw new IllegalStateException(e);
        } catch (SAXException e) {
            throw new IOException("Failed to parse XML response", e);
        }
    }

    // ============================================================================
    // ACCESS RIGHTS VALIDATION
    // ============================================================================

    /**
     * Extract the language code from the URL query parameter.
     */
    private String extractLanguageCodeFromUrl(URI uri) {
        String query = uri.getQuery();

        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && kv[0].equalsIgnoreCase("lang") && kv[1].matches("^[a-zA-Z]{2}$")) {
                    return kv[1].toLowerCase();
                }
            }
        }

        return null;
    }

    private Result checkAccessRights(Document ddiDoc, String recordId) {
        Set<String> approvedValues = getApprovedAccessRights();

        try {
            NodeList nodes = (NodeList) accessRightsXPath.evaluate(ddiDoc, XPathConstants.NODESET);
            logger.log(Level.INFO, "NodeList length: {0}", nodes.getLength());

            if (nodes.getLength() == 0) {
                logger.log(Level.INFO, "No Access Rights element found in DDI document for record: {0}", recordId);
                return Result.FAIL;
            }

            for (int i = 0; i < nodes.getLength(); i++) {
                String val = nodes.item(i).getTextContent().trim();
                if (approvedValues.contains(val)) {
                    logger.log(Level.INFO, "Match found: {0}", val);
                    return Result.PASS;
                }
            }

            logger.log(Level.INFO, "No approved Access Rights found in record: {0}", recordId);
            return Result.FAIL;
        } catch (XPathExpressionException e) {
            throw new IllegalStateException(e);
        }
    }

    // ============================================================================
    // PID SCHEMA VALIDATION
    // ============================================================================

    private Result checkPidSchemas(Document ddiDoc, String recordId) {
        try {
            NodeList idNoNodes = (NodeList) pidXPath.evaluate(ddiDoc, XPathConstants.NODESET);

            if (idNoNodes == null || idNoNodes.getLength() == 0) {
                logger.log(Level.INFO, "No IDNo elements found in DDI document for record: {0}", recordId);
                return Result.FAIL;
            }

            Set<String> approvedSchemas = getApprovedPidSchemas();
            for (int i = 0; i < idNoNodes.getLength(); i++) {
                Node idNoNode = idNoNodes.item(i);
                Node agencyAttr = idNoNode.getAttributes().getNamedItem("agency");
                if (agencyAttr != null) {
                    String agency = agencyAttr.getNodeValue();
                    if (approvedSchemas.contains(agency)) {
                        logger.log(Level.INFO, "Found approved PID schema ''{0}'' in record: {1}", new String[]{agency, recordId});
                        return Result.PASS;
                    }
                }
            }
            logger.log(Level.INFO, "No approved PID schemas found in record: {0}", recordId);
            return Result.FAIL;
        } catch (XPathExpressionException e) {
            logger.severe("Error checking document for approved PID: " + e.getMessage());
            return Result.INDETERMINATE;
        }
    }

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
            logger.log(Level.SEVERE, "Failed to fetch AccessRights vocabulary: ", e);
            return defaultAccessRightsTerms();
        }
    }

    private Result validateElsstKeywords(Document doc, String languageCode) {
        try {
            NodeList nodes = (NodeList) keywordXPath.evaluate(doc, XPathConstants.NODESET);

            if (nodes.getLength() == 0) {
                logger.info("No keywords found");
                return Result.FAIL;
            }

            List<KeywordCandidate> candidates = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!(node instanceof Element element)) {
                    continue;
                }

                String vocabAttr = element.getAttribute("vocab");
                String vocabURI = element.getAttribute("vocabURI");
                String text = element.getTextContent().trim();

                if (!text.isEmpty()) {
                    boolean hasVocab = ELSST_VOCAB_NAME.equals(vocabAttr);
                    boolean hasVocabURI = vocabURI.contains(ELSST_URI_SUBSTRING);

                    logger.log(Level.INFO, "Keyword ''{0}'': vocab={1}, vocabURI={2}", new Object[]{text, hasVocab, hasVocabURI});

                    if (hasVocab && hasVocabURI) {
                        candidates.add(new KeywordCandidate(text.trim(), true, true));
                        logger.log(Level.INFO, "Candidate keyword found: ''{0}'' (has vocab and vocabURI)", text);
                    }
                }
            }

            if (candidates.isEmpty()) {
                logger.info("No keywords found with both vocab='ELSST' and vocabURI containing 'elsst.cessda.eu'");
                return Result.FAIL;
            }

            logger.log(Level.INFO, "Checking {0} candidate keyword(s) via ELSST API", candidates.size());

            if (languageCode == null) {
                logger.info("No language code available for ELSST API validation");
                return Result.INDETERMINATE;
            }

            return validateCandidatesAgainstElsstApi(candidates, languageCode);

        } catch (XPathExpressionException e) {
            logger.severe("XPath evaluation error: " + e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    // ============================================================================
    // ELSST KEYWORD VALIDATION
    // ============================================================================

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

            logger.log(Level.INFO, "Fetched {0} approved PID schemas: {1}", new Object[]{schemas.size(), cachedPidSchemas});
            return cachedPidSchemas;

        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to fetch PID vocabulary", e);
            return defaultPidSchemas();
        }
    }

    private Result validateCandidatesAgainstElsstApi(List<KeywordCandidate> candidates, String languageCode) {
        try {

            List<String> candidateTexts = candidates.stream().map(KeywordCandidate::text).toList();
            Set<String> elsstKeywords = fetchElsstKeywords(candidateTexts, languageCode);

            Set<String> elsstSet = elsstKeywords.stream()
                    .filter(k -> k.startsWith(languageCode + ":"))
                    .map(k -> k.substring(languageCode.length() + 2).replace("\"", "").trim().toUpperCase())
                    .collect(Collectors.toSet());

            for (KeywordCandidate candidate : candidates) {
                if (elsstSet.contains(candidate.text().toUpperCase())) {
                    logger.info(Result.PASS + ": Keyword '" + candidate.text() + "' meets ALL three conditions (vocab, vocabURI, and API match)");
                    return Result.PASS;
                }
            }

            logger.info(Result.FAIL + ": No keywords meet all three conditions");
            return Result.FAIL;

        } catch (IOException e) {
            logger.severe("Failed to fetch ELSST keywords: " + e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    private Set<String> fetchElsstKeywords(List<String> keywords, String langCode) throws IOException {
        if (!cachedElsstKeywords.isEmpty()) {
            return cachedElsstKeywords;
        }

        String encodedLangCode = URLEncoder.encode(langCode, StandardCharsets.UTF_8);


        for (var k : keywords) {
            String url = ELSST_API_BASE
                + "?filter=cf.search.labels:" + URLEncoder.encode(k, StandardCharsets.UTF_8)
                + ",cf.search.language:" + encodedLangCode;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(HTTP_HEADER_ACCEPT, "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET().build();

            var response = getHTTPResponse(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new IOException("ELSST API returned " + response.statusCode() + " for: " + url);
            }

            Set<String> keywordsToCache = new HashSet<>();
            JsonNode results = mapper.readTree(response.body()).path("results");
            for (JsonNode r : results) {
                JsonNode labels = r.path("labels");
                if (labels.isObject()) {
                    labels.propertyStream().forEach(e -> keywordsToCache.add(e.getKey() + ":\"" + e.getValue().asText() + "\""));
                }
            }
            cachedElsstKeywords.addAll(keywordsToCache);
        }

        logger.log(Level.INFO, "Number of cachedElsstKeywords: {0}", cachedElsstKeywords.size());
        return cachedElsstKeywords;
    }

    // Enums
    private enum TestTypes {
        ACCESS_RIGHTS("access-rights"),
        PID("pid"),
        ELSST_KEYWORDS("elsst-keywords");

        private final String name;

        TestTypes(String name) {
            this.name = name;
        }

        public String testName() {
            return name;
        }
    }

    // ============================================================================
    // SHARED VOCABULARY FETCHING
    // ============================================================================

    private <T> HttpResponse<T> getHTTPResponse(HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) throws IOException {
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
    // MAIN METHOD
    // ============================================================================

    private Set<String> fetchVocabularyTerms(String vocabUrl, String vocabType) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vocabUrl))
            .header(HTTP_HEADER_ACCEPT, "application/json")
            .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        var response = getHTTPResponse(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException(vocabType + " vocabulary API returned " + response.statusCode());
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
                        logger.log(Level.INFO, "Found {0} entry: {1}", new String[]{vocabType, value});
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
