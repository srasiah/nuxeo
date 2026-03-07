/*
 * (C) Copyright 2015-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */
package org.nuxeo.audit.io;

import static org.junit.Assert.assertTrue;
import static org.nuxeo.common.utils.DateUtils.formatISODateTime;
import static org.nuxeo.common.utils.DateUtils.parseISODateTime;
import static org.nuxeo.common.utils.DateUtils.toDate;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriterTest;
import org.nuxeo.ecm.core.io.marshallers.json.JsonAssert;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.platform.test.UserManagerFeature;
import org.nuxeo.runtime.test.runner.Features;

@Features(UserManagerFeature.class) // for testWhenPrincipalFetcherIsProvided test
public class LogEntryJsonWriterTest extends AbstractJsonWriterTest.External<LogEntryJsonWriter, LogEntry> {

    @Test
    public void test() throws Exception {
        var eventDate = toDate(parseISODateTime("2024-08-05T17:10:00.000+02:00"));
        var extendedDate = toDate(parseISODateTime("2024-08-05T17:00:00.000+02:00"));
        var logEntry = LogEntry.builder("eventIdForTests", eventDate)
                               .id(1L)
                               .principalName("test")
                               .logDate(eventDate)
                               .docUUID("bd40cb24-73ea-4d4d-9445-4ed5fcad96c7")
                               .docPath("/some-doc")
                               .docType("File")
                               .category("eventDocumentCategory")
                               .comment("a comment")
                               .docLifeCycle("default")
                               .repositoryId("test")
                               .extended("string", "some string")
                               .extended("date", extendedDate)
                               .extended("boolean", true)
                               .extended("number", 1)
                               .extended("array", List.of("child 0", "child 1"))
                               .extended("object", Map.of("string", "another string", "boolean", false))
                               .build();
        JsonAssert json = jsonAssert(logEntry);
        json.properties(14);
        // order is the same as in writer
        json.has("entity-type").isEquals("logEntry");
        json.has("id").isEquals(1L);
        json.has("category").isEquals("eventDocumentCategory");
        json.has("principalName").isEquals("test");
        json.hasNot("principal");
        json.has("comment").isEquals("a comment");
        json.has("docLifeCycle").isEquals("default");
        json.has("docPath").isEquals("/some-doc");
        json.has("docType").isEquals("File");
        json.has("docUUID").isEquals("bd40cb24-73ea-4d4d-9445-4ed5fcad96c7");
        json.has("eventId").isEquals("eventIdForTests");
        json.has("repositoryId").isEquals("test");
        json.has("eventDate").isEquals(formatISODateTime(eventDate));
        json.has("logDate").isEquals(formatISODateTime(eventDate));
        json.has("extended").properties(6);
        json.has("extended.string").isEquals("some string");
        json.has("extended.date").isEquals(formatISODateTime(extendedDate));
        json.has("extended.boolean").isEquals(true);
        json.has("extended.number").isEquals(1);
        json.has("extended.array").isArray().length(2).contains("child 0", "child 1");
        json.has("extended.object").isObject().properties(2);
        json.has("extended.object.string").isEquals("another string");
        json.has("extended.object.boolean").isEquals(false);
    }

    @Test
    public void testArrayInExtendedInfo() throws IOException {
        var logEntry = LogEntry.builder("eventIdForTests", new Date())
                               .extended("params", new Object[] { "a simple string" })
                               .build();
        JsonAssert json = jsonAssert(logEntry);
        json.has("extended").properties(1).has("params").isArray().contains("a simple string");
    }

    @Test
    public void testIntegerArrayInExtendedInfo() throws IOException {
        var logEntry = LogEntry.builder("eventIdForTests", new Date())
                               .extended("params", new Integer[] { 1, 2, 3 })
                               .build();
        JsonAssert json = jsonAssert(logEntry);
        json.has("extended").properties(1).has("params").isArray().contains(1, 2, 3);
    }

    @Test
    public void testEmptyArrayInExtendedInfo() throws IOException {
        var logEntry = LogEntry.builder("eventIdForTests", new Date()).extended("params", new Integer[] {}).build();
        JsonAssert json = jsonAssert(logEntry);
        json.has("extended").properties(1).has("params").isArray().length(0);
    }

    @Test
    public void testBlobArrayInExtendedInfo() throws IOException {
        var logEntry = LogEntry.builder("eventIdForTests", new Date())
                               .extended("params", new Blob[] { Blobs.createBlob("a simple string blob") })
                               .build();
        JsonAssert json = jsonAssert(logEntry);
        json.has("extended").properties(0);
    }

    @Test
    public void testBlobListInExtendedInfo() throws IOException {
        var logEntry = LogEntry.builder("eventIdForTests", new Date())
                               .extended("params", List.of(Blobs.createBlob("a simple string blob")))
                               .build();
        JsonAssert json = jsonAssert(logEntry);
        json.has("extended").properties(0);
    }

    @Test
    public void testSingleBlobInExtendedInfo() throws IOException {
        var logEntry = LogEntry.builder("eventIdForTests", new Date())
                               .extended("params", Blobs.createBlob("a simple string blob"))
                               .build();
        JsonAssert json = jsonAssert(logEntry);
        assertTrue(json.has("extended")
                       .properties(1)
                       .has("params")
                       .toString()
                       .startsWith("\"org.nuxeo.ecm.core.api.impl.blob.StringBlob@"));
    }

    @Test
    public void testMapInExtendedInfo() throws IOException {
        Date now = new Date();
        var paramsMap = new HashMap<String, Object>();
        paramsMap.put("String", "abcde");
        paramsMap.put("Date", now);
        paramsMap.put("Boolean", false);
        paramsMap.put("Integer", 1);
        paramsMap.put("Double", 2.0);
        paramsMap.put("Blob", Blobs.createBlob("Some blob"));
        var logEntry = LogEntry.builder("eventIdForTests", new Date()).extended("params", paramsMap).build();
        JsonAssert json = jsonAssert(logEntry);

        JsonAssert params = json.has("extended").properties(1).has("params").isObject();
        params.has("String").isEquals("abcde");
        params.has("Date").isEquals(formatISODateTime(now));
        params.has("Integer").isEquals(1);
        params.has("Double").isEquals(2.0, 0.0);
        params.has("Boolean").isEquals(false);
        params.hasNot("Blob");
    }

    @Test
    public void testWhenPrincipalFetcherIsProvided() throws IOException {
        var logEntry = LogEntry.builder("eventIdForTests", new Date()).principalName("Administrator").build();
        RenderingContext ctx = RenderingContext.CtxBuilder.fetch("logEntry", "principal").get();
        JsonAssert json = jsonAssert(logEntry, ctx);
        json.has("entity-type").isEquals("logEntry");
        json.has("principalName").isEquals("Administrator");
        var principalJson = json.has("principal").isObject();
        principalJson.has("entity-type").isEquals("user");
        principalJson.has("id").isText();
        principalJson.has("properties").isObject().has("username").isEquals("Administrator");
    }
}
