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

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.cessda.fairtests.FairTests;
import eu.cessda.fairtests.Result;
import eu.cessda.fairtests.TestType;
import eu.cessda.fairtests.dto.ResourceAssessmentRequest;
import eu.cessda.fairtests.dto.TestResult;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/assess/test")
/**
 * REST controller for handling FAIR test assessments.
 * Provides an endpoint to run a specified FAIR test on a given resource URL.
 * The controller uses the FairTests service to execute the tests and return results.
 * The postTestAssessment method accepts a test identifier as a path variable and a
 * resource assessment request containing the resource URL in the request body,
 * validates the input, and returns the test results. 
 * Overall, this controller serves as the main entry point for clients to submit resources
 * for FAIR test assessments, providing a structured and validated way to receive input and return results based on the specified tests.
 */
public class AssessmentController {
    private final FairTests fairTests;

    /* Constructor for AssessmentController, injecting the FairTests service */
    public AssessmentController(FairTests fairTests) {
        this.fairTests = fairTests;
    }

    /**
     * Runs the specified FAIR test on the given resource following the FTR specification.
     * The test to run is specified as a path variable, and the resource URL to test is provided in the request body
     * as part of the ResourceAssessmentRequest DTO.
     * The method uses the FairTests service to execute the test and returns the results as a ResponseEntity<Result>.
     *
     * @param test_identifier the name of the FAIR test to run, provided as a path variable
     * @param request the resource assessment request containing the resource_identifier
     * @return the test result wrapped in a TestResult DTO for consistent API response structure
     */
    @PostMapping(path = "/{test_identifier}", consumes = "application/json", produces = "application/json")
    public TestResult postTestAssessment(
            @PathVariable(name = "test") TestType testIdentifier,
            @Valid @RequestBody ResourceAssessmentRequest request) {
        
        URI url = URI.create(request.getResourceIdentifier());
        Result result = fairTests.runTest(testIdentifier, url);

        /* Return the test result wrapped in a TestResult DTO for consistent API response structure */
        return new TestResult(testIdentifier.getTestName(), url.toString(), result);
       
    }
}