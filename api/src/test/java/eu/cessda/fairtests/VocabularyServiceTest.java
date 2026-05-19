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
 *
 */

package eu.cessda.fairtests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VocabularyServiceTest {

    private VocabularyService vocabularyService;

    private HttpClient httpClient;

    private HttpResponse<InputStream> response;

    @BeforeEach
    void setUp() throws Exception {

        vocabularyService = new VocabularyService();

        httpClient = mock(HttpClient.class);

        response = mock(HttpResponse.class);

        injectHttpClient(httpClient);
    }

    @Test
    @DisplayName("Should fetch and cache access rights vocabulary")
    void shouldFetchAndCacheAccessRightsVocabulary()
            throws Exception {

        String json = """
                {
                  "versions": [
                    {
                      "concepts": [
                        { "title": "Open" },
                        { "title": "Restricted" }
                      ]
                    }
                  ]
                }
                """;

        mockVocabularyResponse(json);

        Set<String> result =
                vocabularyService.getApprovedAccessRightsTerms();

        assertEquals(2, result.size());
        assertTrue(result.contains("Open"));
        assertTrue(result.contains("Restricted"));

        assertFalse(
                vocabularyService.cachedAccessRightsTerms.isEmpty());
    }

    @Test
    @DisplayName("Should return default access rights when vocabulary empty")
    void shouldReturnDefaultAccessRightsWhenVocabularyEmpty()
            throws Exception {

        String json = """
                {
                  "versions": [
                    {
                      "concepts": []
                    }
                  ]
                }
                """;

        mockVocabularyResponse(json);

        Set<String> result =
                vocabularyService.getApprovedAccessRightsTerms();

        assertEquals(Set.of("open", "restricted"), result);
    }

    @Test
    @DisplayName("Should return default PID schemas on IOException")
    void shouldReturnDefaultPidSchemasOnIOException()
            throws Exception {

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                        .thenThrow(new IOException("Connection error"));

        Set<String> result =
                vocabularyService.getApprovedPidSchemas();

        assertEquals(
                Set.of("DOI", "Handle", "URN", "ARK"),
                result);
    }

    @Test
    @DisplayName("Should fetch topic classification terms")
    void shouldFetchTopicClassificationTerms()
            throws Exception {

        String json = """
                {
                  "versions": [
                    {
                      "concepts": [
                        { "title": "Health" },
                        { "title": "Education" }
                      ]
                    }
                  ]
                }
                """;

        mockVocabularyResponse(json);

        Set<String> result =
                vocabularyService.getApprovedTopicClassTerms();

        assertEquals(2, result.size());
        assertTrue(result.contains("Health"));
        assertTrue(result.contains("Education"));
    }

    @Test
    @DisplayName("Should fetch analysis unit terms")
    void shouldFetchAnalysisUnitTerms()
            throws Exception {

        String json = """
                {
                  "versions": [
                    {
                      "concepts": [
                        { "title": "Individuals" }
                      ]
                    }
                  ]
                }
                """;

        mockVocabularyResponse(json);

        Set<String> result =
                vocabularyService.getApprovedAnalysisUnitTerms();

        assertEquals(Set.of("Individuals"), result);
    }

    @Test
    @DisplayName("Should fetch collection mode terms")
    void shouldFetchCollectionModeTerms()
            throws Exception {

        String json = """
                {
                  "versions": [
                    {
                      "concepts": [
                        { "title": "Face-to-face interview" }
                      ]
                    }
                  ]
                }
                """;

        mockVocabularyResponse(json);

        Set<String> result =
                vocabularyService.getApprovedCollectionModeTerms();

        assertEquals(
                Set.of("Face-to-face interview"),
                result);
    }

    @Test
    @DisplayName("Should fetch time method terms")
    void shouldFetchTimeMethodTerms()
            throws Exception {

        String json = """
                {
                  "versions": [
                    {
                      "concepts": [
                        { "title": "Longitudinal" }
                      ]
                    }
                  ]
                }
                """;

        mockVocabularyResponse(json);

        Set<String> result =
                vocabularyService.getApprovedTimeMethodTerms();

        assertEquals(Set.of("Longitudinal"), result);
    }

    @Test
    @DisplayName("Should fetch sampling procedure terms")
    void shouldFetchSamplingProcedureTerms()
            throws Exception {

        String json = """
                {
                  "versions": [
                    {
                      "concepts": [
                        { "title": "Probability sampling" }
                      ]
                    }
                  ]
                }
                """;

        mockVocabularyResponse(json);

        Set<String> result =
                vocabularyService.getApprovedSamplingProcTerms();

        assertEquals(
                Set.of("Probability sampling"),
                result);
    }

    @Test
    @DisplayName("Should validate ELSST keywords successfully")
    void shouldValidateElsstKeywordsSuccessfully()
            throws Exception {

        String json = """
                {
                  "@graph": [
                    {
                      "labels": {
                        "en": "Employment"
                      }
                    }
                  ]
                }
                """;

        mockVocabularyResponse(json);

        Result result = vocabularyService.validateElsstKeywords(
                List.of("employment"),
                "en");

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should fail ELSST validation when keyword not found")
    void shouldFailElsstValidationWhenKeywordNotFound()
            throws Exception {

        String json = """
                {
                  "@graph": [
                    {
                      "labels": {
                        "en": "Health"
                      }
                    }
                  ]
                }
                """;

        mockVocabularyResponse(json);

        Result result = vocabularyService.validateElsstKeywords(
                List.of("employment"),
                "en");

        assertEquals(Result.FAIL, result);
    }

    @Test
    @DisplayName("Should return INDETERMINATE when ELSST API fails")
    void shouldReturnIndeterminateWhenElsstApiFails()
            throws Exception {

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                        .thenThrow(new IOException("API unavailable"));

        Result result = vocabularyService.validateElsstKeywords(
                List.of("employment"),
                "en");

        assertEquals(Result.INDETERMINATE, result);
    }

    @Test
    @DisplayName("Should cache ELSST keyword results")
    void shouldCacheElsstKeywordResults()
            throws Exception {

        String json = """
                {
                  "@graph": [
                    {
                      "labels": {
                        "en": "Employment"
                      }
                    }
                  ]
                }
                """;

        mockVocabularyResponse(json);

        vocabularyService.validateElsstKeywords(
                List.of("employment"),
                "en");

        assertFalse(
                vocabularyService.cachedElsstKeywordsByLang.isEmpty());
    }

    @Test
    @DisplayName("Should return empty set when vocabulary API returns non-200")
    void shouldReturnEmptySetWhenVocabularyApiFails()
            throws Exception {

        when(response.statusCode()).thenReturn(500);

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                        .thenReturn(response);

        Set<String> result =
                vocabularyService.getApprovedTopicClassTerms();

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should ignore blank concept titles")
    void shouldIgnoreBlankConceptTitles()
            throws Exception {

        String json = """
                {
                  "versions": [
                    {
                      "concepts": [
                        { "title": "" },
                        { "title": "Health" },
                        { "title": "   " }
                      ]
                    }
                  ]
                }
                """;

        mockVocabularyResponse(json);

        Set<String> result =
                vocabularyService.getApprovedTopicClassTerms();

        assertEquals(Set.of("Health"), result);
    }

    private void mockVocabularyResponse(String body)
            throws Exception {

        when(response.statusCode()).thenReturn(200);

        when(response.body()).thenReturn(input(body));

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                        .thenReturn(response);
    }

    private InputStream input(String content) {

        return new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8));
    }

    private void injectHttpClient(HttpClient mockClient)
            throws Exception {

        Field field = VocabularyService.class.getDeclaredField(
                "httpClient");

        field.setAccessible(true);

        field.set(vocabularyService, mockClient);
    }
}