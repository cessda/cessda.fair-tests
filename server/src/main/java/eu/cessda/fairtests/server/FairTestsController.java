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

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eu.cessda.fairtests.FairTests;
import eu.cessda.fairtests.Result;
import eu.cessda.fairtests.TestType;

@RestController
@RequestMapping("api")
/**
 * REST controller for FAIR tests.
 */
public class FairTestsController {
    private final FairTests fairTests;

    /**
     * Constructor for FairTestsController.
     * @param fairTests the FairTests service
     */
    public FairTestsController(FairTests fairTests) {
        this.fairTests = fairTests;
    }

    @GetMapping(path = "{test}")
    /**
     * Runs the specified FAIR test on the given URL. 
     * @param test 
     * @param url  
     * @return
     */
    public Result accessRights(@PathVariable(name = "test") TestType test, @RequestParam(name = "url") String url) {
        return fairTests.runTest(test, url);
    }
}
