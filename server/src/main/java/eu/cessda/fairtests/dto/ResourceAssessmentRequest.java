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

package eu.cessda.fairtests.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * DTO for resource assessment requests, containing the resource identifier to be assessed.
 * Includes validation annotations to ensure the resource identifier is provided and is a valid URL.
 * The JSON property name is specified to match the expected input from the client.
 * This class is used in the AssessmentController to receive the resource identifier for FAIR test assessments.
 * The class includes a default constructor for deserialisation and a parameterised constructor for convenience,
 * as well as getters and setters for the resourceIdentifier field.
 * The validation annotations ensure that the resource_identifier field is not blank and follows a simple pattern
 * to check if it starts with http:// or https://, providing basic validation for the input URL.
 * The @JsonProperty annotation ensures that the JSON field name matches the expected input from the client,
 * allowing for seamless deserialisation of the request body into this DTO.
 * Overall, this class serves as a structured way to receive and validate the resource identifier for FAIR test
 * assessments in the AssessmentController.
 */
public class ResourceAssessmentRequest {
    
    /* Validation annotations to ensure resource_identifier is provided and is a valid URL */
    @NotBlank(message = "resource_identifier is required")

    /* Simple regex to check if the string starts with http:// or https:// */
    @Pattern(regexp = "^https?://.*", message = "resource_identifier must be a valid HTTP(S) URL")

    /* JSON property name to match the expected input from the client */
    @JsonProperty("resource_identifier")

    /* The resource identifier (URL) to be assessed */
    private String resourceIdentifier;

    /* Default constructor for deserialisation and parameterised constructor for convenience */
    public ResourceAssessmentRequest() {
    }
    /* Constructor to create a ResourceAssessmentRequest with the given resource identifier */
    public ResourceAssessmentRequest(String resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier;
    }

    /* Getters and setters for the resourceIdentifier field */
    public String getResourceIdentifier() {
        return resourceIdentifier;
    }

    /* Setter for resourceIdentifier to allow setting the value during deserialisation */
    public void setResourceIdentifier(String resourceIdentifier) {
        this.resourceIdentifier = resourceIdentifier;
    }
}