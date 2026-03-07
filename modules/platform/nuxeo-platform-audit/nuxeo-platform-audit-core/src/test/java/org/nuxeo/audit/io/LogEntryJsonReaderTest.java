/*
 * (C) Copyright 2024-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     Kevin Leturc <kevin.leturc@hyland.com>
 */
package org.nuxeo.audit.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.common.utils.DateUtils.parseISODateTime;
import static org.nuxeo.common.utils.DateUtils.toDate;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonReaderTest;

/**
 * @since 2025.0
 */
public class LogEntryJsonReaderTest extends AbstractJsonReaderTest.Local<LogEntryJsonReader, LogEntry> {

    @Test
    public void testDefault() throws Exception {
        String entryString = """
                {
                  "entity-type": "logEntry",
                  "principalName": "test",
                  "eventId": "documentCreated",
                  "logDate": "2024-08-05T17:10:00.000+02:00",
                  "eventDate": "2024-08-05T17:10:00.000+02:00",
                  "docUUID": "bd40cb24-73ea-4d4d-9445-4ed5fcad96c7",
                  "docPath": "/some-doc",
                  "docType": "File",
                  "category": "eventDocumentCategory",
                  "comment": "a comment",
                  "docLifeCycle": "default",
                  "repositoryId": "default",
                  "extended": {
                    "string": "some string",
                    "date": "2024-08-05T17:00:00.000+02:00",
                    "boolean": true,
                    "number": 1,
                    "array": [
                      "child 0",
                      "child 1"
                    ],
                    "object": {
                      "string": "another string",
                      "boolean": false
                    }
                  }
                }
                """;
        LogEntry entry = asObject(entryString);
        assertEquals("test", entry.getPrincipalName());
        assertEquals("documentCreated", entry.getEventId());
        assertEquals(toDate(parseISODateTime("2024-08-05T17:10:00.000+02:00")), entry.getLogDate());
        assertEquals(toDate(parseISODateTime("2024-08-05T17:10:00.000+02:00")), entry.getEventDate());
        assertEquals("bd40cb24-73ea-4d4d-9445-4ed5fcad96c7", entry.getDocUUID());
        assertEquals("/some-doc", entry.getDocPath());
        assertEquals("File", entry.getDocType());
        assertEquals("eventDocumentCategory", entry.getCategory());
        assertEquals("a comment", entry.getComment());
        assertEquals("default", entry.getDocLifeCycle());
        assertEquals("default", entry.getRepositoryId());

        var extended = entry.getExtended();
        assertNotNull(extended);
        assertEquals("some string", entry.getExtendedValue("string"));
        assertEquals(toDate(parseISODateTime("2024-08-05T17:00:00.000+02:00")), entry.getExtendedValue("date"));
        assertEquals(Boolean.TRUE, entry.getExtendedValue("boolean"));
        assertEquals(Long.valueOf(1L), entry.getExtendedValue("number"));

        assertTrue(entry.getExtendedValue("array") instanceof List);
        List<String> extendedInfosArray = entry.getExtendedValue("array");
        assertEquals("child 0", extendedInfosArray.get(0));
        assertEquals("child 1", extendedInfosArray.get(1));

        assertTrue(entry.getExtendedValue("object") instanceof Map);
        Map<String, Object> extendedInfosObject = entry.getExtendedValue("object");
        assertEquals("another string", extendedInfosObject.get("string"));
        assertEquals(Boolean.FALSE, extendedInfosObject.get("boolean"));

    }
}
