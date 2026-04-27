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

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Detects the format of raw data by inspecting its content.
 * Use {@link #wrap(InputStream)} to get a stream that can be both sniffed and re-read.
 * The detection logic is intentionally simple and may not be 100% accurate,
 * but it should work well for common cases and is very fast.
 */
public class FormatSniffer {

    public enum Format {
        XML,
        JSON_OBJECT,
        JSON_ARRAY,
        HTML,
        CSV,
        UNKNOWN
    }

    private static final int PEEK_SIZE = 1024;

    /**
     * Result record bundling the detected format with a re-readable stream.
     * 
     * @param format the detected format
     * @param stream a stream that has been rewound to the start, ready for parsing
     */
    public record SniffResult(Format format, InputStream stream) {}

    /**
     * Wraps the given InputStream in a BufferedInputStream, sniffs the format,
     * resets the stream back to the start, and returns both together.
     * The returned stream is safe to pass directly to any parser.
     * 
     * @param inputStream the raw input stream to sniff and wrap
     * @return a SniffResult containing the detected format and a rewound stream
     * @throws IOException if the stream cannot be read or reset
     * 
     */
    public SniffResult wrap(InputStream inputStream) throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(inputStream, PEEK_SIZE * 2);
        buffered.mark(PEEK_SIZE);

        byte[] buffer = new byte[PEEK_SIZE];
        int bytesRead = buffered.read(buffer, 0, PEEK_SIZE);
        buffered.reset();  // rewind so the parser sees the full stream

        if (bytesRead <= 0) return new SniffResult(Format.UNKNOWN, buffered);

        String snippet = new String(buffer, 0, bytesRead).stripLeading();
        Format format = detectFromString(snippet);
        return new SniffResult(format, buffered);
    }

    /**
     * Detects the format from a plain String (no stream rewinding needed).
     * 
     * @param raw the input string to analyze
     * @return the detected Format, or UNKNOWN if it cannot be determined
     */
    public Format detect(String raw) {
        if (raw == null || raw.isBlank()) return Format.UNKNOWN;
        return detectFromString(raw.stripLeading());
    }

    /** 
     * Detects the format from a string snippet, using simple heuristics based on common syntax.
     * This method assumes the input has already been trimmed of leading whitespace.
     * 
     * @param snippet the input string snippet to analyze
     * @return Format the detected format, or UNKNOWN if it cannot be determined
     */
    private Format detectFromString(String snippet) {
        if (snippet.startsWith("{"))           return Format.JSON_OBJECT;
        if (snippet.startsWith("["))           return Format.JSON_ARRAY;
        if (startsWithXmlDeclaration(snippet)) return Format.XML;
        if (snippet.startsWith("<"))           return detectXmlOrHtml(snippet);
        if (looksLikeCsv(snippet))             return Format.CSV;
        return Format.UNKNOWN;
    }

    /** 
     * Checks if the snippet starts with an XML declaration.
     * 
     * This is a common signature for XML documents, but we also allow for an optional UTF-8 BOM before it.
     * The presence of an XML declaration is a strong indicator of XML format, even if the first tag is not <codeBook>.
     * 
     * @param snippet the input string to check
     * @return boolean true if it starts with an XML declaration, false otherwise
     */
    private boolean startsWithXmlDeclaration(String snippet) {
        return snippet.startsWith("<?xml") || snippet.startsWith("\uFEFF<?xml");
    }

    /** 
     * Detects whether the snippet looks like XML or HTML, based on the first tag.  
     * 
     * @param snippet the input string, assumed to start with "<"
     * @return Format either XML or HTML
     */
    private Format detectXmlOrHtml(String snippet) {
        String lower = snippet.toLowerCase();
        if (lower.startsWith("<!doctype html") || lower.startsWith("<html")) {
            return Format.HTML;
        }
        return Format.XML;
    }

    /** 
     * Naive check for CSV: presence of commas in the first line, but no angle brackets or JSON braces.
     * This is not a robust check, but it's a reasonable heuristic for distinguishing CSV from XML/JSON.
     * 
     * @param snippet the input string, assumed to be non-blank and not starting with "<", "{", or "["
     * @return boolean true if it looks like CSV, false otherwise
     */
    private boolean looksLikeCsv(String snippet) {
        String firstLine = snippet.lines().findFirst().orElse("");
        return firstLine.contains(",") && !firstLine.startsWith("<") && !firstLine.startsWith("{");
    }
}