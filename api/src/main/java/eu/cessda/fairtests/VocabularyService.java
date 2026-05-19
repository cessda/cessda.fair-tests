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
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Shared service for fetching and caching CESSDA controlled vocabularies and
 * ELSST keyword lookups.
 * <p>
 * This class is intentionally format-agnostic: it knows nothing about XML,
 * JSON, or any other metadata schema. Format-specific parsers (e.g.
 * {@code XmlParser}, {@code CdcJsonParser}) call into this service to validate
 * terms they have already extracted from the metadata stream.
 * </p>
 * <p>
 * All vocabulary sets are lazily fetched on first use and then cached in
 * thread-safe collections for the lifetime of the instance.
 * </p>
 */
public class VocabularyService {

    // -------------------------------------------------------------------------
    // Vocabulary endpoint URLs
    // -------------------------------------------------------------------------

    private static final String ACCESS_VOCAB_URL =
            "https://vocabularies.cessda.eu/v2/vocabularies/CessdaAccessRights/1.0.0?languageVersion=en-1.0.0&format=json";
    private static final String PID_VOCAB_URL =
            "https://vocabularies.cessda.eu/v2/vocabularies/CessdaPersistentIdentifierTypes/1.0.0?languageVersion=en-1.0.0&format=json";
    private static final String TOPIC_CLASS_VOCAB_URL =
            "https://vocabularies.cessda.eu/v2/vocabularies/TopicClassification/4.2.3?languageVersion=en-4.2.3&format=json";
    private static final String ANALYSIS_UNIT_VOCAB_URL =
            "https://vocabularies.cessda.eu/v2/vocabularies/AnalysisUnit/2.1.3?languageVersion=en-2.1.3&format=json";
    private static final String TIME_METHOD_VOCAB_URL =
            "https://vocabularies.cessda.eu/v2/vocabularies/TimeMethod/1.2.3?languageVersion=en-1.2.3&format=json";
    private static final String SAMPLING_PROC_VOCAB_URL =
            "https://vocabularies.cessda.eu/v2/vocabularies/SamplingProcedure/2.0.1?languageVersion=en-2.0.1&format=json";
    private static final String COLLECTION_MODE_VOCAB_URL =
            "https://vocabularies.cessda.eu/v2/vocabularies/ModeOfCollection/5.0.0?languageVersion=en-5.0.0&format=json";

    // ELSST API
    private static final String ELSST_API_BASE = "https://skg-if-staging.cessda.eu/api/topics";

    private static final String HTTP_HEADER_ACCEPT = "Accept";

    // -------------------------------------------------------------------------
    // Caches
    // -------------------------------------------------------------------------

    final ConcurrentSkipListSet<String> cachedAccessRightsTerms  = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedAnalysisUnitTerms  = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedCollectionModeTerms = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedPidSchemas          = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedSamplingProcTerms  = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedTimeMethodTerms     = new ConcurrentSkipListSet<>();
    final ConcurrentSkipListSet<String> cachedTopicClassTerms     = new ConcurrentSkipListSet<>();

    /** Per-language + per-keyword-list ELSST cache. */
    final ConcurrentMap<String, Set<String>> cachedElsstKeywordsByLang = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Infrastructure
    // -------------------------------------------------------------------------

    private final HttpClient   httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper     = new ObjectMapper();

    // =========================================================================
    // Public vocabulary accessors (lazy-fetch + cache)
    // =========================================================================

    /**
     * Returns the set of approved Access Rights terms, fetching from the CESSDA
     * vocabulary service on the first call.
     */
    public Set<String> getApprovedAccessRightsTerms() {
        if (!cachedAccessRightsTerms.isEmpty()) return cachedAccessRightsTerms;
        FairTests.logInfo("Fetching approved Access Rights terms from CESSDA vocabulary");
        try {
            Set<String> terms = fetchVocabularyTerms(ACCESS_VOCAB_URL, "AccessRights");
            if (terms.isEmpty()) {
                FairTests.logInfo("Using default Access Rights terms due to empty vocabulary");
                return defaultAccessRightsTerms();
            }
            cachedAccessRightsTerms.addAll(terms);
            FairTests.logInfo("Fetched approved Access Rights terms: %s", cachedAccessRightsTerms);
            return cachedAccessRightsTerms;
        } catch (IOException e) {
            FairTests.logSevere("Failed to fetch AccessRights vocabulary: %s", e.getMessage());
            return defaultAccessRightsTerms();
        }
    }

    /**
     * Returns the set of approved PID schema names, fetching from the CESSDA
     * vocabulary service on the first call.
     */
    public Set<String> getApprovedPidSchemas() {
        if (!cachedPidSchemas.isEmpty()) return cachedPidSchemas;
        FairTests.logInfo("Fetching approved PID schemas from CESSDA vocabulary");
        try {
            Set<String> schemas = fetchVocabularyTerms(PID_VOCAB_URL, "PID");
            if (schemas.isEmpty()) return defaultPidSchemas();
            cachedPidSchemas.addAll(schemas);
            FairTests.logInfo("Fetched %s approved PID schemas: %s", schemas.size(), cachedPidSchemas);
            return cachedPidSchemas;
        } catch (IOException e) {
            FairTests.logSevere("Failed to fetch PID vocabulary: %s", e.getMessage());
            return defaultPidSchemas();
        }
    }

    /**
     * Returns the set of approved CESSDA Topic Classification terms, fetching from
     * the CESSDA vocabulary service on the first call.
     */
    public Set<String> getApprovedTopicClassTerms() {
        if (!cachedTopicClassTerms.isEmpty()) return cachedTopicClassTerms;
        FairTests.logInfo("Fetching approved Topic Classification terms from CESSDA vocabulary");
        try {
            Set<String> terms = fetchVocabularyTerms(TOPIC_CLASS_VOCAB_URL, "TopicClassification");
            if (terms.isEmpty()) return Collections.emptySet();
            cachedTopicClassTerms.addAll(terms);
            FairTests.logInfo("Fetched %d approved Topic Classification terms", terms.size());
            return cachedTopicClassTerms;
        } catch (IOException e) {
            FairTests.logSevere("Failed to fetch Topic Classification vocabulary: %s", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Returns the set of approved Analysis Unit terms, fetching from the CESSDA
     * vocabulary service on the first call.
     */
    public Set<String> getApprovedAnalysisUnitTerms() {
        if (!cachedAnalysisUnitTerms.isEmpty()) return cachedAnalysisUnitTerms;
        FairTests.logInfo("Fetching approved Analysis Unit terms from CESSDA vocabulary");
        try {
            Set<String> terms = fetchVocabularyTerms(ANALYSIS_UNIT_VOCAB_URL, "AnalysisUnit");
            if (terms.isEmpty()) return Collections.emptySet();
            cachedAnalysisUnitTerms.addAll(terms);
            FairTests.logInfo("Fetched %s approved Analysis Unit terms", terms.size());
            return cachedAnalysisUnitTerms;
        } catch (IOException e) {
            FairTests.logSevere("Failed to fetch Analysis Unit vocabulary: %s", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Returns the set of approved Time Method terms, fetching from the CESSDA
     * vocabulary service on the first call.
     */
    public Set<String> getApprovedTimeMethodTerms() {
        if (!cachedTimeMethodTerms.isEmpty()) return cachedTimeMethodTerms;
        FairTests.logInfo("Fetching approved Time Method terms from CESSDA vocabulary");
        try {
            Set<String> terms = fetchVocabularyTerms(TIME_METHOD_VOCAB_URL, "TimeMethod");
            if (terms.isEmpty()) return Collections.emptySet();
            cachedTimeMethodTerms.addAll(terms);
            FairTests.logInfo("Fetched %s approved Time Method terms", terms.size());
            return cachedTimeMethodTerms;
        } catch (IOException e) {
            FairTests.logSevere("Failed to fetch Time Method vocabulary", e);
            return Collections.emptySet();
        }
    }

    /**
     * Returns the set of approved Sampling Procedure terms, fetching from the
     * CESSDA vocabulary service on the first call.
     */
    public Set<String> getApprovedSamplingProcTerms() {
        if (!cachedSamplingProcTerms.isEmpty()) return cachedSamplingProcTerms;
        FairTests.logInfo("Fetching Sampling Procedure terms from CESSDA vocabulary");
        try {
            Set<String> terms = fetchVocabularyTerms(SAMPLING_PROC_VOCAB_URL, "SamplingProcedure");
            if (terms.isEmpty()) return Collections.emptySet();
            cachedSamplingProcTerms.addAll(terms);
            FairTests.logInfo("Fetched %s approved Sampling Procedure terms", terms.size());
            return cachedSamplingProcTerms;
        } catch (IOException e) {
            Thread.currentThread().interrupt();
            FairTests.logSevere("Failed to fetch Sampling Procedure vocabulary: %s", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * Returns the set of approved Mode of Collection terms, fetching from the
     * CESSDA vocabulary service on the first call.
     */
    public Set<String> getApprovedCollectionModeTerms() {
        if (!cachedCollectionModeTerms.isEmpty()) return cachedCollectionModeTerms;
        FairTests.logInfo("Fetching approved Mode of Collection terms from CESSDA vocabulary");
        try {
            Set<String> terms = fetchVocabularyTerms(COLLECTION_MODE_VOCAB_URL, "ModeOfCollection");
            if (terms.isEmpty()) return Collections.emptySet();
            cachedCollectionModeTerms.addAll(terms);
            FairTests.logInfo("Fetched %d approved Mode of Collection terms", terms.size());
            return cachedCollectionModeTerms;
        } catch (IOException e) {
            FairTests.logSevere("Failed to fetch Mode of Collection vocabulary: %s", e.getMessage());
            return Collections.emptySet();
        }
    }

    // =========================================================================
    // ELSST keyword validation
    // =========================================================================

    /**
     * Validates a list of candidate keyword texts against the ELSST API for the
     * given language code.
     * <p>
     * Matching is case-insensitive. Results are cached per (langCode, keywords)
     * combination to avoid redundant API calls within the lifetime of this service
     * instance.
     * </p>
     *
     * @param candidateTexts keyword texts to look up (already extracted by the
     *                       format-specific parser)
     * @param langCode       BCP-47 language tag used in the ELSST API query
     *                       (e.g. {@code "en"}, {@code "fr"})
     * @return {@link Result#PASS} if at least one candidate is found in ELSST,
     *         {@link Result#FAIL} if none match, or
     *         {@link Result#INDETERMINATE} if the API call fails
     */
    public Result validateElsstKeywords(List<String> candidateTexts, String langCode) {
        try {
            List<String> uppercased = candidateTexts.stream()
                    .map(t -> t.trim().toUpperCase())
                    .toList();

            Set<String> elsstKeywords = fetchElsstKeywords(uppercased, langCode);
            FairTests.logInfo("ELSST keywords fetched from API: %s", elsstKeywords);

            Set<String> normalisedElsst = elsstKeywords.stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());

            FairTests.logInfo("ELSST keywords normalised for comparison: %s", normalisedElsst);

            for (String candidate : candidateTexts) {
                String normalised = candidate.trim().toUpperCase();
                FairTests.logInfo("Checking candidate keyword against ELSST API results: %s", normalised);
                if (normalisedElsst.contains(normalised)) {
                    FairTests.logInfo("Keyword %s matches ELSST API result", candidate);
                    return Result.PASS;
                }
            }

            FairTests.logInfo("No keywords match ELSST API results");
            return Result.FAIL;

        } catch (IOException e) {
            FairTests.logSevere("Failed to fetch ELSST keywords: %s", e.getMessage());
            return Result.INDETERMINATE;
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Fetches ELSST topic labels from the ELSST API for each keyword text.
     * Results are cached by a composite key of {@code langCode|keyword1,keyword2,...}.
     */
    private Set<String> fetchElsstKeywords(List<String> keywords, String langCode) throws IOException {
        String cacheKey = langCode + "|" + String.join(",", keywords);
        Set<String> cached = cachedElsstKeywordsByLang.get(cacheKey);
        if (cached != null) return cached;

        Set<String> result = ConcurrentHashMap.newKeySet();
        String encodedLang = URLEncoder.encode(langCode, StandardCharsets.UTF_8);

        for (String keyword : keywords) {
            String url = ELSST_API_BASE
                    + "?filter=cf.search.labels:" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                    + ",cf.search.language:" + encodedLang;

            FairTests.logInfo("Fetching ELSST keywords from API URL: %s", url);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header(HTTP_HEADER_ACCEPT, "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = sendRequest(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new IOException("ELSST API returned " + response.statusCode() + " for: " + url);
            }

            try (InputStream body = response.body()) {
                JsonNode root  = mapper.readTree(body);
                JsonNode graph = root.path("@graph");

                if (!graph.isArray()) {
                    throw new IOException("Invalid API response: '@graph' is missing or not an array");
                }

                for (JsonNode topicNode : graph) {
                    JsonNode labels    = topicNode.path("labels");
                    if (!labels.isObject()) continue;
                    JsonNode langLabel = labels.get(langCode);
                    if (langLabel != null && !langLabel.isNull()) {
                        String value = langLabel.asText();
                        if (!value.isBlank()) result.add(value);
                    }
                }
            }
        }

        cachedElsstKeywordsByLang.put(cacheKey, result);
        FairTests.logInfo("Cached ELSST keywords for key %s: %s entries", cacheKey, result.size());
        return result;
    }

    /**
     * Fetches concept titles from a CESSDA CVS vocabulary endpoint.
     * The endpoint must return the standard CVS JSON envelope containing
     * {@code versions[0].concepts[].title}.
     *
     * @param vocabUrl  full URL of the vocabulary endpoint
     * @param vocabType human-readable label used in log messages
     * @return set of concept titles; empty if the endpoint returns nothing useful
     * @throws IOException if the HTTP request fails
     */
    private Set<String> fetchVocabularyTerms(String vocabUrl, String vocabType) throws IOException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(vocabUrl))
                .header(HTTP_HEADER_ACCEPT, "application/json")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<InputStream> response = sendRequest(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            FairTests.logWarning("Vocabulary API returned %s for %s", response.statusCode(), vocabType);
            return Collections.emptySet();
        }

        JsonNode root     = mapper.readTree(response.body());
        Set<String> terms = new HashSet<>();

        JsonNode versions = root.path("versions");
        if (versions.isArray() && !versions.isEmpty()) {
            JsonNode concepts = versions.get(0).path("concepts");
            if (concepts.isArray()) {
                for (JsonNode concept : concepts) {
                    String value = concept.path("title").asText(null);
                    if (value != null && !value.isBlank()) {
                        terms.add(value.trim());
                    }
                }
            }
        }

        if (terms.isEmpty()) {
            FairTests.logSevere("No %s terms found in vocabulary response", vocabType);
        }

        return terms;
    }

    /** Sends an HTTP request, restoring interrupt status on {@link InterruptedException}. */
    private <T> HttpResponse<T> sendRequest(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException {
        try {
            return httpClient.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    // =========================================================================
    // Hardcoded defaults (used when vocabulary endpoints are unavailable)
    // =========================================================================

    private static Set<String> defaultAccessRightsTerms() {
        return Set.of("open", "restricted");
    }

    private static Set<String> defaultPidSchemas() {
        return Set.of("DOI", "Handle", "URN", "ARK");
    }
}