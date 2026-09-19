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

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.nuxeo.audit.service.AuditBackend;

/**
 * @since 2025.16
 */
public record AuditRouterIntrospection(Set<String> events, List<RouteIntrospection> routes,
        List<BackendIntrospection> backends) implements Serializable {

    public AuditRouterIntrospection {
        events = Collections.unmodifiableSet(new LinkedHashSet<>(events));
        routes = List.copyOf(routes);
        backends = List.copyOf(backends);
    }

    public record RouteIntrospection(String name, String backendName) implements Serializable {
    }

    public record BackendIntrospection(String name, Class<? extends AuditBackend> backendClass)
            implements Serializable {
    }
}
