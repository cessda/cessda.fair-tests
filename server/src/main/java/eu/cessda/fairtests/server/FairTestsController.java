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

package eu.cessda.fairtests.server;

import java.net.URI;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eu.cessda.fairtests.FairTests;
import eu.cessda.fairtests.Result;
import eu.cessda.fairtests.TestType;

/**
 * REST controller for FAIR tests.
 * Provides endpoints to serve Turtle files and to run FAIR tests on specified
 * URLs.
 * The controller uses the FairTests service to execute the tests and return
 * results.
 * The serveTurtleFile method serves Turtle files from the resources/static
 * directory,
 * validating the filename to prevent path traversal and returning appropriate
 * HTTP
 * responses based on the existence of the file and any exceptions that may
 * occur.
 * The accessRights method runs the specified FAIR test on the given URL and
 * returns the results,
 * allowing clients to assess the FAIRness of their resources by providing a URL
 * and specifying the test to run.
 * Overall, this controller serves as the main entry point for clients to
 * interact with the FAIR tests API,
 * providing both file serving capabilities and test execution functionality in
 * a structured and secure manner.
 */
@RestController
@RequestMapping("api")
public class FairTestsController {
    private final FairTests fairTests = new FairTests();

    /**
     * Serves a Turtle file from the resources/static directory.
     * 
     * @param filename the name of the Turtle file (without the .ttl extension)
     * @return the Turtle file as a ResponseEntity<Resource>
     */
    @GetMapping(path = "{filename}.ttl", produces = "text/turtle")
    public ResponseEntity<Resource> serveTurtleFile(@PathVariable("filename") String filename) {
        // Validate filename to prevent path traversal
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return ResponseEntity.badRequest().build();
        }

        /* Try to load the file and return it, handling any exceptions that may occur */
        try {
            // Load the file from resources/static
            Resource resource = new ClassPathResource("static/" + filename + ".ttl");

            /*
             * Check if the file exists before attempting to serve it, returning a 404 Not
             * Found if it doesn't
             */
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            /*
             * Serve the file with the correct content type and a content disposition header
             * to suggest inline display in the browser
             */
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/turtle"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + filename + ".ttl\"")
                    .body(resource);

            /* Catch any unexpected exceptions and return a 500 Internal Server Error */
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Runs the specified FAIR test on the given URL.
     * The test to run is specified as a path variable, and the URL to test is
     * provided as a query parameter.
     * The method uses the FairTests service to execute the test and returns the
     * results as a ResponseEntity<Result>.
     *
     * @param test the test to run.
     * @param url  the URL to test.
     * @return the results.
     */
    @GetMapping(path = "{test}")
    public Result accessRights(@PathVariable(name = "test") TestType test, @RequestParam(name = "url") URI url) {
        return fairTests.runTest(test, url);
    }
}
