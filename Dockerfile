#
# Copyright © 2025 CESSDA ERIC (support@cessda.eu)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# ===== Stage 1: Build =====
FROM eclipse-temurin:21-jdk AS builder

# Define environment variables
WORKDIR /opt/cessda/fair-tests

# Copy Maven Wrapper
COPY mvnw .
COPY .mvn/wrapper/maven-wrapper.properties .mvn/wrapper/maven-wrapper.properties

# Copy Maven project files
COPY pom.xml .

# Pre-fetch dependencies for faster incremental builds
RUN ./mvnw dependency:go-offline -B

# Copy the application source code
COPY . .

# Build the JAR
RUN ./mvnw clean verify -DskipTests

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /opt/cessda/fair-tests

# Copy the built JAR from the build stage
COPY --from=builder /opt/cessda/fair-tests/target/*-with-dependencies.jar fair-tests.jar

# Entrypoint — run the Java class with the CDC URL passed as an argument
ENTRYPOINT ["java", "-jar", "/opt/cessda/fair-tests/fair-tests.jar"]

