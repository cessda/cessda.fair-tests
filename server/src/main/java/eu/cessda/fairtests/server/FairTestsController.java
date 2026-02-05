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
 */
@RestController
@RequestMapping("api")
public class FairTestsController {
    private final FairTests fairTests;

    /**
     * Constructor for FairTestsController.
     * 
     * @param fairTests the FairTests service
     */
    public FairTestsController(FairTests fairTests) {
        this.fairTests = fairTests;
    }

    /**
     * Runs the specified FAIR test on the given URL.
     *
     * @param test the test to run.
     * @param url  the URL to test.
     * @return the results.
     */
    @GetMapping(path = "{test}")
    public Result accessRights(@PathVariable(name = "test") TestType test, @RequestParam(name = "url") URI url) {
        return fairTests.runTest(test, url);
    }

    /**
     * Serves a Turtle file from the resources/static directory.
     * 
     * @param filename the name of the Turtle file (without the .ttl extension)
     * @return the Turtle file as a ResponseEntity<Resource>
     */
    @GetMapping("{filename}.ttl")
    public ResponseEntity<Resource> serveTurtleFile(@PathVariable String filename) {
        try {
            // Validate filename to prevent path traversal
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }
            // Load the file from resources/static
            Resource resource = new ClassPathResource("static/" + filename + ".ttl");

            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            // Set appropriate content type for Turtle files
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/turtle"))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + filename + ".ttl\"")
                    .body(resource);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
