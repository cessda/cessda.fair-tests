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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FairTestsTest {

    private FairTests fairTests;

    private HttpClient httpClient;
    private HttpResponse<InputStream> response;

    @BeforeEach
    void setUp() throws Exception {

        fairTests = new FairTests();

        httpClient = mock(HttpClient.class);
        response = mock(HttpResponse.class);

        inject("httpClient", httpClient);
    }

    @Test
    @DisplayName("Should return PASS for valid XML response")
    void shouldReturnPassForXmlResponse() throws Exception {

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <codeBook>
                    <stdyDscr>
                        <citation>
                            <titlStmt>
                                <IDNo agency="DataCite">
                                    doi:10.1234/test
                                </IDNo>
                            </titlStmt>
                        </citation>
                    </stdyDscr>
                </codeBook>
                """;

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(input(xml));

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                        .thenReturn(response);

        Result result = fairTests.runTest(
                TestType.PID,
                URI.create("https://example.org/test.xml"));

        assertEquals(Result.INDETERMINATE, result);
    }

    @Test
    @DisplayName("Should return PASS for valid JSON response")
    void shouldReturnPassForJsonResponse() throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "dataAccess": "Open"
                }
                """;

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(input(json));

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                        .thenReturn(response);

        Result result = fairTests.runTest(
                TestType.ACCESS_RIGHTS,
                URI.create("https://example.org/test.json"));

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should return INDETERMINATE for HTTP error")
    void shouldReturnIndeterminateForHttpError() throws Exception {

        when(response.statusCode()).thenReturn(404);

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                        .thenReturn(response);

        Result result = fairTests.runTest(
                TestType.ACCESS_RIGHTS,
                URI.create("https://example.org/notfound"));

        assertEquals(Result.INDETERMINATE, result);
    }

    @Test
    @DisplayName("Should return INDETERMINATE for unsupported format")
    void shouldReturnIndeterminateForUnsupportedFormat()
            throws Exception {

        String content = """
                plain text content
                """;

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(input(content));

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                        .thenReturn(response);

        Result result = fairTests.runTest(
                TestType.ACCESS_RIGHTS,
                URI.create("https://example.org/test.txt"));

        assertEquals(Result.INDETERMINATE, result);
    }

    @Test
    @DisplayName("Should return INDETERMINATE on IOException")
    void shouldReturnIndeterminateOnIOException() throws Exception {

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                        .thenThrow(new IOException("Connection failed"));

        Result result = fairTests.runTest(
                TestType.ACCESS_RIGHTS,
                URI.create("https://example.org/error"));

        assertEquals(Result.INDETERMINATE, result);
    }

    @Test
    @DisplayName("Should return PASS using convenience method for access rights")
    void shouldUseConvenienceMethodForAccessRights()
            throws Exception {

        String json = """
                {
                  "id": "dataset-1",
                  "dataAccess": "Open"
                }
                """;

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(input(json));

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                        .thenReturn(response);

        Result result = fairTests.containsApprovedAccessRights(
                URI.create("https://example.org/test.json"));

        assertEquals(Result.PASS, result);
    }

    @Test
    @DisplayName("Should return PASS using convenience method for provenance")
    void shouldUseConvenienceMethodForProvenance()
            throws Exception {

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

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(input(json));

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                        .thenReturn(response);

        Result result = fairTests.containsProvenanceInformation(
                URI.create("https://example.org/test.json"));

        assertEquals(Result.FAIL, result);
    }

    @Test
    @DisplayName("Should throw IllegalStateException when interrupted")
    void shouldThrowIllegalStateExceptionWhenInterrupted()
            throws Exception {

        when(httpClient.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                        .thenThrow(new InterruptedException("Interrupted"));

        assertThrows(
                InvocationTargetException.class,
                () -> invokeSendRequest());
    }

    private void invokeSendRequest() throws Exception {

        var method = FairTests.class.getDeclaredMethod(
                "sendRequest",
                HttpRequest.class);

        method.setAccessible(true);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://example.org"))
                .GET()
                .build();

        method.invoke(fairTests, request);
    }

    private InputStream input(String content) {

        return new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8));
    }

    private void inject(String fieldName, Object value)
            throws Exception {

        Field field = FairTests.class.getDeclaredField(fieldName);

        field.setAccessible(true);
        field.set(fairTests, value);
    }
}