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
package org.nuxeo.ecm.restapi.server.management;

import static jakarta.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static jakarta.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static jakarta.servlet.http.HttpServletResponse.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.nuxeo.ecm.core.search.BaseCoreSearchFeature.forceRefresh;
import static org.nuxeo.ecm.core.search.index.IndexingDomainEventProducer.DISABLE_AUTO_INDEXING;

import java.time.Duration;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import org.junit.Test;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.search.SearchIndex;
import org.nuxeo.ecm.core.search.SearchQuery;
import org.nuxeo.ecm.core.search.SearchService;
import org.nuxeo.ecm.core.test.CoreSearchFeature;
import org.nuxeo.ecm.restapi.test.ManagementBaseTest;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @since 2025.0
 */
@Features({ RestServerFeature.class, CoreSearchFeature.class })
public class TestSearchObject extends ManagementBaseTest {

    public static final String GET_ALL_DOCUMENTS_QUERY = "SELECT * from Document";

    @Inject
    protected CoreSession coreSession;

    @Inject
    protected SearchService searchService;

    @Inject
    protected BulkService bulkService;

    @Inject
    protected CoreSearchFeature coreSearchFeature;

    @Test
    public void shouldRunIndexing() {
        // Init indexes and drop if any
        assumeTrue("Only for implementation that can init index", coreSearchFeature.dropAndInitIndex());

        // Initial docs count
        long initialDocCount = coreSession.query(GET_ALL_DOCUMENTS_QUERY).totalSize();

        // Nothing indexed because the index was dropped
        assertEquals(0,
                searchService.search(SearchQuery.builder(GET_ALL_DOCUMENTS_QUERY, coreSession).build()).getHitsCount());

        // Create new documents without indexing them
        createDocuments();

        // Nothing indexed because of disable indexing flag
        assertEquals(0,
                searchService.search(SearchQuery.builder(GET_ALL_DOCUMENTS_QUERY, coreSession).build()).getHitsCount());

        // Start the ES indexing of all document of the coreSession repository
        httpClient.buildPostRequest("/management/search/reindex")
                  .executeAndConsume(new JsonNodeHandler(), node -> verifyIndexingResponse(node, 3 + initialDocCount));
    }

    @Test
    public void shouldRunIndexingByNXQLQuery() {
        assumeTrue("Only for implementation that can init index", coreSearchFeature.dropAndInitIndex());

        // Create new documents without indexing them
        createDocuments();

        String query = "SELECT * FROM Document WHERE dc:title LIKE 'Title of my-file%'";
        // Start the ES indexing of document that match the nxql query (2 files)
        httpClient.buildPostRequest("/management/search/reindex")
                  .addQueryParameter("query", query)
                  .executeAndConsume(new JsonNodeHandler(), node -> verifyIndexingResponse(node, 2));
    }

    @Test
    public void shouldRunIndexingOnDocumentAndItsChildren() {
        assumeTrue("Only for implementation that can init index", coreSearchFeature.dropAndInitIndex());

        // Create new documents without indexing them
        createDocuments();

        // Retrieve the Root document (our Folder) and build the resource path
        String docId = coreSession.getDocument(new PathRef("/default-domain/workspaces/Folder")).getId();

        // Start the ES indexing for a given document (folder) and its children (2 files)
        httpClient.buildPostRequest("/management/search/" + docId + "/reindex")
                  .executeAndConsume(new JsonNodeHandler(), node -> verifyIndexingResponse(node, 3));
    }

    @Test
    public void shouldFailRunningIndexingWhenRepositoryNotExists() {
        httpClient.buildPostRequest("/repo/unExistingRepository/management/search/reindex")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NOT_FOUND, status.intValue()));
    }

    @Test
    public void shouldRunIndexingWithQueryLimit() {
        assumeTrue("Only for implementation that can init index", coreSearchFeature.dropAndInitIndex());

        // Create new documents without indexing them
        createDocuments();

        String query = "SELECT * FROM Document'";
        // Start the ES indexing of document that match the nxql query (2 files)
        httpClient.buildPostRequest("/management/search/reindex")
                  .addQueryParameter("query", query)
                  .addQueryParameter("queryLimit", "2")
                  .executeAndConsume(new JsonNodeHandler(), node -> verifyIndexingResponse(node, 2));
    }

    @Test
    public void shouldRunIndexingWithSpecificIndex() {
        assumeTrue("Only for implementation that can init index", coreSearchFeature.dropAndInitIndex());
        // Create new documents without indexing them
        createDocuments();
        String query = "SELECT * FROM Document'";
        // Unknown index got 400 reply
        httpClient.buildPostRequest("/management/search/reindex")
                  .addQueryParameter("query", query)
                  .addQueryParameter("index", "unknown")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_BAD_REQUEST, status.intValue()));

        // reindex only repository which does nothing, no doc indexed
        httpClient.buildPostRequest("/management/search/reindex")
                  .addQueryParameter("query", query)
                  .addQueryParameter("index", "repository")
                  .executeAndConsume(new JsonNodeHandler(), node -> verifyIndexingResponse(node, 0));

        // reindex only repository and enhanced, this time we should have 2 docs
        httpClient.buildPostRequest("/management/search/reindex")
                  .addQueryParameter("query", query)
                  .addQueryParameter("queryLimit", "2")
                  .addQueryParameter("index", "repository")
                  .addQueryParameter("index", "enhanced")
                  .executeAndConsume(new JsonNodeHandler(), node -> verifyIndexingResponse(node, 2));

    }

    @Test
    public void testCheckSearch() {
        httpClient.buildGetRequest("/management/search/checkSearch")
                  .executeAndConsume(new JsonNodeHandler(), jsonNode -> {
                      assertTrue(jsonNode.isObject());
                      Framework.getService(SearchService.class)
                               .getIndexNames(coreSession.getRepositoryName())
                               .stream()
                               .map(searchService::getSearchIndex)
                               .forEach(searchIndex -> checkAssert(jsonNode,
                                       searchIndex.client() + '/' + searchIndex.index()));
                  });
    }

    @Test
    public void testCheckSearchOnSpecificIndexes() {
        // default all indexes
        httpClient.buildGetRequest("/management/search/checkSearch")
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
        // explicit index
        String anIndex = Framework.getService(SearchService.class)
                                  .getIndexNames(coreSession.getRepositoryName())
                                  .stream()
                                  .map(searchService::getSearchIndex)
                                  .map(SearchIndex::index)
                                  .findFirst()
                                  .orElseThrow(() -> new IllegalStateException("No index found"));
        httpClient.buildGetRequest("/management/search/checkSearch")
                  .addQueryParameter("index", anIndex)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
        // unknown
        httpClient.buildGetRequest("/management/search/checkSearch")
                  .addQueryParameter("index", "unknown")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_BAD_REQUEST, status.intValue()));
    }

    protected void checkAssert(JsonNode response, String searchIndex) {
        assertTrue("Response doesn't have result for searchIndex: " + searchIndex, response.has(searchIndex));
        JsonNode node = response.get(searchIndex);
        assertTrue(searchIndex, node.isObject());
        assertEquals(searchIndex, 4, node.get("resultsCount").asInt());
        assertEquals(searchIndex, 10, node.get("pageSize").asInt());
    }

    @Test
    public void testWait() {
        httpClient.buildPostRequest("/management/search/wait")
                  .addQueryParameter("waitForAudit", "true")
                  .addQueryParameter("waitForBulkService", "true")
                  .addQueryParameter("refresh", "true")
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));
    }

    /**
     * Allows us to verify the indexing process.
     */
    protected void verifyIndexingResponse(JsonNode jsonNode, long expectedHits) {
        assertTrue(jsonNode.has("commandId"));
        assertTrue(jsonNode.has("state"));

        // Check the indexing status: at this step the indexing is launched but we are not sure about the exactly
        // value of its progress status
        assertBulkStatusScheduled(jsonNode);

        // Wait until the end of the ES indexing and then assert our expected indexed documents
        var commandId = jsonNode.get("commandId").asText();
        try {
            assertTrue("Bulk action didn't finish", bulkService.await(commandId, Duration.ofMinutes(1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        forceRefresh();
        var response = searchService.search(SearchQuery.builder(GET_ALL_DOCUMENTS_QUERY, coreSession).build());
        assertEquals(expectedHits, response.getHitsCount());
    }

    /**
     * Creates documents, this method creates two files under a folder (3 documents)
     */
    protected void createDocuments() {
        DocumentModel folder = coreSession.createDocumentModel("/default-domain/workspaces/", "Folder", "Folder");
        folder.setPropertyValue("dc:title", "My Folder");
        folder.putContextData(DISABLE_AUTO_INDEXING, Boolean.TRUE);
        folder = coreSession.createDocument(folder);

        String folderPath = folder.getPathAsString();
        Stream.of("my-file-1", "my-file-2").forEach(fileName -> {
            DocumentModel doc = coreSession.createDocumentModel(folderPath, fileName, "File");
            doc.setPropertyValue("dc:title", String.format("Title of %s", fileName));
            doc.putContextData(DISABLE_AUTO_INDEXING, Boolean.TRUE);
            coreSession.createDocument(doc);
        });
        coreSession.save();
        // Commit the transaction
        txFeature.nextTransaction();
    }
}
