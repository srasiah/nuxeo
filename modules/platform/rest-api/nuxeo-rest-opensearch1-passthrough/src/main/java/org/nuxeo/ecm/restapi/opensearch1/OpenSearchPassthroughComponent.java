/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.restapi.opensearch1;

import java.util.Map;

import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.capabilities.CapabilitiesService;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * @since 2025.13
 */
public class OpenSearchPassthroughComponent extends DefaultComponent {

    public static final String CAPABILITY_PASSTHROUGH = "passthrough";

    public static final String PASSTHROUGH_ELASTICSEARCH_ENABLED_PROPERTY = "nuxeo.passthrough.elasticsearch.enabled";

    public static final String PASSTHROUGH_ELASTICSEARCH_AUDIT_ENABLED_PROPERTY = "nuxeo.passthrough.elasticsearch.audit.enabled";

    @Override
    public void start(ComponentContext context) {
        Framework.getService(CapabilitiesService.class)
                 .registerCapabilities(CAPABILITY_PASSTHROUGH,
                         Map.of("elasticsearch",
                                 Framework.isBooleanPropertyTrue(PASSTHROUGH_ELASTICSEARCH_ENABLED_PROPERTY),
                                 "elasticsearch-audit",
                                 Framework.isBooleanPropertyTrue(PASSTHROUGH_ELASTICSEARCH_AUDIT_ENABLED_PROPERTY)));
    }
}
