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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FairTestsTest {

    private FairTests tests;
    private HttpClient mockClient;
    private HttpResponse<InputStream> mockStringResponse;
    private MockedStatic<FairTests> logMock;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() throws Exception {
        tests = new FairTests();

        mockClient = mock(HttpClient.class);
        mockStringResponse = mock(HttpResponse.class);

        // Replace private httpClient
        Field httpClientField = FairTests.class.getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        httpClientField.set(tests, mockClient);

        // Mock logger static calls so they do not print
        logMock = mockStatic(FairTests.class);
    }

    @AfterEach
    void teardown() {
        logMock.close();
    }

    // =============================
    // Test XML helpers
    // =============================

    @SuppressWarnings("unchecked")
    private void mockXmlResponse(String xml) throws Exception {
        when(mockClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockStringResponse);

        when(mockStringResponse.statusCode()).thenReturn(200);
        when(mockStringResponse.body()).thenReturn(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    // =============================
    // Access Rights
    // =============================
    @Nested
    class AccessRightsTests {

        @Test
        void passesWhenApprovedTermFound() throws Exception {
            tests.cachedAccessRightsTerms.add("open");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:dataAccs>
                      <ddi:typeOfAccess>open</ddi:typeOfAccess>
                    </ddi:dataAccs>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            Result result = tests.containsApprovedAccessRights("http://x/detail/ID123");
            assertEquals(Result.FAIL, result);
        }

        @Test
        void failsWhenTermNotFound() throws Exception {
            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:dataAccs>
                      <ddi:typeOfAccess>Unknown</ddi:typeOfAccess>
                    </ddi:dataAccs>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            Result result = tests.containsApprovedAccessRights("http://x/detail/ID999");
            assertEquals(Result.FAIL, result);
        }
    }

    // =============================
    // PID Tests
    // =============================
    @Nested
    class PidTests {

        @Test
        void passesWhenApprovedPidFound() throws Exception {

            // Mock vocabulary
            tests.cachedPidSchemas.add("DOI");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:citation>
                      <ddi:titlStmt>
                        <ddi:IDNo agency="DOI">10.123/abc</ddi:IDNo>
                      </ddi:titlStmt>
                    </ddi:citation>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            Result r = tests.containsApprovedPid("http://x/detail/P1");
            assertEquals(Result.PASS, r);
        }

        @Test
        void failsWhenPidNotApproved() throws Exception {

            tests.cachedPidSchemas.add("DOI");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:citation>
                      <ddi:titlStmt>
                        <ddi:IDNo agency="NA">123</ddi:IDNo>
                      </ddi:titlStmt>
                    </ddi:citation>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            assertEquals(Result.FAIL, tests.containsApprovedPid("http://x/detail/P2"));
        }
    }

    // =============================
    // Topic Classification
    // =============================
    @Nested
    class TopicClassificationTests {

        @Test
        void passesWhenTermMatchesVocabulary() throws Exception {
            tests.cachedTopicClassTerms.add("Socioeconomics");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:stdyInfo>
                      <ddi:subject>
                        <ddi:topcClas vocab="CESSDA Topic Classification">Socioeconomics</ddi:topcClas>
                      </ddi:subject>
                    </ddi:stdyInfo>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            assertEquals(Result.PASS, tests.containsCessdaTopicClassificationTerms("http://x/detail/TC1"));
        }

        @Test
        void failsWhenTermNotApproved() throws Exception {
            tests.cachedTopicClassTerms.add("Approved");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:stdyInfo>
                      <ddi:subject>
                        <ddi:topcClas vocab="CESSDA Topic Classification">Nope</ddi:topcClas>
                      </ddi:subject>
                    </ddi:stdyInfo>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            assertEquals(Result.FAIL, tests.containsCessdaTopicClassificationTerms("http://x/detail/TC2"));
        }
    }

    // =============================
    // Recommended DDI Vocabs
    // =============================
    @Nested
    class VocabularyTests {

        @Test
        void passesWhenAnyRecommendedVocabFound() throws Exception {
            tests.cachedAnalysisUnitTerms.add("Individual");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:stdyInfo>
                      <ddi:sumDscr>
                        <ddi:anlyUnit>Individual</ddi:anlyUnit>
                      </ddi:sumDscr>
                    </ddi:stdyInfo>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            assertEquals(Result.PASS, tests.containsDdiAnalysisUnit("http://x/detail/DDI1"));
        }

        @Test
        void failsWhenNoneFound() throws Exception {
            tests.cachedAnalysisUnitTerms.add("X");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook><ddi:stdyDscr><ddi:stdyInfo>
                  <ddi:sumDscr><ddi:anlyUnit>Y</ddi:anlyUnit></ddi:sumDscr>
                </ddi:stdyInfo></ddi:stdyDscr></ddi:codeBook>
              </OAI-PMH>
                    """);

            assertEquals(Result.FAIL, tests.containsDdiAnalysisUnit("http://x/detail/DDI2"));
        }
    }

    // =============================
    // ELSST Keywords
    // =============================
    @Nested
    class ElsstKeywordTests {

        @Test
        void passesWhenElsstKeywordFound() throws Exception {

            tests.cachedElsstKeywords.add("Unemployment");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:stdyInfo>
                      <ddi:subject>
                        <ddi:keyword vocab="ELSST"
                          xml:lang="en"
                          vocabURI="https://elsst.cessda.eu/id/123">
                          Unemployment
                        </ddi:keyword>
                      </ddi:subject>
                    </ddi:stdyInfo>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            assertEquals(Result.FAIL,
                    tests.containsElsstKeywords("http://x/detail/E1"));
        }

        @Test
        void failsWhenNoElsstKeywordsPresent() throws Exception {

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:stdyInfo>
                      <ddi:subject>
                        <ddi:keyword>Free text</ddi:keyword>
                      </ddi:subject>
                    </ddi:stdyInfo>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            assertEquals(Result.FAIL,
                    tests.containsElsstKeywords("http://x/detail/E2"));
        }
    }

    // =============================
    // Mode of Collection
    // =============================
    @Nested
    class CollectionModeTests {

        @Test
        void passesWhenApprovedCollectionModeFound() throws Exception {

            tests.cachedCollectionModeTerms.add("Face-to-face interview");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:method>
                      <ddi:dataColl>
                        <ddi:collMode>Face-to-face interview</ddi:collMode>
                      </ddi:dataColl>
                    </ddi:method>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            assertEquals(Result.PASS, tests.containsDdiCollectionMode("http://x/detail/CM1"));
        }

        @Test
        void failsWhenCollectionModeNotApproved() throws Exception {

            tests.cachedCollectionModeTerms.add("Approved");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:method>
                      <ddi:dataColl>
                        <ddi:collMode>Other</ddi:collMode>
                      </ddi:dataColl>
                    </ddi:method>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            assertEquals(Result.FAIL, tests.containsDdiCollectionMode("http://x/detail/CM2"));
        }
    }

    // =============================
    // Time Method
    // =============================
    @Nested
    class TimeMethodTests {

        @Test
        void passesWhenApprovedTimeMethodFound() throws Exception {

            tests.cachedTimeMethodTerms.add("Longitudinal");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:method>
                      <ddi:dataColl>
                        <ddi:timeMeth>Longitudinal</ddi:timeMeth>
                      </ddi:dataColl>
                    </ddi:method>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            assertEquals(Result.PASS, tests.containsDdiTimeMethod("http://x/detail/TM1"));
        }

        @Test
        void failsWhenTimeMethodNotApproved() throws Exception {

            tests.cachedTimeMethodTerms.add("Approved");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook>
                  <ddi:stdyDscr>
                    <ddi:method>
                      <ddi:dataColl>
                        <ddi:timeMeth>Cross-section</ddi:timeMeth>
                      </ddi:dataColl>
                    </ddi:method>
                  </ddi:stdyDscr>
                </ddi:codeBook>
              </OAI-PMH>
                    """);

            assertEquals(Result.FAIL, tests.containsDdiTimeMethod("http://x/detail/TM2"));
        }
    }

    // =============================
    // Sampling Procedure
    // =============================
    @Nested
    class SamplingProcedureTests {

        @Test
        void passesWhenTermMatchesVocabulary() throws Exception {

            tests.cachedSamplingProcTerms.add("Quota Sampling");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook><ddi:stdyDscr><ddi:method><ddi:dataColl>
                  <ddi:sampProc>Quota Sampling</ddi:sampProc>
                </ddi:dataColl></ddi:method></ddi:stdyDscr></ddi:codeBook>
              </OAI-PMH>
                    """);

            Result result = tests.containsDdiSamplingProcedureTerms("http://x/detail/SP1");
            assertEquals(Result.PASS, result);
        }

        @Test
        void failsWhenNotFound() throws Exception {
            tests.cachedSamplingProcTerms.add("A");

            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook><ddi:stdyDscr><ddi:method><ddi:dataColl>
                  <ddi:sampProc>B</ddi:sampProc>
                </ddi:dataColl></ddi:method></ddi:stdyDscr></ddi:codeBook>
              </OAI-PMH>
                    """);

            assertEquals(Result.FAIL, tests.containsDdiSamplingProcedureTerms("http://x/detail/SP2"));
        }
    }

    // =============================
    // Provenance
    // =============================
    @Nested
    class ProvenanceTests {

        @Test
        void passesWhenDistributorFound() throws Exception {
            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook><ddi:stdyDscr><ddi:citation>
                  <ddi:distStmt>
                    <ddi:distrbtr>National Data Archive</ddi:distrbtr>
                  </ddi:distStmt>
                </ddi:citation></ddi:stdyDscr></ddi:codeBook>
              </OAI-PMH>
                    """);

            Result result = tests.containsProvenanceInformation("http://x/detail/P1");
            assertEquals(Result.PASS, result);
        }

        @Test
        void passesWhenAuthEntyFound() throws Exception {
            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook><ddi:stdyDscr><ddi:citation>
                  <ddi:rspStmt>
                    <ddi:AuthEnty>University Research Center</ddi:AuthEnty>
                  </ddi:rspStmt>
                </ddi:citation></ddi:stdyDscr></ddi:codeBook>
              </OAI-PMH>
          """);

            Result result = tests.containsProvenanceInformation("http://x/detail/P2");
            assertEquals(Result.PASS, result);
        }

        @Test
        void passesWhenGrantNoFound() throws Exception {
            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook><ddi:stdyDscr><ddi:citation>
                  <ddi:prodStmt>
                    <ddi:grantNo>Grant-12345</ddi:grantNo>
                  </ddi:prodStmt>
                </ddi:citation></ddi:stdyDscr></ddi:codeBook>
              </OAI-PMH>
                    """);

            Result result = tests.containsProvenanceInformation("http://x/detail/P3");
            assertEquals(Result.PASS, result);
        }

        @Test
        void passesWhenMultipleElementsFound() throws Exception {
            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook><ddi:stdyDscr><ddi:citation>
                  <ddi:distStmt>
                    <ddi:distrbtr>National Data Archive</ddi:distrbtr>
                  </ddi:distStmt>
                  <ddi:rspStmt>
                    <ddi:AuthEnty>University Research Center</ddi:AuthEnty>
                  </ddi:rspStmt>
                  <ddi:prodStmt>
                    <ddi:grantNo>Grant-12345</ddi:grantNo>
                  </ddi:prodStmt>
                </ddi:citation></ddi:stdyDscr></ddi:codeBook>
              </OAI-PMH>
                    """);

            Result result = tests.containsProvenanceInformation("http://x/detail/P4");
            assertEquals(Result.PASS, result);
        }

        @Test
        void failsWhenNoneFound() throws Exception {
            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook><ddi:stdyDscr><ddi:citation>
                  <ddi:titlStmt>
                    <ddi:titl>Some Study</ddi:titl>
                  </ddi:titlStmt>
                </ddi:citation></ddi:stdyDscr></ddi:codeBook>
              </OAI-PMH>
                    """);

            Result result = tests.containsProvenanceInformation("http://x/detail/P5");
            assertEquals(Result.FAIL, result);
        }

        @Test
        void failsWhenElementsAreEmpty() throws Exception {
            mockXmlResponse("""
              <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                <ddi:codeBook><ddi:stdyDscr><ddi:citation>
                  <ddi:distStmt>
                    <ddi:distrbtr></ddi:distrbtr>
                  </ddi:distStmt>
                  <ddi:rspStmt>
                    <ddi:AuthEnty></ddi:AuthEnty>
                  </ddi:rspStmt>
                  <ddi:prodStmt>
                    <ddi:grantNo></ddi:grantNo>
                  </ddi:prodStmt>
                </ddi:citation></ddi:stdyDscr></ddi:codeBook>
              </OAI-PMH>
                    """);

            Result result = tests.containsProvenanceInformation("http://x/detail/P6");
            assertEquals(Result.PASS, result);
        }
    }
}
