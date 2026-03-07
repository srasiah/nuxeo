/*
 * (C) Copyright 2016-2025 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nicolas Chapurlat
 *     Thomas Roger
 */
package org.nuxeo.ecm.core.io.marshallers.json.document;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_ARRAY_DOUBLE_PROP;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_ARRAY_INTEGER_PROP;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_ARRAY_LONG_PROP;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_ARRAY_STRING_PROP;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_BOOLEAN_PROP;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_COMPLEX_PROP;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_COMPLEX_STRING_PROP;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_DATE_PROP;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_DOC_TYPE;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_DOUBLE_PROP;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_INTEGER_PROP;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_LONG_PROP;
import static org.nuxeo.ecm.core.schema.test.CommonDocumentConstants.COMMON_STRING_PROP;

import java.io.IOException;
import java.util.Calendar;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.impl.SimpleDocumentModel;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriterTest;
import org.nuxeo.ecm.core.io.marshallers.json.JsonAssert;
import org.nuxeo.ecm.core.io.marshallers.json.JsonFactoryProvider;
import org.nuxeo.ecm.core.io.registry.MarshallingException;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext.CtxBuilder;
import org.nuxeo.ecm.core.schema.utils.DateParser;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;

@Features(CoreFeature.class)
@Deploy("org.nuxeo.ecm.core.test.tests:OSGI-INF/defaultvalue-docTypes.xml")
public class DocumentModelJsonReaderTest extends AbstractJsonWriterTest.Local<DocumentModelJsonWriter, DocumentModel> {

    private DocumentModel document;

    @Inject
    private CoreSession session;

    @Before
    public void setup() {
        document = session.createDocumentModel("/", "myNote", "Note");
        document.setPropertyValue("dc:title", "My Note");
        document = session.createDocument(document);
    }

    @Test
    public void testDefault() throws IOException {
        RenderingContext renderingContext = CtxBuilder.get();
        renderingContext.setExistingSession(session);
        DocumentModelJsonReader reader = registry.getInstance(renderingContext, DocumentModelJsonReader.class);
        JsonAssert json = jsonAssert(document);
        DocumentModel doc = reader.read(json.getNode());
        assertTrue(doc instanceof DocumentModelImpl);
        assertEquals("myNote", doc.getName());
        assertEquals("My Note", doc.getPropertyValue("dc:title"));
    }

    @Test
    public void testReadSchemaWithoutPrefix() throws IOException {
        String noteJson = """
                {
                  "entity-type": "document",
                  "type": "Note",
                  "name": "aNote",
                  "properties": {
                    "dc:title": "A note",
                    "note:note": "note content"
                  }
                }
                """;

        DocumentModelJsonReader reader = registry.getInstance(CtxBuilder.get(), DocumentModelJsonReader.class);
        DocumentModel noteDocument;
        try (JsonParser jp = JsonFactoryProvider.get().createParser(noteJson)) {
            JsonNode jn = jp.readValueAsTree();
            noteDocument = reader.read(jn);
        }
        assertNotNull(noteDocument);
        assertTrue(noteDocument instanceof SimpleDocumentModel);
        assertEquals("note content", noteDocument.getPropertyValue("note:note"));
    }

    @Test
    public void testScalarCreatedWithDefaultValue() throws IOException {
        // given a doc json with a property with a default value not modified
        String noteJson = """
                {
                  "entity-type": "document",
                  "type": "DocDefaultValue",
                  "name": "aDoc"
                }
                """;

        // when I parse it it
        DocumentModelJsonReader reader = registry.getInstance(CtxBuilder.get(), DocumentModelJsonReader.class);
        DocumentModel noteDocument;
        try (JsonParser jp = JsonFactoryProvider.get().createParser(noteJson)) {
            JsonNode jn = jp.readValueAsTree();
            noteDocument = reader.read(jn);
        }

        // then the default value must be set
        String[] schemas = noteDocument.getSchemas();
        assertEquals(1, schemas.length);
        assertEquals("defaultvalue", schemas[0]);
        Map<String, Object> values = noteDocument.getProperties("defaultvalue");
        assertNull(values.get("dv:simpleWithoutDefault"));
        assertEquals("value", values.get("dv:simpleWithDefault"));
    }

    @Test
    public void testScalarSetOnNullDontSetDefaultValueAgain() throws IOException {
        // given a doc json with a property with a default value set to null
        String noteJson = """
                {
                  "entity-type": "document",
                  "type": "DocDefaultValue",
                  "name": "aDoc",
                  "properties": {
                    "dv:simpleWithDefault": null
                  }
                }
                """;

        // when I parse it it
        DocumentModelJsonReader reader = registry.getInstance(CtxBuilder.get(), DocumentModelJsonReader.class);
        DocumentModel noteDocument;
        try (JsonParser jp = JsonFactoryProvider.get().createParser(noteJson)) {
            JsonNode jn = jp.readValueAsTree();
            noteDocument = reader.read(jn);
        }

        // then the property with the default value must null
        Map<String, Object> values = noteDocument.getProperties("defaultvalue");
        assertNull(values.get("dv:simpleWithDefault"));
    }

    @Test
    public void testMultiCreatedWithDefaultValue() throws IOException {
        // given a doc json with a property with a default value not modified
        String noteJson = """
                {
                  "entity-type": "document",
                  "type": "DocDefaultValue",
                  "name": "aDoc"
                }
                """;

        // when I parse it
        DocumentModelJsonReader reader = registry.getInstance(CtxBuilder.get(), DocumentModelJsonReader.class);
        DocumentModel noteDocument;
        try (JsonParser jp = JsonFactoryProvider.get().createParser(noteJson)) {
            JsonNode jn = jp.readValueAsTree();
            noteDocument = reader.read(jn);
        }

        // then the default value must be set
        String[] schemas = noteDocument.getSchemas();
        assertEquals(1, schemas.length);
        assertEquals("defaultvalue", schemas[0]);
        Map<String, Object> values = noteDocument.getProperties("defaultvalue");
        assertNull(values.get("dv:multiWithoutDefault"));
        assertArrayEquals(new String[] { "value1", "value2" }, (String[]) values.get("dv:multiWithDefault"));
    }

    @Test
    public void testMultiSetOnNullDontSetDefaultValueAgain() throws IOException {
        // given a doc json with a property with a default value not modified
        String noteJson = """
                {
                  "entity-type": "document",
                  "type": "DocDefaultValue",
                  "name": "aDoc",
                  "properties": {
                    "dv:multiWithDefault": null
                  }
                }
                """;

        // when I parse it
        DocumentModelJsonReader reader = registry.getInstance(CtxBuilder.get(), DocumentModelJsonReader.class);
        DocumentModel noteDocument;
        try (JsonParser jp = JsonFactoryProvider.get().createParser(noteJson)) {
            JsonNode jn = jp.readValueAsTree();
            noteDocument = reader.read(jn);
        }

        // then the property with the default value must null
        Map<String, Object> values = noteDocument.getProperties("defaultvalue");
        assertNull(values.get("dv:multiWithDefault"));
    }

    // NXP-30680
    // NXP-30806
    // NXP-31199
    @Test
    public void testPropertyValuePossibilities() throws IOException {
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:string\": \"Some string\"}", COMMON_STRING_PROP,
                "Some string");
        testPropertyWithAcceptedRepresentationWorks(String.format("{\"tcs:string\": %s}", Long.MAX_VALUE),
                COMMON_STRING_PROP, String.valueOf(Long.MAX_VALUE));
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:string\": 1234}", COMMON_STRING_PROP, "1234");
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:string\": 12.34}", COMMON_STRING_PROP, "12.34");
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:string\": true}", COMMON_STRING_PROP, "true");
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:string\": null}", COMMON_STRING_PROP, null);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:string\": \"\"}", COMMON_STRING_PROP, "");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:string\": {\"key\": true}}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:string\": [0]}");

        // numbers are always handled as Long in Nuxeo
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:integer\": 1234}", COMMON_INTEGER_PROP, 1234L);
        testPropertyWithAcceptedRepresentationWorks(String.format("{\"tcs:integer\": %s}", Long.MAX_VALUE),
                COMMON_INTEGER_PROP, Long.MAX_VALUE);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:integer\": \"1234\"}", COMMON_INTEGER_PROP, 1234L);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:integer\": null}", COMMON_INTEGER_PROP, null);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:integer\": \"\"}", COMMON_INTEGER_PROP, null);
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:integer\": \"Some string\"}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:integer\": \"12.34\"}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:integer\": true}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:integer\": 12.34}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:integer\": {\"key\": true}}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:integer\": [0]}");

        testPropertyWithAcceptedRepresentationWorks(String.format("{\"tcs:long\": %s}", Long.MAX_VALUE),
                COMMON_LONG_PROP, Long.MAX_VALUE);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:long\": 1234}", COMMON_LONG_PROP, 1234L);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:long\": \"1234\"}", COMMON_LONG_PROP, 1234L);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:long\": null}", COMMON_LONG_PROP, null);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:long\": \"\"}", COMMON_LONG_PROP, null);
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:long\": \"Some string\"}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:long\": \"12.34\"}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:long\": true}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:long\": 12.34}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:long\": {\"key\": true}}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:long\": [0]}");

        testPropertyWithAcceptedRepresentationWorks("{\"tcs:boolean\": true}", COMMON_BOOLEAN_PROP, true);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:boolean\": \"true\"}", COMMON_BOOLEAN_PROP, true);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:boolean\": \"Some string\"}", COMMON_BOOLEAN_PROP, false);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:boolean\": 1234}", COMMON_BOOLEAN_PROP, true);
        testPropertyWithAcceptedRepresentationWorks(String.format("{\"tcs:boolean\": %s}", Long.MAX_VALUE),
                COMMON_BOOLEAN_PROP, true);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:boolean\": 1}", COMMON_BOOLEAN_PROP, true);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:boolean\": 0}", COMMON_BOOLEAN_PROP, false);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:boolean\": null}", COMMON_BOOLEAN_PROP, null);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:boolean\": \"\"}", COMMON_BOOLEAN_PROP, null);
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:boolean\": 12.34}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:boolean\": {\"key\": true}}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:boolean\": [0]}");

        testPropertyWithAcceptedRepresentationWorks("{\"tcs:double\": 1234}", COMMON_DOUBLE_PROP, 1234.0);
        testPropertyWithAcceptedRepresentationWorks(String.format("{\"tcs:double\": %s}", Long.MAX_VALUE),
                COMMON_DOUBLE_PROP, Long.valueOf(Long.MAX_VALUE).doubleValue());
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:double\": 12.34}", COMMON_DOUBLE_PROP, 12.34);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:double\": \"12.34\"}", COMMON_DOUBLE_PROP, 12.34);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:double\": null}", COMMON_DOUBLE_PROP, null);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:double\": \"\"}", COMMON_DOUBLE_PROP, null);
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:double\": \"Some string\"}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:double\": true}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:double\": {\"key\": true}}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:double\": [0]}");

        var date = DateParser.parseW3CDateTime("2022-01-18T17:20:21.123");
        var cal = Calendar.getInstance();
        cal.setTime(date);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:date\": \"2022-01-18T17:20:21.123\"}", COMMON_DATE_PROP,
                cal);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:date\": null}", COMMON_DATE_PROP, null);
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:date\": \"\"}", COMMON_DATE_PROP, null);
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:date\": \"Some string\"}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:date\": true}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:date\": 1234}");
        testPropertyWithWrongRepresentationThrowsException(String.format("{\"tcs:date\": %s}", Long.MAX_VALUE));
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:date\": 12.34}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:date\": {\"key\": true}}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:date\": [0]}");

        testPropertyWithAcceptedRepresentationWorks("{\"tcs:arrayString\": [\"Some string\"]}",
                COMMON_ARRAY_STRING_PROP, new String[] { "Some string" });
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:arrayString\": null}", COMMON_ARRAY_STRING_PROP, null);
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:arrayString\": \"Some string\"}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:arrayString\": true}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:arrayString\": 1234}");
        testPropertyWithWrongRepresentationThrowsException(String.format("{\"tcs:arrayString\": %s}", Long.MAX_VALUE));
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:arrayString\": 12.34}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcs:arrayString\": {\"key\": true}}");
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:arrayLong\": []}", COMMON_ARRAY_LONG_PROP, new Long[] {});
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:arrayLong\": [1, 2, 3]}", COMMON_ARRAY_LONG_PROP,
                new Long[] { 1L, 2L, 3L });
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:arrayLong\": [2147483648, 9223372036854775807]}",
                COMMON_ARRAY_LONG_PROP, new Long[] { 2147483648L, 9223372036854775807L });
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:arrayInteger\": []}", COMMON_ARRAY_INTEGER_PROP,
                new Long[] {});
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:arrayInteger\": [4, 5, 6]}", COMMON_ARRAY_INTEGER_PROP,
                new Long[] { 4L, 5L, 6L });
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:arrayDouble\": []}", COMMON_ARRAY_DOUBLE_PROP,
                new Double[] {});
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:arrayDouble\": [7, 8, 9]}", COMMON_ARRAY_DOUBLE_PROP,
                new Double[] { 7D, 8D, 9D });
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:arrayDouble\": [7.8, 8.8, 9.8]}", COMMON_ARRAY_DOUBLE_PROP,
                new Double[] { 7.8D, 8.8D, 9.8D });
        testPropertyWithAcceptedRepresentationWorks("{\"tcs:arrayDouble\": [9223372036854775807]}",
                COMMON_ARRAY_DOUBLE_PROP, new Double[] { 9223372036854775807D });

        // complex
        testPropertyWithAcceptedRepresentationWorks("{\"tcc:complex\": {\"string\":\"foo\"}}",
                COMMON_COMPLEX_STRING_PROP, "foo");
        testPropertyWithAcceptedRepresentationWorks("{\"tcc:complex\": null}", COMMON_COMPLEX_PROP, Map.of());
        testPropertyWithWrongRepresentationThrowsException("{\"tcc:complex\": \"Some string\"}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcc:complex\": true}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcc:complex\": 1234}");
        testPropertyWithWrongRepresentationThrowsException(String.format("{\"tcc:complex\": %s}", Long.MAX_VALUE));
        testPropertyWithWrongRepresentationThrowsException("{\"tcc:complex\": 12.34}");
        testPropertyWithWrongRepresentationThrowsException("{\"tcc:complex\": [0]}");
    }

    protected void testPropertyWithWrongRepresentationThrowsException(String properties) throws IOException {
        String json = """
                {
                  "entity-type": "document",
                  "type": "%s",
                  "name": "myDoc",
                  "properties": %s
                }
                """.formatted(COMMON_DOC_TYPE, properties);
        try (JsonParser jp = JsonFactoryProvider.get().createParser(json)) {
            JsonNode jn = jp.readValueAsTree();

            DocumentModelJsonReader reader = registry.getInstance(CtxBuilder.get(), DocumentModelJsonReader.class);
            reader.read(jn);
            fail("Read should have failed due to wrong type");
        } catch (NuxeoException e) {
            assertTrue(e instanceof MarshallingException);
            assertTrue(e.getMessage().startsWith("Unable to deserialize property:"));
        }
    }

    protected void testPropertyWithAcceptedRepresentationWorks(String properties, String expectedProperty,
            Object expectedValue) throws IOException {
        String json = """
                {
                  "entity-type": "document",
                  "type": "%s",
                  "name": "myDoc",
                  "properties": %s
                }
                """.formatted(COMMON_DOC_TYPE, properties);
        try (JsonParser jp = JsonFactoryProvider.get().createParser(json)) {
            JsonNode jn = jp.readValueAsTree();

            DocumentModelJsonReader reader = registry.getInstance(CtxBuilder.get(), DocumentModelJsonReader.class);
            var doc = reader.read(jn);
            if (expectedValue instanceof Object[]) {
                assertArrayEquals((Object[]) expectedValue, (Object[]) doc.getPropertyValue(expectedProperty));
            } else {
                assertEquals(expectedValue, doc.getPropertyValue(expectedProperty));
            }
        }
    }
}
