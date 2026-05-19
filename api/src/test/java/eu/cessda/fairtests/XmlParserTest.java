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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class XmlParserTest {

    private XmlParser parser;
    private VocabularyService vocabulary;

    @BeforeEach
    void setUp() {
        parser = new XmlParser();
        vocabulary = mock(VocabularyService.class);
    }

    @Test
    void structuredMetadata_shouldPass_forSupportedNamespace() throws Exception {

        String xml = """
                <codeBook xmlns=\"ddi:codebook:2_5\">
                    <stdyDscr/>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.STRUCTURED_METADATA,
                input(xml),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    void structuredMetadata_shouldFail_forUnsupportedNamespace() throws Exception {

        String xml = """
                <codeBook xmlns=\"ddi:codebook:3_0\">
                    <stdyDscr/>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.STRUCTURED_METADATA,
                input(xml),
                vocabulary);

        assertEquals(Result.FAIL, result);
    }

    @Test
    void accessRights_shouldPass_whenApprovedTermContained() throws Exception {

        when(vocabulary.getApprovedAccessRightsTerms())
                .thenReturn(Set.of("open access"));

        String xml = """
                <codeBook xmlns=\"ddi:codebook:2_5\">
                    <conditions>
                        This dataset is available with Open Access conditions.
                    </conditions>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.ACCESS_RIGHTS,
                input(xml),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    void accessRights_shouldFail_whenNoApprovedTermMatches() throws Exception {

        when(vocabulary.getApprovedAccessRightsTerms())
                .thenReturn(Set.of("restricted"));

        String xml = """
                <codeBook xmlns=\"ddi:codebook:2_5\">
                    <conditions>
                        Publicly available dataset.
                    </conditions>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.ACCESS_RIGHTS,
                input(xml),
                vocabulary);

        assertEquals(Result.FAIL, result);
    }

    @Test
    void pid_shouldPass_whenAgencyMatchesApprovedSchema() throws Exception {

        when(vocabulary.getApprovedPidSchemas())
                .thenReturn(Set.of("doi"));

        String xml = """
                <codeBook xmlns=\"ddi:codebook:2_5\">
                    <IDNo agency=\"DOI\">10.1234/test</IDNo>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.PID,
                input(xml),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    void pid_shouldFail_whenAgencyDoesNotMatchApprovedSchema() throws Exception {

        when(vocabulary.getApprovedPidSchemas())
                .thenReturn(Set.of("handle"));

        String xml = """
                <codeBook xmlns=\"ddi:codebook:2_5\">
                    <IDNo agency=\"DOI\">10.1234/test</IDNo>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.PID,
                input(xml),
                vocabulary);

        assertEquals(Result.FAIL, result);
    }

    @Test
    void analysisUnit_shouldPass_usingDirectTextExtraction() throws Exception {

        when(vocabulary.getApprovedAnalysisUnitTerms())
                .thenReturn(Set.of("individual"));

        String xml = """
                <codeBook xmlns=\"ddi:codebook:2_5\">
                    <anlyUnit>
                        Individual
                        <nested>ignored</nested>
                    </anlyUnit>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.DDI_ANALYSIS_UNIT,
                input(xml),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    void collectionMode_shouldPass_withWhitespaceAndCaseDifferences() throws Exception {

        when(vocabulary.getApprovedCollectionModeTerms())
                .thenReturn(Set.of("face to face interview"));

        String xml = """
                <codeBook xmlns=\"ddi:codebook:2_5\">
                    <collMode>
                        FACE   TO    FACE INTERVIEW
                    </collMode>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.DDI_COLLECTION_MODE,
                input(xml),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    void provenance_shouldPass_whenCreatorExists() throws Exception {

        String xml = """
                <codeBook xmlns=\"ddi:codebook:2_5\">
                    <AuthEnty>CESSDA</AuthEnty>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.PROVENANCE,
                input(xml),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    void provenance_shouldFail_whenNoRelevantElementsExist() throws Exception {

        String xml = """
                <codeBook xmlns=\"ddi:codebook:2_5\">
                    <stdyDscr/>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.PROVENANCE,
                input(xml),
                vocabulary);

        assertEquals(Result.FAIL, result);
    }

    @Test
    void searchable_shouldPass_whenOaiMetadataExists() throws Exception {

        String xml = """
                <oai:record
                    xmlns:oai=\"http://www.openarchives.org/OAI/2.0/\"
                    xmlns=\"ddi:codebook:2_5\">

                    <oai:header>
                        <oai:identifier>
                            oai:test:123
                        </oai:identifier>
                    </oai:header>

                    <codeBook/>
                </oai:record>
                """;

        Result result = parser.runTest(
                TestType.SEARCHABLE,
                input(xml),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    void searchable_shouldFail_whenOaiMetadataMissing() throws Exception {

        String xml = """
                <codeBook xmlns=\"ddi:codebook:2_5\">
                    <stdyDscr/>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.SEARCHABLE,
                input(xml),
                vocabulary);

        assertEquals(Result.FAIL, result);
    }

    @Test
    void formalLanguage_shouldPass_forSupportedDdiNamespace() throws Exception {

        String xml = """
                <codeBook
                    xmlns=\"ddi:codebook:2_5\"
                    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"
                    xsi:schemaLocation=\"
                        ddi:codebook:2_5
                        https://example.org/ddi.xsd
                    \">
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.FORMAL_KR_LANGUAGE,
                input(xml),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    void formalLanguage_shouldFail_forMissingNamespace() throws Exception {

        String xml = """
                <codeBook>
                    <stdyDscr/>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.FORMAL_KR_LANGUAGE,
                input(xml),
                vocabulary);

        assertEquals(Result.FAIL, result);
    }

    @Test
    void elsstKeywords_shouldPass_whenVocabularyValidationPasses() throws Exception {

        when(vocabulary.validateElsstKeywords(
                anyList(),
                eq("en")))
                .thenReturn(Result.PASS);

        String xml = """
                <codeBook xmlns=\"ddi:codebook:2_5\">
                    <keyword
                        vocab=\"ELSST\"
                        vocabURI=\"https://elsst.cessda.eu/\"
                        xml:lang=\"en\">
                        Education
                    </keyword>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.ELSST_KEYWORDS,
                input(xml),
                vocabulary);

        assertEquals(Result.PASS, result);
    }

    @Test
    void elsstKeywords_shouldIndeterminate_whenLanguageMissing() throws Exception {

        String xml = """
                <codeBook xmlns=\"ddi:codebook:2_5\">
                    <keyword
                        vocab=\"ELSST\"
                        vocabURI=\"https://elsst.cessda.eu/\">
                        Education
                    </keyword>
                </codeBook>
                """;

        Result result = parser.runTest(
                TestType.ELSST_KEYWORDS,
                input(xml),
                vocabulary);

        assertEquals(Result.INDETERMINATE, result);
    }

    @Test
    void shouldThrowIOException_forMalformedXml() {

        String xml = "<codeBook><broken></codeBook>";

        assertThrows(IOException.class, () ->
                parser.runTest(
                        TestType.PROVENANCE,
                        input(xml),
                        vocabulary));
    }

    private InputStream input(String xml) {
        return new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8));
    }
}
