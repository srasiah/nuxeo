/*
 * (C) Copyright 2006-2026 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 */
package org.nuxeo.audit.sql.job;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Date;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.audit.api.job.JobHistoryHelper;
import org.nuxeo.audit.service.AuditBackend;
import org.nuxeo.audit.sql.SQLAuditFeature;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(SQLAuditFeature.class)
public class TestJobHistoryHelper {

    @Inject
    protected AuditBackend backend;

    @Test
    public void testLogger() {
        StringBuilder query = new StringBuilder("from LogEntry log where ");
        query.append(" log.category='");
        query.append("MyExport");
        query.append("'  ORDER BY log.eventDate DESC");

        List<?> result = backend.nativeQuery(query.toString(), 1, 1);
        assertEquals(0, result.size());

        JobHistoryHelper helper = new JobHistoryHelper("MyExport");

        helper.logJobStarted();
        helper.logJobFailed("some error");
        helper.logJobEnded();

        result = backend.nativeQuery(query.toString(), 1, 10);
        assertEquals(3, result.size());

    }

    @Test
    public void testLoggerHelper() throws Exception {
        JobHistoryHelper helper = new JobHistoryHelper("MyExport2");

        helper.logJobStarted();

        Date exportDate = helper.getLastSuccessfulRun();
        assertNull(exportDate);

        helper.logJobFailed("some other error");

        exportDate = helper.getLastSuccessfulRun();
        assertNull(exportDate);

        helper.logJobEnded();

        exportDate = helper.getLastSuccessfulRun();
        assertNotNull(exportDate);

        Thread.sleep(3000);
        long t0 = System.currentTimeMillis();

        exportDate = helper.getLastSuccessfulRun();
        long loggedT0 = exportDate.getTime();
        assertTrue(loggedT0 < t0);

        helper.logJobEnded();
        exportDate = helper.getLastSuccessfulRun();
        long loggedT1 = exportDate.getTime();

        long elapsed = loggedT1 - loggedT0;
        int min = 3000;
        if (SystemUtils.IS_OS_WINDOWS) {
            // windows has strange clock granularity: elapsed = 2677 has been observed
            min = 2000;
        }
        assertTrue(elapsed + " should be >= " + min, elapsed >= min);
    }

}
