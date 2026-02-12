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

import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import eu.cessda.fairtests.TestType;

/**
 * Converter to convert String to TestType.
 */
@Component
public class StringToTestTypeConverter implements Converter<String, TestType> {
    private final Map<String, TestType> testMap;

    public StringToTestTypeConverter() {
        this.testMap = new ConcurrentHashMap<>();
        for (TestType testType : EnumSet.allOf(TestType.class)) {
            testMap.put(testType.getTestName(), testType);
        }
    }

    @Override
    public TestType convert(String source) {
        return testMap.get(source);
    }
}
