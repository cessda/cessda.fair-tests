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

import org.junit.jupiter.api.Test;

class FormatSnifferTest {

    private final FormatSniffer sniffer = new FormatSniffer();

    @Test void detectsJsonObject()  { assertEquals(FormatSniffer.Format.JSON_OBJECT, sniffer.detect("{ \"id\": 1 }")); }
    @Test void detectsJsonArray()   { assertEquals(FormatSniffer.Format.JSON_ARRAY,  sniffer.detect("[{\"id\":1}]")); }
    @Test void detectsXmlDecl()     { assertEquals(FormatSniffer.Format.XML,         sniffer.detect("<?xml version=\"1.0\"?><root/>")); }
    @Test void detectsXmlTag()      { assertEquals(FormatSniffer.Format.XML,         sniffer.detect("<codeBook><docDscr/></codeBook>")); }
    @Test void detectsHtmlDoctype() { assertEquals(FormatSniffer.Format.HTML,        sniffer.detect("<!DOCTYPE html><html/>")); }
    @Test void detectsHtmlTag()     { assertEquals(FormatSniffer.Format.HTML,        sniffer.detect("<html lang=\"en\"></html>")); }
    @Test void detectsCsv()         { assertEquals(FormatSniffer.Format.CSV,         sniffer.detect("id,name,value\n1,foo,bar")); }
    @Test void detectsUnknown()     { assertEquals(FormatSniffer.Format.UNKNOWN,     sniffer.detect("plain text")); }
}