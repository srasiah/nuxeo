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
package org.nuxeo.ecm.core.search;

import java.util.Set;

/**
 * Interface used by the SearchService to access an external Search Cluster. The client must take care of the retry
 * mechanism, in case of failure to process a request or accessing the search backend a SearchClientException must be
 * raised.
 *
 * @since 2025.0
 */
public interface SearchClient extends AutoCloseable {

    String DEFAULT_CLIENT_NAME_PROP = "nuxeo.search.client.default.name";

    /**
     * Gets the client name.
     */
    String getName();

    /**
     * Is the client ready and the search engine healthy to serve requests.
     */
    boolean isReady();

    /**
     * Checks whether the client has the capability.
     */
    boolean hasCapability(Capability capability);

    /**
     * Returns the supported capabilities.
     */
    Set<Capability> getCapabilities();

    /**
     * Drops an index.
     */
    void dropIndex(String name);

    /**
     * Recreate an existing index using the same settings and mapping.
     */
    void dropAndInitIndex(String indexName);

    /**
     * Index documents. Check the response for possible indexing failure.
     *
     * @throws SearchClientException when the search backend is not able to process the request after retries.
     */
    BulkIndexingResponse indexDocuments(BulkIndexingRequest request);

    /**
     * Refreshes an index so newly indexed documents are searchable.
     */
    void refresh(String indexName);

    /**
     * Returns a Json document representation or null if not found.
     */
    String getDocument(String indexName, String documentId);

    /**
     * Returns the version which is the timestamp when document was loaded for indexing
     */
    Long getDocumentVersion(String indexName, String documentId);

    /**
     * Executes a search query.
     */
    SearchResponse search(SearchQuery query);

    /**
     * Iterate on results for a scroll search. The end of scroll is reached when there is no more hit, i.e.
     * {@link SearchResponse#getHitsCount()} returns {@code 0}.
     *
     * @param scrollContext provided by the previous {@link SearchResponse#getScrollContext()};
     */
    SearchResponse searchScroll(SearchScrollContext scrollContext);

    /**
     * Explicitly clear the search scroll context, without waiting for the scroll keep alive to timeout.
     *
     * @return {@code true} if the context is successfully cleared.
     */
    boolean clearScroll(SearchScrollContext scrollContext);

    @Override
    void close();

    enum Capability {
        INIT_INDEX, // The search client can initialize the index configuration @since 2025.16
        INDEXING, // The search client handles indexing of document
        AGGREGATE, // Search with aggregate supported
        HIGHLIGHT, // Search with highlight supported
        MULTI_REPOSITORIES // Search on multiple repositories supported
    }
}
