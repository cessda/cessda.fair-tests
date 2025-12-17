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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FairTestsTest {

    private FairTests tests;
    private HttpClient mockClient;
    private HttpResponse<InputStream> mockByteResponse;
    private HttpResponse<InputStream> mockStringResponse;
    private MockedStatic<FairTests> logMock;

    @BeforeEach
    void setup() throws Exception {
        tests = new FairTests();

        mockClient = mock(HttpClient.class);
        mockByteResponse = mock(HttpResponse.class);
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
    private Document xml(String s) throws Exception {
        return DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

    private void mockXmlResponse(String xml) throws Exception {
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockByteResponse);

        when(mockByteResponse.statusCode()).thenReturn(200);
        when(mockByteResponse.body()).thenReturn(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private void mockJsonResponse(String json) throws Exception {
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockStringResponse);

        when(mockStringResponse.statusCode()).thenReturn(200);
        when(mockStringResponse.body()).thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    // =============================
    // extractRecordIdentifier()
    // =============================
    @Nested
    class IdentifierExtractionTests {

        @Test
        void extractsIdCorrectly() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException {
            String id = TestsHelper.invokeExtractRecordIdentifier(tests,
                    "https://catalog/detail/ABC123?lang=en");
            assertEquals("ABC123", id);
        }

        @Test
        void throwsOnMissingDetailSegment() {
            assertThrows(InvocationTargetException.class,
                    () -> TestsHelper.invokeExtractRecordIdentifier(tests, "https://example.com/no"));
        }
    }

    // =============================
    // Access Rights
    // =============================
    @Nested
    class AccessRightsTests {

        @Test
        void passesWhenApprovedTermFound() throws Exception {
            tests.cachedAccessRightsTerms.add("Open");

            mockXmlResponse("""
                <OAI-PMH xmlns:ddi="ddi:codebook:2_5">
                  <ddi:codeBook>
                    <ddi:stdyDscr>
                      <ddi:dataAccs>
                        <ddi:typeOfAccess>Open</ddi:typeOfAccess>
                      </ddi:dataAccs>
                    </ddi:stdyDscr>
                  </ddi:codeBook>
                </OAI-PMH>
            """);

            Result result = tests.containsApprovedAccessRights("http://x/detail/ID123");
            assertEquals(Result.PASS, result);
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

            Result result = tests.containsCessdaTopicClassificationTerms("http://x/detail/TC1");
            assertEquals(Result.PASS, result);
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

            assertEquals(Result.PASS, tests.containsRecommendedDdiVocabularies("http://x/detail/DDI1"));
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

            assertEquals(Result.FAIL, tests.containsRecommendedDdiVocabularies("http://x/detail/DDI2"));
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
}

