/*
 * (C) Copyright 2014-2024 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.platform.query.nxql;

import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_AVG;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_CARDINALITY;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_COUNT;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_MAX;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_MIN;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_MISSING;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_SUM;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_TYPE_DATE_HISTOGRAM;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_TYPE_DATE_RANGE;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_TYPE_HISTOGRAM;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_TYPE_RANGE;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_TYPE_TERMS;
import static org.nuxeo.ecm.platform.query.api.PageProviderService.NAMED_PARAMETERS;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.query.QueryParseException;
import org.nuxeo.ecm.core.search.SearchIndex;
import org.nuxeo.ecm.core.search.SearchQuery;
import org.nuxeo.ecm.core.search.SearchResponse;
import org.nuxeo.ecm.core.search.SearchService;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.AggregateDefinition;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.core.AggregateAvg;
import org.nuxeo.ecm.platform.query.core.AggregateCardinality;
import org.nuxeo.ecm.platform.query.core.AggregateCount;
import org.nuxeo.ecm.platform.query.core.AggregateDateHistogram;
import org.nuxeo.ecm.platform.query.core.AggregateDateRange;
import org.nuxeo.ecm.platform.query.core.AggregateHistogram;
import org.nuxeo.ecm.platform.query.core.AggregateMax;
import org.nuxeo.ecm.platform.query.core.AggregateMin;
import org.nuxeo.ecm.platform.query.core.AggregateMissing;
import org.nuxeo.ecm.platform.query.core.AggregateRange;
import org.nuxeo.ecm.platform.query.core.AggregateSum;
import org.nuxeo.ecm.platform.query.core.AggregateTerm;
import org.nuxeo.ecm.platform.query.core.SearchServicePageProviderDescriptor;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.services.config.ConfigurationService;

/**
 * Search Service Page provider that converts the NXQL query build by CoreQueryDocumentPageProvider.
 *
 * @since 5.9.3
 */
public class SearchServicePageProvider extends CoreQueryDocumentPageProvider {

    @Serial
    private static final long serialVersionUID = 1L;

    protected static final Logger log = LogManager.getLogger(SearchServicePageProvider.class);

    public static final String SEARCH_ON_ALL_REPOSITORIES_PROPERTY = "searchAllRepositories";

    public static final String MAX_RESULT_WINDOW_PROPERTY = "org.nuxeo.elasticsearch.provider.maxResultWindow";

    public static final int DEFAULT_MAX_RESULT_WINDOW = 10_000;

    /** @since 2025.11 */
    public static final String SEARCH_INDEX_OVERRIDE_PARAM = "index";

    protected List<SearchIndex> searchIndexes;

    protected Map<String, Aggregate<? extends Bucket>> currentAggregates;

    protected Long maxResultWindow;

    @Override
    public List<DocumentModel> getCurrentPage() {
        long t0 = System.currentTimeMillis();
        // use a cache
        if (currentPageDocuments != null) {
            return currentPageDocuments;
        }
        error = null;
        errorMessage = null;
        log.debug("Perform query for provider '{}': with pageSize={}, offset={}", this::getName,
                this::getMinMaxPageSize, this::getCurrentPageOffset);
        currentPageDocuments = new ArrayList<>();
        CoreSession coreSession = getCoreSession();
        NuxeoPrincipal principal = coreSession.getPrincipal();
        if (useUnrestrictedSession() && !principal.isAdministrator()) {
            coreSession = CoreInstance.getCoreSessionSystem(coreSession.getRepositoryName(), principal.getName());
            principal = coreSession.getPrincipal();
        }
        if (query == null) {
            buildQuery(coreSession);
        }
        if (query == null) {
            throw new NuxeoException(String.format("Cannot perform null query: check provider '%s'", getName()));
        }
        // Build and execute the query using the Search Service
        SearchService service = Framework.getService(SearchService.class);
        try {
            var searchIndexes = getSearchIndexes(service, coreSession.getRepositoryName());
            var queryBuilder = SearchQuery.builder(query, principal)
                                          .searchIndex(searchIndexes)
                                          .offset((int) getCurrentPageOffset())
                                          .limit(getLimit())
                                          .addAggregates(buildAggregates())
                                          .addHighlights(emptyIfNull(getHighlights()));
            SearchResponse ret = service.search(queryBuilder.build());
            DocumentModelList dmList = ret.loadDocuments(coreSession);
            currentAggregates = new HashMap<>(ret.getAggregates().size());
            for (Aggregate<? extends Bucket> agg : ret.getAggregates()) {
                currentAggregates.put(agg.getId(), agg);
            }
            setResultsCount(ret.getTotal());
            currentPageDocuments = dmList;
        } catch (QueryParseException e) {
            error = e;
            errorMessage = e.getMessage();
            log.warn(e.getMessage(), e);
        }

        // send event for statistics !
        fireSearchEvent(getCoreSession().getPrincipal(), query, currentPageDocuments, System.currentTimeMillis() - t0);

        return currentPageDocuments;
    }

    protected List<SearchIndex> getSearchIndexes(SearchService service, String repository) {
        if (searchIndexes == null) {
            String pageProviderName = getDefinition().getName();
            var defaultClient = service.getSearchIndex(service.getDefaultIndexName(repository)).client();
            String clientName = requireNonNullElse(getClientName(), defaultClient);
            if (searchOnAllRepositories()) {
                // multi repositories search
                searchIndexes = getSearchIndexesForAllRepositories(service, clientName, pageProviderName);
            } else {
                searchIndexes = getSearchIndexForRepository(repository, service, clientName, pageProviderName);
            }
        }
        return searchIndexes;
    }

    protected List<SearchIndex> getSearchIndexesForAllRepositories(SearchService service, String clientName,
            String pageProviderName) {
        List<SearchIndex> indexes = service.getRepositoryNames()
                                           .stream()
                                           .map(service::getIndexNames)
                                           .flatMap(Collection::stream)
                                           .map(service::getSearchIndex)
                                           .filter(index -> clientName.equals(index.client()))
                                           .toList();
        if (indexes.size() > service.getRepositoryNames().size()) {
            log.warn("Page provider '{}' used on multiple repositories, but some repository has multiple indexes "
                    + "defined for client '{}': {}", pageProviderName, clientName, indexes);
        }
        return indexes;
    }

    protected List<SearchIndex> getSearchIndexForRepository(String repository, SearchService service, String clientName,
            String pageProviderName) {
        List<String> configuredIndexes = getIndexes();
        if (isEmpty(configuredIndexes)) {
            return getDefaultIndexesForRepository(service, repository, clientName, pageProviderName);
        } else {
            return getConfiguredIndexes(service, configuredIndexes, clientName, pageProviderName, repository);
        }
    }

    protected List<SearchIndex> getDefaultIndexesForRepository(SearchService service, String repository,
            String clientName, String pageProviderName) {
        if (getClientName() == null) {
            return List.of(service.getSearchIndex(service.getDefaultIndexName(repository)));
        } else {
            // Use the first index defined for the repository and the specified client
            return List.of(service.getIndexNames(repository)
                                  .stream()
                                  .map(service::getSearchIndex)
                                  .filter(index -> clientName.equals(index.client()))
                                  .findFirst()
                                  .orElseThrow(() -> throwInvalidConfigurationException(pageProviderName,
                                          "No search index found for client '" + clientName + "' and repository '"
                                                  + repository + "'")));
        }
    }

    protected List<SearchIndex> getConfiguredIndexes(SearchService service, List<String> configuredIndexes,
            String clientName, String pageProviderName, String repository) {
        List<SearchIndex> searchIndexes = service.getRepositoryNames()
                                                 .stream()
                                                 .map(service::getIndexNames)
                                                 .flatMap(Collection::stream)
                                                 .map(service::getSearchIndex)
                                                 .filter(index -> configuredIndexes.contains(index.index()))
                                                 .toList();
        validateConfiguredIndexes(searchIndexes, configuredIndexes, pageProviderName, repository);
        return searchIndexes;
    }

    protected void validateConfiguredIndexes(List<SearchIndex> searchIndexes, List<String> configuredIndexes,
            String pageProviderName, String repository) {
        if (searchIndexes.isEmpty()) {
            throw throwInvalidConfigurationException(pageProviderName,
                    "Configured indexes " + configuredIndexes + " not found for repository '" + repository + "'");
        }
        // Verify all indexes use the same client
        long distinctClientCount = searchIndexes.stream().map(SearchIndex::client).distinct().count();
        if (distinctClientCount != 1) {
            throw throwInvalidConfigurationException(pageProviderName,
                    "Configured indexes " + searchIndexes + " must share the same client");
        }
    }

    protected IllegalArgumentException throwInvalidConfigurationException(String pageProviderName, String reason) {
        return new IllegalArgumentException(
                "Invalid page provider configuration '" + pageProviderName + "': " + reason);
    }

    protected String getClientName() {
        if (getDefinition() instanceof SearchServicePageProviderDescriptor searchServiceDescriptor) {
            return searchServiceDescriptor.getSearchClient();
        }
        return null;
    }

    protected List<String> getIndexes() {
        if (getSearchDocumentModel() != null
                && getSearchDocumentModel().getContextData(NAMED_PARAMETERS) instanceof HashMap<?, ?> contextData) {
            if (contextData.containsKey(SEARCH_INDEX_OVERRIDE_PARAM)) {
                String searchIndex = (String) contextData.get(SEARCH_INDEX_OVERRIDE_PARAM);
                if (StringUtils.isNotBlank(searchIndex)) {
                    log.debug("Override search Index for pp: {} to index: {}", () -> getDefinition().getName(),
                            () -> searchIndex);
                    return List.of(searchIndex);
                }
            }
        }
        if (getDefinition() instanceof SearchServicePageProviderDescriptor searchServiceDescriptor) {
            return searchServiceDescriptor.getSearchIndexes();
        }
        return null;
    }

    public void setSearchIndexes(List<SearchIndex> searchIndexes) {
        this.searchIndexes = searchIndexes;
    }

    protected int getLimit() {
        int ret = (int) getMinMaxPageSize();
        if (ret == 0) {
            ret = (int) Long.min(getMaxResultWindow(), Integer.MAX_VALUE);
        }
        return ret;
    }

    @Override
    protected void pageChanged() {
        currentAggregates = null;
        super.pageChanged();
    }

    @Override
    public void refresh() {
        currentAggregates = null;
        super.refresh();
    }

    protected List<Aggregate<? extends Bucket>> buildAggregates() {
        ArrayList<Aggregate<? extends Bucket>> ret = new ArrayList<>(getAggregateDefinitions().size());
        boolean skip = isSkipAggregates();
        for (AggregateDefinition def : getAggregateDefinitions()) {
            Aggregate<? extends Bucket> agg = switch (def.getType()) {
                case AGG_TYPE_TERMS -> new AggregateTerm(def, getSearchDocumentModel());
                case AGG_TYPE_RANGE -> new AggregateRange(def, getSearchDocumentModel());
                case AGG_TYPE_DATE_RANGE -> new AggregateDateRange(def, getSearchDocumentModel());
                case AGG_TYPE_HISTOGRAM -> new AggregateHistogram(def, getSearchDocumentModel());
                case AGG_TYPE_DATE_HISTOGRAM -> new AggregateDateHistogram(def, getSearchDocumentModel());
                case AGG_CARDINALITY -> new AggregateCardinality(def, getSearchDocumentModel());
                case AGG_SUM -> new AggregateSum(def, getSearchDocumentModel());
                case AGG_MIN -> new AggregateMin(def, getSearchDocumentModel());
                case AGG_MAX -> new AggregateMax(def, getSearchDocumentModel());
                case AGG_AVG -> new AggregateAvg(def, getSearchDocumentModel());
                case AGG_COUNT -> new AggregateCount(def, getSearchDocumentModel());
                case AGG_MISSING -> new AggregateMissing(def, getSearchDocumentModel());
                default -> throw new IllegalArgumentException("Invalid aggregate type: " + def.getType() + ", " + def);
            };
            if (!skip || !agg.getSelection().isEmpty()) {
                // if we want to skip aggregates but one is selected, it has to be computed to filter the result set
                ret.add(agg);
            }
        }
        return ret;
    }

    protected boolean searchOnAllRepositories() {
        String value = (String) getProperties().get(SEARCH_ON_ALL_REPOSITORIES_PROPERTY);
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(value);
    }

    @Override
    public boolean hasAggregateSupport() {
        return true;
    }

    @Override
    public Map<String, Aggregate<? extends Bucket>> getAggregates() {
        getCurrentPage();
        return currentAggregates;
    }

    /**
     * Extends the default implementation to add results of aggregates
     *
     * @since 7.4
     */
    @Override
    protected void incorporateAggregates(Map<String, Serializable> eventProps) {
        super.incorporateAggregates(eventProps);
        if (currentAggregates != null) {
            HashMap<String, Serializable> aggregateMatches = new HashMap<>();
            for (String key : currentAggregates.keySet()) {
                Aggregate<? extends Bucket> ag = currentAggregates.get(key);
                ArrayList<HashMap<String, Serializable>> buckets = new ArrayList<>();
                for (Bucket bucket : ag.getBuckets()) {
                    HashMap<String, Serializable> b = new HashMap<>();
                    b.put("key", bucket.getKey());
                    b.put("count", bucket.getDocCount());
                    buckets.add(b);
                }
                aggregateMatches.put(key, buckets);
            }
            eventProps.put("aggregatesMatches", aggregateMatches);
        }
    }

    @Override
    public boolean isLastPageAvailable() {
        if ((getResultsCount() + getPageSize()) <= getMaxResultWindow()) {
            return super.isNextPageAvailable();
        }
        return false;
    }

    @Override
    public boolean isNextPageAvailable() {
        if ((getCurrentPageOffset() + 2 * getPageSize()) <= getMaxResultWindow()) {
            return super.isNextPageAvailable();
        }
        return false;
    }

    @Override
    public long getPageLimit() {
        return getMaxResultWindow() / getPageSize();
    }

    /**
     * Returns the max result window where the PP can navigate without raising QueryPhaseExecutionException.
     * {@code from + size} must be less than or equal to this value.
     *
     * @since 9.2
     */
    public long getMaxResultWindow() {
        if (maxResultWindow == null) {
            ConfigurationService cs = Framework.getService(ConfigurationService.class);
            maxResultWindow = cs.getLong(MAX_RESULT_WINDOW_PROPERTY, DEFAULT_MAX_RESULT_WINDOW);
        }
        return maxResultWindow;
    }

    @Override
    public long getResultsCountLimit() {
        return getMaxResultWindow();
    }

    /**
     * Set the max result window where the PP can navigate, for testing purpose.
     *
     * @since 9.2
     */
    public void setMaxResultWindow(long maxResultWindow) {
        this.maxResultWindow = maxResultWindow;
    }

    @Override
    public String getScroller() {
        if (getDefinition() instanceof SearchServicePageProviderDescriptor searchServiceDescriptor) {
            return StringUtils.defaultIfBlank(searchServiceDescriptor.getScroller(), "search");
        } else {
            // SSPP replaces another PP implementation, the query and scroll are processed by the search service
            return "search";
        }
    }
}
