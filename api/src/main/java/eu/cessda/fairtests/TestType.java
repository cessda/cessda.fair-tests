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

package eu.cessda.fairtests;

/**
 * Enumeration of the different types of FAIR tests.
 */
public enum TestType {
    // A – Accessible
    /**
     * Checks whether records contain approved Access Rights terms
     */
    ACCESS_RIGHTS("access-rights"),

    // F – Findable
    /**
     * Checks whether records contain a Persistent Identifier
     */
    PID("pid"),

    // I – Interoperable
    /** Checks whether records contain ELSST keywords */
    ELSST_KEYWORDS("elsst-keywords"),
    /** Checks whether records contain DDI topic classifications */
    TOPIC_CLASS("topic-class"),

    // R – Reusable
    /** Checks whether records contain approved DDI terms for various fields */
    DDI_ANALYSIS_UNIT("ddi-analysis-unit"),
    /** Checks whether records contain approved DDI terms for various fields */
    DDI_COLLECTION_MODE("ddi-collection-mode"),
    /** Checks whether records contain approved DDI terms for various fields */
    DDI_TIME_METHOD("ddi-time-method"),
    /** Checks whether records contain approved DDI terms for various fields */
    DDI_SAMPLEPROC("ddi-sampleproc"),
    /** Checks whether records contain provenance information */
    PROVENANCE ("provenance");

    /** The name of the test, used for the REST API and CLI */
    private final String name;

    /**
     * Constructs a TestType with a given test name.
     * @param name the test name as used by the REST API and the CLI
     */
    TestType(String name) {
        this.name = name.toLowerCase();
    }

    /**
     * Gets the test name.
     */
    public String getTestName() {
        return name.toLowerCase();
    }
}
