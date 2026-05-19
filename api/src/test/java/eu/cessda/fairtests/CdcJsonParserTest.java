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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CdcJsonParserTest {

    private CdcJsonParser parser;
    private VocabularyService vocabulary;

    @BeforeEach
    void setUp() {
        parser = new CdcJsonParser();
        vocabulary = mock(VocabularyService.class);

        when(vocabulary.getApprovedAccessRightsTerms())
                .thenReturn(Set.of("Open", "Restricted"));

        when(vocabulary.getApprovedPidSchemas())
                .thenReturn(Set.of("doi", "handle"));

        when(vocabulary.getApprovedTopicClassTerms())
                .thenReturn(Set.of("Health"));

        when(vocabulary.getApprovedAnalysisUnitTerms())
                .thenReturn(Set.of("Individuals"));

        when(vocabulary.getApprovedCollectionModeTerms())
                .thenReturn(Set.of("Face-to-face interview"));

        when(vocabulary.getApprovedTimeMethodTerms())
                .thenReturn(Set.of("Longitudinal"));

        when(vocabulary.getApprovedSamplingProcTerms())
                .thenReturn(Set.of("Probability sampling"));
    }

    @Test
    @DisplayName("Should return INDETERMINATE when dataset ID is missing")
    void shouldReturnIndeterminateWhenIdMissing() throws Exception {

        String json = """
                {
                  "dataAccess": "Open"
                }
                """;

        Result result = parser.runTest(
                TestType.ACCESS_RIGHTS,
                input(json),
                vocabulary);

        assertEquals(Result.INDETERMINATE, result);
    }

    @Test
    @DisplayName("Should pass ACCESS_RIGHTS when approved term is contained")
    void shouldPassAccessRightsContainsMatch() throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "dataAccess": "Restricted - see documentation"
                }
                """;

        Result result = parser.runTest(
                TestType.ACCESS_RIGHTS,
                input(json),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should fail ACCESS_RIGHTS when no approved term exists")
    void shouldFailAccessRightsWhenNoMatch() throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "dataAccess": "Completely closed"
                }
                """;

        Result result = parser.runTest(
                TestType.ACCESS_RIGHTS,
                input(json),
                vocabulary);

        assertEquals(Result.FAIL, result);
    }

    @Test
    @DisplayName("Should pass PID when approved schema is present")
    void shouldPassPidSchemaValidation() throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "pidStudies": [
                    {
                      "value": "doi"
                    }
                  ]
                }
                """;

        Result result = parser.runTest(
                TestType.PID,
                input(json),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should fail PID when schema is not approved")
    void shouldFailPidSchemaValidation() throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "pidStudies": [
                    {
                      "value": "ark"
                    }
                  ]
                }
                """;

        Result result = parser.runTest(
                TestType.PID,
                input(json),
                vocabulary);

        assertEquals(Result.FAIL, result);
    }

    @Test
    @DisplayName("Should pass TOPIC_CLASS with matching term")
    void shouldPassTopicClassification() throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "classifications": [
                    {
                      "term": "Health"
                    }
                  ]
                }
                """;

        Result result = parser.runTest(
                TestType.TOPIC_CLASS,
                input(json),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should pass DDI_ANALYSIS_UNIT with matching term")
    void shouldPassAnalysisUnitValidation() throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "unitTypes": [
                    {
                      "label": "Individuals"
                    }
                  ]
                }
                """;

        Result result = parser.runTest(
                TestType.DDI_ANALYSIS_UNIT,
                input(json),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should pass DDI_COLLECTION_MODE with matching term")
    void shouldPassCollectionModeValidation() throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "typeOfModeOfCollections": [
                    {
                      "term": "Face-to-face interview"
                    }
                  ]
                }
                """;

        Result result = parser.runTest(
                TestType.DDI_COLLECTION_MODE,
                input(json),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should pass DDI_TIME_METHOD with matching term")
    void shouldPassTimeMethodValidation() throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "typeOfTimeMethods": [
                    {
                      "value": "Longitudinal"
                    }
                  ]
                }
                """;

        Result result = parser.runTest(
                TestType.DDI_TIME_METHOD,
                input(json),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should pass DDI_SAMPLEPROC using contains match")
    void shouldPassSamplingProcedureContainsMatch() throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "samplingProcedureFreeTexts": [
                    "Probability sampling with regional stratification"
                  ]
                }
                """;

        Result result = parser.runTest(
                TestType.DDI_SAMPLEPROC,
                input(json),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should pass PROVENANCE when creator name exists")
    void shouldPassProvenanceValidation() throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "creators": [
                    {
                      "name": "Jane Doe"
                    }
                  ]
                }
                """;

        Result result = parser.runTest(
                TestType.PROVENANCE,
                input(json),
                vocabulary);

        assertEquals(Result.FAIL, result);
    }

    @Test
    @DisplayName("Should fail PROVENANCE when no provenance data exists")
    void shouldFailProvenanceValidation() throws Exception {

        String json = """
                {
                  "id": "dataset-1"
                }
                """;

        Result result = parser.runTest(
                TestType.PROVENANCE,
                input(json),
                vocabulary);

        assertEquals(Result.FAIL, result);
    }

    @Test
    @DisplayName("Should pass ELSST_KEYWORDS when vocabulary validates")
    void shouldPassElsstKeywordValidation() throws Exception {

        when(vocabulary.validateElsstKeywords(
                 List.of("employment"),
        "en"))
                        .thenReturn(Result.PASS);

        String json = """
                {
                  "id": "dataset-1",
                  "langAvailableIn": ["en"],
                  "keywords": [
                    {
                      "term": "employment",
                      "vocab": "ELSST",
                      "vocabUri": "https://elsst.cessda.eu/"
                    }
                  ]
                }
                """;

        Result result = parser.runTest(
                TestType.ELSST_KEYWORDS,
                input(json),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should fail ELSST_KEYWORDS when no language is present")
    void shouldFailElsstKeywordValidationWithoutLanguage()
            throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "keywords": [
                    {
                      "term": "employment",
                      "vocab": "ELSST",
                      "vocabUri": "https://elsst.cessda.eu/"
                    }
                  ]
                }
                """;

        Result result = parser.runTest(
                TestType.ELSST_KEYWORDS,
                input(json),
                vocabulary);

        assertEquals(Result.FAIL, result);
    }

    @Test
    @DisplayName("Should fail ELSST_KEYWORDS when keywords are not an array")
    void shouldFailElsstKeywordValidationWhenKeywordsInvalid()
            throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "langAvailableIn": ["en"],
                  "keywords": "invalid"
                }
                """;

        Result result = parser.runTest(
                TestType.ELSST_KEYWORDS,
                input(json),
                vocabulary);

        assertEquals(Result.FAIL, result);
    }

    private InputStream input(String json) {
        return new ByteArrayInputStream(
                json.getBytes(StandardCharsets.UTF_8));
    }
}
