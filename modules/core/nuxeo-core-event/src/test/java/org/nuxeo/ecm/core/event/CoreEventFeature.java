/*
 * (C) Copyright 2022-2026 Nuxeo (http://nuxeo.com/) and others.
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
 *       Kevin Leturc <kleturc@nuxeo.com>
 */
package org.nuxeo.ecm.core.event;

import jakarta.inject.Inject;

import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.runtime.cluster.ClusterFeature;
import org.nuxeo.runtime.stream.RuntimeStreamFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 2021.19
 */
@Deploy("org.nuxeo.ecm.core.event")
@Deploy("org.nuxeo.ecm.core.event.test:OSGI-INF/test-scheduler-without-start-delay-config.xml")
@Features({ //
        RuntimeStreamFeature.class, // needed for domain event
        TransactionalFeature.class, // needed for domain event
        ClusterFeature.class // needed for scheduler service
})
public class CoreEventFeature implements RunnerFeature {

    @Inject
    protected EventService eventService;

    public void fireEvent(String eventName) {
        CoreSession session = CoreInstance.getCoreSession("default");
        NuxeoPrincipal principal = session == null ? null : session.getPrincipal();
        EventContext ctx = new EventContextImpl(session, principal);
        Event event = ctx.newEvent(eventName);
        event.setInline(false);
        event.setImmediate(true);
        eventService.fireEvent(event);
    }
}
