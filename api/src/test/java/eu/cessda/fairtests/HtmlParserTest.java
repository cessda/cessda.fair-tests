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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HtmlParserTest {

    private HtmlParser parser;

    private VocabularyService vocabulary;

    @BeforeEach
    void setUp() {

        parser = new HtmlParser();

        vocabulary = mock(VocabularyService.class);

        when(vocabulary.getApprovedAccessRightsTerms())
                .thenReturn(Set.of("Open", "Restricted"));

        when(vocabulary.getApprovedPidSchemas())
                .thenReturn(Set.of("doi"));

        when(vocabulary.validateElsstKeywords(
                List.of("employment"),
                "en"))
                        .thenReturn(Result.PASS);
    }

    @Test
    @DisplayName("Should pass ACCESS_RIGHTS from embedded JSON-LD")
    void shouldPassAccessRightsFromJsonLd()
            throws Exception {

        String html = """
                <html>
                  <head>
                    <script id="json-ld" type="application/ld+json">
                    {
                      "id": "dataset-1",
                      "dataAccess": "Open"
                    }
                    </script>
                  </head>
                </html>
                """;

        Result result = parser.runTest(
                TestType.ACCESS_RIGHTS,
                input(html),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should pass PID validation from embedded JSON-LD")
    void shouldPassPidValidationFromJsonLd()
            throws Exception {

        String html = """
                <html>
                  <script id="json-ld" type="application/ld+json">
                  {
                    "id": "dataset-1",
                    "pidStudies": [
                      {
                        "value": "doi"
                      }
                    ]
                  }
                  </script>
                </html>
                """;

        Result result = parser.runTest(
                TestType.PID,
                input(html),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should return INDETERMINATE when JSON-LD block missing")
    void shouldReturnIndeterminateWhenJsonLdMissing()
            throws Exception {

        String html = """
                <html>
                  <body>
                    <h1>No JSON-LD here</h1>
                  </body>
                </html>
                """;

        Result result = parser.runTest(
                TestType.ACCESS_RIGHTS,
                input(html),
                vocabulary);

        assertEquals(Result.INDETERMINATE, result);
    }

    @Test
    @DisplayName("Should return INDETERMINATE when JSON-LD block empty")
    void shouldReturnIndeterminateWhenJsonLdEmpty()
            throws Exception {

        String html = """
                <html>
                  <script id="json-ld" type="application/ld+json">
                  </script>
                </html>
                """;

        Result result = parser.runTest(
                TestType.ACCESS_RIGHTS,
                input(html),
                vocabulary);

        assertEquals(Result.INDETERMINATE, result);
    }

    @Test
    @DisplayName("Should return INDETERMINATE when closing script tag missing")
    void shouldReturnIndeterminateWhenClosingTagMissing()
            throws Exception {

        String html = """
                <html>
                  <script id="json-ld" type="application/ld+json">
                  {
                    "id": "dataset-1"
                  }
                </html>
                """;

        Result result = parser.runTest(
                TestType.ACCESS_RIGHTS,
                input(html),
                vocabulary);

        assertEquals(Result.INDETERMINATE, result);
    }

    @Test
    @DisplayName("Should support case-insensitive script tag matching")
    void shouldSupportCaseInsensitiveScriptTagMatching()
            throws Exception {

        String html = """
                <HTML>
                  <SCRIPT ID="JSON-LD" TYPE="APPLICATION/LD+JSON">
                  {
                    "id": "dataset-1",
                    "dataAccess": "Open"
                  }
                  </SCRIPT>
                </HTML>
                """;

        Result result = parser.runTest(
                TestType.ACCESS_RIGHTS,
                input(html),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should pass ELSST keyword validation from JSON-LD")
    void shouldPassElsstValidationFromJsonLd()
            throws Exception {

        String html = """
                <html>
                  <script id="json-ld" type="application/ld+json">
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
                  </script>
                </html>
                """;

        Result result = parser.runTest(
                TestType.ELSST_KEYWORDS,
                input(html),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should extract JSON-LD block via reflection")
    void shouldExtractJsonLdBlockViaReflection()
            throws Exception {

        Method method = HtmlParser.class.getDeclaredMethod(
                "extractJsonLdBlock",
                String.class);

        method.setAccessible(true);

        String html = """
                <html>
                  <script id="json-ld" type="application/ld+json">
                  { "id": "dataset-1" }
                  </script>
                </html>
                """;

        String result = (String) method.invoke(parser, html);

        assertNotNull(result);

        assertEquals(
                "{ \"id\": \"dataset-1\" }",
                result);
    }

    @Test
    @DisplayName("Should return null when HTML content blank")
    void shouldReturnNullWhenHtmlBlank()
            throws Exception {

        Method method = HtmlParser.class.getDeclaredMethod(
                "extractJsonLdBlock",
                String.class);

        method.setAccessible(true);

        String result = (String) method.invoke(parser, "   ");

        assertEquals(null, result);
    }

    @Test
    @DisplayName("Should read stream correctly")
    void shouldReadStreamCorrectly()
            throws Exception {

        Method method = HtmlParser.class.getDeclaredMethod(
                "readStream",
                InputStream.class);

        method.setAccessible(true);

        String content = "test content";

        String result = (String) method.invoke(
                null,
                input(content));

        assertEquals(content, result);
    }

    @Test
    @DisplayName("Should convert JSON string to InputStream")
    void shouldConvertJsonStringToInputStream()
            throws Exception {

        Method method = HtmlParser.class.getDeclaredMethod(
                "toStream",
                String.class);

        method.setAccessible(true);

        InputStream result = (InputStream) method.invoke(
                null,
                "{\"id\":\"1\"}");

        assertNotNull(result);
    }

    private InputStream input(String content) {

        return new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8));
    }
}