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
 *     Guillaume Renard
 */
package org.nuxeo.ecm.restapi.test;

import jakarta.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriterTest;
import org.nuxeo.ecm.core.io.marshallers.json.JsonAssert;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentModelJsonWriter;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext.CtxBuilder;
import org.nuxeo.ecm.core.test.CoreSearchFeature;
import org.nuxeo.ecm.restapi.server.enrichers.HasFolderishChildJsonEnricher;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

/**
 * @since 2025.17
 */
@Features(CoreSearchFeature.class)
@Deploy("org.nuxeo.ecm.platform.restapi.server:OSGI-INF/json-enrichers-contrib.xml")
public class HasFolderishChildJsonEnricherTest
        extends AbstractJsonWriterTest.Local<DocumentModelJsonWriter, DocumentModel> {

    @Inject
    protected CoreSession session;

    @Inject
    protected TransactionalFeature txFeature;

    @Before
    public void setup() {
        var document = session.createDocumentModel("/", "child1", "Folder");
        session.createDocument(document);
        document = session.createDocumentModel("/", "child2", "Folder");
        session.createDocument(document);
        document = session.createDocumentModel("/", "child3", "File");
        session.createDocument(document);
        txFeature.nextTransaction();
    }

    @Test
    public void test() throws Exception {
        var root = session.getDocument(new PathRef("/"));
        JsonAssert json = jsonAssert(root, CtxBuilder.enrichDoc(HasFolderishChildJsonEnricher.NAME).get());
        json = json.has("contextParameters").isObject();
        json.properties(1);
        json = json.has(HasFolderishChildJsonEnricher.NAME).isBool();
        json.isEquals(true);

        var child1 = session.getDocument(new PathRef("/child1"));
        json = jsonAssert(child1, CtxBuilder.enrichDoc(HasFolderishChildJsonEnricher.NAME).get());
        json = json.has("contextParameters").isObject();
        json.properties(1);
        json = json.has(HasFolderishChildJsonEnricher.NAME).isBool();
        json.isEquals(false);

        var child3 = session.getDocument(new PathRef("/child3"));
        json = jsonAssert(child3, CtxBuilder.enrichDoc(HasFolderishChildJsonEnricher.NAME).get());
        json = json.has("contextParameters").isObject();
        json.properties(1);
        json = json.has(HasFolderishChildJsonEnricher.NAME).isBool();
        json.isEquals(false);
    }

}
