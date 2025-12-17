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

package cessda.fairtests;

import java.lang.reflect.InvocationTargetException;

class TestsHelper {

    static String invokeExtractRecordIdentifier(FairTests tests, String url) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        var m = FairTests.class.getDeclaredMethod("extractRecordIdentifier", String.class);
        m.setAccessible(true);
        return (String) m.invoke(tests, url);
    }
}
