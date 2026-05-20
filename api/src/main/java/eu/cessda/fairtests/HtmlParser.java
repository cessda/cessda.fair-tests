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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Implementation for HTML responses that embed a JSON-LD
 * block.
 *
 * The parser scans the raw HTML byte stream for a {@code <script>} element
 * with {@code id="json-ld"} and {@code type="application/ld+json"}, extracts
 * the text content between that opening tag and the next {@code </script>}
 * closing tag, then delegates all test logic to {@link CdcJsonParser} using
 * the extracted JSON as its input stream.
 *
 * If no such block is found the method returns
 * {@link Result#INDETERMINATE} and logs the reason; no exception is thrown.
 * Additional HTML-specific checks can be added in further {@code switch} cases
 * in {@link #runTest(TestType, InputStream, VocabularyService)} without
 * disturbing the JSON-LD extraction path.
 *
 * Expected HTML fragment
 * <pre>{@code
 * <script id="json-ld" type="application/ld+json">
 * { ... }
 * </script>
 * }</pre>
 *
 * Structural note
 * This class intentionally mirrors the structure of {@link CdcJsonParser}:
 * it implements {@link FormatParser}, uses the same method signature, and
 * returns the same {@link Result} values. Once the JSON-LD block has been
 * located, every test is handled by an inner {@link CdcJsonParser} instance,
 * so there is no duplication of vocabulary-matching logic.
 */
public class HtmlParser implements FormatParser {

    /** Opening tag that marks the start of the JSON-LD block (case-insensitive match applied at runtime). */
    private static final String JSON_LD_OPEN_TAG  = "<script id=\"json-ld\" type=\"application/ld+json\">";

    /** Closing tag that marks the end of the JSON-LD block (case-insensitive match applied at runtime). */
    private static final String JSON_LD_CLOSE_TAG = "</script>";

    /** Downstream parser that handles all JSON test logic once the block is extracted. */
    private final CdcJsonParser cdcJsonParser = new CdcJsonParser();

    // =========================================================================
    // FormatParser
    // =========================================================================

    /**
     * {@inheritDoc}
     *
     * Reads the full HTML byte stream, locates the JSON-LD {@code <script>}
     * block, and passes its content to {@link CdcJsonParser#runTest} for
     * evaluation. Returns {@link Result#INDETERMINATE} if the block cannot be
     * found or the stream cannot be read.
     */
    @Override
    public Result runTest(TestType test, InputStream inputStream, VocabularyService vocabulary)
            throws IOException {

        String html = readStream(inputStream);

        String jsonBlock = extractJsonLdBlock(html);
        if (jsonBlock == null) {
            // Reason already logged by extractJsonLdBlock
            return Result.INDETERMINATE;
        }

        FairTests.logInfo("Extracted JSON-LD content: %s", jsonBlock);

        try (InputStream jsonStream = toStream(jsonBlock)) {
            return cdcJsonParser.runTest(test, jsonStream, vocabulary);
        }
    }

    // =========================================================================
    // JSON-LD extraction
    // =========================================================================

    /**
     * Locates and returns the raw JSON text between the JSON-LD {@code <script>}
     * open tag and the following {@code </script>} close tag.
     *
     * The search is case-insensitive so that variant capitalisations of the
     * tag (e.g. {@code <SCRIPT>}) are handled correctly.
     *
     * @param html the full HTML document as a string
     * @return the trimmed JSON text, or {@code null} if the block was not found
     */
    private String extractJsonLdBlock(String html) {
        if (html == null || html.isBlank()) {
            FairTests.logWarning("Received HTML content: %s", html);
            return null;
        }

        String lower = html.toLowerCase(java.util.Locale.ROOT);

        // Locate the opening <script id="json-ld" …> tag
        int openTagStart = lower.indexOf(JSON_LD_OPEN_TAG.toLowerCase(java.util.Locale.ROOT));
        if (openTagStart == -1) {
            FairTests.logWarning("No <script id=\"json-ld\" type=\"application/ld+json\"> tag found in HTML response");
            return null;
        }

        // Content starts immediately after the closing '>' of the open tag
        int contentStart = openTagStart + JSON_LD_OPEN_TAG.length();

        // Locate the next </script> after the open tag
        int closeTagStart = lower.indexOf(JSON_LD_CLOSE_TAG.toLowerCase(java.util.Locale.ROOT),
                contentStart);
        if (closeTagStart == -1) {
            FairTests.logWarning(
                    "Found JSON-LD <script> open tag but no matching </script> close tag");
            return null;
        }

        String jsonBlock = html.substring(contentStart, closeTagStart).trim();

        if (jsonBlock.isEmpty()) {
            FairTests.logWarning("JSON-LD <script> block is present but contains no content");
            return null;
        }

        return jsonBlock;
    }

    // =========================================================================
    // Stream helpers
    // =========================================================================

    /**
     * Reads all bytes from the supplied stream and decodes them as UTF-8.
     *
     * @param inputStream the stream to read; will be fully consumed
     * @return the stream content as a {@link String}
     * @throws IOException if the stream cannot be read
     */
    private static String readStream(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    /**
     * Wraps a JSON string in a UTF-8 {@link ByteArrayInputStream} suitable for
     * passing to {@link CdcJsonParser#runTest}.
     *
     * @param json the JSON text to wrap
     * @return a fresh {@link InputStream} over the UTF-8 bytes of {@code json}
     */
    private static InputStream toStream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}