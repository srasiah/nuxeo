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
 *     Benoit Delbosc
 */
package org.nuxeo.ecm.restapi.opensearch1.filter;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.search.SearchIndexingService;
import org.nuxeo.ecm.core.search.SearchService;
import org.nuxeo.ecm.core.search.client.opensearch1.OpenSearchSearchClient;
import org.nuxeo.ecm.core.security.SecurityService;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Validate request inputs.
 *
 * @since 7.3
 */
public class RequestValidator {

    protected static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
                                                                  .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                                                                  .build();

    final private Map<String, List<String>> indexTypes;

    public RequestValidator() {
        var types = List.of("doc");
        var service = Framework.getService(SearchService.class);
        var indexingService = Framework.getService(SearchIndexingService.class);
        indexTypes = service.getRepositoryNames()
                            .stream()
                            .map(service::getIndexNames)
                            .flatMap(List::stream)
                            .map(service::getSearchIndex)
                            .filter(idx -> "opensearch".equals(idx.client()))
                            .map(idx -> ((OpenSearchSearchClient) indexingService.getClient(
                                    idx.client())).getTechnicalIndexes().get(idx.index()))
                            .collect(Collectors.toMap(Function.identity(), index -> types));
    }

    public void checkValidDocumentId(String documentId) {
        if (documentId == null) {
            throw new IllegalArgumentException("Invalid document id");
        }
    }

    @Nonnull
    public String getIndices(String indices) {
        if (indices == null || "*".equals(indices) || "_all".equals(indices)) {
            return StringUtils.join(indexTypes.keySet(), ',');
        }

        for (String index : indices.split(",")) {
            if (!indexTypes.containsKey(index)) {
                throw new IllegalArgumentException("Invalid index submitted: " + index);
            }
        }
        return indices;
    }

    public String getPayload(String payload) {
        if (payload == null) {
            return null;
        }
        // validate payload
        try {
            OBJECT_MAPPER.readValue(payload, Map.class);
        } catch (JsonProcessingException e) {
            throw new NuxeoException("Unable to read the payload", HttpServletResponse.SC_BAD_REQUEST);
        }
        return payload;
    }

    public void checkAccess(NuxeoPrincipal principal, String docAcl) {
        try {
            JSONObject docAclJson = new JSONObject(docAcl);
            JSONArray acl = docAclJson.getJSONObject("fields").getJSONArray("ecm:acl");
            String[] principals = SecurityService.getPrincipalsToCheck(principal);
            for (int i = 0; i < acl.length(); i++)
                for (String name : principals) {
                    if (name.equals(acl.getString(i))) {
                        return;
                    }
                }
        } catch (JSONException e) {
            // throw a securityException
        }
        throw new SecurityException("Unauthorized access");
    }
}
