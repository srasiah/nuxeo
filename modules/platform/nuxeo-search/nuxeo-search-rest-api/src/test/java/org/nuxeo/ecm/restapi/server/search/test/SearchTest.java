/*
 * (C) Copyright 2016-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Gabriel Barata <gbarata@nuxeo.com
 */
package org.nuxeo.ecm.restapi.server.search.test;

import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;
import static org.nuxeo.ecm.platform.query.nxql.SearchServicePageProvider.SEARCH_INDEX_OVERRIDE_PARAM;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.search.IgnoreIfSearchClientDoesNotHaveAggregateCapability;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderDefinition;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.restapi.server.search.QueryExecutor;
import org.nuxeo.ecm.restapi.test.JsonNodeHelper;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.ConditionalIgnore;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * Test the various ways to perform queries via search endpoint.
 *
 * @since 8.3
 */
@RunWith(FeaturesRunner.class)
@Features(SearchRestFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
public class SearchTest {

    @Inject
    protected CoreSession session;

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature transactionalFeature;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultJsonClient(
            () -> restServerFeature.getRestApiUrl());

    protected static final String QUERY_EXECUTE_PATH = "search/execute";

    protected static final String SAVED_SEARCH_PATH = "search/saved";

    protected String getSearchPageProviderPath(String providerName) {
        return "search/pp/" + providerName;
    }

    protected String getSavedSearchPath(String id) {
        return "search/saved/" + id;
    }

    protected String getSavedSearchExecutePath(String id) {
        return "search/saved/" + id + "/execute";
    }

    protected String getSearchPageProviderExecutePath(String providerName) {
        return "search/pp/" + providerName + "/execute";
    }

    @Test
    public void iCanPerformQueriesOnRepository() {
        // Given a repository, when I perform a query in NXQL on it
        httpClient.buildGetRequest(QUERY_EXECUTE_PATH)
                  .addQueryParameter("query", "SELECT * FROM Document WHERE ecm:isVersion = 0")
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then I get document listing as result
                          node -> assertEquals(20, JsonNodeHelper.getEntriesSize(node)));

        // Given parameters as page size and ordered parameters
        // Given a repository, when I perform a query in NXQL on it
        httpClient.buildGetRequest(QUERY_EXECUTE_PATH)
                  .addQueryParameter("query", "SELECT * FROM Document WHERE dc:creator = ?")
                  .addQueryParameter("queryParams", "$currentUser")
                  .addQueryParameter("pageSize", "2")
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then I get document listing as result
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanPerformQueriesWithNamedParametersOnRepository() {
        // Given a repository and named parameters, when I perform a query in NXQL on it
        DocumentModel folder = RestServerInit.getFolder(1, session);
        httpClient.buildGetRequest(QUERY_EXECUTE_PATH)
                  .addQueryParameter("query", """
                          SELECT * FROM Document WHERE ecm:parentId = :parentIdVar
                                  AND ecm:mixinType != 'HiddenInNavigation'
                                  AND dc:title IN (:note1,:note2)
                                  AND ecm:isVersion = 0
                                  AND ecm:isTrashed = 0
                          """)
                  .addQueryParameter("note1", "Note 1")
                  .addQueryParameter("note2", "Note 2")
                  .addQueryParameter("parentIdVar", folder.getId())
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then I get document listing as result
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanPerformPageProviderOnRepositoryWithDefaultSort() {
        // Given a repository, when I perform a pageprovider on it
        DocumentModel folder = RestServerInit.getFolder(1, session);
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("TEST_PP"))
                  .addQueryParameter("queryParams", folder.getId())
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then I get document listing as result
                      List<JsonNode> entries = JsonNodeHelper.getEntries(node);
                      assertEquals(2, entries.size());
                      JsonNode jsonNode = entries.get(0);
                      assertEquals("Note 2", jsonNode.get("title").asText());
                      jsonNode = entries.get(1);
                      assertEquals("Note 1", jsonNode.get("title").asText());
                  });
    }

    /**
     * @since 9.3
     */
    @Test
    public void iCanPerformPageProviderOnRepositoryWithOffset() {
        // Given a repository, when I fetched the first page
        ArrayNode notes = (ArrayNode) httpClient.buildGetRequest(getSearchPageProviderExecutePath("TEST_PP_ALL_NOTE"))
                                                .addQueryParameter(QueryExecutor.CURRENT_PAGE_OFFSET, "0")
                                                .addQueryParameter(QueryExecutor.PAGE_SIZE,
                                                        String.valueOf(RestServerInit.MAX_FILE))
                                                .execute(new JsonNodeHandler())
                                                .get("entries");

        // Then I can retrieve the same result using offset
        for (int i = 0; i < RestServerInit.MAX_FILE; i++) {
            JsonNode node = httpClient.buildGetRequest(getSearchPageProviderExecutePath("TEST_PP_ALL_NOTE"))
                                      .addQueryParameter(QueryExecutor.CURRENT_PAGE_OFFSET, String.valueOf(i))
                                      .addQueryParameter(QueryExecutor.PAGE_SIZE, "1")
                                      .execute(new JsonNodeHandler());
            assertEquals(1, JsonNodeHelper.getEntriesSize(node));
            String retrievedTitle = node.get("entries").get(0).get("title").textValue();
            assertEquals(notes.get(i).get("title").textValue(), retrievedTitle);
        }

        // Then I can retrieve the same result using offset and NXQL
        for (int i = 0; i < RestServerInit.MAX_FILE; i++) {
            JsonNode node = httpClient.buildGetRequest(QUERY_EXECUTE_PATH)
                                      .addQueryParameter(QueryExecutor.CURRENT_PAGE_OFFSET, i + "")
                                      .addQueryParameter(QueryExecutor.PAGE_SIZE, "1")
                                      .addQueryParameter("query",
                                              "SELECT * FROM Note WHERE ecm:isVersion = 0 ORDER BY dc:title ASC")
                                      .execute(new JsonNodeHandler());
            assertEquals(1, JsonNodeHelper.getEntriesSize(node));
            String retrievedTitle = node.get("entries").get(0).get("title").textValue();
            assertEquals(notes.get(i).get("title").textValue(), retrievedTitle);
        }
    }

    @Test
    public void iCanPerformPageProviderOnRepositoryWithCustomSort() {
        // Given a repository, when I perform a pageprovider on it
        DocumentModel folder = RestServerInit.getFolder(1, session);
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("TEST_PP"))
                  .addQueryParameter("queryParams", folder.getId())
                  .addQueryParameter("sortBy", "dc:title")
                  .addQueryParameter("sortOrder", "asc")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then I get document listing as result
                      List<JsonNode> entries = JsonNodeHelper.getEntries(node);
                      assertEquals(2, entries.size());
                      JsonNode jsonNode = entries.get(0);
                      assertEquals("Note 1", jsonNode.get("title").asText());
                      jsonNode = entries.get(1);
                      assertEquals("Note 2", jsonNode.get("title").asText());
                  });
    }

    /**
     * @since 9.3
     */
    @Test
    public void iCanSeeMaxResults() {
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("TEST_PP_ALL_NOTE"))
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(4444, node.get("resultsCountLimit").intValue()));
    }

    @Test
    public void iCanPerformPageProviderWithNamedParametersOnRepository() {
        // Given a repository, when I perform a pageprovider on it
        DocumentModel folder = RestServerInit.getFolder(1, session);
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("TEST_PP_PARAM"))
                  .addQueryParameter("note1", "Note 1")
                  .addQueryParameter("note2", "Note 2")
                  .addQueryParameter("parentIdVar", folder.getId())
                  .executeAndConsume(new JsonNodeHandler(),
                          // Then I get document listing as result
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanPerformPageProviderWithQuickFilter() {
        // Given a repository, when I perform a pageprovider on it
        DocumentModel folder = RestServerInit.getFolder(1, session);
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("TEST_PP_QUICK_FILTER"))
                  .addQueryParameter("quickFilters", "testQF,testQF2")
                  .addQueryParameter("parentIdVar", folder.getId())
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      // Then I get document listing as result
                      assertEquals(2, JsonNodeHelper.getEntriesSize(node));

                      assertTrue(node.get("quickFilters").isArray());
                      assertEquals(3, node.get("quickFilters").size());
                      for (JsonNode qf : node.get("quickFilters")) {
                          String name = qf.get("name").textValue();
                          boolean active = qf.get("active").booleanValue();
                          assertEquals("testQF".equals(name) || "testQF2".equals(name), active);
                      }
                  });
    }

    /**
     * @since 8.4
     */
    @Test
    public void iDontAlterPageProviderDefWithQuickFilter() {
        // Given a repository, when I perform a pageprovider on it
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("TEST_PP_QUICK_FILTER2"))
                  .addQueryParameter("quickFilters", "testQF,testQF2")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));

        httpClient.buildGetRequest(getSearchPageProviderExecutePath("TEST_PP_QUICK_FILTER2"))
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(20, JsonNodeHelper.getEntriesSize(node)));
    }

    /**
     * @since 8.4
     */
    @Test
    public void iCanUseAssociativeQuickFilter() {
        // Given a repository, when I perform a pageprovider on it with quick filters
        JsonNode node = httpClient.buildGetRequest(getSearchPageProviderExecutePath("TEST_PP_QUICK_FILTER2"))
                                  .addQueryParameter("quickFilters", "testQF4,testQF")
                                  .execute(new JsonNodeHandler());
        int nbResults = JsonNodeHelper.getEntriesSize(node);

        // When I set the quick filters the other way around
        node = httpClient.buildGetRequest(getSearchPageProviderExecutePath("TEST_PP_QUICK_FILTER2"))
                         .addQueryParameter("quickFilters", "testQF,testQF4")
                         .execute(new JsonNodeHandler());
        // Then I expect the same number of result
        assertEquals(nbResults, JsonNodeHelper.getEntriesSize(node));
    }

    // NXP-30360
    @Test
    public void iCanPerformPageProviderWithQuickFilterAndSortInfo() {
        DocumentModel rootFolder = RestServerInit.getFolder(1, session);
        DocumentModel folder = session.createDocumentModel(rootFolder.getPathAsString(), "folder", "Folder");
        folder.setPropertyValue("dc:title", "Folder");
        session.createDocument(folder);
        transactionalFeature.nextTransaction();

        httpClient.buildGetRequest(getSearchPageProviderExecutePath("default_search"))
                  .addQueryParameter("ecm_path", "[\"" + rootFolder.getPathAsString() + "\"]")
                  .addQueryParameter("sortBy", "dc:title")
                  .addQueryParameter("sortOrder", "desc")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      List<JsonNode> entries = JsonNodeHelper.getEntries(node);
                      assertEquals(6, entries.size());
                      assertEquals("Note 4", entries.get(0).get("title").textValue());
                      assertEquals("Note 3", entries.get(1).get("title").textValue());
                      assertEquals("Note 2", entries.get(2).get("title").textValue());
                      assertEquals("Note 1", entries.get(3).get("title").textValue());
                      assertEquals("Note 0", entries.get(4).get("title").textValue());
                      assertEquals("Folder", entries.get(5).get("title").textValue());
                  });

        httpClient.buildGetRequest(getSearchPageProviderExecutePath("default_search"))
                  .addQueryParameter("ecm_path", "[\"" + rootFolder.getPathAsString() + "\"]")
                  .addQueryParameter("sortBy", "dc:title")
                  .addQueryParameter("sortOrder", "desc")
                  .addQueryParameter("quickFilters", "noFolder")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      List<JsonNode> entries = JsonNodeHelper.getEntries(node);
                      assertEquals(5, entries.size());
                      assertEquals("Note 4", entries.get(0).get("title").textValue());
                      assertEquals("Note 3", entries.get(1).get("title").textValue());
                      assertEquals("Note 2", entries.get(2).get("title").textValue());
                      assertEquals("Note 1", entries.get(3).get("title").textValue());
                      assertEquals("Note 0", entries.get(4).get("title").textValue());
                  });
    }

    @Test
    public void iCanPerformPageProviderWithNamedParametersInvalid() {
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("namedParamProviderInvalid"))
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), node -> assertEquals(
                          "Failed to execute query: SELECT * FROM Document where dc:title=:foo ORDER BY dc:title, Lexical Error: Illegal character <:> at offset 38",
                          JsonNodeHelper.getErrorMessage(node)));
    }

    @Test
    public void iCanPerformPageProviderWithNamedParametersAndDoc() {
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("namedParamProviderWithDoc"))
                  .addQueryParameter("np:title", "Folder 0")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(1, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanPerformPageProviderWithNamedParametersAndDocInvalid() {
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("namedParamProviderWithDocInvalid"))
                  .addQueryParameter("np:title", "Folder 0")
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), node -> assertEquals(
                          "Failed to execute query: SELECT * FROM Document where dc:title=:foo, Lexical Error: Illegal character <:> at offset 38",
                          JsonNodeHelper.getErrorMessage(node)));
    }

    @Test
    public void iCanPerformPageProviderWithNamedParametersInWhereClause() {
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("namedParamProviderWithWhereClause"))
                  .addQueryParameter("parameter1", "Folder 0")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(1, JsonNodeHelper.getEntriesSize(node)));

        // retry without params
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("namedParamProviderWithWhereClause"))
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanPerformPageProviderWithNamedParametersComplex() {
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("namedParamProviderComplex"))
                  .addQueryParameter("parameter1", "Folder 0")
                  .addQueryParameter("np:isCheckedIn", Boolean.FALSE.toString())
                  .addQueryParameter("np:dateMin", "2007-01-30 01:02:03+04:00")
                  .addQueryParameter("np:dateMax", "2007-03-23 01:02:03+04:00")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(1, JsonNodeHelper.getEntriesSize(node)));

        // remove filter on dates
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("namedParamProviderComplex"))
                  .addQueryParameter("parameter1", "Folder 0")
                  .addQueryParameter("np:isCheckedIn", Boolean.FALSE.toString())
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(1, JsonNodeHelper.getEntriesSize(node)));

        // remove filter on parameter1
        httpClient.buildGetRequest(getSearchPageProviderExecutePath("namedParamProviderComplex"))
                  .addQueryParameter("np:isCheckedIn", Boolean.FALSE.toString())
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    /**
     * @since 10.2
     */
    @Test
    @ConditionalIgnore(condition = IgnoreIfSearchClientDoesNotHaveAggregateCapability.class)
    public void iCanPerformPageProviderWithOrWithoutAggregates() {
        String searchPath = getSearchPageProviderExecutePath("aggregates");
        String aggKey;
        int docCount;
        JsonNode jsonNode = httpClient.buildGetRequest(searchPath).execute(new JsonNodeHandler());
        assertTrue(jsonNode.has("aggregations"));
        aggKey = jsonNode.get("aggregations").get("dc_created_agg").get("buckets").get(0).get("key").textValue();
        docCount = jsonNode.get("aggregations").get("dc_created_agg").get("buckets").get(0).get("docCount").intValue();

        httpClient.buildGetRequest(searchPath)
                  .addHeader(PageProvider.SKIP_AGGREGATES_PROP, "true")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertFalse(node.has("aggregations")));

        httpClient.buildGetRequest(searchPath)
                  .addHeader(PageProvider.SKIP_AGGREGATES_PROP, "true")
                  .addQueryParameter("dc_created_agg", "[\"" + aggKey + "\"]")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertTrue(node.has("aggregations"));
                      assertEquals(docCount, node.get("entries").size());
                  });
    }

    @Test
    public void iCanGetPageProviderDefinition() {
        PageProviderService pageProviderService = Framework.getService(PageProviderService.class);
        PageProviderDefinition def = pageProviderService.getPageProviderDefinition("namedParamProviderComplex");

        httpClient.buildGetRequest(getSearchPageProviderPath("namedParamProviderComplex"))
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(def.getName(), node.get("name").textValue()));
    }

    @Test
    public void iCanOverrideSearchServicePPIndex() {
        // default searchIndex
        httpClient.buildGetRequest(getSearchPageProviderPath("TEST_SSPP_ALL_NOTE") + "/execute")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertTrue(JsonNodeHelper.getEntriesSize(node) > 0));
        // explicit searchIndex
        httpClient.buildGetRequest(getSearchPageProviderPath("TEST_SSPP_ALL_NOTE") + "/execute")
                  .addQueryParameter(SEARCH_INDEX_OVERRIDE_PARAM, "repository")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertTrue(JsonNodeHelper.getEntriesSize(node) > 0));
        // unknown searchIndex
        httpClient.buildGetRequest(getSearchPageProviderPath("TEST_SSPP_ALL_NOTE") + "/execute")
                  .addQueryParameter(SEARCH_INDEX_OVERRIDE_PARAM, "unknown")
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), node -> assertEquals(
                          "java.lang.IllegalArgumentException: Invalid page provider configuration 'TEST_SSPP_ALL_NOTE': Configured indexes [unknown] not found for repository 'test'",
                          JsonNodeHelper.getErrorMessage(node)));
    }

    @Test
    public void iCanSaveSearchByQuery() {
        String data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"query\": \"select * from Document where dc:creator = ?\",\n" + "  \"queryLanguage\": \"NXQL\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"2\"\n," + "  \"contentViewData\": \"{"
                + "\\\"viewVar\\\": \\\"value\\\"" + "}\"\n" + "}";

        httpClient.buildPostRequest(SAVED_SEARCH_PATH).entity(data).executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals("savedSearch", node.get("entity-type").textValue());
            assertEquals("search by query", node.get("title").textValue());
            assertEquals("select * from Document where dc:creator = ?", node.get("query").textValue());
            assertEquals("NXQL", node.get("queryLanguage").textValue());
            assertEquals("$currentUser", node.get("queryParams").textValue());
            assertEquals("2", node.get("pageSize").textValue());
            assertEquals("{\"viewVar\": \"value\"}", node.get("contentViewData").textValue());
        });
    }

    @Test
    public void iCanSaveSearchByPageProvider() {
        DocumentModel folder = RestServerInit.getFolder(1, session);
        String data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by page provider\",\n"
                + "  \"pageProviderName\": \"TEST_PP\",\n" + "  \"queryParams\": \"" + folder.getId() + "\",\n"
                + "  \"contentViewData\": \"{" + "\\\"viewVar\\\": \\\"value\\\"" + "}\"\n" + "}";

        httpClient.buildPostRequest(SAVED_SEARCH_PATH).entity(data).executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals("savedSearch", node.get("entity-type").textValue());
            assertEquals("search by page provider", node.get("title").textValue());
            assertEquals("TEST_PP", node.get("pageProviderName").textValue());
            assertEquals(folder.getId(), node.get("queryParams").textValue());
            assertEquals("{\"viewVar\": \"value\"}", node.get("contentViewData").textValue());
        });
    }

    @Test
    public void iCanSaveDefaultSearch() {
        String data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by page provider 2\",\n"
                + "  \"pageProviderName\": \"default_search\",\n" + "  \"pageSize\": \"2\",\n" + "  \"params\": {\n"
                + "    \"ecm_fulltext\": \"Note*\",\n" + "    \"dc_modified_agg\": [\"last24h\"]\n" + "  },\n"
                + "  \"contentViewData\": \"{" + "\\\"viewVar\\\": \\\"value\\\"" + "}\"\n" + "}";
        httpClient.buildPostRequest(SAVED_SEARCH_PATH)
                  .addHeader("x-nxdocumentproperties", "default_search, saved_search")
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertEquals("savedSearch", node.get("entity-type").textValue());
                      assertEquals("search by page provider 2", node.get("title").textValue());
                      assertEquals("default_search", node.get("pageProviderName").textValue());
                      assertEquals("2", node.get("pageSize").textValue());
                      assertEquals("{\"viewVar\": \"value\"}", node.get("contentViewData").textValue());
                      assertTrue(node.has("params"));
                      JsonNode params = node.get("params");
                      assertEquals("Note*", params.get("ecm_fulltext").textValue());
                      assertEquals(1, params.get("dc_modified_agg").size());
                      assertEquals("last24h", params.get("dc_modified_agg").get(0).textValue());
                      assertTrue(node.has("properties"));
                      JsonNode properties = node.get("properties");
                      assertEquals("Note*", properties.get("defaults:ecm_fulltext").textValue());
                      assertEquals(1, properties.get("defaults:dc_modified_agg").size());
                      assertEquals("last24h", properties.get("defaults:dc_modified_agg").get(0).textValue());
                      assertEquals("default_search", properties.get("saved:providerName").textValue());
                  });
    }

    @Test
    public void iCantSaveSearchInvalidParams() {
        String data = "{\n" + "  \"entity-type\": \"savedSearch\",\n"
                + "  \"query\": \"select * from Document where dc:creator = ?\",\n" + "  \"queryLanguage\": \"NXQL\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"2\"\n" + "}";
        httpClient.buildPostRequest(SAVED_SEARCH_PATH)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertInvalidTitle);

        data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"query\": \"select * from Document where dc:creator = ?\",\n" + "  \"queryLanguage\": \"NXQL\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"2\",\n"
                + "  \"pageProviderName\": \"TEST_PP\"\n" + "}";
        httpClient.buildPostRequest(SAVED_SEARCH_PATH)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertMixedQueryAndPageProvider);

        data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"query\": \"select * from Document where dc:creator = ?\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"2\",\n"
                + "  \"pageProviderName\": \"TEST_PP\"\n" + "}";
        httpClient.buildPostRequest(SAVED_SEARCH_PATH)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertMixedQueryAndPageProvider);

        data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"queryLanguage\": \"NXQL\",\n" + "  \"queryParams\": \"$currentUser\",\n"
                + "  \"pageSize\": \"2\",\n" + "  \"pageProviderName\": \"TEST_PP\"\n" + "}";
        httpClient.buildPostRequest(SAVED_SEARCH_PATH)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertMixedQueryAndPageProvider);

        data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"query\": \"select * from Document where dc:creator = ?\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"2\"\n" + "}";
        httpClient.buildPostRequest(SAVED_SEARCH_PATH)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertMissingQueryLanguage);

        data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"queryLanguage\": \"NXQL\",\n" + "  \"queryParams\": \"$currentUser\",\n"
                + "  \"pageSize\": \"2\"\n" + "}";
        httpClient.buildPostRequest(SAVED_SEARCH_PATH)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertMissingQuery);

        data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"2\"\n" + "}";
        httpClient.buildPostRequest(SAVED_SEARCH_PATH)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertMissingParams);
    }

    // NXP-31456
    @Test
    public void iCanSaveSearchWithNullValueParams() {
        String data = "{\n" + "  \"entity-type\": \"savedSearch\",\n"
                + "  \"query\": \"select * from Document where dc:creator = ?\",\n" + "  \"queryLanguage\": \"NXQL\",\n"
                + "  \"params\":{\"dublincore_modified\":null},\"title\":\"bar\"}";
        httpClient.buildPostRequest(SAVED_SEARCH_PATH).entity(data).executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals(node.get("entity-type").asText(), "savedSearch");
            assertEquals(node.get("title").asText(), "bar");
            assertEquals(node.get("params").size(), 1);
            assertTrue(node.get("params").get("dublincore_modified") instanceof NullNode);
        });
    }

    @Test
    public void iCanGetSavedSearchByQuery() {
        String path = getSavedSearchPath(RestServerInit.getSavedSearchId(1, session));
        httpClient.buildGetRequest(path).executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals("savedSearch", node.get("entity-type").textValue());
            assertEquals("my saved search 1", node.get("title").textValue());
            assertEquals("select * from Document where dc:creator = ?", node.get("query").textValue());
            assertEquals("NXQL", node.get("queryLanguage").textValue());
            assertEquals("$currentUser", node.get("queryParams").textValue());
            assertEquals("2", node.get("pageSize").textValue());
        });
    }

    @Test
    public void iCanGetSavedSearchByPageProvider() {
        String path = getSavedSearchPath(RestServerInit.getSavedSearchId(2, session));
        httpClient.buildGetRequest(path).executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals("savedSearch", node.get("entity-type").textValue());
            assertEquals("my saved search 2", node.get("title").textValue());
            assertEquals("TEST_PP", node.get("pageProviderName").textValue());
            DocumentModel folder = RestServerInit.getFolder(1, session);
            assertEquals(folder.getId(), node.get("queryParams").textValue());
        });
    }

    @Test
    public void iCantGetSavedSearchInvalidId() {
        httpClient.buildGetRequest(getSavedSearchPath("-1"))
                  .executeAndConsume(new JsonNodeHandler(SC_NOT_FOUND),
                          node -> assertEquals("-1", JsonNodeHelper.getErrorMessage(node)));
    }

    @Test
    public void iCanUpdateSearchByQuery() {
        String data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"my search 1\",\n"
                + "  \"query\": \"select * from Document where dc:creator = ?\",\n" + "  \"queryLanguage\": \"NXQL\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"1\",\n" + "  \"contentViewData\": \"{"
                + "\\\"viewVar\\\": \\\"another value\\\"" + "}\"\n" + "}";

        String path = getSavedSearchPath(RestServerInit.getSavedSearchId(1, session));
        httpClient.buildPutRequest(path).entity(data).executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals("savedSearch", node.get("entity-type").textValue());
            assertEquals("my search 1", node.get("title").textValue());
            assertEquals("select * from Document where dc:creator = ?", node.get("query").textValue());
            assertEquals("NXQL", node.get("queryLanguage").textValue());
            assertEquals("$currentUser", node.get("queryParams").textValue());
            assertEquals("1", node.get("pageSize").textValue());
            assertEquals("{\"viewVar\": \"another value\"}", node.get("contentViewData").textValue());
        });
    }

    @Test
    public void iCanUpdateSearchByPageProvider() {
        String data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"my search 2\",\n"
                + "  \"pageProviderName\": \"TEST_PP\",\n" + "  \"contentViewData\": \"{"
                + "\\\"viewVar\\\": \\\"another value\\\"" + "}\"\n" + "}";

        String path = getSavedSearchPath(RestServerInit.getSavedSearchId(2, session));
        httpClient.buildPutRequest(path).entity(data).executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals("savedSearch", node.get("entity-type").textValue());
            assertEquals("my search 2", node.get("title").textValue());
            assertEquals("TEST_PP", node.get("pageProviderName").textValue());
            assertNull(node.get("queryParams").textValue());
            assertEquals("{\"viewVar\": \"another value\"}", node.get("contentViewData").textValue());
        });
    }

    @Test
    public void iCantUpdateSearchInvalidId() {
        String data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"my search 1\",\n"
                + "  \"query\": \"select * from Document where dc:creator = ?\",\n" + "  \"queryLanguage\": \"NXQL\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"1\",\n" + "  \"contentViewData\": \"{"
                + "\\\"viewVar\\\": \\\"another value\\\"" + "}\"\n" + "}";

        httpClient.buildPutRequest(getSavedSearchPath("-1"))
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_NOT_FOUND),
                          node -> assertEquals("-1", JsonNodeHelper.getErrorMessage(node)));
    }

    @Test
    public void iCantUpdateSearchInvalidQueryOrPageProvider() {
        String path = getSavedSearchPath(RestServerInit.getSavedSearchId(1, session));

        String data = "{\n" + "  \"entity-type\": \"savedSearch\",\n"
                + "  \"query\": \"select * from Document where dc:creator = ?\",\n" + "  \"queryLanguage\": \"NXQL\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"1\"\n" + "}";
        httpClient.buildPutRequest(path)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertInvalidTitle);

        data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"query\": \"select * from Document where dc:creator = ?\",\n" + "  \"queryLanguage\": \"NXQL\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"2\",\n"
                + "  \"pageProviderName\": \"TEST_PP\"\n" + "}";
        httpClient.buildPutRequest(path)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertMixedQueryAndPageProvider);

        data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"query\": \"select * from Document where dc:creator = ?\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"2\",\n"
                + "  \"pageProviderName\": \"TEST_PP\"\n" + "}";
        httpClient.buildPutRequest(path)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertMixedQueryAndPageProvider);

        data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"queryLanguage\": \"NXQL\",\n" + "  \"queryParams\": \"$currentUser\",\n"
                + "  \"pageSize\": \"2\",\n" + "  \"pageProviderName\": \"TEST_PP\"\n" + "}";
        httpClient.buildPutRequest(path)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertMixedQueryAndPageProvider);

        data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"query\": \"select * from Document where dc:creator = ?\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"2\"\n" + "}";
        httpClient.buildPutRequest(path)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertMissingQueryLanguage);

        data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"queryLanguage\": \"NXQL\",\n" + "  \"queryParams\": \"$currentUser\",\n"
                + "  \"pageSize\": \"2\"\n" + "}";
        httpClient.buildPutRequest(path)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertMissingQuery);

        data = "{\n" + "  \"entity-type\": \"savedSearch\",\n" + "  \"title\": \"search by query\",\n"
                + "  \"queryParams\": \"$currentUser\",\n" + "  \"pageSize\": \"2\"\n" + "}";
        httpClient.buildPutRequest(path)
                  .entity(data)
                  .executeAndConsume(new JsonNodeHandler(SC_BAD_REQUEST), this::assertMissingParams);
    }

    @Test
    public void iCanDeleteSearch() {
        httpClient.buildDeleteRequest(getSavedSearchPath(RestServerInit.getSavedSearchId(1, session)))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));
    }

    @Test
    public void iCantDeleteSearchInvalidId() {
        httpClient.buildDeleteRequest(getSavedSearchPath("-1"))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NOT_FOUND, status.intValue()));
    }

    @Test
    public void iCanExecuteSavedSearchByQuery() {
        httpClient.buildGetRequest(getSavedSearchExecutePath(RestServerInit.getSavedSearchId(1, session)))
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanExecuteSavedSearchByQueryWithParams() {
        httpClient.buildGetRequest(getSavedSearchExecutePath(RestServerInit.getSavedSearchId(1, session)))
                  .addQueryParameter("pageSize", "5")
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(5, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanExecuteSavedSearchByPageProvider() {
        httpClient.buildGetRequest(getSavedSearchExecutePath(RestServerInit.getSavedSearchId(2, session)))
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanExecuteDefaultSavedSearch() {
        assumeTrue("fulltext search not supported", coreFeature.getStorageConfiguration().supportsFulltextSearch());
        // this saved search uses ecm:fulltext so some databases doing async fulltext indexing will need a pause
        transactionalFeature.nextTransaction();

        httpClient.buildGetRequest(getSavedSearchExecutePath(RestServerInit.getSavedSearchId(3, session)))
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(2, JsonNodeHelper.getEntriesSize(node)));
    }

    @Test
    public void iCanSearchSavedSearches() {
        httpClient.buildGetRequest(SAVED_SEARCH_PATH).executeAndConsume(new JsonNodeHandler(), node -> {
            assertTrue(node.isContainerNode());
            assertTrue(node.has("entries"));
            assertTrue(node.get("entries").isArray());
            assertEquals(3, node.get("entries").size());
        });
    }

    @Test
    public void iCanSearchSavedSearchesParamPageProvider() {
        httpClient.buildGetRequest(SAVED_SEARCH_PATH)
                  .addQueryParameter("pageProvider", "TEST_PP")
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertTrue(node.isContainerNode());
                      assertTrue(node.has("entries"));
                      assertTrue(node.get("entries").isArray());
                      assertEquals(1, node.get("entries").size());
                      node = node.get("entries").get(0);
                      assertEquals("my saved search 2", node.get("title").textValue());
                      assertEquals("TEST_PP", node.get("pageProviderName").textValue());
                      DocumentModel folder = RestServerInit.getFolder(1, session);
                      assertEquals(folder.getId(), node.get("queryParams").textValue());
                  });
    }

    private void assertInvalidTitle(JsonNode node) {
        assertEquals("title cannot be empty", JsonNodeHelper.getErrorMessage(node));
    }

    private void assertMixedQueryAndPageProvider(JsonNode node) {
        assertEquals("query and page provider parameters are mutually exclusive"
                + " (query, queryLanguage, pageProviderName)", JsonNodeHelper.getErrorMessage(node));
    }

    private void assertMissingParams(JsonNode node) {
        assertEquals("query or page provider parameters are missing" + " (query, queryLanguage, pageProviderName)",
                JsonNodeHelper.getErrorMessage(node));
    }

    private void assertMissingQueryLanguage(JsonNode node) {
        assertEquals("queryLanguage parameter is missing", JsonNodeHelper.getErrorMessage(node));
    }

    private void assertMissingQuery(JsonNode node) {
        assertEquals("query parameter is missing", JsonNodeHelper.getErrorMessage(node));
    }

}
