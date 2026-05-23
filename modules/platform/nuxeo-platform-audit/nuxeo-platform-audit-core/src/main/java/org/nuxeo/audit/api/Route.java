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
package org.nuxeo.audit.api;

import java.util.function.Predicate;

/**
 * Represents a rule for routing audit {@link LogEntry logEntries} to a specific backend.
 * <p>
 * A {@code Route} is a {@link Predicate} that determines whether a given {@link LogEntry} should be routed to a
 * particular backend, identified by its name. Implementations can define custom logic in the {@link #test(Object)}
 * method to filter events.
 * </p>
 *
 * @since 2025.16
 */
public interface Route extends Predicate<LogEntry> {

    /**
     * Returns the name of the backend to which matching {@link LogEntry} events should be routed.
     *
     * @return the backend name
     */
    String getBackendName();

    /**
     * Creates a {@code Route} that matches all events, and routes them to the given backend.
     *
     * @param backendName the name of the backend
     * @return a {@code Route} that matches all events
     */
    static Route allEventsTo(String backendName) {
        return of(backendName, logEntry -> true);
    }

    /**
     * Creates a {@code Route} that matches events based on the provided {@code predicate}, and routes them to the given
     * backend.
     * 
     * @param backendName the name of the backend
     * @param predicate the predicate to apply to {@link LogEntry} events for matching
     * @return a {@code Route} that matches events based on the provided predicate
     */
    static Route of(String backendName, Predicate<LogEntry> predicate) {
        return new Route() {

            @Override
            public String getBackendName() {
                return backendName;
            }

            @Override
            public boolean test(LogEntry logEntry) {
                return predicate.test(logEntry);
            }
        };
    }
}
