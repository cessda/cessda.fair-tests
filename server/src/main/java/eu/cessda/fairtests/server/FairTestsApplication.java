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

package eu.cessda.fairtests.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import eu.cessda.fairtests.FairTests;

/**
 * Main class to start the Fair Tests application.
 */
@SpringBootApplication
public class FairTestsApplication {
    public static void main(String[] args) {
        SpringApplication.run(FairTestsApplication.class, args);
    }

    /**
     * Creates a FairTests bean.
     */
    @Bean
    public FairTests fairTests() {
        return new FairTests();
    }
}
