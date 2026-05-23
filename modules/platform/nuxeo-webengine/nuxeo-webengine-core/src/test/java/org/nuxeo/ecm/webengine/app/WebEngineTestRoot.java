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

import java.util.Map;

import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.webengine.model.AdapterResource;
import org.nuxeo.ecm.webengine.model.Template;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;

/**
 * @since 2025.0
 */
@Path("/webengine-test")
@Produces("application/json;charset=UTF-8")
@WebObject(type = "WebEngineTestRoot")
public class WebEngineTestRoot extends ModuleRoot {

    @GET
    @Path("/simple-string")
    public String getSimpleString() {
        return """
                {
                  "key1": "value1",
                  "key2": "value2"
                }""";
    }

    @GET
    @Path("/simple-map")
    public Map<String, String> getSimpleMap() {
        return Map.of("key1", "value1", "key2", "value2");
    }

    @GET
    @Path("/simple-response")
    public Response getSimpleResponse() {
        return Response.ok(getSimpleString()).build();
    }

    @GET
    @Path("/simple-exception")
    public String getSimpleException() {
        throw new RuntimeException("Just throwing an exception");
    }

    @GET
    @Path("/nuxeo-exception")
    @Produces("*/*")
    public String getNuxeoException(@QueryParam("statusCode") @DefaultValue("500") Integer statusCode) {
        throw new NuxeoException("Throwing an exception with given status code", statusCode);
    }

    @POST
    @Path("/simple-string")
    public String postSimpleString(String body) {
        String name = body.replaceFirst("^(?:.|[\n\r])*\"name\":\\s*\"(.+)\"(?:.|[\n\r])*$", "$1");
        return """
                {
                  "message": "Hello %s, String body was received!"
                }
                """.formatted(name);
    }

    @POST
    @Path("/simple-map")
    public Map<String, String> postSimpleMap(Map<String, String> body) {
        String name = body.get("name");
        return Map.of("message", "Hello %s, Map body was received!".formatted(name));
    }

    @GET
    @Path("/simple-template")
    public Template getSimpleTemplate() {
        return getTemplate("simple-template.ftl");
    }

    @GET
    @Path("/simple-view")
    public Template getSimpleView() {
        return getView("simple-view");
    }

    @GET
    @Path("/bindings-view")
    public Template getBindingsView() {
        return getView("bindings-view");
    }

    @Path("/web-object")
    public WebEngineTestObject getWebObject() {
        return newObject(WebEngineTestObject.class, getClass().getSimpleName());
    }

    @Override
    @Path("@{segment}")
    public AdapterResource disptachAdapter(@PathParam("segment") String adapterName) {
        // override to provide the origin
        return newAdapter(adapterName, getClass().getSimpleName());
    }
}
