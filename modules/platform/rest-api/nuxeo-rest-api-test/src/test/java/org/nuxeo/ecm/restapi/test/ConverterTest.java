/*
 * (C) Copyright 2015-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Thomas Roger
 */
package org.nuxeo.ecm.restapi.test;

import static org.apache.http.HttpStatus.SC_ACCEPTED;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * @since 7.3
 */
@RunWith(FeaturesRunner.class)
@Features({ RestServerFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
@WithFrameworkProperty(name = "nuxeo.test.workmanager.stream.storestate.enabled", value = "true")
@Deploy("org.nuxeo.ecm.core.cache")
@Deploy("org.nuxeo.ecm.platform.convert")
public class ConverterTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected EventService eventService;

    @Inject
    protected RestServerFeature restServerFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void shouldConvertBlobUsingNamedConverter() {
        doSynchronousConversion("converter", "any2pdf", false);
    }

    protected void doSynchronousConversion(String paramName, String paramValue, boolean convertDocument) {
        DocumentModel doc = createDummyDocument();

        String path = "/path" + doc.getPathAsString() + "/";
        if (!convertDocument) {
            path += "@blob/file:content/";
        }
        path += "@convert";
        httpClient.buildPostRequest(path)
                  .entity(Map.of(paramName, paramValue))
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    protected DocumentModel createDummyDocument() {
        DocumentModel doc = session.createDocumentModel("/", "adoc", "File");
        Blob blob = Blobs.createBlob("Dummy txt", "text/plain", null, "dummy.txt");
        doc.setPropertyValue("file:content", (Serializable) blob);
        doc = session.createDocument(doc);
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
        return doc;
    }

    @Test
    public void shouldConvertBlobUsingMimeType() {
        doSynchronousConversion("type", "application/pdf", false);
    }

    @Test
    public void shouldConvertBlobUsingFormat() {
        doSynchronousConversion("format", "pdf", false);
    }

    @Test
    public void shouldConvertDocument() {
        doSynchronousConversion("converter", "any2pdf", true);
    }

    @Test
    public void shouldScheduleAsynchronousConversionUsingNamedConverter() {
        doAsynchronousConversion("converter", "any2pdf");
    }

    public void doAsynchronousConversion(String paramName, String paramValue) {
        DocumentModel doc = createDummyDocument();

        String pollingURLFormat = "/conversions/%s/poll";
        String resultURLFormat = "/conversions/%s/result";
        String conversionId = httpClient.buildPostRequest("path" + doc.getPathAsString() + "/@convert")
                                        .entity(Map.of(paramName, paramValue, "async", "true"))
                                        .executeAndThen(new JsonNodeHandler(SC_ACCEPTED), node -> {
                                            assertNotNull(node);
                                            assertEquals("conversionScheduled", node.get("entity-type").textValue());
                                            String id = node.get("conversionId").textValue();
                                            assertNotNull(id);
                                            String pollingURL = node.get("pollingURL").textValue();
                                            String computedPollingURL = String.format(
                                                    restServerFeature.getRestApiUrl() + pollingURLFormat, id);
                                            assertEquals(computedPollingURL, pollingURL);
                                            String resultURL = node.get("resultURL").textValue();
                                            String computedResultURL = String.format(
                                                    restServerFeature.getRestApiUrl() + resultURLFormat, id);
                                            assertEquals(computedResultURL, resultURL);
                                            return id;
                                        });

        String pollingURL = String.format(pollingURLFormat, conversionId);
        String computedResultURL = String.format(resultURLFormat, conversionId);

        httpClient.buildGetRequest(pollingURL).executeAndConsume(new JsonNodeHandler(), node -> {
            assertNotNull(node);
            assertEquals("conversionStatus", node.get("entity-type").textValue());
            String id = node.get("conversionId").textValue();
            assertNotNull(id);
            String resultURL = node.get("resultURL").textValue();
            assertEquals(restServerFeature.getRestApiUrl() + computedResultURL, resultURL);
            String status = node.get("status").textValue();
            assertTrue(status, status.equals("scheduled") || status.equals("running") || status.equals("completed"));
        });

        // wait for the conversion to finish
        eventService.waitForAsyncCompletion();

        // polling URL should redirect to the result URL when done
        httpClient.buildGetRequest(pollingURL).executeAndConsume(new JsonNodeHandler(), node -> {
            String url = node.get("resultURL").textValue();
            assertEquals(restServerFeature.getRestApiUrl() + computedResultURL, url);
        });

        // retrieve the converted blob
        httpClient.buildGetRequest(computedResultURL)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    @Test
    public void shouldScheduleAsynchronousConversionUsingMimeType() {
        doAsynchronousConversion("type", "application/pdf");
    }

    @Test
    public void shouldScheduleAsynchronousConversionUsingFormat() {
        doAsynchronousConversion("format", "pdf");
    }

    @Test
    public void shouldAllowSynchronousConversionUsingPOST() {
        DocumentModel doc = createDummyDocument();

        httpClient.buildPostRequest("/path" + doc.getPathAsString() + "/@convert")
                  .entity(Map.of("converter", "any2pdf"))
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

}
