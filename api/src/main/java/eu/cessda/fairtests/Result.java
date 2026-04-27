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

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Result of the FAIR test
 */
public enum Result {
    /**
     * Test passed
     */
    PASS,
    /**
     * Test failed
     */
    FAIL,
    /**
     * Test encountered an error
     */
    INDETERMINATE;

    @JsonValue
    /* Method to return the enum value as a lowercase string for JSON serialisation */
    public String toLowerCase() {
        return this.name().toLowerCase();
    }

     @Override
     /*  Override the toString method to return the enum value as a lowercase string */
    public String toString() { 
        return this.name().toLowerCase();
    }
}
