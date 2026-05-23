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
package org.nuxeo.ecm.core.test;

import static org.nuxeo.common.test.configuration.ThirdPartyUnderTest.SEARCH_SERVICE_VALUE;
import static org.nuxeo.common.test.configuration.ThirdPartyUnderTest.STORAGE_MEM;
import static org.nuxeo.common.test.configuration.ThirdPartyUnderTest.STORAGE_OPENSEARCH_1;
import static org.nuxeo.common.test.configuration.ThirdPartyUnderTest.STORAGE_REPOSITORY;
import static org.nuxeo.common.test.configuration.ThirdPartyUnderTest.computeSystemProperty;
import static org.nuxeo.common.test.logging.NuxeoLoggingConstants.MARKER_CONSOLE_OVERRIDE;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.test.configuration.ThirdPartyUnderTest.SystemProperty;
import org.nuxeo.ecm.core.search.BaseCoreSearchFeature;
import org.nuxeo.ecm.core.search.SearchClient;
import org.nuxeo.ecm.core.search.SearchIndexingService;
import org.nuxeo.ecm.core.search.SearchService;
import org.nuxeo.ecm.core.search.client.mock.MockSearchClientFeature;
import org.nuxeo.ecm.core.search.client.opensearch1.OpenSearchCoreSearchFeature;
import org.nuxeo.ecm.core.search.client.repository.RepositorySearchClientFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.DynamicFeaturesLoader;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;

/**
 * @since 2025.0
 */
@Features({ CoreFeature.class, BaseCoreSearchFeature.class })
public class CoreSearchFeature implements RunnerFeature {

    private static final Logger log = LogManager.getLogger(CoreSearchFeature.class);

    /** @since 2025.4 */
    public static final SystemProperty SEARCH_EXTERNAL_FEATURE_PROPERTY = new SystemProperty(
            "nuxeo.test.search.feature");

    public CoreSearchFeature(DynamicFeaturesLoader loader) {
        if (SEARCH_EXTERNAL_FEATURE_PROPERTY.isConfigured()) {
            loader.loadFeature(computeSystemProperty(SEARCH_EXTERNAL_FEATURE_PROPERTY));
        } else {
            var feature = switch (SEARCH_SERVICE_VALUE) {
                case STORAGE_MEM -> MockSearchClientFeature.class;
                case STORAGE_OPENSEARCH_1 -> OpenSearchCoreSearchFeature.class;
                case STORAGE_REPOSITORY -> RepositorySearchClientFeature.class;
                default -> throw new UnsupportedOperationException(
                        "Search type: " + SEARCH_SERVICE_VALUE + " is not supported");
            };
            loader.loadFeature(feature);
        }
    }

    @Override
    public void start(FeaturesRunner runner) {
        log.info(MARKER_CONSOLE_OVERRIDE, "Deploying Search using {}",
                () -> StringUtils.capitalize(SEARCH_SERVICE_VALUE.toLowerCase()));
    }

    public boolean hasMemoryClient() {
        return SEARCH_SERVICE_VALUE.equals(STORAGE_MEM);
    }

    public boolean hasOpenSearchClient() {
        return SEARCH_SERVICE_VALUE.equals(STORAGE_OPENSEARCH_1);
    }

    public boolean hasRepositoryClient() {
        return SEARCH_SERVICE_VALUE.equals(STORAGE_REPOSITORY);
    }

    /**
     * Returns {@code true} if the implementation is able to drop and initialize the index.
     */
    public boolean dropAndInitIndex() {
        var searchService = Framework.getService(SearchService.class);
        var searchIndex = searchService.getSearchIndex(searchService.getDefaultIndexName());
        if (searchIndex == null) {
            return false;
        }
        var searchClient = Framework.getService(SearchIndexingService.class).getClient(searchIndex.client());
        if (searchClient == null || !searchClient.hasCapability(SearchClient.Capability.INIT_INDEX)) {
            return false;
        }
        searchClient.dropAndInitIndex(searchIndex.index());
        return true;
    }
}
