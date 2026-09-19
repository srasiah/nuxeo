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
package org.nuxeo.audit.io;

import static org.junit.Assert.assertEquals;
import static org.nuxeo.audit.service.AuditComponent.DEFAULT_AUDIT_BACKEND;
import static org.nuxeo.common.test.ModuleUnderTest.getClassLoaderResourceAsString;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.nuxeo.audit.api.AuditRouterIntrospection;
import org.nuxeo.audit.api.AuditRouterIntrospection.BackendIntrospection;
import org.nuxeo.audit.api.AuditRouterIntrospection.RouteIntrospection;
import org.nuxeo.audit.mem.MemAuditBackend;
import org.nuxeo.ecm.core.io.marshallers.puml.AbstractPlantUMLWriterTest;

/**
 * @since 2025.16
 */
public class AuditRouterIntrospectionPlantUMLWriterTest
        extends AbstractPlantUMLWriterTest.External<AuditRouterIntrospectionPlantUMLWriter, AuditRouterIntrospection> {

    @Test
    public void testSimplePumlConversion() throws IOException {
        AuditRouterIntrospection introspection = new AuditRouterIntrospection(Set.of("documentModified"),
                List.of(new RouteIntrospection("route-everything-to-" + DEFAULT_AUDIT_BACKEND, DEFAULT_AUDIT_BACKEND)),
                List.of(new BackendIntrospection(DEFAULT_AUDIT_BACKEND, MemAuditBackend.class)));
        String puml = asPuml(introspection);
        assertEquals(getClassLoaderResourceAsString("data/audit-router-introspection-simple.puml"), puml);
    }

    @Test
    public void testMultiPumlConversion() throws IOException {
        AuditRouterIntrospection introspection = new AuditRouterIntrospection(Set.of("documentModified"),
                List.of(new RouteIntrospection("route-everything-to-" + DEFAULT_AUDIT_BACKEND, DEFAULT_AUDIT_BACKEND),
                        new RouteIntrospection("route-everything-to-other", "other")),
                List.of(new BackendIntrospection(DEFAULT_AUDIT_BACKEND, MemAuditBackend.class),
                        new BackendIntrospection("other", MemAuditBackend.class)));
        String puml = asPuml(introspection);
        assertEquals(getClassLoaderResourceAsString("data/audit-router-introspection-multi.puml"), puml);
    }
}
