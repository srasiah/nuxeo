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
 *     Guillaume Renard
 */
package org.nuxeo.ecm.restapi.opensearch1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.audit.opensearch1.OpenSearchAuditFeature;
import org.nuxeo.ecm.restapi.opensearch1.filter.AuditRequestFilter;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 7.4
 */
@RunWith(FeaturesRunner.class)
@Features({ OpenSearchAuditFeature.class, OpenSearchPassthroughFeature.class })
@Deploy("org.nuxeo.ecm.restapi.opensearch1")
public class TestAuditRequestFilter {

    @Test
    public void testMatchAllAuditAsAdmin() {
        String payload = "{\"query\": {\"match_all\": {}}}";
        AuditRequestFilter filter = new AuditRequestFilter();
        filter.init(TestSearchRequestFilter.getAdminCoreSession(), "not used", "pretty", payload);
        assertEquals(payload, filter.getPayload());
    }

    @Test
    public void testMatchAllAuditAsNonAdmin() {
        String payload = "{\"query\": {\"match_all\": {}}}";
        AuditRequestFilter filter = new AuditRequestFilter();
        assertThrows("Non Admin should not be able to access audit", IllegalArgumentException.class,
                () -> filter.init(TestSearchRequestFilter.getNonAdminCoreSession(), "not used", "pretty", payload));
    }

}
