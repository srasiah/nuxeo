/*
 * (C) Copyright 2015-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     <a href="mailto:grenard@nuxeo.com">Guillaume Renard</a>
 */
package org.nuxeo.ecm.restapi.opensearch1.filter;

import static org.nuxeo.ecm.restapi.opensearch1.OpenSearchPassthroughComponent.PASSTHROUGH_ELASTICSEARCH_AUDIT_ENABLED_PROPERTY;

import org.json.JSONException;
import org.nuxeo.audit.opensearch1.OpenSearchAuditBackend;
import org.nuxeo.audit.service.AuditBackend;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.restapi.opensearch1.AbstractSearchRequestFilterImpl;
import org.nuxeo.runtime.api.Framework;

/**
 * Define a elasticsearch passthrough filter for audit index. Only administrator can access the audit index.
 *
 * @since 7.4
 */
public class AuditRequestFilter extends AbstractSearchRequestFilterImpl {

    @Override
    public void init(CoreSession session, String indices, String rawQuery, String payload) {
        try {
            RequestValidator validator = new RequestValidator();
            principal = session.getPrincipal();
            if (!principal.isAdministrator()) {
                throw new IllegalArgumentException("Invalid index submitted: " + indices);
            }
            if (!Framework.isBooleanPropertyTrue(PASSTHROUGH_ELASTICSEARCH_AUDIT_ENABLED_PROPERTY)) {
                throw new IllegalArgumentException("Audit OpenSearch passthrough is disabled");
            }
            var auditBackend = Framework.getService(AuditBackend.class);
            if (!(auditBackend instanceof OpenSearchAuditBackend osAuditBackend)) {
                throw new IllegalArgumentException("Invalid configured audit backend");
            }
            this.indices = osAuditBackend.getIndexName();
            this.rawQuery = rawQuery;
            this.payload = validator.getPayload(payload);
            if (payload == null && !principal.isAdministrator()) {
                // here we turn the UriSearch query_string into a body search
                extractPayloadFromQuery();
            }
        } catch (NoClassDefFoundError e) {
            throw new IllegalArgumentException("Audit OpenSearch Backend is not installed", e);
        }
    }

    @Override
    public String getPayload() throws JSONException {
        return payload;
    }

}
