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
    ACCESS_RIGHTS("access-rights", "FM_Gen2-MI-A1.2_M_CARV"),

    // F – Findable
    /**
     * Checks whether records contain a Persistent Identifier
     */
    PID("pid", "FT-F1-PID_M_CESSDA"),

    // I – Interoperable
    /** Checks whether records contain ELSST keywords */
    ELSST_KEYWORDS("elsst-keywords", "FT_I2_M_CEK"),
    /** Checks whether records contain DDI topic classifications */
    TOPIC_CLASS("topic-class", "FT_Gen2-MI-I2A_M_CTV"),

    // R – Reusable
    /** Checks whether records contain approved DDI terms for various fields */
    DDI_ANALYSIS_UNIT("ddi-analysis-unit", "FT_Gen2-MI-I2A_M_DAUV"),
    /** Checks whether records contain approved DDI terms for various fields */
    DDI_COLLECTION_MODE("ddi-collection-mode", "FT_Gen2-MI-I2A_M_DMOCV"),
    /** Checks whether records contain approved DDI terms for various fields */
    DDI_TIME_METHOD("ddi-time-method", "FT_Gen2-MI-I2A_M_DTMV"),
    /** Checks whether records contain approved DDI terms for various fields */
    DDI_SAMPLEPROC("ddi-sampleproc", "FM_Gen2-MI-I2A_M_DSPV"),
    /** Checks whether records contain provenance information */
    PROVENANCE ("provenance", "FT_R1-2_M_CPI");

    /** The name of the test, used for the REST API and CLI */
    private final String name;
    private final String fairTestId;

    /**
     * Constructs a TestType with a given test name.
     * @param name the test name as used by the REST API and the CLI
     */
    TestType(String name, String fairTestId) {
        this.name = name.toLowerCase();
        this.fairTestId = fairTestId;
    }
    
    /**
     * Gets the FAIR test ID.
     * @param testName the test name as used by the REST API and the CLI
     * @return the FAIR test ID, which relates to the filename of the test description document
     */
    public static String getFairTestId(String testName) {
        for (TestType testType : TestType.values()) {
            if (testType.name.equals(testName)) {
                return testType.fairTestId;
            }
        }
        return null;
    }

    /**
     * Gets the test name.
     * @return the test name as used by the REST API and the CLI
     */
    public String getTestName() {
        return name;
    }
}
