/*
 * (C) Copyright 2019-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     Salem Aouana
 */
package org.nuxeo.ecm.webengine.rest.coreiodelegate;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.nuxeo.ecm.core.io.marshallers.NuxeoMediaType.TEXT_CSV;
import static org.nuxeo.ecm.core.io.marshallers.NuxeoMediaType.TEXT_D2;
import static org.nuxeo.ecm.core.io.marshallers.NuxeoMediaType.TEXT_PLANT_UML;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Optional;

import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.io.registry.Reader;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.platform.web.common.RequestContext;

/**
 * A Jakarta RS {@link MessageBodyWriter} that try to delegate the marshalling to all nuxeo-core-io {@link Writer} and
 * {@link Reader}. This singleton is also registering an injection of {@link RenderingContext}
 *
 * @since 11.1
 */
@Singleton
@Provider
@Produces({ APPLICATION_JSON, TEXT_CSV, TEXT_D2, TEXT_PLANT_UML })
public class CoreIODelegate extends PartialCoreIODelegate implements Feature {

    @Override
    protected boolean accept(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return true;
    }

    protected RenderingContext getRenderingContextOrFail() {
        return Optional.ofNullable(RequestContext.getActiveContext())
                       .map(RequestContext::getRequest)
                       .map(RenderingContextWebUtils::getContext)
                       .orElseThrow(() -> new NuxeoException("No RenderingContext in the request"));
    }

    @Override
    public boolean configure(FeatureContext context) {
        context.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bindFactory(CoreIODelegate.this::getRenderingContextOrFail).to(RenderingContext.class)
                                                                           .in(RequestScoped.class);
            }
        });
        return true;
    }
}
