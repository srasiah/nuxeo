/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
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

import java.time.Duration;
import java.util.List;

/**
 * Service responsible for managing search index operations including indexing, reindexing, and refreshing documents.
 * This service is primarily intended for internal usage as document indexing typically occurs automatically.
 * 
 * @since 2025.0
 */
public interface SearchIndexingService {

    /**
     * Internal: Indexes documents according to the specified bulk indexing request.
     *
     * @param request the bulk indexing request containing documents to index
     * @return the indexing response with operation details
     */
    BulkIndexingResponse indexDocuments(BulkIndexingRequest request);

    /**
     * Internal: Reindexes the entire repository by dropping and recreating all associated search indexes.
     *
     * @param repository the repository name
     * @return the bulk command id for the reindexing operation
     */
    String reindexRepository(String repository);

    /**
     * Internal: Reindexes the entire repository by dropping and recreating the given search indexes.
     *
     * @param indexNames the list of indexes to reindex, all indexes must be on the same repository
     * @return the bulk command id for the reindexing operation
     * @since 2025.11
     */
    String reindexRepository(String repository, List<String> indexNames);

    /**
     * Reindexes documents matching the specified NXQL query for all search indexes in the repository.
     *
     * @param repository the repository name
     * @param nxql the NXQL query to select documents
     * @return the bulk command id for the reindexing operation
     */
    default String reindexDocuments(String repository, String nxql) {
        return reindexDocuments(repository, nxql, -1);
    }

    /**
     * Reindexes documents matching the specified NXQL query up to the given limit for all search indexes of the
     * repository.
     *
     * @param repository the repository name
     * @param nxql the NXQL query to select documents
     * @param queryLimit maximum number of documents to index, or -1 for no limit
     * @return the bulk command id for the reindexing operation
     * @since 2025.8
     */
    String reindexDocuments(String repository, String nxql, long queryLimit);

    /**
     * Reindexes documents matching the specified NXQL query for the given limit only for the specified indexes.
     *
     * @param repository the repository name
     * @param nxql the NXQL query to select documents
     * @param queryLimit maximum number of documents to index, or -1 for no limit
     * @param indexNames search indexes to reindex, all search indexes must point to the same repository
     * @return the bulk command id for the reindexing operation
     * @since 2025.11
     */
    String reindexDocuments(String repository, String nxql, long queryLimit, List<String> indexNames);

    /**
     * Refreshes a search index to make newly indexed documents immediately searchable.
     *
     * @param index the search index to refresh
     */
    void refresh(SearchIndex index);

    /**
     * Internal: Retrieves a search client by name.
     *
     * @param clientName the name of the search client
     * @return the search client instance
     */
    SearchClient getClient(String clientName);

    /**
     * Waits for completion of all indexing activities. <b>Intended for testing purposes only</b>.
     *
     * @param duration the maximum duration to wait
     * @return {@code true} if all indexing operations completed, {@code false} if timeout occurred
     * @throws InterruptedException if the current thread is interrupted while waiting
     */
    boolean await(Duration duration) throws InterruptedException;
}
