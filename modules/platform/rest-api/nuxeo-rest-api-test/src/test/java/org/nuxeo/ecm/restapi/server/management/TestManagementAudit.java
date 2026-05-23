/*
 * (C) Copyright 2026 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.restapi.server.management;

import static org.junit.Assert.assertEquals;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_EVENT_ID;
import static org.nuxeo.ecm.restapi.server.management.ManagementObject.MANAGEMENT_API_ACCESS_EVENT;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.MediaType;

import org.junit.Test;
import org.nuxeo.audit.service.AuditBackend;
import org.nuxeo.audit.test.AuditFeature;
import org.nuxeo.ecm.core.query.sql.model.Predicates;
import org.nuxeo.ecm.core.query.sql.model.QueryBuilder;
import org.nuxeo.ecm.restapi.test.ManagementBaseTest;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 2025.16
 */
@Features(AuditFeature.class)
@Deploy("org.nuxeo.ecm.platform.restapi.test.test:test-management-api-access-audit-contrib.xml")
public class TestManagementAudit extends ManagementBaseTest {

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Inject
    protected AuditBackend auditBackend;

    @Test
    public void testManagementAccessCanBeAudited() {
        httpClient.buildGetRequest("/management/distribution")
                  .accept(MediaType.APPLICATION_JSON)
                  .addQueryParameter("dryRun", "true")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(HttpServletResponse.SC_OK, status.intValue()));

        transactionalFeature.nextTransaction();

        var logEntries = auditBackend.queryLogs(
                new QueryBuilder().predicate(Predicates.eq(LOG_EVENT_ID, MANAGEMENT_API_ACCESS_EVENT)));
        assertEquals(1, logEntries.size());
        var logEntry = logEntries.getFirst();
        assertEquals(MANAGEMENT_API_ACCESS_EVENT, logEntry.getEventId());
        assertEquals("Administrator", logEntry.getPrincipalName());
        assertEquals("Administrator called GET on /api/v1/management/distribution", logEntry.getComment());
        assertEquals("GET", logEntry.getExtendedValue("method"));
        assertEquals("/api/v1/management/distribution", logEntry.getExtendedValue("requestURI"));
        assertEquals("dryRun=true", logEntry.getExtendedValue("queryString"));
        assertEquals(Long.valueOf(-1), logEntry.getExtendedValue("contentLength"));
        assertEquals(MediaType.APPLICATION_JSON, logEntry.getExtendedValue("headerAccept"));
    }
}
