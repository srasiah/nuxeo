/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nour AL KOTOB
 */
package org.nuxeo.audit.io;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.nuxeo.audit.io.LogEntryJsonWriter.ENTITY_TYPE;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.LongFunction;

import org.junit.Test;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.ecm.core.io.marshallers.csv.AbstractCSVWriterTest;
import org.nuxeo.ecm.core.io.marshallers.csv.CSVAssert;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;

/**
 * @since 11.1
 */
public class LogEntryCSVWriterTest extends AbstractCSVWriterTest.External<LogEntryCSVWriter, LogEntry> {

    @Test
    public void testDefaultProperties() throws IOException {
        // No fetch param in the context, retrieve all default properties
        RenderingContext renderingCtx = RenderingContext.CtxBuilder.get();
        for (LogEntry entry : getLogEntries()) {
            CSVAssert csv = csvAssert(entry, renderingCtx);
            csv.has("id");
            csv.has("category").isEquals("categoryForTests");
            csv.has("principalName").isEquals("Administrator"); // NOSONAR
            csv.has("comment").isEquals("comment");
            csv.has("docLifeCycle").isEquals("project"); // NOSONAR
            csv.has("docPath").isEquals("/myFile"); // NOSONAR
            csv.has("docType").isEquals("File"); // NOSONAR
            csv.has("docUUID"); // NOSONAR
            csv.has("eventId").isEquals("eventIdForTests");
            csv.has("repositoryId").isEquals("test");
            csv.has("eventDate");
            csv.has("logDate");
        }
    }

    @Test
    public void testCustomProperties() throws IOException {
        // Fetching only specific properties via context param
        RenderingContext renderingCtx = RenderingContext.CtxBuilder.fetch(ENTITY_TYPE, "principalName", "docLifeCycle",
                "docPath", "docType", "docUUID").get();
        for (LogEntry entry : getLogEntries()) {
            CSVAssert csv = csvAssert(entry, renderingCtx);
            csv.has("principalName").isEquals("Administrator");
            csv.has("docLifeCycle").isEquals("project");
            csv.has("docPath").isEquals("/myFile");
            csv.has("docType").isEquals("File");
            csv.has("docUUID");
            // Default properties that are not requested and shouldn't be there
            List<String> unwantedProps = List.of("category", "comment", "eventId", "eventDate", "logDate");
            for (String property : unwantedProps) {
                // expected to catch
                try {
                    csv.has(property);
                    fail();
                } catch (AssertionError e) {
                    assertEquals("no field " + property, e.getMessage());
                }
            }
        }
    }

    protected List<LogEntry> getLogEntries() {
        LongFunction<LogEntry> logEntryGenerator = id -> LogEntry.builder("eventIdForTests", new Date())
                                                                 .id(id)
                                                                 .principalName("Administrator")
                                                                 .logDate(new Date())
                                                                 .docUUID(UUID.randomUUID().toString())
                                                                 .docPath("/myFile")
                                                                 .docType("File")
                                                                 .category("categoryForTests")
                                                                 .comment("comment")
                                                                 .docLifeCycle("project")
                                                                 .repositoryId("test")
                                                                 .extended("stringExtended", String.valueOf(id))
                                                                 .build();
        return List.of(logEntryGenerator.apply(1L), logEntryGenerator.apply(2L), logEntryGenerator.apply(3L));
    }
}
