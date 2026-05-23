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
package org.nuxeo.audit.service;

import java.util.List;

import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.audit.api.Route;
import org.nuxeo.ecm.core.event.Event;

/**
 * @since 2025.16
 */
public interface AuditRouter {

    /**
     * Computes the log entries from the given event based on contributed audit routes.
     *
     * @param event the event to compute log entries from
     * @return the log entries to route, it could be an empty list if the event doesn't match any route
     */
    List<LogEntry> computeLogEntries(Event event);

    /**
     * Routes the given log entries to the appropriate backends based on contributed live audit routes.
     * 
     * @param logEntries the log entries to route
     */
    void routeToBackends(List<LogEntry> logEntries);

    /**
     * Routes the given log entries to the appropriate backends based on given audit routes.
     * <p>
     * Each given route will be evaluated and log entry routed if it matches.
     *
     * @param logEntries the log entries to route
     * @param routes the routes to evaluate on log entries
     */
    void routeToBackends(List<LogEntry> logEntries, List<Route> routes);
}
