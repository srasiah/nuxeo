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
 *
 * Contributors:
 *     Kevin Leturc <kevin.leturc@hyland.com>
 */
package org.nuxeo.ecm.webengine.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.function.UnaryOperator;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.io.CoreIOFeature;
import org.nuxeo.ecm.core.test.ServletContainerTransactionalFeature;
import org.nuxeo.ecm.webengine.WebEngineCoreFeature;
import org.nuxeo.runtime.cluster.ClusterFeature;
import org.nuxeo.runtime.test.runner.ConsoleLogLevelThreshold;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.nuxeo.runtime.test.runner.LogFeature;
import org.nuxeo.runtime.test.runner.LoggerLevel;
import org.nuxeo.runtime.test.runner.ServletContainerFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlFactory;

/**
 * @since 2025.0
 */
@RunWith(FeaturesRunner.class)
@Features({ ServletContainerTransactionalFeature.class, CoreIOFeature.class, ClusterFeature.class,
        WebEngineCoreFeature.class, LogFeature.class, LogCaptureFeature.class })
@Deploy("org.nuxeo.ecm.webengine.rest")
@Deploy("org.nuxeo.ecm.webengine.core.test")
@LoggerLevel(klass = TransactionHelper.class, level = "OFF") // mute No transaction associated with current thread
@SuppressWarnings("unchecked")
public class TestWebEngineApplication {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    protected ServletContainerFeature servletContainerFeature;

    @Inject
    protected LogCaptureFeature.Result logCaptureResult;

    @Test
    public void testGetSimpleString() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/simple-string");

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("value1", json.get("key1"));
        assertEquals("value2", json.get("key2"));
    }

    @Test
    public void testGetSimpleMap() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/simple-map");

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("value1", json.get("key1"));
        assertEquals("value2", json.get("key2"));
    }

    @Test
    public void testGetSimpleResponse() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/simple-response");

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("value1", json.get("key1"));
        assertEquals("value2", json.get("key2"));
    }

    @Test
    @ConsoleLogLevelThreshold("FATAL") // mute No transaction associated with current thread
    @LogCaptureFeature.FilterOn(loggerClass = WebEngineExceptionMapper.class)
    public void testGetSimpleException() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/simple-exception");

        assertEquals(500, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("exception", json.get("entity-type"));
        assertEquals(500, json.get("status"));
        assertEquals("Internal Server Error", json.get("message"));

        var caughtEventMessages = logCaptureResult.getCaughtEventMessages();
        assertEquals(1, caughtEventMessages.size());
        assertEquals("java.lang.RuntimeException: Just throwing an exception", caughtEventMessages.getFirst());
    }

    @Test
    @LogCaptureFeature.FilterOn(loggerClass = WebEngineExceptionMapper.class)
    public void testGetNuxeoExceptionWithoutAccept() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/nuxeo-exception?statusCode=400");

        assertEquals(400, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("exception", json.get("entity-type"));
        assertEquals(400, json.get("status"));
        assertEquals("Throwing an exception with given status code", json.get("message"));

        var caughtEventMessages = logCaptureResult.getCaughtEventMessages();
        assertEquals(0, caughtEventMessages.size());
    }

    @Test
    @LogCaptureFeature.FilterOn(loggerClass = WebEngineExceptionMapper.class)
    public void testGetNuxeoExceptionWithJsonAccept() throws IOException {
        HttpResponse<String> response = executeRequest("/webengine-test/nuxeo-exception?statusCode=400",
                builder -> builder.GET().setHeader("Accept", "application/json"));

        assertEquals(400, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("exception", json.get("entity-type"));
        assertEquals(400, json.get("status"));
        assertEquals("Throwing an exception with given status code", json.get("message"));

        var caughtEventMessages = logCaptureResult.getCaughtEventMessages();
        assertEquals(0, caughtEventMessages.size());
    }

    // NXP-33086
    @Test
    @LogCaptureFeature.FilterOn(loggerClass = WebEngineExceptionMapper.class)
    public void testGetNuxeoExceptionWithUnsupportedAccept() throws IOException {
        // this test is to check that we're reaching JsonNuxeoExceptionWriter when Accept is not application/json
        // as it we're avoiding the MessageBodyProviderNotFoundException, and we're not flooding the logs
        HttpResponse<String> response = executeRequest("/webengine-test/nuxeo-exception?statusCode=400",
                builder -> builder.GET().setHeader("Accept", "application/something"));

        assertEquals(400, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("exception", json.get("entity-type"));
        assertEquals(400, json.get("status"));
        assertEquals("Throwing an exception with given status code", json.get("message"));

        var caughtEventMessages = logCaptureResult.getCaughtEventMessages();
        assertEquals(0, caughtEventMessages.size());
    }

    // NXP-33086
    @Test
    @LogCaptureFeature.FilterOn(loggerClass = WebEngineExceptionMapper.class)
    public void testGetNuxeoExceptionWithHtmlAccept() {
        HttpResponse<String> response = executeRequest("/webengine-test/nuxeo-exception?statusCode=400",
                builder -> builder.GET().setHeader("Accept", "text/html"));

        assertEquals(400, response.statusCode());
        assertEquals("""
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8"/>
                  <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
                  <title>An error occurred</title>
                  <meta name="viewport" content="width=device-width">

                  <link rel="icon" type="image/png" href="/nuxeo/icons/favicon.png" />
                  <link rel="shortcut icon" type="image/x-icon" href="/nuxeo/icons/favicon.ico" />
                  <style type="text/css">
                      <!--
                      body {
                        background: url("/nuxeo/img/error_pages/page_background.gif") repeat scroll 0 0 transparent;
                        color: #999;
                        font: normal 100%/1.5 "Lucida Grande", Arial, Verdana, sans-serif;
                        margin: 0;
                        text-align: center
                      }

                      .container {
                        margin: 2em auto;
                        text-align: center;
                        width: 70%
                      }

                      h1 {
                        color: #000;
                        font-size: 150%;
                        margin: 3.5em 0 .5em 0
                      }

                      h2 {
                        color: #b20000;
                        font-size: 110%;
                        margin: 1em
                      }

                      h1, h2 {
                        font-weight: bold
                      }

                      p {
                        max-width: 600px;
                        margin: .4em auto
                      }

                      .errorDetail {
                        background-color: #fff;
                        height: 40%;
                        margin: 1em auto;
                        overflow: auto;
                        text-align: left;
                        width: 100%
                      }

                      .errorDetail .scrollableBlock {
                        max-height: 50em;
                        overflow-y: scroll;
                      }

                      .block {
                        border: 1px solid #ccc;
                        border-radius: 5px;
                        margin: 0.5em;
                        padding: 0.5em;
                      }
                      -->
                  </style>
                </head>
                <body>

                <section>
                  <div class="container">
                    <h1>An error occurred</h1>
                    <h2>400 - Throwing an exception with given status code</h2>
                  </div>
                </section>

                </body>
                </html>
                """, response.body());

        var caughtEventMessages = logCaptureResult.getCaughtEventMessages();
        assertEquals(0, caughtEventMessages.size());
    }

    @Test
    public void testPostSimpleString() throws IOException {
        HttpResponse<String> response = executeRequest("/webengine-test/simple-string",
                builder -> builder.POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "name": "WebEngine Tester"
                        }""")));

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("Hello WebEngine Tester, String body was received!", json.get("message"));
    }

    @Test
    public void testPostSimpleMap() throws IOException {
        HttpResponse<String> response = executeRequest("/webengine-test/simple-map",
                builder -> builder.POST(HttpRequest.BodyPublishers.ofString("""
                        {
                          "name": "WebEngine Tester"
                        }""")));

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("Hello WebEngine Tester, Map body was received!", json.get("message"));
    }

    @Test
    public void testGetSimpleTemplate() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/simple-template");

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("simple-template.ftl", json.get("location"));
    }

    @Test
    public void testGetSimpleView() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/simple-view");

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("view/WebEngineTestRoot/simple-view.ftl", json.get("location"));
    }

    @Test
    public void testGetSimpleViewWithMediaType() throws IOException {
        HttpResponse<String> response = executeRequest("/webengine-test/simple-view",
                builder -> builder.GET().setHeader("Content-Type", "application/xml"));

        assertEquals(200, response.statusCode());
        Map<String, Object> xml = new ObjectMapper(new XmlFactory()).readValue(response.body(), Map.class);
        assertEquals("view/WebEngineTestRoot/simple-view-xml.ftl", xml.get("location"));
    }

    @Test
    public void testGetBindingsView() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/bindings-view");

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("view/WebEngineTestRoot/bindings-view.ftl", json.get("location"));
        // NXP-33137 - test Resource#getPath returns the Resource path and not the matched path
        assertEquals("/webengine-test", json.get("Root.path"));
    }

    @Test
    public void testGetWebObject() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/web-object");

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("WebEngineTestObject", json.get("location"));
        assertEquals("WebEngineTestRoot", json.get("origin"));
    }

    // NXP-21290 - test injection of jersey object
    @Test
    public void testGetWebObjectMyHeader() throws IOException {
        HttpResponse<String> response = executeRequest("/webengine-test/web-object/my-header",
                builder -> builder.GET().setHeader("X-My-Header", "What a value"));

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("What a value", json.get("header"));
    }

    // NXP-33137 - test Resource#getPath returns the Resource path and not the matched path
    @Test
    public void testGetWebObjectMyResourcePath() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/web-object/my-resource-path");

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("/webengine-test/web-object", json.get("resourcePath"));
    }

    @Test
    public void testGetWebObjectMyObject() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/web-object/my-object");

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("MyObjectPerRequestProvider", json.get("location"));
        var providerReference = json.get("providerReference");
        var uuid = json.get("uuid");

        response = executeGETRequest("/webengine-test/web-object/my-object");

        assertEquals(200, response.statusCode());
        json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("MyObjectPerRequestProvider", json.get("location"));
        // providerReference should be a singleton
        assertEquals(providerReference, json.get("providerReference"));
        // uuids should be different as the injected object has PerRequest scope
        assertNotEquals(uuid, json.get("uuid"));
    }

    @Test
    public void testGetWebAdapter() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/@web-adapter");

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("WebEngineTestAdapter", json.get("location"));
        assertEquals("WebEngineTestRoot", json.get("origin"));
    }

    @Test
    public void testGetWebAdapterGetWebObject() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/@web-adapter/web-object");

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("WebEngineTestObject", json.get("location"));
        assertEquals("WebEngineTestRoot/WebEngineTestAdapter", json.get("origin"));
    }

    // NXP-21398
    @Test
    public void testGetWebAdapterGetWebObjectGetWebAdapter() throws IOException {
        HttpResponse<String> response = executeGETRequest("/webengine-test/@web-adapter/web-object/@web-adapter");

        assertEquals(200, response.statusCode());
        Map<String, Object> json = MAPPER.readValue(response.body(), Map.class);
        assertEquals("WebEngineTestAdapter", json.get("location"));
        assertEquals("WebEngineTestRoot/WebEngineTestAdapter/WebEngineTestObject", json.get("origin"));
    }

    protected HttpResponse<String> executeGETRequest(String endpoint) {
        return executeRequest(endpoint, HttpRequest.Builder::GET);
    }

    protected HttpResponse<String> executeRequest(String endpoint, UnaryOperator<HttpRequest.Builder> customizer) {
        try (var client = HttpClient.newHttpClient()) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(
                    new URI("http://localhost:" + servletContainerFeature.getPort() + endpoint))
                                                     .setHeader("Content-Type", "application/json");
            HttpRequest request = customizer.andThen(HttpRequest.Builder::build).apply(builder);
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new AssertionError("Unexpected exception", e);
        }
    }
}
