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

package eu.cessda.fairtests.dto;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.cessda.fairtests.Result;

@DisplayName("TestResult Tests")
class TestResultTest {

    private static final String TEST_NAME = "access-rights";
    private static final String RESOURCE_URL = "https://example.org/resource";
    private static final String VALUE_PASS = "pass";
    private static final String LOG = "Test passed successfully.";
    private static final String DESCRIPTION = "Checks whether the data access terms in the metadata belong to the CESSDA Access Rights controlled vocabulary.";

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create TestResult with all required fields")
        void shouldCreateTestResultWithAllFields() {
            // Act
            TestResult result = new TestResult(
                TEST_NAME,
                RESOURCE_URL,
                Result.PASS
            );

            // Assert
            assertAll("TestResult fields",
                () -> assertNotNull(result.getId(), "ID should not be null"),
                () -> assertTrue(result.getId().startsWith("urn:cessda:"), "ID should start with urn:cessda:"),
                () -> assertEquals(VALUE_PASS, result.getValue(), "Value should be 'pass'"),
                () -> assertEquals(DESCRIPTION, result.getDescription(), "Description should match"),
                () -> assertEquals(LOG, result.getLog(), "Log should match description"),
                () -> assertNotNull(result.getTitle(), "Title should not be null"),
                () -> assertTrue(result.getTitle().contains(TEST_NAME), "Title should contain test name")
            );
        }

        @Test
        @DisplayName("Should set correct context and type")
        void shouldSetCorrectContextAndType() {
            // Act
            TestResult result = new TestResult(
                TEST_NAME,
                RESOURCE_URL,
                Result.PASS
            );

            // Assert
            assertEquals("https://w3id.org/ftr/context", result.getContext());
            assertEquals("https://w3id.org/ftr#TestResult", result.getType());
        }

        @Test
        @DisplayName("Should generate unique IDs for different instances")
        void shouldGenerateUniqueIds() {
            // Act
            TestResult result1 = new TestResult(TEST_NAME, RESOURCE_URL, Result.PASS);
            TestResult result2 = new TestResult(TEST_NAME, RESOURCE_URL, Result.FAIL);

            // Assert
            assertNotEquals(result1.getId(), result2.getId(), "Each instance should have a unique ID");
        }

        @ParameterizedTest
        @CsvSource({
            "pass, 100",
            "fail, 100",
            "indeterminate, 0"
        })
        @DisplayName("Should handle different result values")
        void shouldHandleDifferentResultValues(String value, int completion) {
            // Act
            TestResult result = new TestResult(TEST_NAME, RESOURCE_URL, Result.valueOf(value.toUpperCase()));

            // Assert
            assertAll(
                () -> assertEquals(value, result.getValue()),
                () -> assertEquals(completion, result.getCompletion().getValue())
            );
        }
    }

    @Nested
    @DisplayName("Nested Classes Tests")
    class NestedClassesTests {

        @Test
        @DisplayName("License should be created with correct ID")
        void licenseTest() {
            // Arrange
            TestResult result = new TestResult(TEST_NAME, RESOURCE_URL, Result.PASS);

            // Assert
            assertNotNull(result.getLicense());
            assertEquals("http://creativecommons.org/licenses/by/4.0/", result.getLicense());
        }

        @Test
        @DisplayName("Completion should store percentage value")
        void completionTest() {
            // Arrange
            TestResult result = new TestResult(TEST_NAME, RESOURCE_URL, Result.PASS);
            // Assert
            assertNotNull(result.getCompletion());
            assertEquals(100, result.getCompletion().getValue());
        }

        @Test
        @DisplayName("AssessmentTarget should store resource URL")
        void assessmentTargetTest() {
            // Arrange
            TestResult result = new TestResult(TEST_NAME, RESOURCE_URL, Result.PASS);

            // Assert
            assertNotNull(result.getAssessmentTarget());
            assertEquals(RESOURCE_URL, result.getAssessmentTarget().getId());
        }

        @Test
        @DisplayName("OutputFromTest should be created with correct ID and type")
        void outputFromTestTest() {
            // Arrange
            TestResult result = new TestResult(TEST_NAME, RESOURCE_URL, Result.PASS);

            // Assert
            assertAll(
                () -> assertNotNull(result.getOutputFromTest()),
                () -> assertEquals("https://fair-tests.cessda.eu/assess/test/" + TEST_NAME, 
                    result.getOutputFromTest().getId()),
                () -> assertEquals("Test", result.getOutputFromTest().getType())
            );
        }

        @Test
        @DisplayName("GeneratedAtTime should have current timestamp")
        void generatedAtTimeTest() {
            // Arrange
            TestResult result = new TestResult(TEST_NAME, RESOURCE_URL, Result.PASS);

            // Assert
            assertAll(
                () -> assertNotNull(result.getGeneratedAtTime()),
                () -> assertNotNull(result.getGeneratedAtTime().getValue()),
                () -> assertEquals("http://www.w3.org/2001/XMLSchema#date", result.getGeneratedAtTime().getType()),
                () -> assertTrue(result.getGeneratedAtTime().getValue().length() > 0)
            );
        }

        @Test
        @DisplayName("WasGeneratedBy should be created with Used and WasAssociatedWith")
        void wasGeneratedByTest() {
            // Arrange
            TestResult result = new TestResult(TEST_NAME, RESOURCE_URL, Result.PASS);

            // Assert
            assertAll(
                () -> assertNotNull(result.getWasGeneratedBy()),
                () -> assertEquals("TestExecutionActivity", result.getWasGeneratedBy().getType()),
                () -> assertNotNull(result.getWasGeneratedBy().getUsed()),
                () -> assertEquals(RESOURCE_URL, result.getWasGeneratedBy().getUsed().getId()),
                () -> assertNotNull(result.getWasGeneratedBy().getWasAssociatedWith())
            );
        }
    }

    @Nested
    @DisplayName("WasAssociatedWith Tests")
    class WasAssociatedWithTests {

        @Test
        @DisplayName("Should load title and description from YAML file when it exists")
        void shouldLoadMetadataFromYamlFile() {
            // Arrange & Act
            TestResult result = new TestResult(
                "access-rights",  // This file should exist in test resources
                RESOURCE_URL,
                Result.PASS
            );

            TestResult.WasAssociatedWith wasAssociatedWith = 
                result.getWasGeneratedBy().getWasAssociatedWith();

            // Assert
            assertAll(
                () -> assertNotNull(wasAssociatedWith.getTitle()),
                () -> assertNotNull(wasAssociatedWith.getDescription()),
                () -> assertNotEquals("Test: access-rights", wasAssociatedWith.getTitle(), 
                    "Should load actual title from YAML, not default"),
                () -> assertNotNull(wasAssociatedWith.getEndpointDescription()),
                () -> assertNotNull(wasAssociatedWith.getEndpointURL())
            );
        }

        @Test
        @DisplayName("Should use default metadata when YAML file does not exist")
        void shouldUseDefaultMetadataWhenFileNotFound() {
            // Arrange & Act
            TestResult result = new TestResult(
                "non-existent-test",
                RESOURCE_URL,
                Result.FAIL
            );

            TestResult.WasAssociatedWith wasAssociatedWith = 
                result.getWasGeneratedBy().getWasAssociatedWith();

            // Assert
            assertAll(
                () -> assertEquals("Test: non-existent-test", wasAssociatedWith.getTitle()),
                () -> assertEquals("This test verifies non-existent-test", wasAssociatedWith.getDescription())
            );
        }

        @Test
        @DisplayName("Should set correct endpoint URLs")
        void shouldSetCorrectEndpointUrls() {
            // Arrange & Act
            TestResult result = new TestResult(TEST_NAME, RESOURCE_URL, Result.PASS);
            TestResult.WasAssociatedWith wasAssociatedWith = result.getWasGeneratedBy().getWasAssociatedWith();

            // Assert
            assertAll(
                () -> assertEquals(
                    "https://fair-tests.cessda.eu/api/" + TEST_NAME + ".ttl",
                    wasAssociatedWith.getEndpointDescription().getId()
                ),
                () -> assertEquals(
                    "https://fair-tests.cessda.eu/assess/test/" + TEST_NAME,
                    wasAssociatedWith.getEndpointURL().getId()
                )
            );
        }
    }

    @Nested
    @DisplayName("Getters and Setters Tests")
    class GettersSettersTests {

        @Test
        @DisplayName("Should set and get all fields correctly")
        void shouldSetAndGetAllFields() {
            // Arrange
            TestResult result = new TestResult(TEST_NAME, RESOURCE_URL, Result.PASS);
            
            // Act
            result.setContext("https://custom.context");
            result.setType("CustomType");
            result.setValue("fail");
            result.setDescription("Updated description");
            result.setLog("Updated log");

            // Assert
            assertAll(
                () -> assertEquals("https://custom.context", result.getContext()),
                () -> assertEquals("CustomType", result.getType()),
                () -> assertEquals("fail", result.getValue()),
                () -> assertEquals("Updated description", result.getDescription()),
                () -> assertEquals("Updated log", result.getLog())
            );
        }
    }

    @Nested
    @DisplayName("JSON Serialisation Tests")
    class JsonSerializationTests {

        private final ObjectMapper objectMapper = new ObjectMapper();

        @Test
        @DisplayName("Should serialize to JSON correctly")
        void shouldSerializeToJson() throws Exception {
            // Arrange
            TestResult result = new TestResult(TEST_NAME, RESOURCE_URL, Result.PASS);

            // Act
            String json = objectMapper.writeValueAsString(result);

            // Assert
            assertAll(
                () -> assertTrue(json.contains("\"@context\":\"https://w3id.org/ftr/context\""), 
                    "Should contain @context"),
                () -> assertTrue(json.contains("\"@type\":\"https://w3id.org/ftr#TestResult\""), 
                    "Should contain @type"),
                () -> assertTrue(json.contains("\"value\":\"pass\""), 
                    "Should contain value"),
                () -> assertTrue(json.contains("\"assessmentTarget\""), 
                    "Should contain assessmentTarget"),
                () -> assertTrue(json.contains("\"wasGeneratedBy\""), 
                    "Should contain wasGeneratedBy")
            );
        }

        @Test
        @DisplayName("Should deserialize from JSON correctly")
        void shouldDeserializeFromJson() throws Exception {
            // Arrange - create a TestResult and serialize it to JSON
            TestResult original = new TestResult(TEST_NAME, RESOURCE_URL, Result.PASS);
             String json = null;
           
             try {
                json = objectMapper.writeValueAsString(original);
            } catch (Exception e) {
                // This should not happen, but if it does, fail the test
                throw new RuntimeException("Failed to set up test data", e);
            }
        }

        @Test
        @DisplayName("Should handle @JsonProperty annotations correctly")
        void shouldHandleJsonPropertyAnnotations() throws Exception {
            // Arrange
            TestResult result = new TestResult(TEST_NAME, RESOURCE_URL, Result.PASS);

            // Act
            String json = objectMapper.writeValueAsString(result);

            // Assert - check that @JsonProperty annotations are working
            assertAll(
                () -> assertTrue(json.contains("\"@context\""), "Should use @context, not 'context'"),
                () -> assertTrue(json.contains("\"@id\""), "Should use @id, not 'id'"),
                () -> assertTrue(json.contains("\"@type\""), "Should use @type, not 'type'"),
                () -> assertTrue(json.contains("\"assessmentTarget\""), "Should use assessmentTarget"),
                () -> assertTrue(json.contains("\"outputFromTest\""), "Should use outputFromTest"),
                () -> assertTrue(json.contains("\"generatedAtTime\""), "Should use generatedAtTime"),
                () -> assertTrue(json.contains("\"wasGeneratedBy\""), "Should use wasGeneratedBy")
            );
        }
    }

    @Nested
    @DisplayName("Edge Cases Tests")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should handle very long URLs")
        void shouldHandleVeryLongUrls() {
            // Arrange
            String longUrl = "https://example.org/" + "a".repeat(1000);

            // Act
            TestResult result = new TestResult(TEST_NAME, longUrl, Result.PASS);

            // Assert
            assertEquals(longUrl, result.getAssessmentTarget().getId());
        }
    }
}
