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

/**
 * Common contract for format-specific parsers used by {@link FairTests}.
 * <p>
 * Each implementation receives a fully rewound {@link InputStream} (produced by
 * {@link FormatSniffer#wrap(InputStream)}) and a {@link VocabularyService} for
 * shared vocabulary/ELSST lookups, then maps a {@link TestType} to a
 * {@link Result}.
 * </p>
 */
public interface FormatParser {

    /**
     * Run the given test against the content in the supplied stream.
     *
     * @param test        the test to run
     * @param inputStream a fully rewound stream containing the raw response body
     * @param vocabulary  shared vocabulary and ELSST lookup service
     * @return {@link Result#PASS}, {@link Result#FAIL}, or
     *         {@link Result#INDETERMINATE}
     * @throws IOException if the stream cannot be read or parsed
     */
    Result runTest(TestType test, InputStream inputStream, VocabularyService vocabulary) throws IOException;
}