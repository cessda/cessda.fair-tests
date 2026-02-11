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

package eu.cessda.fairtests.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Jackson configuration for JSON serialization.
 * 
 * This configuration disables alphabetical property sorting to ensure
 * that @JsonPropertyOrder annotations are respected when serializing
 * objects to JSON.
 */
@Configuration
public class JacksonConfig {
    
    /**
     * Configure ObjectMapper to respect @JsonPropertyOrder annotations.
     * 
     * By default, some Jackson configurations sort properties alphabetically,
     * which overrides @JsonPropertyOrder. This bean ensures that property
     * order follows the annotations in the model classes.
     * 
     * @return configured JsonMapper instance
     */
    @Bean
    public JsonMapper objectMapper() {
        return JsonMapper.builder()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, false)
                .build();
    }
}
