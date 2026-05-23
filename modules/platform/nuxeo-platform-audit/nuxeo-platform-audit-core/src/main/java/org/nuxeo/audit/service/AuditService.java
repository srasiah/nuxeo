/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.audit.service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.ecm.core.event.Event;

/**
 * @since 2025.0
 */
public interface AuditService {

    /**
     * @return the event names to write to the audit
     * @deprecated since 2025.16, no direct replacement see {@link AuditRouter#computeLogEntries(Event)}
     */
    @Deprecated(since = "2025.16", forRemoval = true)
    Set<String> getAuditableEventNames();

    /**
     * @param eventName the event name to write to the audit
     * @return the extended info mappers to apply when writing to the audit
     * @deprecated since 2025.16, unused
     */
    @Deprecated(since = "2025.16", forRemoval = true)
    List<ExtendedInfoMapper> getExtendedInfoMappers(String eventName);

    /**
     * @return the {@link AuditBackend} with the given {@code name}
     */
    <B extends AuditBackend> B getAuditBackend(String name);

    /**
     * Returns a log entry representation of an event.
     *
     * @deprecated since 2025.16, use {@link AuditRouter#computeLogEntries(Event)} instead
     */
    @Deprecated(since = "2025.16", forRemoval = true)
    LogEntry buildEntryFromEvent(Event event);

    /**
     * Awaits the audit ingestion.
     */
    boolean await(Duration duration) throws InterruptedException;
}
