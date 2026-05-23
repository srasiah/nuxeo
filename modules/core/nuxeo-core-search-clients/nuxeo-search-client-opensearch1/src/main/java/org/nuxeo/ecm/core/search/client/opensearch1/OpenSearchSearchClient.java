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
package org.nuxeo.ecm.core.search.client.opensearch1;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.nuxeo.ecm.core.search.client.opensearch1.OpenSearchQueryTransformer.getKeepAlive;

import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.search.AbstractSearchClient;
import org.nuxeo.ecm.core.search.BulkIndexingRequest;
import org.nuxeo.ecm.core.search.BulkIndexingResponse;
import org.nuxeo.ecm.core.search.IndexingRequest;
import org.nuxeo.ecm.core.search.SearchClientException;
import org.nuxeo.ecm.core.search.SearchQuery;
import org.nuxeo.ecm.core.search.SearchResponse;
import org.nuxeo.ecm.core.search.SearchScrollContext;
import org.nuxeo.runtime.RetryableException;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.opensearch1.OpenSearchClientService;
import org.nuxeo.runtime.opensearch1.OpenSearchComponent;
import org.nuxeo.runtime.opensearch1.client.OpenSearchClient;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.delete.DeleteRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.action.support.WriteRequest;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.VersionType;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.builder.SearchSourceBuilder;

/**
 * @since 2025.0
 */
public class OpenSearchSearchClient extends AbstractSearchClient {

    private static final Logger log = LogManager.getLogger(OpenSearchSearchClient.class);

    public static final String ECM_ANCESTOR_FIELDS = "ecm:ancestorId";

    protected final OpenSearchResponseTransformer responseTransformer;

    protected final OpenSearchClient client;

    protected final Map<String, String> indexes;

    protected final OpenSearchQueryTransformer queryTransformer;

    public OpenSearchSearchClient(OpenSearchSearchClientDescriptor descriptor,
            Map<String, OpenSearchHintQueryBuilder> hints) {
        super(descriptor);
        client = Framework.getService(OpenSearchClientService.class).getClient(descriptor.getClientId());
        indexes = descriptor.getSearchIndexes()
                            .entrySet()
                            .stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getTechnicalName()));
        queryTransformer = new OpenSearchQueryTransformer(indexes, hints);
        responseTransformer = new OpenSearchResponseTransformer(getCapabilities());
    }

    @Override
    public boolean isReady() {
        return client.isReady();
    }

    @Override
    public boolean hasCapability(Capability capability) {
        return switch (capability) {
            case INIT_INDEX -> true;
            case INDEXING -> true;
            case HIGHLIGHT -> true;
            case AGGREGATE -> true;
            case MULTI_REPOSITORIES -> true;
        };
    }

    @Override
    public void dropIndex(String name) {
        client.dropIndex(name);
    }

    @Override
    public void dropAndInitIndex(String indexName) {
        ((OpenSearchComponent) Framework.getService(OpenSearchClientService.class)).dropAndInitIndex(
                indexes.get(indexName));
    }

    @Override
    public BulkIndexingResponse indexDocuments(BulkIndexingRequest request) {
        log.debug("indexDocuments: {}", request);
        String indexName = indexes.get(request.getSearchIndex().index());
        BulkRequest bulkRequest = new BulkRequest();
        int count = 0;
        for (IndexingRequest item : request.getRequests()) {
            if (item.isDelete()) {
                bulkRequest.add(new DeleteRequest(indexName, item.getDocumentId()).versionType(VersionType.EXTERNAL)
                                                                                  .version(request.getVersion()));
                if (item.isDeleteRecursive()) {
                    // recurse delete are processed first before bulk index/delete command
                    var query = QueryBuilders.constantScoreQuery(
                            QueryBuilders.termQuery(ECM_ANCESTOR_FIELDS, item.getDocumentId()));
                    doRecurseDelete(indexName, query);
                }
                count++;
            } else {
                String source = item.getSource();
                if (isEmpty(source)) {
                    // the doc might have been deleted, skip it
                    // TODO: Could be filter in SearchService#indexDocuments or reported as such in the response
                    log.debug("No json source, skipping indexing command: {}", item);
                } else {
                    bulkRequest.add(new IndexRequest(indexName).id(item.getDocumentId())
                                                               .versionType(VersionType.EXTERNAL)
                                                               .version(request.getVersion())
                                                               .source(source, XContentType.JSON));
                    count++;
                }
            }
        }
        if (request.isRefresh()) {
            bulkRequest.setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE);
        }
        // TODO check bulk request size and split if necessary
        var responseBuilder = BulkIndexingResponse.buildResponse(request.getSearchIndex());
        if (count > 0) {
            try {
                var bulkResponse = client.bulk(bulkRequest);
                bulkResponse.forEach(item -> {
                    if (item.isFailed()) {
                        responseBuilder.addFailure(item.getId(), item.getFailureMessage());
                    }
                });
            } catch (RuntimeServiceException e) {
                throw new SearchClientException(e);
            }
        }
        return responseBuilder.build();
    }

    @Override
    public void refresh(String indexName) {
        try {
            client.refresh(indexes.get(indexName));
        } catch (RuntimeServiceException e) {
            throw new SearchClientException(e);
        }
    }

    protected void doRecurseDelete(String indexName, QueryBuilder queryBuilder) {
        log.debug("delete recurse: {}", queryBuilder);
        try {
            TimeValue keepAlive = TimeValue.timeValueMinutes(1);
            SearchSourceBuilder search = new SearchSourceBuilder().size(100).query(queryBuilder).fetchSource(false);
            SearchRequest request = new SearchRequest(indexName).scroll(keepAlive).source(search);
            request.scroll(TimeValue.timeValueMinutes(1));

            org.opensearch.action.search.SearchResponse response = null;
            try {
                for (response = client.search(request); //
                        response.getHits().getHits().length > 0; //
                        response = client.scroll(new SearchScrollRequest(response.getScrollId()).scroll(keepAlive))) {
                    // Build bulk delete request
                    BulkRequest bulkRequest = new BulkRequest();
                    for (SearchHit hit : response.getHits().getHits()) {
                        bulkRequest.add(new DeleteRequest(hit.getIndex(), hit.getId()));
                    }
                    log.debug("Bulk delete request on {} elements", bulkRequest.numberOfActions());
                    // Run bulk delete request
                    client.bulk(bulkRequest);
                }
            } finally {
                clearScrollContext(response);
            }
        } catch (RuntimeServiceException e) {
            throw new SearchClientException(e);
        }
    }

    protected void clearScrollContext(org.opensearch.action.search.SearchResponse response) {
        if (response != null && response.getScrollId() != null) {
            ClearScrollRequest closeScrollRequest = new ClearScrollRequest();
            closeScrollRequest.addScrollId(response.getScrollId());
            client.clearScroll(closeScrollRequest);
        }
    }

    @Override
    public String getDocument(String indexName, String documentId) {
        try {
            GetResponse response = client.get(new GetRequest(indexes.get(indexName)).id(documentId));
            if (response.isExists()) {
                return response.getSourceAsString();
            }
            return null;
        } catch (RuntimeServiceException e) {
            throw new SearchClientException(e);
        }
    }

    @Override
    public Long getDocumentVersion(String indexName, String documentId) {
        try {
            GetResponse response = client.get(new GetRequest(indexes.get(indexName)).id(documentId));
            if (response.isExists()) {
                return response.getVersion();
            }
            return null;
        } catch (RuntimeServiceException e) {
            throw new SearchClientException(e);
        }
    }

    @Override
    public SearchResponse search(SearchQuery query) {
        try {
            var osSearchRequest = queryTransformer.apply(query);
            var osSearchResponse = client.search(osSearchRequest);
            if (osSearchResponse.isTimedOut()) {
                log.debug("Search operation timed out: {}", osSearchResponse);
                throw new RetryableException("Search operation timed out");
            }
            return responseTransformer.apply(query, osSearchResponse);
        } catch (IllegalArgumentException e) {
            throw new QueryParseException(e);
        } catch (RetryableException e) {
            throw e;
        } catch (RuntimeServiceException e) {
            // OpenSearchStatusException is raised when using phrase prefix on keyword type
            throw new SearchClientException(e);
        }
    }

    @Override
    public SearchResponse searchScroll(SearchScrollContext scrollContext) {
        var osRequest = new SearchScrollRequest(scrollContext.scrollId()).scroll(
                getKeepAlive(scrollContext.searchQuery()));
        try {
            var osSearchResponse = client.scroll(osRequest);
            if (osSearchResponse.isTimedOut()) {
                log.debug("SearchScroll operation timed out: {}", osSearchResponse);
                throw new RetryableException("SearchScroll operation timed out" + scrollContext);
            }
            return responseTransformer.apply(scrollContext.searchQuery(), osSearchResponse);
        } catch (RetryableException e) {
            throw e;
        } catch (RuntimeServiceException e) {
            throw new SearchClientException(e);
        }
    }

    @Override
    public boolean clearScroll(SearchScrollContext scrollContext) {
        ClearScrollRequest request = new ClearScrollRequest();
        request.addScrollId(scrollContext.scrollId());
        try {
            var response = client.clearScroll(request);
            return response.isSucceeded();
        } catch (RuntimeServiceException e) {
            throw new SearchClientException(e);
        }
    }

    @Override
    public void close() {
        // nothing, the client is handled by its Nuxeo component
    }

    public Map<String, String> getTechnicalIndexes() {
        return indexes;
    }

    @Override
    public String toString() {
        return "<OpenSearchSearchClient name=\"" + name + "\" clientId=\"" + client.getId() + "\" />";
    }
}
