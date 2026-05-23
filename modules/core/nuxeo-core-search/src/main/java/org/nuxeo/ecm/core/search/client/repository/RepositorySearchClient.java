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
package org.nuxeo.ecm.core.search.client.repository;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;

import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.ScrollResult;
import org.nuxeo.ecm.core.search.AbstractSearchClient;
import org.nuxeo.ecm.core.search.BulkIndexingRequest;
import org.nuxeo.ecm.core.search.BulkIndexingResponse;
import org.nuxeo.ecm.core.search.SearchClientException;
import org.nuxeo.ecm.core.search.SearchHit;
import org.nuxeo.ecm.core.search.SearchIndex;
import org.nuxeo.ecm.core.search.SearchQuery;
import org.nuxeo.ecm.core.search.SearchResponse;
import org.nuxeo.ecm.core.search.SearchScrollContext;
import org.nuxeo.ecm.core.search.index.DefaultIndexingJsonWriter;
import org.nuxeo.ecm.core.search.index.IndexingJsonWriter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A SearchClient baked on repository search.
 *
 * @since 2025.0
 */
public class RepositorySearchClient extends AbstractSearchClient {

    protected static final IndexingJsonWriter INDEXING_WRITER = new DefaultIndexingJsonWriter();

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected final RepositorySearchResponseTransformer responseTransformer;

    protected final String repository;

    public RepositorySearchClient(RepositorySearchClientDescriptor descriptor) {
        super(descriptor);
        repository = descriptor.repository;
        responseTransformer = new RepositorySearchResponseTransformer(getCapabilities());
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean hasCapability(Capability capability) {
        return switch (capability) {
            default -> false;
        };
    }

    @Override
    public void dropIndex(String name) {
        // not needed
    }

    @Override
    public void dropAndInitIndex(String indexName) {
        // not needed
    }

    @Override
    public BulkIndexingResponse indexDocuments(BulkIndexingRequest request) {
        return BulkIndexingResponse.buildResponse(request.getSearchIndex()).build();
    }

    @Override
    public void refresh(String indexName) {
        // not needed
    }

    @Override
    public String getDocument(String indexName, String documentId) {
        CoreSession session = CoreInstance.getCoreSession(repository);
        try {
            DocumentModel doc = session.getDocument(new IdRef(documentId));
            try (StringWriter stringWriter = new StringWriter();
                    JsonGenerator generator = MAPPER.getFactory().createGenerator(stringWriter)) {
                INDEXING_WRITER.writeDocument(generator, doc);
                return stringWriter.toString();
            } catch (IOException e) {
                throw new SearchClientException(e);
            }
        } catch (DocumentNotFoundException e) {
            throw new SearchClientException(e);
        }
    }

    @Override
    public Long getDocumentVersion(String indexName, String documentId) {
        CoreSession session = CoreInstance.getCoreSession(repository);
        try {
            DocumentModel doc = session.getDocument(new IdRef(documentId));
            return (Long) doc.getPropertyValue("dc:modified");
        } catch (DocumentNotFoundException e) {
            throw new SearchClientException(e);
        }
    }

    @Override
    public SearchResponse search(SearchQuery query) {
        CoreSession session = CoreInstance.getCoreSession(repository, query.getPrincipal());
        String nxql = query.getQuery().toString();
        if (query.isScrollSearch()) {
            ScrollResult<String> repositoryScrollResponse = session.scroll(nxql, query.getScrollSize(),
                    Math.toIntExact(query.getScrollKeepAlive().toSeconds()));
            SearchScrollContext scrollContext = new SearchScrollContext(query, repositoryScrollResponse.getScrollId());
            return SearchResponse.builder(makeSearchHits(query.getSearchIndexes().getFirst(), repositoryScrollResponse))
                                 .scroll(scrollContext)
                                 .build();
        }
        var repositorySearchResponse = session.queryProjection(nxql, query.getLimit(), query.getOffset(), true);
        return responseTransformer.apply(query, repositorySearchResponse);
    }

    @Override
    public SearchResponse searchScroll(SearchScrollContext scrollContext) {
        var searchIndex = scrollContext.searchQuery().getSearchIndexes().getFirst();
        CoreSession session = CoreInstance.getCoreSession(repository);
        ScrollResult<String> repositoryScrollResponse = session.scroll(scrollContext.scrollId());
        SearchScrollContext newScrollId = new SearchScrollContext(scrollContext.searchQuery(),
                repositoryScrollResponse.getScrollId());
        return SearchResponse.builder(makeSearchHits(searchIndex, repositoryScrollResponse))
                             .scroll(newScrollId)
                             .build();
    }

    protected List<SearchHit> makeSearchHits(SearchIndex searchIndex, ScrollResult<String> repositoryScrollResponse) {
        String index = searchIndex.index();
        return repositoryScrollResponse.getResults()
                                       .stream()
                                       .map(docId -> SearchHit.builder(index, docId)
                                                              .repository(repository)
                                                              .docId(docId)
                                                              .build())
                                       .toList();
    }

    @Override
    public boolean clearScroll(SearchScrollContext scrollContext) {
        return true;
    }

    @Override
    public void close() {
        // not needed
    }
}
