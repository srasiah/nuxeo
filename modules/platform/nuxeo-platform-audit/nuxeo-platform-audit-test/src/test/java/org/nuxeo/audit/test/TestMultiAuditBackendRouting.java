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
package org.nuxeo.audit.test;

import static org.junit.Assert.assertEquals;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.audit.service.AuditBackend;
import org.nuxeo.ecm.core.event.CoreEventFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 2025.16
 */
@RunWith(FeaturesRunner.class)
@Features({ MultiAuditFeature.class })
public class TestMultiAuditBackendRouting {

    @Inject
    protected CoreEventFeature eventFeature;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Inject
    protected AuditBackend defaultBackend;

    @Inject
    @Named("other")
    protected AuditBackend otherBackend;

    @Test
    @Deploy("org.nuxeo.audit.test.test:OSGI-INF/test-multi-audit-basic-other-route.xml")
    public void testBasicOtherRoute() {
        // test the route
        assertEquals(Long.valueOf(0), defaultBackend.getEventsCount("otherEvent"));
        assertEquals(Long.valueOf(0), otherBackend.getEventsCount("otherEvent"));

        eventFeature.fireEvent("otherEvent");
        transactionalFeature.nextTransaction();

        assertEquals(Long.valueOf(0), defaultBackend.getEventsCount("otherEvent"));
        assertEquals(Long.valueOf(1), otherBackend.getEventsCount("otherEvent"));

        // test nothing else than the route
        assertEquals(Long.valueOf(0), defaultBackend.getEventsCount("loginSuccess"));
        assertEquals(Long.valueOf(0), otherBackend.getEventsCount("loginSuccess"));

        eventFeature.fireEvent("loginSuccess");
        transactionalFeature.nextTransaction();

        assertEquals(Long.valueOf(1), defaultBackend.getEventsCount("loginSuccess"));
        assertEquals(Long.valueOf(0), otherBackend.getEventsCount("loginSuccess"));
    }

    @Test
    @Deploy("org.nuxeo.audit.test.test:OSGI-INF/test-multi-audit-several-matching-routes.xml")
    public void testSeveralMatchingRoutes() {
        // test event goes to both backends
        assertEquals(Long.valueOf(0), defaultBackend.getEventsCount("commonEvent"));
        assertEquals(Long.valueOf(0), otherBackend.getEventsCount("commonEvent"));

        eventFeature.fireEvent("commonEvent");
        transactionalFeature.nextTransaction();

        assertEquals(Long.valueOf(1), defaultBackend.getEventsCount("commonEvent"));
        assertEquals(Long.valueOf(1), otherBackend.getEventsCount("commonEvent"));

        // test event goes only once to the backend
        assertEquals(Long.valueOf(0), defaultBackend.getEventsCount("twiceSameBackendEvent"));
        assertEquals(Long.valueOf(0), otherBackend.getEventsCount("twiceSameBackendEvent"));

        eventFeature.fireEvent("twiceSameBackendEvent");
        transactionalFeature.nextTransaction();

        assertEquals(Long.valueOf(1), defaultBackend.getEventsCount("twiceSameBackendEvent"));
        assertEquals(Long.valueOf(0), otherBackend.getEventsCount("twiceSameBackendEvent"));
    }
}
