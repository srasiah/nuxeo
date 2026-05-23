/*
 * (C) Copyright 2006-2024 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.platform.audit.api;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Nullable;

import org.nuxeo.ecm.core.event.Event;

/**
 * Interface for adding audit logs.
 *
 * @author tiry
 * @param <L> to give the log entry type for the new {@link org.nuxeo.audit.service.AuditBackend} interface that defines
 *            a new entry type.
 * @deprecated since 2025.0, use {@link org.nuxeo.audit.service.AuditBackend} instead
 */
@SuppressWarnings("removal")
@Deprecated(since = "2025.0", forRemoval = true)
public interface AuditLogger<L extends LogEntry> {

    /**
     * Returns the list of auditable event names.
     *
     * @return a set of String representing event names.
     * @deprecated since 2025.0, use {@code org.nuxeo.ecm.platform.audit.service.AuditService#getAuditableEventNames()}
     *             instead
     */
    @Deprecated(since = "2025.0", forRemoval = true)
    Set<String> getAuditableEventNames();

    /**
     * Create a new LogEntry instance.
     *
     * @deprecated since 2025.0, use {@link org.nuxeo.audit.api.LogEntry#builder()} instead
     */
    @Deprecated(since = "2025.0", forRemoval = true)
    L newLogEntry();

    /**
     * Create a new ExtendedInfo instance
     * 
     * @deprecated since 2025.0, use {@link org.nuxeo.audit.api.LogEntryBuilder#extended(String, Object)} instead
     */
    @Deprecated(since = "2025.0", forRemoval = true)
    ExtendedInfo newExtendedInfo(Serializable value);

    /**
     * Adds given log entries.
     *
     * @param entries the list of log entries.
     */
    void addLogEntries(List<L> entries);

    /**
     * @since 8.2
     * @deprecated since 2025.0, use {@code org.nuxeo.ecm.platform.audit.service.AuditService#await(Duration)} instead
     */
    @Deprecated(since = "2025.0", forRemoval = true)
    boolean await(long time, TimeUnit unit) throws InterruptedException;

    /**
     * Returns a log entry representation of an event.
     *
     * @since 9.3
     * @deprecated since 2025.0, use {@code org.nuxeo.ecm.platform.audit.service.AuditService#buildEntryFromEvent()}
     *             instead
     */
    @Nullable
    @Deprecated(since = "2025.0", forRemoval = true)
    L buildEntryFromEvent(Event event);
}
