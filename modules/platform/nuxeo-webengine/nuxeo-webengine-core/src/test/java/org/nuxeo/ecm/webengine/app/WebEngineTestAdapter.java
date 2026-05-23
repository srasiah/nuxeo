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

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.nuxeo.ecm.webengine.model.WebAdapter;
import org.nuxeo.ecm.webengine.model.impl.DefaultAdapter;

/**
 * @since 2025.0
 */
@WebAdapter(name = "web-adapter", type = "WebEngineTestAdapter")
@Produces("application/json;charset=UTF-8")
public class WebEngineTestAdapter extends DefaultAdapter {

    protected String origin;

    @Override
    public void initialize(Object... args) {
        assert args != null && args.length == 1;
        origin = (String) args[0];
    }

    @GET
    public Map<String, String> get() {
        return Map.of("location", getClass().getSimpleName(), "origin", origin);
    }

    @Path("/web-object")
    public WebEngineTestObject getWebObject() {
        return newObject(WebEngineTestObject.class, origin + '/' + getClass().getSimpleName());
    }
}
