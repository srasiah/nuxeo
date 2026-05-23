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

import java.io.Serializable;
import java.security.Principal;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.audit.api.Route;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.ScrollResult;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.query.sql.model.QueryBuilder;
import org.nuxeo.ecm.platform.audit.api.ExtendedInfo;
import org.nuxeo.runtime.api.Framework;

/**
 * Abstract class to share code between {@link AuditBackend} implementations
 *
 * @since 2025.0
 */
@SuppressWarnings("removal")
public abstract class AbstractAuditBackend extends org.nuxeo.ecm.platform.audit.service.AbstractAuditBackend<LogEntry>
        implements AuditBackend {

    protected static final Logger log = LogManager.getLogger(AbstractAuditBackend.class);

    /** @deprecated since 2025.16, introduced for {@link #addLogEntries(List)} backward compatibility mechanism */
    @Deprecated(since = "2025.16", forRemoval = true)
    protected String name;

    @Override
    @Deprecated(since = "2025.16", forRemoval = true)
    public void addLogEntries(List<LogEntry> entries) {
        log.warn("Using a deprecated API, please use AuditRouter.routeToBackend instead");
        Framework.getService(AuditRouter.class).routeToBackends(entries, List.of(Route.allEventsTo(name)));
    }

    protected LogEntry doCreateAndFillEntryFromDocument(DocumentModel doc, Principal principal) {
        Date eventDate = new Date();
        Calendar creationDate = (Calendar) doc.getProperty("dublincore", "created");
        if (creationDate != null) {
            eventDate = creationDate.getTime();
        }
        var builder = LogEntry.builder(DocumentEventTypes.DOCUMENT_CREATED, eventDate)
                              .docPath(doc.getPathAsString())
                              .docType(doc.getType())
                              .docUUID(doc.getId())
                              .repositoryId(doc.getRepositoryName())
                              .principalName(SecurityConstants.SYSTEM_USERNAME)
                              .category("eventDocumentCategory")
                              // why hard-code it if we have the document life cycle?
                              .docLifeCycle("project");

        var auditComponent = (AuditComponent) Framework.getService(AuditService.class);
        auditComponent.doPutExtendedInfos(builder, null, doc, principal);

        return builder.build();
    }

    /**
     * INTERNAL METHOD FOR TESTS, DO NOT USE.
     */
    protected void clearEntries() {
        // nothing in the default implementation
    }

    // un-implemented deprecated APIs

    @Override
    @Deprecated(since = "2025.0", forRemoval = true)
    public ExtendedInfo newExtendedInfo(Serializable value) {
        throw new NuxeoException("The operation is not supported on org.nuxeo.audit.service.AuditBackend");
    }

    @Override
    @Deprecated(since = "2025.0", forRemoval = true)
    public int getApplicationStartedOrder() {
        throw new NuxeoException("The operation is not supported on org.nuxeo.audit.service.AuditBackend");
    }

    @Override
    @Deprecated(since = "2025.0", forRemoval = true)
    public void onApplicationStarted() {
        throw new NuxeoException("The operation is not supported on org.nuxeo.audit.service.AuditBackend");
    }

    @Override
    @Deprecated(since = "2025.0", forRemoval = true)
    public void onApplicationStopped() {
        throw new NuxeoException("The operation is not supported on org.nuxeo.audit.service.AuditBackend");
    }

    @Override
    @Deprecated(since = "2025.0", forRemoval = true)
    public long syncLogCreationEntries(String repoId, String path, Boolean recurs) {
        throw new NuxeoException("The operation is not supported on org.nuxeo.audit.service.AuditBackend");
    }

    @Override
    public void append(List<String> jsonEntries) {
        throw new NuxeoException("The operation is not supported on org.nuxeo.audit.service.AuditBackend");
    }

    @Override
    public ScrollResult<String> scroll(QueryBuilder queryBuilder, int batchSize, int keepAliveSeconds) {
        throw new NuxeoException("The operation is not supported on org.nuxeo.audit.service.AuditBackend");
    }

    @Override
    public ScrollResult<String> scroll(String scrollId) {
        throw new NuxeoException("The operation is not supported on org.nuxeo.audit.service.AuditBackend");
    }

    /** @deprecated since 2025.16 */
    @Deprecated(since = "2025.16", forRemoval = true)
    protected void setName(String name) {
        this.name = name;
    }
}
