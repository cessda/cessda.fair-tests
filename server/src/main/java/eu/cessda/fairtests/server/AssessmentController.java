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

@RestController
@RequestMapping("assess/test")
public class AssessmentController {
    private final FairTests fairTests;

    public AssessmentController(FairTests fairTests) {
        this.fairTests = fairTests;
    }

    /**
     * Runs the specified FAIR test on the given resource following the FTR specification.
     *
     * @param testIdentifier the identifier of the test to run
     * @param request the resource assessment request containing the resource_identifier
     * @return the test results
     */
    @PostMapping(path = "/{test_identifier}")
    public Result postTestAssessment(
            @PathVariable(name = "test_identifier") TestType testIdentifier,
            @RequestBody ResourceAssessmentRequest request) {
        
        URI url = URI.create(request.getResourceIdentifier());
        return fairTests.runTest(testIdentifier, url);
    }
}