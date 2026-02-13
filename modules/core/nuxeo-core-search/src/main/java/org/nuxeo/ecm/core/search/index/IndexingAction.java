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
package org.nuxeo.ecm.core.search.index;

import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.ecm.core.search.SearchClient.Capability.INDEXING;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.bulk.action.computation.AbstractBulkComputation;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.search.BulkIndexingRequest;
import org.nuxeo.ecm.core.search.IndexingRequest;
import org.nuxeo.ecm.core.search.SearchClient;
import org.nuxeo.ecm.core.search.SearchIndexingService;
import org.nuxeo.ecm.core.search.SearchService;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

/**
 * @since 2025.0
 */
public class IndexingAction implements StreamProcessorTopology {

    public static final String ACTION_NAME = "indexing";

    public static final String ACTION_FULL_NAME = "bulk/" + ACTION_NAME;

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                       .addComputation(() -> new IndexingComputation(ACTION_FULL_NAME),
                               List.of(INPUT_1 + ":" + ACTION_FULL_NAME, //
                                       OUTPUT_1 + ":" + STATUS_STREAM))
                       .build();

    }

    public static class IndexingComputation extends AbstractBulkComputation {

        public static final String DELETE_PARAM = "delete";

        protected boolean delete;

        protected Set<String> filterIndexes = Set.of();

        public IndexingComputation(String name) {
            super(name);
        }

        @Override
        public void startBucket(String bucketKey) {
            BulkCommand command = getCurrentCommand();
            Serializable deleteParam = command.getParam(DELETE_PARAM);
            delete = deleteParam != null && Boolean.parseBoolean(deleteParam.toString());
        }

        @Override
        protected void compute(CoreSession session, List<String> ids, Map<String, Serializable> properties) {
            String repository = session.getRepositoryName();
            var requestBuilder = BulkIndexingRequest.buildRequest(false);
            for (String id : ids) {
                requestBuilder.add(delete ? IndexingRequest.delete(id) : IndexingRequest.upsert(id));
            }
            var indexingService = Framework.getService(SearchIndexingService.class);
            var searchService = Framework.getService(SearchService.class);
            var indexes = searchService.getIndexNames(repository);
            Set<String> failures = new HashSet<>();
            for (String index : indexes) {
                if (!filterIndexes.isEmpty() && !filterIndexes.contains(index)) {
                    continue;
                }
                var searchIndex = searchService.getSearchIndex(index);
                SearchClient client = indexingService.getClient(searchIndex.client());
                if (!client.hasCapability(INDEXING)) {
                    continue;
                }
                var response = indexingService.indexDocuments(requestBuilder.build(searchIndex));
                for (var failure : response.getFailures()) {
                    delta.inError(0, String.format("Cannot index doc: %s, failure: %s, index: %s", failure.documentId(),
                            failure.failureMessage(), index), 0);
                    failures.add(failure.documentId());
                }
            }
            delta.setErrorCount(failures.size());
        }
    }
}
