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

    // XPath expressions
    private static final String ACCESS_RIGHTS_PATH = "//ddi:codeBook/ddi:stdyDscr/ddi:dataAccs/ddi:typeOfAccess";
    private static final String PID_PATH = "//ddi:codeBook/ddi:stdyDscr/ddi:citation/ddi:titlStmt/ddi:IDNo";
    private static final String KEYWORD_PATH = "//ddi:codeBook/ddi:stdyDscr/ddi:stdyInfo/ddi:subject/ddi:keyword";

    // Vocabulary URLs
    private static final String ACCESS_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/CessdaAccessRights/1.0.0?languageVersion=en-1.0.0&format=json";
    private static final String PID_VOCAB_URL = "https://vocabularies.cessda.eu/v2/vocabularies/CessdaPersistentIdentifierTypes/1.0.0?languageVersion=en-1.0.0&format=json";
    private static final String ELSST_API_BASE = "https://skg-if-openapi.cessda.eu/api/topics";

    // ELSST constants
    private static final String ELSST_VOCAB_NAME = "ELSST";
    private static final String ELSST_URI_SUBSTRING = "elsst.cessda.eu";

    // Shared components
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final DocumentBuilderFactory documentBuilderFactory;
    private final XPathFactory xPathFactory;
    private static final Logger logger = Logger.getLogger(FairTests.class.getName());

    // Cached vocabularies
    private static volatile Set<String> cachedAccessRightsTerms;
    private static volatile Set<String> cachedPidSchemas;
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
            logSevere("Error processing document: " + e.getMessage());
        } catch (Exception e) {
            logSevere("Error: " + e.getMessage());
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
            logSevere("Error processing document: " + e.getMessage());
        } catch (Exception e) {
            logSevere("Error: " + e.getMessage());
        }
        return RESULT_INDETERMINATE;
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
            logSevere("Error processing document: " + e.getMessage());
        } catch (Exception e) {
            logSevere("Error: " + e.getMessage());
        }
        return RESULT_INDETERMINATE;
    }

    // ============================================================================
    // SHARED UTILITY METHODS
    // ============================================================================

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
     * Extract the language code from the URL query parameter.
     */
    private void extractLanguageCodeFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            String query = uri.getQuery();
            if (query == null) return;
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
     * Fetch the OAI-PMH GetRecord XML and parse to extract the DDI codeBook element.
     * @param url The OAI-PMH GetRecord URL
     * @return The DDI codeBook Document
     * @throws IOException - if an I/O error occurs
     * @throws InterruptedException - if the operation is interrupted
    */
    public Document fetchAndParseDocument(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/xml, text/xml, */*")
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
                logInfo("Fetched " + schemas.size() + " approved Access Rights schemas: " + cachedAccessRightsTerms);
                return cachedAccessRightsTerms;

            } catch (Exception e) {
                logSevere("Failed to fetch AccessRights vocabulary: " + e.getMessage());
                return defaultAccessRightsTerms();
            }
        }
    }

    private static Set<String> defaultAccessRightsTerms() {
        return Set.of("Open", "Restricted");
    }

    // ============================================================================
    // PID SCHEMA VALIDATION
    // ============================================================================

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
                logInfo("Fetched " + schemas.size() + " approved PID schemas: " + cachedPidSchemas);
                return cachedPidSchemas;

            } catch (Exception e) {
                logSevere("Failed to fetch PID vocabulary: " + e.getMessage());
                return defaultPidSchemas();
            }
        }
    }

    private static Set<String> defaultPidSchemas() {
        return Set.of("DOI", "Handle", "URN", "ARK");
    }

    // ============================================================================
    // ELSST KEYWORD VALIDATION
    // ============================================================================

    private String validateElsstKeywords(Document doc, String url) {
        try {
            XPath xpath = createXPath();
            NodeList nodes = (NodeList) xpath.evaluate(KEYWORD_PATH, doc, XPathConstants.NODESET);

            if (nodes.getLength() == 0) {
                logInfo("No keywords found");
                return RESULT_FAIL;
            }

            List<KeywordCandidate> candidates = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (!(node instanceof Element e)) continue;

                String vocabAttr = e.getAttribute("vocab");
                String vocabURI = e.getAttribute("vocabURI");
                String text = e.getTextContent();

                if (text == null || text.trim().isEmpty()) continue;

                boolean hasVocab = ELSST_VOCAB_NAME.equals(vocabAttr);
                boolean hasVocabURI = vocabURI != null && vocabURI.contains(ELSST_URI_SUBSTRING);

                logInfo("Keyword '" + text.trim() + "': vocab=" + hasVocab + ", vocabURI=" + hasVocabURI);

                if (hasVocab && hasVocabURI) {
                    candidates.add(new KeywordCandidate(text.trim(), hasVocab, hasVocabURI));
                    logInfo("Candidate keyword found: '" + text.trim() + "' (has vocab and vocabURI)");
                }
            }

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

    private String validateCandidatesAgainstElsstApi(List<KeywordCandidate> candidates, String urlToCheck) {
        try {
            if (languageCode == null) extractLanguageCodeFromUrl(urlToCheck);
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
                    logInfo(RESULT_PASS + ": Keyword '" + candidate.text + "' meets ALL three conditions (vocab, vocabURI, and API match)");
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

    private Set<String> fetchElsstKeywords(List<String> keywords, String langCode) throws InterruptedException {
        if (cachedElsstKeywords != null) return cachedElsstKeywords;
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
                                .header("Accept", "application/json")
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
                    labels.fields().forEachRemaining(e -> keywords.add(e.getKey() + ":\"" + e.getValue().asText() + "\""));
                }
            }
        }
        return keywords;
    }

    // ============================================================================
    // SHARED VOCABULARY FETCHING
    // ============================================================================

    private Set<String> fetchVocabularyTerms(String vocabUrl, String vocabType) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vocabUrl))
                .header("Accept", "application/json")
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

    static void logInfo(String msg) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info(msg.replace("%", "%%"));
        }
    }

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
     * @param args
     *           args[0]: test type ("access-rights", "pid", "elsst-keywords")
     *           args[1]: CESSDA detail URL
     * 
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            logSevere("Usage: java FairTests <test-type> <url>");
            logSevere("Test types: access-rights, pid, elsst-keywords");
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