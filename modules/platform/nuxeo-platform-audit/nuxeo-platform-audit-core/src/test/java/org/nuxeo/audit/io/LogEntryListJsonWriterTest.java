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

import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.audit.api.PaginableLogEntryList;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriterTest;
import org.nuxeo.ecm.core.io.marshallers.json.JsonAssert;
import org.nuxeo.ecm.platform.query.api.AbstractPageProvider;
import org.nuxeo.runtime.test.runner.Deploy;

@Deploy("org.nuxeo.ecm.platform.audit:OSGI-INF/marshallers-contrib.xml")
public class LogEntryListJsonWriterTest
        extends AbstractJsonWriterTest.External<LogEntryListJsonWriter, List<LogEntry>> {

    @Test
    public void test() throws Exception {
        var pp = new LogEntryPageProvider(List.of(LogEntry.builder("eventIdForTests", new Date()).build()));
        var list = new PaginableLogEntryList(pp);
        JsonAssert json = jsonAssert(list);
        json.properties(19);
        json.has("entity-type").isEquals("logEntries");
        json.has("isPaginable").isTrue();
        json.has("resultsCount").isEquals(pp.getResultsCount());
        json.has("pageSize").isEquals(pp.getPageSize());
        json.has("maxPageSize").isEquals(pp.getMaxPageSize());
        json.has("currentPageSize").isEquals(pp.getCurrentPageSize());
        json.has("currentPageIndex").isEquals(0);
        json.has("numberOfPages").isEquals(pp.getNumberOfPages());
        json.has("isPreviousPageAvailable").isEquals(pp.isPreviousPageAvailable());
        json.has("isNextPageAvailable").isEquals(pp.isNextPageAvailable());
        json.has("isLastPageAvailable").isEquals(pp.isLastPageAvailable());
        json.has("isSortable").isEquals(pp.isSortable());
        json.has("hasError").isEquals(pp.hasError());
        json.has("errorMessage").isNull();
        json.has("pageIndex").isEquals(pp.getCurrentPageIndex());
        json.has("pageCount").isEquals(pp.getNumberOfPages());
        json.has("currentPageOffset").isEquals(pp.getCurrentPageOffset());
        json = json.has("entries").isArray();
        json = json.has(0).isObject();
        json.has("entity-type").isEquals("logEntry");
    }

    protected static class LogEntryPageProvider extends AbstractPageProvider<LogEntry> {

        protected final List<LogEntry> entries;

        public LogEntryPageProvider(List<LogEntry> entries) {
            this.entries = entries;
        }

        @Override
        public List<LogEntry> getCurrentPage() {
            return entries;
        }
    }
}
