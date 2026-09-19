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

import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.IOException;

import org.nuxeo.audit.api.AuditRouterIntrospection;
import org.nuxeo.audit.impl.StreamAuditWriter;
import org.nuxeo.audit.listener.StreamAuditEventListener;
import org.nuxeo.common.function.ThrowableConsumer;
import org.nuxeo.ecm.core.io.marshallers.puml.AbstractPlantUMLWriter;
import org.nuxeo.ecm.core.io.marshallers.puml.PlantUMLPrinter;
import org.nuxeo.ecm.core.io.marshallers.puml.PlantUMLPrinter.ArrowStyle;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;

/**
 * @since 2025.16
 */
@Setup(mode = SINGLETON, priority = REFERENCE)
public class AuditRouterIntrospectionPlantUMLWriter extends AbstractPlantUMLWriter<AuditRouterIntrospection> {

    @Override
    protected void write(AuditRouterIntrospection entity, PlantUMLPrinter pumlPrinter) throws IOException {
        pumlPrinter.writeStartDocument();
        pumlPrinter.writeTitle("Audit Router Introspection");

        pumlPrinter.writeHide("members");

        pumlPrinter.writeSkinparam("defaultFontName", "Courier");
        pumlPrinter.writeSkinparam("handwritten", "false");
        pumlPrinter.writeSkinparam("queue", "BackgroundColor", "LightYellow");
        pumlPrinter.writeSkinparam("node", "BackgroundColor", "White");
        pumlPrinter.writeSkinparam("component", "BackgroundColor", "Azure", "BorderColor", "black", "ArrowColor",
                "#CC6655");

        pumlPrinter.writeStartComponent("event_listener");
        pumlPrinter.writeFreeText("Event Listener");
        pumlPrinter.writeTextSeparator();
        pumlPrinter.writeFreeText("events:");
        entity.events().forEach(ThrowableConsumer.asConsumer(event -> pumlPrinter.writeFreeText("- " + event)));
        pumlPrinter.writeEndComponent();

        String streamIdentifier = "stream_" + replaceNonWordCharacters(StreamAuditEventListener.STREAM_NAME);
        String computationIdentifier = "computation_" + replaceNonWordCharacters(StreamAuditWriter.COMPUTATION_NAME);
        pumlPrinter.writeStartQueue(streamIdentifier, StreamAuditEventListener.STREAM_NAME);
        pumlPrinter.writeStartComponent(computationIdentifier);
        pumlPrinter.writeFreeText(StreamAuditWriter.COMPUTATION_NAME);
        pumlPrinter.writeEndComponent();
        pumlPrinter.writeEndQueue();

        pumlPrinter.writeArrow("event_listener", streamIdentifier);
        pumlPrinter.writeArrow(streamIdentifier, streamIdentifier + '.' + computationIdentifier, ArrowStyle.HIDDEN);

        // write route components
        pumlPrinter.writeStartPackage("audit_router", "Audit Router");
        entity.routes().forEach(ThrowableConsumer.asConsumer(route -> write(route, pumlPrinter)));
        pumlPrinter.writeEndPackage();

        // write backend components
        pumlPrinter.writeStartPackage("audit_backend", "Audit Backends");
        entity.backends().forEach(ThrowableConsumer.asConsumer(backend -> write(backend, pumlPrinter)));
        pumlPrinter.writeEndPackage();

        // write arrows between router, routes and backends
        pumlPrinter.writeArrow(streamIdentifier + '.' + computationIdentifier, "audit_router");
        entity.routes().forEach(ThrowableConsumer.asConsumer(route -> {
            String routeIdentifier = getRouteIdentifier(route.name());
            pumlPrinter.writeArrow("audit_router", "audit_router." + routeIdentifier);
            pumlPrinter.writeArrow("audit_router." + routeIdentifier,
                    "audit_backend." + getBackendIdentifier(route.backendName()));
        }));

        pumlPrinter.writeEndDocument();
    }

    protected void write(AuditRouterIntrospection.RouteIntrospection route, PlantUMLPrinter pumlPrinter)
            throws IOException {
        String routeIdentifier = getRouteIdentifier(route.name());
        pumlPrinter.writeStartComponent(routeIdentifier);
        pumlPrinter.writeFreeText(route.name());
        pumlPrinter.writeEndComponent();
    }

    protected void write(AuditRouterIntrospection.BackendIntrospection backend, PlantUMLPrinter pumlPrinter)
            throws IOException {
        String backendIdentifier = getBackendIdentifier(backend.name());
        pumlPrinter.writeStartDatabase(backendIdentifier, backend.name());
        pumlPrinter.writeStartClass("implementation", backend.backendClass().getName());
        pumlPrinter.writeEndClass();
        pumlPrinter.writeEndDatabase();
        pumlPrinter.writeArrow(backendIdentifier, backendIdentifier + ".implementation", ArrowStyle.HIDDEN);
    }

    protected String getRouteIdentifier(String routeName) {
        return "route_" + replaceNonWordCharacters(routeName);
    }

    protected String getBackendIdentifier(String backendName) {
        return "backend_" + replaceNonWordCharacters(backendName);
    }

    protected String replaceNonWordCharacters(String input) {
        return input.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
