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

import static org.nuxeo.ecm.restapi.opensearch1.OpenSearchPassthroughComponent.PASSTHROUGH_ELASTICSEARCH_AUDIT_ENABLED_PROPERTY;
import static org.nuxeo.ecm.restapi.opensearch1.OpenSearchPassthroughComponent.PASSTHROUGH_ELASTICSEARCH_ENABLED_PROPERTY;

import org.nuxeo.ecm.core.search.client.opensearch1.IgnoreIfNotOpenSearchSearchClient;
import org.nuxeo.ecm.core.test.CoreSearchFeature;
import org.nuxeo.runtime.test.runner.ConditionalIgnore;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.RunnerFeature;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

/**
 * @since 2025.13
 */
@Features(CoreSearchFeature.class)
@ConditionalIgnore(condition = IgnoreIfNotOpenSearchSearchClient.class)
@WithFrameworkProperty(name = PASSTHROUGH_ELASTICSEARCH_ENABLED_PROPERTY, value = "true")
@WithFrameworkProperty(name = PASSTHROUGH_ELASTICSEARCH_AUDIT_ENABLED_PROPERTY, value = "true")
public class OpenSearchPassthroughFeature implements RunnerFeature {
}
