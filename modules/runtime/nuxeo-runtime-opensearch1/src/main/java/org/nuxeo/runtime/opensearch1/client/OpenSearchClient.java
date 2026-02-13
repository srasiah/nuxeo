/*
 * (C) Copyright 2017-2024 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.runtime.opensearch1.client;

import java.time.Duration;

import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.get.GetRequest;
import org.opensearch.action.get.GetResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.action.index.IndexResponse;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.ClearScrollResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.cluster.health.ClusterHealthStatus;

/**
 * @since 9.3
 */
public interface OpenSearchClient extends AutoCloseable {

    /**
     * @return The client id
     */
    String getId();

    // -------------------------------------------------------------------
    // Admin
    //

    /**
     * @return Whether the targeted OpenSearch is ready or not (its status is at least
     *         {@link ClusterHealthStatus#YELLOW}
     * @since 2025.0
     */
    boolean isReady();

    boolean waitForYellowStatus(String[] indexNames, Duration timeout);

    void refresh(String indexName);

    void flush(String indexName);

    void optimize(String indexName);

    void createIndex(String indexName, String jsonSettings);

    boolean indexExists(String indexName);

    /**
     * Drops the index and wait for the action to be effective until 5 minutes.
     */
    default void dropIndex(String indexName) {
        dropIndex(indexName, Duration.ofMinutes(5));
    }

    void dropIndex(String indexName, Duration timeout);

    void createMapping(String indexName, String jsonMapping);

    boolean mappingExists(String indexName);

    /**
     * Returns the mapping from elastic, exposed for testing purposes
     *
     * @since 2021.17
     */
    String getMapping(String indexName);

    // -------------------------------------------------------------------
    // Search
    //

    IndexResponse index(IndexRequest request);

    BulkResponse bulk(BulkRequest request);

    GetResponse get(GetRequest request);

    SearchResponse search(SearchRequest request);

    SearchResponse scroll(SearchScrollRequest request);

    ClearScrollResponse clearScroll(ClearScrollRequest request);
}
