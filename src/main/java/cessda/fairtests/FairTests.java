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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * FairTests
 *
 * Consolidated utility class for checking CESSDA Data Catalogue records against
 * various FAIR data criteria:
 * - Access Rights compliance
 * - Persistent Identifier (PID) schema validation
 * - ELSST controlled vocabulary keyword validation
 * - Use of CESSDA controlled vocabularies
 *
 * All tests fetch DDI 2.5 metadata via the CESSDA OAI-PMH endpoint and validate
 * against approved vocabularies from the CESSDA vocabulary service.
 *
 * Return values for all tests:
 * - "pass": the record meets the criteria
 * - "fail": the record does not meet the criteria
 * - "indeterminate": an error occurred preventing definitive determination
 */
public class FairTests {

    // Common constants
    private static final String DDI_NAMESPACE = "ddi:codebook:2_5";
    private static final String OAI_PMH_BASE = "https://datacatalogue.cessda.eu/oai-pmh/v0/oai?verb=GetRecord&metadataPrefix=oai_ddi25&identifier=";
    private static final String DETAIL_SEGMENT = "/detail/";
    private static final String RESULT_PASS = "pass";
    private static final String RESULT_FAIL = "fail";
    private static final String RESULT_INDETERMINATE = "indeterminate";
    private static final String DOC_PROC_ERROR = "Error processing document: ";
    private static final String ERROR = "Error: ";
    private static final String HEAD_ACCEPT = "Accept";
    private static final String FETCHED = "Fetched ";

    // XPath expressions
    private static final String ACCESS_RIGHTS_PATH = "//ddi:codeBook/ddi:stdyDscr/ddi:dataAccs/ddi:typeOfAccess";
    private static final String PID_PATH = "//ddi:codeBook/ddi:stdyDscr/ddi:citation/ddi:titlStmt/ddi:IDNo";
    private static final String KEYWORD_PATH = "//ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:keyword";
    private static final String TOPIC_CLASS_PATH = "//ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:topcClas";
    private static final String ANALYSIS_UNIT_PATH = "//ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:sumDscr/ddi:anlyUnit";
    private static final String TIME_METHOD_PATH = "//ddi:codeBook/ddi:stdyDscr/ddi:method/ddi:dataColl/ddi:timeMeth";
    private static final String SAMPLING_PROC_PATH = "//ddi:codeBook/ddi:stdyDscr/ddi:method/ddi:dataColl/ddi:sampProc";
    private static final String COLLECTION_MODE_PATH = "//ddi:codeBook/ddi:stdyDscr/ddi:method/ddi:dataColl/ddi:collMode";

    // Vocabulary URLs
    private static final String ACCESS_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/CessdaAccessRights/1.0.0?languageVersion=en-1.0.0&format=json";
    private static final String PID_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/CessdaPersistentIdentifierTypes/1.0.0?languageVersion=en-1.0.0&format=json";
    private static final String ELSST_API_BASE = "https://skg-if-openapi.cessda.eu/api/topics";
    private static final String TOPIC_CLASS_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/TopicClassification/4.0.0?languageVersion=en-4.0.0&format=json";
    private static final String ANALYSIS_UNIT_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/AnalysisUnit/1.2.0?languageVersion=en-1.2.0&format=json";
    private static final String TIME_METHOD_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/TimeMethod/1.2.1?languageVersion=en-1.2.1&format=json";
    private static final String SAMPLING_PROC_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/SamplingProcedure/2.0.0?languageVersion=en-2.0.0&format=json";
    private static final String COLLECTION_MODE_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/ModeOfCollection/4.0.0?languageVersion=en-4.0.0&format=json";

    // ELSST constants
    private static final String ELSST_VOCAB_NAME = "ELSST";
    private static final String ELSST_URI_SUBSTRING = "elsst.cessda.eu";

    // Topic Classification constant
    private static final String TOPIC_CLASS_VOCAB_NAME = "CESSDA Topic Classification";

    // Shared components
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final DocumentBuilderFactory documentBuilderFactory;
    private final XPathFactory xPathFactory;
    private static final Logger logger = Logger.getLogger(FairTests.class.getName());

    // Cached vocabularies
    private static volatile Set<String> cachedAccessRightsTerms;
    private static volatile Set<String> cachedPidSchemas;
    private static volatile Set<String> cachedTopicClassTerms;
    private static volatile Set<String> cachedAnalysisUnitTerms;
    private static volatile Set<String> cachedTimeMethodTerms;
    private static volatile Set<String> cachedSamplingProcTerms;
    private static volatile Set<String> cachedCollectionModeTerms;
    private volatile Set<String> cachedElsstKeywords;
    private String languageCode;

    /**
     * Constructor initialises shared components.
     * 
     */
    public FairTests() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.mapper = new ObjectMapper();
        this.documentBuilderFactory = DocumentBuilderFactory.newInstance();
        this.documentBuilderFactory.setNamespaceAware(true);
        this.xPathFactory = XPathFactory.newInstance();
        logger.setLevel(Level.INFO);
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
    public String containsApprovedAccessRights(String url) {
        try {
            String recordId = extractRecordIdentifier(url);
            Document doc = fetchAndParseDocument(OAI_PMH_BASE + recordId);
            return checkAccessRights(doc, recordId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logSevere(DOC_PROC_ERROR + e.getMessage());
        } catch (Exception e) {
            logSevere(ERROR + e.getMessage());
        }
        return RESULT_INDETERMINATE;
    }

    /**
     * Checks whether a CESSDA record contains an approved PID schema.
     *
     * @param url The CESSDA detail URL
     * @return "pass", "fail", or "indeterminate"
     */
    public String containsApprovedPid(String url) {
        try {
            String recordId = extractRecordIdentifier(url);
            Document doc = fetchAndParseDocument(OAI_PMH_BASE + recordId);
            return checkPidSchemas(doc, recordId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logSevere(DOC_PROC_ERROR + e.getMessage());
        } catch (Exception e) {
            logSevere(ERROR + e.getMessage());
        }
        return RESULT_INDETERMINATE;
    }

    /**
     * Checks whether a CESSDA record contains ELSST keywords that meet ALL three
     * criteria:
     * 1. vocab attribute equals "ELSST"
     * 2. vocabURI attribute contains "elsst.cessda.eu"
     * 3. Keyword text matches an ELSST API label
     *
     * @param url The CESSDA detail URL
     * @return "pass", "fail", or "indeterminate"
     */
    public String containsElsstKeywords(String url) {
        try {
            String recordId = extractRecordIdentifier(url);
            logInfo("Extracted record identifier: " + recordId);

            extractLanguageCodeFromUrl(url);
            logInfo("Extracted language code: " + languageCode);

            Document doc = fetchAndParseDocument(OAI_PMH_BASE + recordId);
            return validateElsstKeywords(doc, url);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logSevere(DOC_PROC_ERROR + e.getMessage());
        } catch (Exception e) {
            logSevere(ERROR + e.getMessage());
        }
        return RESULT_INDETERMINATE;
    }

    /**
     * Checks whether a CESSDA record uses recommended DDI controlled vocabularies.
     * Tests for presence of:
     * - DDI Analysis Unit
     * - DDI Time Method
     * - DDI Mode of Collection
     *
     * @param url The CESSDA detail URL
     * @return "pass", "fail", or "indeterminate"
     */
    public String containsRecommendedDdiVocabularies(String url) {
        try {
            String recordId = extractRecordIdentifier(url);
            logInfo("Checking CESSDA vocabularies for record: " + recordId);

            Document doc = fetchAndParseDocument(OAI_PMH_BASE + recordId);
            return checkRecommendedDdiVocabularies(doc, recordId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logSevere(DOC_PROC_ERROR + e.getMessage());
        } catch (Exception e) {
            logSevere(ERROR + e.getMessage());
        }
        return RESULT_INDETERMINATE;
    }

    /**
     * Checks whether a CESSDA record uses Topic Classification vocabulary terms.
     *
     * @param url The CESSDA detail URL
     * @return "pass", "fail", or "indeterminate"
     */
    public String containsCessdaTopicClassificationTerms(String url) {
        try {
            String recordId = extractRecordIdentifier(url);
            XPath xpath = createXPath();
            logInfo("Checking Topic Classification for record: " + recordId);

            Document doc = fetchAndParseDocument(OAI_PMH_BASE + recordId);
            return checkCessdaTopicClassification(xpath, doc, recordId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logSevere(DOC_PROC_ERROR + e.getMessage());
        } catch (Exception e) {
            logSevere(ERROR + e.getMessage());
        }
        return RESULT_INDETERMINATE;
    }
    
     /**
     * Checks whether a CESSDA record uses DDI Sampling Procedure vocabulary terms.
     *
     * @param url The CESSDA detail URL
     * @return "pass", "fail", or "indeterminate"
     */
    public String containsDdiSamplingProcedureTerms(String url) {
        try {
            String recordId = extractRecordIdentifier(url);
            logInfo("Checking Sampling Procedure for record: " + recordId);

            Document doc = fetchAndParseDocument(OAI_PMH_BASE + recordId);
            return checkDdiSamplingProcedure(doc, recordId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logSevere(DOC_PROC_ERROR + e.getMessage());
        } catch (Exception e) {
            logSevere(ERROR + e.getMessage());
        }
        return RESULT_INDETERMINATE;
    }

    // ============================================================================
    // SHARED UTILITY METHODS
    // ============================================================================

    /**
     * Extract the record identifier from the CESSDA detail URL.
     * 
     * @param url The CESSDA detail URL
     * @return The record identifier
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
     * Extract the language code from the URL query parameter.
     * 
     * @param url The CESSDA detail URL
     */
    private void extractLanguageCodeFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            String query = uri.getQuery();
            if (query == null)
                return;
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && kv[0].equalsIgnoreCase("lang") && kv[1].matches("^[a-zA-Z]{2}$")) {
                    languageCode = kv[1].toLowerCase();
                    return;
                }
            }
        } catch (Exception e) {
            logSevere("URL Exception: " + e.getMessage());
        }
    }

    /**
     * Fetch the OAI-PMH GetRecord XML and parse to extract the DDI codeBook
     * element.
     * 
     * @param url The OAI-PMH GetRecord URL
     * @return The DDI codeBook Document
     * @throws IOException          - if an I/O error occurs
     * @throws InterruptedException - if the operation is interrupted
     */
    public Document fetchAndParseDocument(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header(HEAD_ACCEPT, "application/xml, text/xml, */*")
                .header("User-Agent", "Java-HttpClient")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200)
            throw new IOException("Failed to fetch document: HTTP " + response.statusCode());
        if (response.body() == null || response.body().length == 0)
            throw new IOException("Empty response body");

        try {
            logInfo("Parsing XML response from OAI-PMH endpoint at: " + url);
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            Document oaiDoc = builder.parse(new ByteArrayInputStream(response.body()));

            XPath xpath = createXPath();
            Node codeBookNode = (Node) xpath.evaluate("//ddi:codeBook", oaiDoc, XPathConstants.NODE);
            if (codeBookNode == null)
                throw new IllegalArgumentException("No DDI codeBook found");

            Document ddiDoc = builder.newDocument();
            ddiDoc.appendChild(ddiDoc.importNode(codeBookNode, true));
            return ddiDoc;

        } catch (Exception e) {
            logSevere("Failed to parse XML. Preview: "
                    + new String(response.body(), 0, Math.min(500, response.body().length), StandardCharsets.UTF_8));
            throw new IOException("Failed to parse XML response", e);
        }
    }

    /**
     * Create an XPath instance with DDI namespace context.
     * 
     * @return Configured XPath instance
     */
    private XPath createXPath() {
        XPath xpath = xPathFactory.newXPath();
        xpath.setNamespaceContext(new NamespaceContext() {
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
        return xpath;
    }

    // ============================================================================
    // ACCESS RIGHTS VALIDATION
    // ============================================================================

    /**
     * Check the DDI document for approved Access Rights terms.
     * 
     * @param ddiDoc   The DDI document
     * @param recordId The record identifier (for logging)
     * @return "pass", "fail", or "indeterminate"
     */
    private String checkAccessRights(Document ddiDoc, String recordId) {
        Set<String> approvedValues = getApprovedAccessRights();

        try {
            XPath xpath = createXPath();
            NodeList nodes = (NodeList) xpath.evaluate(ACCESS_RIGHTS_PATH, ddiDoc, XPathConstants.NODESET);
            logInfo("NodeList length: " + (nodes != null ? nodes.getLength() : "null"));

            if (nodes == null || nodes.getLength() == 0) {
                logInfo("No Access Rights element found in DDI document for record: " + recordId);
                return RESULT_FAIL;
            }

            for (int i = 0; i < nodes.getLength(); i++) {
                String val = nodes.item(i).getTextContent().trim();
                if (approvedValues.contains(val)) {
                    logInfo("Match found: " + val);
                    return RESULT_PASS;
                }
            }

            logInfo("No approved Access Rights found in record: " + recordId);
            return RESULT_FAIL;
        } catch (Exception e) {
            logSevere("Error checking document for approved Access Rights: " + e.getMessage());
            return RESULT_INDETERMINATE;
        }
    }

    /**
     * Fetch the approved Access Rights terms from the CESSDA vocabulary service.
     * 
     * @return Set of approved Access Rights terms
     */
    private Set<String> getApprovedAccessRights() {
        if (cachedAccessRightsTerms != null && !cachedAccessRightsTerms.isEmpty()) {
            return cachedAccessRightsTerms;
        }

        synchronized (FairTests.class) {
            if (cachedAccessRightsTerms != null && !cachedAccessRightsTerms.isEmpty()) {
                return cachedAccessRightsTerms;
            }

            logInfo("Fetching approved Access Rights schemas from CESSDA vocabulary...");
            try {
                Set<String> schemas = fetchVocabularyTerms(ACCESS_VOCAB_URL, "AccessRights");
                if (schemas.isEmpty()) {
                    logInfo("Using default Access Rights terms due to empty vocabulary");
                    return defaultAccessRightsTerms();
                }

                cachedAccessRightsTerms = Collections.unmodifiableSet(schemas);
                logInfo(FETCHED + schemas.size() + " approved Access Rights schemas: " + cachedAccessRightsTerms);
                return cachedAccessRightsTerms;

            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                logSevere("Failed to fetch AccessRights vocabulary: " + e.getMessage());
                return defaultAccessRightsTerms();
            }
        }
    }

    /**
     * Default Access Rights terms if vocabulary fetch fails.
     * 
     * @return Set of default Access Rights terms
     */
    private static Set<String> defaultAccessRightsTerms() {
        return Set.of("Open", "Restricted");
    }

    // ============================================================================
    // PID SCHEMA VALIDATION
    // ============================================================================

    /**
     * Check the DDI document for approved PID schemas.
     * 
     * @param ddiDoc   The DDI document
     * @param recordId The record identifier (for logging)
     * @return "pass", "fail", or "indeterminate"
     */
    private String checkPidSchemas(Document ddiDoc, String recordId) {
        try {
            XPath xpath = createXPath();
            NodeList idNoNodes = (NodeList) xpath.evaluate(PID_PATH, ddiDoc, XPathConstants.NODESET);

            if (idNoNodes == null || idNoNodes.getLength() == 0) {
                logInfo("No IDNo elements found in DDI document for record: " + recordId);
                return RESULT_FAIL;
            }

            Set<String> approvedSchemas = getApprovedPidSchemas();
            for (int i = 0; i < idNoNodes.getLength(); i++) {
                Node idNoNode = idNoNodes.item(i);
                Node agencyAttr = idNoNode.getAttributes().getNamedItem("agency");
                if (agencyAttr != null) {
                    String agency = agencyAttr.getNodeValue();
                    if (approvedSchemas.contains(agency)) {
                        logInfo("Found approved PID schema '" + agency + "' in record: " + recordId);
                        return RESULT_PASS;
                    }
                }
            }
            logInfo("No approved PID schemas found in record: " + recordId);
            return RESULT_FAIL;
        } catch (Exception e) {
            logSevere("Error checking document for approved PID: " + e.getMessage());
            return RESULT_INDETERMINATE;
        }
    }

    /**
     * Fetch the approved PID schemas from the CESSDA vocabulary service.
     * 
     * @return Set of approved PID schema names
     */
    private Set<String> getApprovedPidSchemas() {
        if (cachedPidSchemas != null && !cachedPidSchemas.isEmpty()) {
            return cachedPidSchemas;
        }

        synchronized (FairTests.class) {
            if (cachedPidSchemas != null && !cachedPidSchemas.isEmpty()) {
                return cachedPidSchemas;
            }

            logInfo("Fetching approved PID schemas from CESSDA vocabulary...");
            try {
                Set<String> schemas = fetchVocabularyTerms(PID_VOCAB_URL, "PID");
                if (schemas.isEmpty()) {
                    return defaultPidSchemas();
                }

                cachedPidSchemas = Collections.unmodifiableSet(schemas);
                logInfo(FETCHED + schemas.size() + " approved PID schemas: " + cachedPidSchemas);
                return cachedPidSchemas;

            } catch (IOException | InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                logSevere("Failed to fetch PID vocabulary: " + e.getMessage());
                return defaultPidSchemas();
            }
        }
    }

    /**
     * Default PID schemas if vocabulary fetch fails.
     * 
     * @return Set of default PID schema names
     */
    private static Set<String> defaultPidSchemas() {
        return Set.of("DOI", "Handle", "URN", "ARK");
    }

    // ============================================================================
    // ELSST KEYWORD VALIDATION
    // ============================================================================

    /**
     * Validate ELSST keywords in the DDI document.
     * 
     * @param doc The DDI document
     * @param url The original CESSDA catalogue detail page URL
     * @return "pass", "fail", or "indeterminate"
     */
    private String validateElsstKeywords(Document doc, String url) {
        try {
            XPath xpath = createXPath();
            NodeList nodes = (NodeList) xpath.evaluate(KEYWORD_PATH, doc, XPathConstants.NODESET);

            if (nodes.getLength() == 0) {
                logInfo("No keywords found");
                return RESULT_FAIL;
            }

            List<KeywordCandidate> candidates = extractKeywordCandidates(nodes);

            if (candidates.isEmpty()) {
                logInfo("No keywords found with both vocab='ELSST' and vocabURI containing 'elsst.cessda.eu'");
                return RESULT_FAIL;
            }

            logInfo("Checking " + candidates.size() + " candidate keyword(s) via ELSST API");
            return validateCandidatesAgainstElsstApi(candidates, url);

        } catch (XPathExpressionException e) {
            logSevere("XPath evaluation error: " + e.getMessage());
            return RESULT_INDETERMINATE;
        } catch (Exception e) {
            logSevere("Error validating keywords: " + e.getMessage());
            return RESULT_INDETERMINATE;
        }
    }

    /**
     * Extract candidate keywords from the NodeList.
     * 
     * @param nodes The NodeList of keyword elements
     * @return List of KeywordCandidate objects
     */
    private List<KeywordCandidate> extractKeywordCandidates(NodeList nodes) {
        List<KeywordCandidate> candidates = new ArrayList<>();

        for (int i = 0; i < nodes.getLength(); i++) {
            Element keywordElement = (Element) nodes.item(i);
            String text = keywordElement.getTextContent().trim();
            String vocab = keywordElement.getAttribute("vocab");
            String vocabURI = keywordElement.getAttribute("vocabURI");

            boolean hasVocab = ELSST_VOCAB_NAME.equalsIgnoreCase(vocab);
            boolean hasVocabURI = vocabURI != null && vocabURI.toLowerCase().contains(ELSST_URI_SUBSTRING);

            if (hasVocab && hasVocabURI && !text.isEmpty()) {
                candidates.add(new KeywordCandidate(text, hasVocab, hasVocabURI));
                logInfo("Candidate keyword found: " + text);
            }
        }

        return candidates;
    }

    /**
     * Keyword candidate data class.
     */
    private static class KeywordCandidate {
        final String text;
        final boolean hasVocab;
        final boolean hasVocabURI;

        KeywordCandidate(String text, boolean hasVocab, boolean hasVocabURI) {
            this.text = text;
            this.hasVocab = hasVocab;
            this.hasVocabURI = hasVocabURI;
        }
    }

    /**
     * Validate candidate keywords against the ELSST API.
     * 
     * @param candidates List of keyword candidates to check
     * @param urlToCheck The original CESSDA catalogue detail page URL
     * @return "pass", "fail", or "indeterminate"
     */
    private String validateCandidatesAgainstElsstApi(List<KeywordCandidate> candidates, String urlToCheck) {
        try {
            if (languageCode == null)
                extractLanguageCodeFromUrl(urlToCheck);
            if (languageCode == null) {
                logInfo("No language code available for ELSST API validation");
                return RESULT_INDETERMINATE;
            }

            List<String> candidateTexts = candidates.stream().map(c -> c.text).toList();
            Set<String> elsstKeywords = fetchElsstKeywords(candidateTexts, languageCode);

            Set<String> elsstSet = elsstKeywords.stream()
                    .filter(k -> k.startsWith(languageCode + ":"))
                    .map(k -> k.substring(languageCode.length() + 2).replace("\"", "").trim().toUpperCase())
                    .collect(Collectors.toSet());

            for (KeywordCandidate candidate : candidates) {
                if (elsstSet.contains(candidate.text.toUpperCase())) {
                    logInfo(RESULT_PASS + ": Keyword '" + candidate.text
                            + "' meets ALL three conditions (vocab, vocabURI, and API match)");
                    return RESULT_PASS;
                }
            }

            logInfo(RESULT_FAIL + ": No keywords meet all three conditions");
            return RESULT_FAIL;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logSevere("Failed to fetch ELSST keywords: " + e.getMessage());
            return RESULT_INDETERMINATE;
        }
    }

    /**
     * Fetch ELSST keywords from the ELSST API for the given keywords and language
     * code.
     * 
     * @param keywords List of keyword texts to check
     * @param langCode The language code (e.g., "en", "de")
     * @return Set of ELSST keywords in the format langCode:"keyword"
     * @throws InterruptedException - if the operation is interrupted
     */
    private Set<String> fetchElsstKeywords(List<String> keywords, String langCode) throws InterruptedException {
        if (cachedElsstKeywords != null)
            return cachedElsstKeywords;
        String encodedLangCode = URLEncoder.encode(langCode, StandardCharsets.UTF_8);

        List<String> queryUrls = keywords.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> ELSST_API_BASE
                        + "?filter=cf.search.labels:" + URLEncoder.encode(k, StandardCharsets.UTF_8)
                        + ",cf.search.language:" + encodedLangCode)
                .toList();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Set<String>>> tasks = queryUrls.stream()
                    .map(url -> (Callable<Set<String>>) () -> {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header(HEAD_ACCEPT, "application/json")
                                .timeout(Duration.ofSeconds(30))
                                .GET().build();
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() != 200) {
                            logSevere("ELSST API returned " + response.statusCode() + " for: " + url);
                            return Set.of();
                        }
                        return parseElsstKeywords(response.body());
                    }).toList();

            cachedElsstKeywords = executor.invokeAll(tasks).stream()
                    .flatMap(f -> {
                        try {
                            return f.get().stream();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logSevere("Interrupted while fetching ELSST keywords");
                        } catch (ExecutionException e) {
                            logSevere("Execution exception: " + e.getMessage());
                        }
                        return Stream.empty();
                    }).collect(Collectors.toSet());
        }

        logInfo("Number of cachedElsstKeywords: " + cachedElsstKeywords.size());
        return cachedElsstKeywords;
    }

    private Set<String> parseElsstKeywords(String json) throws IOException {
        Set<String> keywords = new HashSet<>();
        JsonNode results = mapper.readTree(json).path("results");
        if (results.isArray()) {
            for (JsonNode r : results) {
                JsonNode labels = r.path("labels");
                if (labels.isObject()) {
                    labels.fields()
                            .forEachRemaining(e -> keywords.add(e.getKey() + ":\"" + e.getValue().asText() + "\""));
                }
            }
        }
        return keywords;
    }

    // ============================================================================
    // VOCABULARIES VALIDATION
    // ============================================================================

    /**
     * Check the DDI document for recommended DDI controlled vocabularies.
     * 
     * @param ddiDoc   The DDI document
     * @param recordId The record identifier (for logging)
     * @return "pass", "fail", or "indeterminate"
     */
    private String checkRecommendedDdiVocabularies(Document ddiDoc, String recordId) {
        try {
            XPath xpath = createXPath();
            boolean foundAny = false;

            // Check Analysis Unit
            if (checkAnalysisUnit(xpath, ddiDoc, recordId)) {
                foundAny = true;
            }

            // Check Time Method
            if (checkTimeMethod(xpath, ddiDoc, recordId)) {
                foundAny = true;
            }

            // Check Mode of Collection
            if (checkCollectionMode(xpath, ddiDoc, recordId)) {
                foundAny = true;
            }

            if (foundAny) {
                logInfo("Record contains at least one recommended DDI controlled vocabulary");
                return RESULT_PASS;
            } else {
                logInfo("No recommended DDI vocabularies found in record: " + recordId);
                return RESULT_FAIL;
            }
        } catch (Exception e) {
            logSevere("Error checking recommended DDI vocabularies: " + e.getMessage());
            return RESULT_INDETERMINATE;
        }
    }

   
    /**
     * Check for CESSDA Topic Classification in the DDI document.
     * 
     * @param xpath    The XPath instance
     * @param ddiDoc   The DDI document
     * @param recordId The record identifier (for logging)
     * @return "pass", "fail", or "indeterminate" 
     */
    private String checkCessdaTopicClassification(XPath xpath, Document ddiDoc, String recordId) {
        try {
            NodeList nodes = (NodeList) xpath.evaluate(TOPIC_CLASS_PATH, ddiDoc,
                    XPathConstants.NODESET);
            if (nodes == null || nodes.getLength() == 0) {
                logInfo("No Topic Classification elements found");
                return RESULT_FAIL;
            }

            Set<String> approvedTerms = getApprovedTopicClassTerms();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element element = (Element) nodes.item(i);
                String vocabAttr = element.getAttribute("vocab");
                String text = element.getTextContent().trim();

                if (TOPIC_CLASS_VOCAB_NAME.equals(vocabAttr) && !text.isEmpty() &&
                        approvedTerms.contains(text)) {
                    logInfo("Found CESSDA Topic Classification : " + text);
                    return RESULT_PASS;
                }

            }
            logInfo("No approved Topic Classification found in record: " +
                    recordId);
            return RESULT_FAIL;
        } catch (Exception e) {
            logSevere("Error checking Topic Classification " + e.getMessage());
            return RESULT_INDETERMINATE;
        }
    }
    

    /**
     *         Check for DDI Analysis Unit in the DDI document.
     * 
     * @param xpath    The XPath instance
     * @param ddiDoc   The DDI document
     * @param recordId The record identifier (for logging)
     * @return true if approved Analysis Unit found, false otherwise
     */
    private boolean checkAnalysisUnit(XPath xpath, Document ddiDoc, String recordId) {
        try {
            NodeList nodes = (NodeList) xpath.evaluate(ANALYSIS_UNIT_PATH, ddiDoc, XPathConstants.NODESET);
            if (nodes == null || nodes.getLength() == 0) {
                logInfo("No Analysis Unit terms found");
                return false;
            }

            Set<String> approvedTerms = getApprovedAnalysisUnitTerms();
            for (int i = 0; i < nodes.getLength(); i++) {
                String text = nodes.item(i).getTextContent().trim();
                if (!text.isEmpty() && approvedTerms.contains(text)) {
                    logInfo("Found DDI Analysis Unit: " + text);
                    return true;
                }
            }
            logInfo("No approved Analysis Unit term found in record: " + recordId);
            return false;
        } catch (Exception e) {
            logSevere("Error checking Analysis Unit: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check for DDI Time Method in the DDI document.
     * 
     * @param xpath    The XPath instance
     * @param ddiDoc   The DDI document
     * @param recordId The record identifier (for logging)
     * @return true if approved Time Method found, false otherwise
     */
    private boolean checkTimeMethod(XPath xpath, Document ddiDoc, String recordId) {
        try {
            NodeList nodes = (NodeList) xpath.evaluate(TIME_METHOD_PATH, ddiDoc, XPathConstants.NODESET);
            if (nodes == null || nodes.getLength() == 0) {
                logInfo("No Time Method elements found");
                return false;
            }

            Set<String> approvedTerms = getApprovedTimeMethodTerms();
            for (int i = 0; i < nodes.getLength(); i++) {
                String text = nodes.item(i).getTextContent().trim();
                if (!text.isEmpty() && approvedTerms.contains(text)) {
                    logInfo("Found DDI Time Method: " + text);
                    return true;
                }
            }
            logInfo("No approved Time Method found in record: " + recordId);
            return false;
        } catch (Exception e) {
            logSevere("Error checking Time Method in record: " + recordId + e.getMessage());
            return false;
        }
    }

    /**
     * Check for DDI Sampling Procedure in the DDI document.
     * 
     * @param ddiDoc   The DDI document
     * @param recordId The record identifier (for logging)
     * @return "pass", "fail", or "indeterminate"
     */ 
      private String checkDdiSamplingProcedure(Document ddiDoc, String recordId) {
        try {
            XPath xpath = createXPath();

            NodeList nodes = (NodeList) xpath.evaluate(SAMPLING_PROC_PATH,
                    ddiDoc, XPathConstants.NODESET);
            if (nodes == null || nodes.getLength() == 0) {
                logInfo("No Sampling Procedure terms found");
                return RESULT_FAIL;
            }

            Set<String> approvedTerms = getApprovedSamplingProcTerms();
            for (int i = 0; i < nodes.getLength(); i++) {
                String text = nodes.item(i).getTextContent().trim();
                if (!text.isEmpty() && approvedTerms.contains(text)) {
                    logInfo("Found DDI Sampling Procedure term: " + text);
                    return RESULT_PASS;
                }
            }
            logInfo("No Sampling Procedure terms found in record: " +
                    recordId);
            return RESULT_FAIL;
        } catch (Exception e) {
            logSevere("Error checking for Sampling Procedure terms: " + e.getMessage());
            return RESULT_INDETERMINATE;
        }
    }
    

    /**
     * Check for DDI Mode of Collection in the DDI document.
     * 
     * @param xpath    The XPath instance
     * @param ddiDoc   The DDI document
     * @param recordId The record identifier (for logging)
     * @return true if approved Mode of Collection found, false otherwise
     */
    private boolean checkCollectionMode(XPath xpath, Document ddiDoc, String recordId) {
        try {
            NodeList nodes = (NodeList) xpath.evaluate(COLLECTION_MODE_PATH, ddiDoc, XPathConstants.NODESET);
            if (nodes == null || nodes.getLength() == 0) {
                logInfo("No Mode of Collection elements found");
                return false;
            }

            Set<String> approvedTerms = getApprovedCollectionModeTerms();
            for (int i = 0; i < nodes.getLength(); i++) {
                String text = nodes.item(i).getTextContent().trim();
                if (!text.isEmpty() && approvedTerms.contains(text)) {
                    logInfo("Found DDI Mode of Collection: " + text);
                    return true;
                }
            }
            logInfo("No approved Mode of Collection found in record: " + recordId);
            return false;
        } catch (Exception e) {
            logSevere("Error checking Mode of Collection: " + e.getMessage());
            return false;
        }
    }

    /**
     * Fetch and cache approved Topic Classification terms from CESSDA vocabulary.
     * 
     * @return Set of approved Topic Classification terms
     */
    private Set<String> getApprovedTopicClassTerms() {
        if (cachedTopicClassTerms != null && !cachedTopicClassTerms.isEmpty()) {
            return cachedTopicClassTerms;
        }

        synchronized (FairTests.class) {
            if (cachedTopicClassTerms != null && !cachedTopicClassTerms.isEmpty()) {
                return cachedTopicClassTerms;
            }

            logInfo("Fetching approved Topic Classification terms from CESSDA vocabulary...");
            try {
                Set<String> terms = fetchVocabularyTerms(TOPIC_CLASS_VOCAB_URL, "TopicClassification");
                if (terms.isEmpty()) {
                    logInfo("Using default Topic Classification terms");
                    return Set.of();
                }

                cachedTopicClassTerms = Collections.unmodifiableSet(terms);
                logInfo(FETCHED + terms.size() + " approved Topic Classification terms");
                return cachedTopicClassTerms;
            } catch (InterruptedException | IOException e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                logSevere("Failed to fetch Topic Classification vocabulary: " + e.getMessage());
                return Set.of();
            }
        }
    }

    /**
     * Fetch and cache approved Analysis Unit terms from CESSDA vocabulary.
     * 
     * @return Set of approved Analysis Unit terms
     */
    private Set<String> getApprovedAnalysisUnitTerms() {
        if (cachedAnalysisUnitTerms != null && !cachedAnalysisUnitTerms.isEmpty()) {
            return cachedAnalysisUnitTerms;
        }

        synchronized (FairTests.class) {
            if (cachedAnalysisUnitTerms != null && !cachedAnalysisUnitTerms.isEmpty()) {
                return cachedAnalysisUnitTerms;
            }

            logInfo("Fetching approved Analysis Unit terms from CESSDA vocabulary...");
            try {
                Set<String> terms = fetchVocabularyTerms(ANALYSIS_UNIT_VOCAB_URL, "AnalysisUnit");
                if (terms.isEmpty()) {
                    return Set.of();
                }

                cachedAnalysisUnitTerms = Collections.unmodifiableSet(terms);
                logInfo(FETCHED + terms.size() + " approved Analysis Unit terms");
                return cachedAnalysisUnitTerms;
            } catch (IOException e) {
                logSevere("Failed to fetch Analysis Unit vocabulary: " + e.getMessage());
                return Set.of();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logSevere("Failed to fetch Analysis Unit vocabulary: " + e.getMessage());
                return Set.of();
            }
        }
    }

    /**
     * Fetch and cache approved Time Method terms from CESSDA vocabulary.
     * 
     * @return Set of approved Time Method terms
     */
    private Set<String> getApprovedTimeMethodTerms() {
        if (cachedTimeMethodTerms != null && !cachedTimeMethodTerms.isEmpty()) {
            return cachedTimeMethodTerms;
        }

        synchronized (FairTests.class) {
            if (cachedTimeMethodTerms != null && !cachedTimeMethodTerms.isEmpty()) {
                return cachedTimeMethodTerms;
            }

            logInfo("Fetching approved Time Method terms from CESSDA vocabulary...");
            try {
                Set<String> terms = fetchVocabularyTerms(TIME_METHOD_VOCAB_URL, "TimeMethod");
                if (terms.isEmpty()) {
                    return Set.of();
                }

                cachedTimeMethodTerms = Collections.unmodifiableSet(terms);
                logInfo(FETCHED + terms.size() + " approved Time Method terms");
                return cachedTimeMethodTerms;
            } catch (Exception e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                logSevere("Failed to fetch Time Method vocabulary: " + e.getMessage());
                return Set.of();
            }
        }
    }

    /**
     * Fetch and cache approved Sampling Procedure terms from DDI vocabulary.
     * 
     * @return Set of approved Sampling Procedure terms
     */ 
    private Set<String> getApprovedSamplingProcTerms() {
        if (cachedSamplingProcTerms != null &&
            !cachedSamplingProcTerms.isEmpty()) {
        return cachedSamplingProcTerms;
        }

        synchronized (FairTests.class) {
        if (cachedSamplingProcTerms != null &&
            !cachedSamplingProcTerms.isEmpty()) {
        return cachedSamplingProcTerms;
        }

        logInfo("Fetching Sampling Procedure terms from CESSDA vocabulary...");
        try {
            Set<String> terms = fetchVocabularyTerms(SAMPLING_PROC_VOCAB_URL,
            "SamplingProcedure");
            if (terms.isEmpty()) {
                return Set.of();
            }

            cachedSamplingProcTerms = Collections.unmodifiableSet(terms);
            logInfo(FETCHED + terms.size() + " approved Sampling Procedure terms");
            return cachedSamplingProcTerms;
            } catch (Exception e) {
            Thread.currentThread().interrupt(); // Restore interrupt status
            logSevere("Failed to fetch Sampling Procedure vocabulary: " + e.getMessage());
            return Set.of();
            }
        }
    }

    /**
     * Fetch and cache approved Mode of Collection terms from CESSDA vocabulary.
     * 
     * @return Set of approved Mode of Collection terms
     */
    private Set<String> getApprovedCollectionModeTerms() {
        if (cachedCollectionModeTerms != null && !cachedCollectionModeTerms.isEmpty()) {
            return cachedCollectionModeTerms;
        }

        synchronized (FairTests.class) {
            if (cachedCollectionModeTerms != null && !cachedCollectionModeTerms.isEmpty()) {
                return cachedCollectionModeTerms;
            }

            logInfo("Fetching approved Mode of Collection terms from CESSDA vocabulary...");
            try {
                Set<String> terms = fetchVocabularyTerms(COLLECTION_MODE_VOCAB_URL, "ModeOfCollection");
                if (terms.isEmpty()) {
                    return Set.of();
                }

                cachedCollectionModeTerms = Collections.unmodifiableSet(terms);
                logInfo(FETCHED + terms.size() + " approved Mode of Collection terms");
                return cachedCollectionModeTerms;
            } catch (Exception e) {
                Thread.currentThread().interrupt(); // Restore interrupt status
                logSevere("Failed to fetch Mode of Collection vocabulary: " + e.getMessage());
                return Set.of();
            }
        }
    }

    // ============================================================================
    // SHARED VOCABULARY FETCHING
    // ============================================================================

    /**
     * Fetch vocabulary terms from the given CESSDA vocabulary URL.
     * 
     * @param vocabUrl  The vocabulary API URL
     * @param vocabType The type of vocabulary (for logging)
     * @return Set of vocabulary terms
     * @throws IOException
     * @throws InterruptedException
     */
    private Set<String> fetchVocabularyTerms(String vocabUrl, String vocabType)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vocabUrl))
                .header(HEAD_ACCEPT, "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() != 200) {
            logSevere(vocabType + " vocabulary API returned " + response.statusCode());
            return Set.of();
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
                        logInfo("Found " + vocabType + " entry: " + value);
                        terms.add(value);
                    }
                }
            }
        }

        if (terms.isEmpty()) {
            logSevere("No valid " + vocabType + " terms found in vocabulary response");
        }

        return terms;
    }

    // ============================================================================
    // LOGGING UTILITIES
    // ============================================================================

    /**
     * Log info messages, escaping % characters.
     * 
     * @param msg The message to log
     */
    static void logInfo(String msg) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info(msg.replace("%", "%%"));
        }
    }

    /**
     * Log severe messages, escaping % characters.
     * 
     * @param msg The message to log
     */
    static void logSevere(String msg) {
        if (logger.isLoggable(Level.SEVERE)) {
            logger.severe(msg.replace("%", "%%"));
        }
    }

    // ============================================================================
    // MAIN METHOD
    // ============================================================================

    /**
     * Main method for command-line execution.
     * 
     * @param args
     *             args[0]: test type ("access-rights", "pid", "elsst-keywords",
     *             "ddi-vocabs, ddi-sampleproc, topoc-class")
     *             args[1]: CESSDA detail URL
     * 
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            logSevere("Usage: java FairTests <test-type> <url>");
            logSevere("Test types: access-rights, pid, elsst-keywords, ddi-vocabs, ddi-sampleproc, topic-class");
            System.exit(1);
        }

        String testType = args[0].toLowerCase();
        String url = args[1];
        FairTests tests = new FairTests();
        String result;

        switch (testType) {
            case "access-rights" -> result = tests.containsApprovedAccessRights(url);
            case "pid" -> result = tests.containsApprovedPid(url);
            case "elsst-keywords" -> result = tests.containsElsstKeywords(url);
            case "ddi-vocabs" -> result = tests.containsRecommendedDdiVocabularies(url);
            case "ddi-sampleproc" -> result = tests.containsDdiSamplingProcedureTerms(url);
            case "topic-class" -> result = tests.containsCessdaTopicClassificationTerms(url);
            default -> {
                logSevere("Unknown test type: " + testType);
                System.exit(1);
                return;
            }
        }

        logInfo("Result: " + result);
        System.exit(result.equals(RESULT_PASS) ? 0 : 1);
    }
}
