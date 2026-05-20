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
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * FairTests
 *
 * Orchestrator for FAIR-data compliance checks against metadata endpoints.
 *
 * This class is responsible for three things only:
 * 
 * Fetching the raw HTTP response from the supplied URL.
 * Sniffing the response format using {@link FormatSniffer}.
 * Delegating the requested {@link TestType} to the appropriate
 * {@link FormatParser} implementation via a {@code switch} expression.
 * 
 * All XML-specific logic lives in {@link XmlParser}; all JSON-specific logic
 * lives in {@link CdcJsonParser}. Shared vocabulary/ELSST lookups live in
 * {@link VocabularyService}.
 * 
 *
 * Supported formats
 * 
 * XML — DDI Codebook 2.5, optionally wrapped in an OAI-PMH envelope
 * ({@link XmlParser})
 * JSON object / JSON array — stub implementation, ready for your schema
 * ({@link CdcJsonParser})
 *
 * Supported tests ({@link TestType})
 * 
 * Access Rights compliance
 * Persistent Identifier (PID) schema validation
 * ELSST controlled vocabulary keyword validation
 * CESSDA Topic Classification vocabulary usage
 * DDI Analysis Unit vocabulary usage
 * DDI Collection Mode vocabulary usage
 * DDI Time Method vocabulary usage
 * DDI Sampling Procedure vocabulary usage
 * Provenance information presence
 *
 * Return values
 * 
 * {@code pass} — the record meets the criteria
 * {@code fail} — the record does not meet the criteria
 * {@code indeterminate} — an error occurred, or the format is unsupported
 * for this test
 */
public class FairTests {

    private static final Logger logger = Logger.getLogger(FairTests.class.getName());
    private static final String HTTP_HEADER_ACCEPT = "Accept";

    // -------------------------------------------------------------------------
    // Shared services (one instance per FairTests, injected into each parser)
    // -------------------------------------------------------------------------

    private VocabularyService vocabulary = new VocabularyService();
    private FormatSniffer sniffer = new FormatSniffer();
    private XmlParser xmlParser = new XmlParser();
    private CdcJsonParser cdcJsonParser = new CdcJsonParser();
    private HtmlParser htmlParser = new HtmlParser();
    private HttpClient httpClient = HttpClient.newHttpClient();

    // =========================================================================
    // CLI entry point
    // =========================================================================

    /**
     * Command-line entry point.
     *
     * @param args {@code args[0]} test type name (see {@link TestType});
     *             {@code args[1]} URL returning metadata in a supported format
     * @throws ParseException     if the command line cannot be parsed
     * @throws URISyntaxException if the supplied URL is malformed
     */
    @SuppressWarnings("java:S106")
    public static void main(String[] args) throws ParseException, URISyntaxException {

        @SuppressWarnings("deprecation")
        HelpFormatter formatter = new HelpFormatter();
        var options = new Options();
        var commandLine = new DefaultParser().parse(options, args);

        var testMap = new HashMap<String, TestType>();
        for (TestType t : EnumSet.allOf(TestType.class)) {
            testMap.put(t.getTestName(), t);
        }

        if (commandLine.getArgList().size() < 2
                || !testMap.containsKey(commandLine.getArgList().get(0))) {
            formatter.printHelp(
                    "FairTests <test-type> <url>\ntest types: " + getValidTestTypes(),
                    null, options, null, false);
            System.exit(1);
        }

        TestType test = testMap.get(commandLine.getArgList().get(0));
        URI url = new URI(commandLine.getArgList().get(1));

        FairTests fairTests = new FairTests();
        Result result = fairTests.runTest(test, url);

        logInfo("Result: %s", result);
        System.out.println(result);
        System.exit(Result.PASS == result ? 0 : 1);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Fetches the resource at {@code url}, detects its format, and runs the
     * requested test by delegating to the appropriate {@link FormatParser}.
     * 
     * The URL is expected to return metadata in a supported format (e.g. DDI XML or CDC JSON).
     * If the format is unsupported, or if any error occurs during fetching, sniffing, or parsing,
     * the method returns {@link Result#INDETERMINATE}.
     * Otherwise, it returns PASS or FAIL based on the test criteria.
     *
     * @param test the test to run
     * @param url  a URL that returns metadata in a supported format
     * @return {@link Result#PASS}, {@link Result#FAIL}, or
     *         {@link Result#INDETERMINATE}
     */
    public Result runTest(TestType test, URI url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(url)
                    .header(HTTP_HEADER_ACCEPT, "application/xml, application/json, text/xml, */*")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = sendRequest(request);

            if (response.statusCode() != 200) {
                logWarning("HTTP {0} fetching {1}", response.statusCode(), url);
                return Result.INDETERMINATE;
            }

            // Sniff the format and get a rewound stream safe to pass to any parser
            FormatSniffer.SniffResult sniffed = sniffer.wrap(response.body());
            logInfo("Detected format %s at %s", sniffed.format(), url);

            FormatParser parser = switch (sniffed.format()) {
                case XML                       -> xmlParser;
                case JSON_OBJECT               -> cdcJsonParser; // JSON_ARRAY is not handled at present
                case HTML                      -> htmlParser; // HTML is treated as a special case: extract the JSON-LD block and pass it to a JSON parser
                default -> {
                    logWarning("Unsupported format %s returned by %s", sniffed.format(), url);
                    yield null;
                }
            };

            if (parser == null) return Result.INDETERMINATE;

            return parser.runTest(test, sniffed.stream(), vocabulary);

        } catch (IOException e) {
            logSevere("I/O error running test %s against %s: %s", test, url, e.getMessage());
            return Result.INDETERMINATE;
        }
    }


    // =========================================================================
    // Convenience public methods (preserve original API surface)
    // =========================================================================

    /** @see TestType#ACCESS_RIGHTS */
    public Result containsApprovedAccessRights(URI url) {
        return runTest(TestType.ACCESS_RIGHTS, url);
    }

    /** @see TestType#PID */
    public Result containsApprovedPid(URI url) {
        return runTest(TestType.PID, url);
    }

    /** @see TestType#ELSST_KEYWORDS */
    public Result containsElsstKeywords(URI url) {
        return runTest(TestType.ELSST_KEYWORDS, url);
    }

    /** @see TestType#TOPIC_CLASS */
    public Result containsCessdaTopicClassificationTerms(URI url) {
        return runTest(TestType.TOPIC_CLASS, url);
    }

    /** @see TestType#DDI_ANALYSIS_UNIT */
    public Result containsDdiAnalysisUnit(URI url) {
        return runTest(TestType.DDI_ANALYSIS_UNIT, url);
    }

    /** @see TestType#DDI_COLLECTION_MODE */
    public Result containsDdiCollectionMode(URI url) {
        return runTest(TestType.DDI_COLLECTION_MODE, url);
    }

    /** @see TestType#DDI_TIME_METHOD */
    public Result containsDdiTimeMethod(URI url) {
        return runTest(TestType.DDI_TIME_METHOD, url);
    }

    /** @see TestType#DDI_SAMPLEPROC */
    public Result containsDdiSamplingProcedureTerms(URI url) {
        return runTest(TestType.DDI_SAMPLEPROC, url);
    }

    /** @see TestType#PROVENANCE */
    public Result containsProvenanceInformation(URI url) {
        return runTest(TestType.PROVENANCE, url);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private HttpResponse<InputStream> sendRequest(HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String getValidTestTypes() {
        return Arrays.stream(TestType.values())
                .map(TestType::getTestName)
                .collect(Collectors.joining(", "));
    }


    // Logging helpers to avoid unnecessary string formatting when the log level is not enabled

    /**
     * Logs an info message if the log level is enabled.
     * 
     * @param message the message to log
     * @param args the arguments for the message
     */
     static void logInfo(String message, Object... args) {
        if (logger.isLoggable(Level.INFO)) {
            logger.info(args.length == 0 ? message : String.format(message, args));
        }
    }

     /** 
     * Logs a warning message if the log level is enabled.
     * 
     * @param message the message to log
     * @param args the arguments for the message
     */
    static void logWarning(String message, Object... args) {
        if (logger.isLoggable(Level.WARNING)) {
            logger.warning(args.length == 0 ? message : String.format(message, args));
        }
    }

    /** 
     * Logs a severe message if the log level is enabled.
     * 
     * @param message the message to log
     * @param args the arguments for the message
     */
    static void logSevere(String message, Object... args) {
        if (logger.isLoggable(Level.SEVERE)) {
            logger.severe(args.length == 0 ? message : String.format(message, args));
        }
    }
}