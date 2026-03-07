/*
 * (C) Copyright 2024-2025 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.io.marshallers.json.document;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.junit.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriterTest;
import org.nuxeo.ecm.core.io.marshallers.json.JsonAssert;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.test.MultiRepositoryFeature;
import org.nuxeo.runtime.test.runner.Features;

/**
 * @since 2025.0
 */
@Features(MultiRepositoryFeature.class)
public class DocumentModelJsonWriterMultiRepositoryTest
        extends AbstractJsonWriterTest.Local<DocumentModelJsonWriter, DocumentModel> {

    @Inject
    private CoreSession session;

    @Inject
    @Named("other")
    protected CoreSession otherSession;

    // NXP-30615
    @Test
    public void testMultiRepo() throws IOException {
        // create a document in another repo
        DocumentModel doc = otherSession.createDocumentModel("/", "file", "File");
        doc.setPropertyValue("dc:title", "bar foo");
        doc = otherSession.createDocument(doc);

        // write the document with the default session in ctx
        RenderingContext ctxDefault = RenderingContext.CtxBuilder.properties("*").session(session).get();
        JsonAssert json = jsonAssert(doc, ctxDefault);
        assertNotNull(json);
    }
}
