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
 *     bdelbosc
 */
package org.nuxeo.ecm.core.search.client.mock;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.search.AbstractSearchClient;
import org.nuxeo.ecm.core.search.BulkIndexingRequest;
import org.nuxeo.ecm.core.search.BulkIndexingResponse;
import org.nuxeo.ecm.core.search.IndexingRequest;
import org.nuxeo.ecm.core.search.SearchClientException;
import org.nuxeo.ecm.core.search.SearchQuery;
import org.nuxeo.ecm.core.search.SearchResponse;
import org.nuxeo.ecm.core.search.SearchScrollContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @since 2025.0
 */
public class MockSearchClient extends AbstractSearchClient {

    private static final Logger log = LogManager.getLogger(MockSearchClient.class);

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected final Map<String, String> documents = new ConcurrentHashMap<>();

    protected final Map<String, Long> indexTime = new ConcurrentHashMap<>();

    public MockSearchClient(MockSearchClientDescriptor descriptor) {
        super(descriptor);
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean hasCapability(Capability capability) {
        return switch (capability) {
            case INDEXING -> true;
            default -> false;
        };
    }

    @Override
    public void dropIndex(String name) {
        documents.clear();
    }

    @Override
    public void dropAndInitIndex(String indexName) {
        dropIndex(indexName);
    }

    @Override
    public BulkIndexingResponse indexDocuments(BulkIndexingRequest bulk) {
        long now = System.currentTimeMillis();
        for (IndexingRequest request : bulk.getRequests()) {
            String key = keyOf(bulk.getSearchIndex().index(), request.getDocumentId());
            if (request.isDelete()) {
                log.info("Delete {}, recursive: {}", key, request.isDeleteRecursive());
                String deleted = documents.remove(key);
                indexTime.remove(key);
                if (request.isDeleteRecursive() && deleted != null) {
                    String path = getPathFromDoc(deleted) + "/";
                    log.info("Removing all doc with path: {}", path);
                    for (Iterator<Map.Entry<String, String>> iter = documents.entrySet().iterator(); iter.hasNext();) {
                        Map.Entry<String, String> entry = iter.next();
                        String docPath = getPathFromDoc(entry.getValue());
                        if (docPath.startsWith(path)) {
                            log.info("Deleting doc: {}, with path: {}", entry.getKey(), docPath);
                            iter.remove();
                            indexTime.remove(entry.getKey());
                        }
                    }
                }
            } else {
                String source = request.getSource();
                log.info("Upsert {}: {}", key, source);
                documents.put(key, source);
                indexTime.put(key, now);
            }
        }
        return BulkIndexingResponse.buildResponse(bulk.getSearchIndex()).build();
    }

    @Override
    public void refresh(String indexName) {
        log.debug("Refresh: {}", indexName);
    }

    protected String getPathFromDoc(String doc) {
        if (isBlank(doc)) {
            return "";
        }
        try {
            JsonNode node = MAPPER.readTree(doc).get("ecm:path");
            if (node == null) {
                return "";
            }
            return node.asText();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    protected String keyOf(String indexName, String documentId) {
        return indexName + ":" + documentId;
    }

    @Override
    public String getDocument(String indexName, String documentId) {
        return documents.get(keyOf(indexName, documentId));
    }

    @Override
    public Long getDocumentVersion(String indexName, String documentId) {
        return indexTime.getOrDefault(keyOf(indexName, documentId), null);
    }

    @Override
    public SearchResponse search(SearchQuery query) {
        throw new SearchClientException(getName() + ": Search not implemented");
    }

    @Override
    public SearchResponse searchScroll(SearchScrollContext scrollContext) {
        throw new SearchClientException(getName() + ": Search not implemented");
    }

    @Override
    public boolean clearScroll(SearchScrollContext scrollContext) {
        return false;
    }

    @Override
    public void close() {
        // nothing to do
    }

}
