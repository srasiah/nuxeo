/*
 * (C) Copyright 2024-2026 Nuxeo (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.drive.service.impl;

import static org.nuxeo.audit.service.AuditComponent.DEFAULT_AUDIT_BACKEND;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.audit.service.AuditBackend;
import org.nuxeo.audit.service.AuditService;
import org.nuxeo.drive.service.FileSystemChangeFinder;
import org.nuxeo.drive.service.NuxeoDriveEvents;
import org.nuxeo.drive.service.SynchronizationRoots;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.api.Framework;

/**
 * Implementation of {@link FileSystemChangeFinder} using the {@link AuditBackend}.
 */
@SuppressWarnings("removal")
public class SQLAuditChangeFinder extends AuditChangeFinder {

    private static final Logger log = LogManager.getLogger(SQLAuditChangeFinder.class);

    /**
     * Returns the last available log id in the audit log table (primary key) to be used as the upper bound of the event
     * log id range clause in the change query.
     */
    @Override
    @SuppressWarnings("unchecked")
    public long getUpperBound() {
        var auditBackend = Framework.getService(AuditService.class).getAuditBackend(DEFAULT_AUDIT_BACKEND);
        String auditQuery = "from LogEntry log order by log.id desc";
        log.debug("Querying audit log for greatest id: {}", auditQuery);

        var entries = (List<LogEntry>) auditBackend.nativeQuery(auditQuery, 1, 1);
        if (entries.isEmpty()) {
            log.debug("Found no audit log entries, returning -1");
            return -1;
        }
        return entries.getFirst().getId();
    }

    @SuppressWarnings("unchecked")
    protected List<LogEntry> queryAuditEntries(CoreSession session, SynchronizationRoots activeRoots,
            Set<String> collectionSyncRootMemberIds, long lowerBound, long upperBound, int limit) {
        var auditBackend = Framework.getService(AuditService.class).getAuditBackend(DEFAULT_AUDIT_BACKEND);
        // Set fixed query parameters
        Map<String, Object> params = new HashMap<>();
        params.put("repositoryId", session.getRepositoryName());

        // Build query and set dynamic parameters
        StringBuilder auditQuerySb = new StringBuilder("from LogEntry log where ");
        auditQuerySb.append("log.repositoryId = :repositoryId");
        auditQuerySb.append(" and ");
        auditQuerySb.append("(");
        if (!activeRoots.getPaths().isEmpty()) {
            // detect changes under the currently active roots for the
            // current user
            auditQuerySb.append("(");
            auditQuerySb.append("log.category = 'eventDocumentCategory'");
            // TODO: don't hardcode event ids (contribute them?)
            auditQuerySb.append(
                    " and (log.eventId = 'documentCreated' or log.eventId = 'documentModified' or log.eventId = 'documentMoved' or log.eventId = 'documentCreatedByCopy' or log.eventId = 'documentRestored' or log.eventId = 'addedToCollection' or log.eventId = 'documentProxyPublished' or log.eventId = 'documentLocked' or log.eventId = 'documentUnlocked' or log.eventId = 'documentUntrashed' or log.eventId = 'blobDigestUpdated')");
            auditQuerySb.append(") and (");
            auditQuerySb.append("(");
            auditQuerySb.append(getCurrentRootFilteringClause(activeRoots.getPaths(), params));
            auditQuerySb.append(")");
            if (collectionSyncRootMemberIds != null && !collectionSyncRootMemberIds.isEmpty()) {
                auditQuerySb.append(" or (");
                auditQuerySb.append(getCollectionSyncRootFilteringClause(collectionSyncRootMemberIds, params));
                auditQuerySb.append(")");
            }
            auditQuerySb.append(") or ");
        }
        // Detect any root (un-)registration changes for the roots previously
        // seen by the current user.
        // Exclude 'rootUnregistered' since root unregistration is covered by a
        // "deleted" virtual event.
        auditQuerySb.append("(");
        auditQuerySb.append("log.category = '");
        auditQuerySb.append(NuxeoDriveEvents.EVENT_CATEGORY);
        auditQuerySb.append("' and log.eventId != 'rootUnregistered'");
        auditQuerySb.append(")");
        auditQuerySb.append(") and (");
        auditQuerySb.append(getJPARangeClause(lowerBound, upperBound, params));
        // we intentionally sort by eventDate even if the range filtering is
        // done on the log id: eventDate is useful to reflect the ordering of
        // events occurring inside the same transaction while the
        // monotonic behavior of log id is useful for ensuring that consecutive
        // range queries to the audit won't miss any events even when long
        // running transactions are logged after a delay.
        auditQuerySb.append(") order by log.repositoryId asc, log.eventDate desc");
        String auditQuery = auditQuerySb.toString();

        log.debug("Querying audit log for changes: {} with params: {}", auditQuery, params);

        List<LogEntry> entries = (List<LogEntry>) auditBackend.nativeQuery(auditQuery, params, 1, limit);

        // Post filter the output to remove (un)registration that are unrelated
        // to the current user.
        List<LogEntry> postFilteredEntries = new ArrayList<>();
        String principalName = session.getPrincipal().getName();
        for (LogEntry entry : entries) {
            String impactedUser = entry.getExtendedValue("impactedUserName");
            if (impactedUser != null && !principalName.equals(impactedUser)) {
                // ignore event that only impact other users
                continue;
            }
            log.debug("Change detected: {}", entry);
            postFilteredEntries.add(entry);
        }
        return postFilteredEntries;
    }

    protected String getCurrentRootFilteringClause(Set<String> rootPaths, Map<String, Object> params) {
        StringBuilder rootPathClause = new StringBuilder();
        int rootPathCount = 0;
        for (String rootPath : rootPaths) {
            rootPathCount++;
            String rootPathParam = "rootPath" + rootPathCount;
            if (!rootPathClause.isEmpty()) {
                rootPathClause.append(" or ");
            }
            rootPathClause.append(String.format("log.docPath like :%s", rootPathParam));
            params.put(rootPathParam, rootPath + '%');

        }
        return rootPathClause.toString();
    }

    protected String getCollectionSyncRootFilteringClause(Set<String> collectionSyncRootMemberIds,
            Map<String, Object> params) {
        String paramName = "collectionMemberIds";
        params.put(paramName, collectionSyncRootMemberIds);
        return String.format("log.docUUID in (:%s)", paramName);
    }

    /**
     * Using event log id to ensure consistency, see https://jira.nuxeo.com/browse/NXP-14826.
     */
    protected String getJPARangeClause(long lowerBound, long upperBound, Map<String, Object> params) {
        params.put("lowerBound", lowerBound);
        params.put("upperBound", upperBound);
        return "log.id > :lowerBound and log.id <= :upperBound";
    }
}
