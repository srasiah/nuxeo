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
 *     Kevin Leturc <kevin.leturc@hyland.com>
 */
package org.nuxeo.runtime.opensearch1;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.function.ThrowableConsumer;
import org.nuxeo.runtime.RuntimeServiceException;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentStartOrders;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.opensearch1.client.OpenSearchClient;
import org.nuxeo.runtime.opensearch1.client.OpenSearchClientConfig;
import org.nuxeo.runtime.opensearch1.client.OpenSearchClientFactory;
import org.nuxeo.runtime.opensearch1.client.OpenSearchRestClientFactory;

/**
 * Component used to get a client configured to hit an OpenSearch instance.
 *
 * @since 2025.0
 */
public class OpenSearchComponent extends DefaultComponent implements OpenSearchClientService {

    private static final Logger log = LogManager.getLogger(OpenSearchComponent.class);

    protected static final String XP_CLIENT = "client";

    protected static final String XP_INDEX = "index";

    protected static final String DEFAULT_CLIENT_ID = "default";

    protected static final Duration TIMEOUT_WAIT_FOR_CLUSTER = Duration.ofSeconds(30);

    protected static final OpenSearchClientFactory CLIENT_FACTORY = new OpenSearchRestClientFactory();

    protected final Map<String, OpenSearchClient> clients = new HashMap<>();

    @Override
    public int getApplicationStartedOrder() {
        return ComponentStartOrders.OPENSEARCH;
    }

    @Override
    public void start(ComponentContext context) {
        this.<OpenSearchClientConfig> getDescriptors(XP_CLIENT)
            .stream()
            .map(this::instantiateClient)
            .forEach(c -> clients.put(c.id(), c.client()));
        // TODO review, handle missing "default" client? this is not done in MongoDBComponent
        this.<OpenSearchIndexConfig> getDescriptors(XP_INDEX)
            .stream()
            .filter(OpenSearchIndexConfig::isEnabled)
            .forEach(config -> initIndex(config, false));
    }

    protected OpenSearchClientWithId instantiateClient(OpenSearchClientConfig descriptor) {
        log.info("Instantiating OpenSearchClient with id: {}", descriptor::getId);
        return new OpenSearchClientWithId(descriptor.getId(), CLIENT_FACTORY.create(descriptor));
    }

    protected void initIndex(OpenSearchIndexConfig config, boolean dropIfExists) {
        createIndex(config.getName(), config, dropIfExists);
    }

    protected void createIndex(String indexName, OpenSearchIndexConfig config, boolean dropIfExists) {
        if (!config.mustCreate()) {
            return;
        }
        log.info("Initializing index: {} with conf: {}", indexName, config);
        var clients = new ArrayList<>(config.getClientIds());
        if (clients.isEmpty()) {
            clients.add(DEFAULT_CLIENT_ID);
        }
        for (String clientId : clients) {
            var client = getClient(clientId);
            log.debug("Initializing index: {} on cluster defined by client: {}", indexName, clientId);
            boolean indexExists = client.indexExists(indexName);
            boolean mappingExists = false;
            if (indexExists) {
                log.debug("Index: {} already exists on cluster defined by client: {}", indexName, clientId);
                if (dropIfExists) {
                    log.atLevel(Framework.isTestModeSet() ? Level.DEBUG : Level.WARN)
                       .log("Dropping index: {} on cluster defined by client: {}", indexName, clientId);
                    client.dropIndex(indexName);
                    indexExists = false;
                } else {
                    log.debug("Retrieve index: {} metadata (mapping, ...) from cluster defined by client: {}",
                            indexName, clientId);
                    mappingExists = client.mappingExists(indexName);
                }
            }
            if (!indexExists) {
                log.debug("Creating index: {} with client: {}", indexName, clientId);
                try {
                    client.createIndex(indexName, config.getSettingsContent());
                } catch (RuntimeServiceException e) {
                    if (Strings.CS.contains(e.getMessage(), "resource_already_exists_exception")) {
                        log.warn("Index: {} on cluster defined by client: {} has been concurrently created", indexName,
                                clientId);
                    } else {
                        throw new RuntimeServiceException(
                                "Unable to create index: %s with client: %s".formatted(indexName, clientId), e);
                    }
                }
            }
            if (!mappingExists) {
                log.debug("Creating mapping on index: {} with client: {}", indexName, clientId);
                client.createMapping(indexName, config.getMappingContent());
                for (String extraMapping : config.getExtraMappingContents()) {
                    try {
                        client.createMapping(indexName, extraMapping);
                    } catch (RuntimeServiceException e) {
                        throw new RuntimeServiceException(
                                "An error occurred while putting the extra mapping for index: %s with client: %s".formatted(
                                        indexName, clientId),
                                e);
                    }
                }
            }
            // make sure the index is ready before continuing
            client.waitForYellowStatus(new String[] { indexName }, TIMEOUT_WAIT_FOR_CLUSTER);
        }
    }

    @Override
    public void stop(ComponentContext context) {
        clients.values().forEach(ThrowableConsumer.asConsumer(OpenSearchClient::close));
        clients.clear();
    }

    @Override
    public OpenSearchClient getClient(String id) {
        var client = clients.get(id);
        if (client == null) {
            log.debug("OpenSearchClient with id: {} isn't configured, falling back to 'default'", id);
            return clients.get(DEFAULT_CLIENT_ID);
        }
        return client;
    }

    // used by tests and reindex
    public void dropAndInitIndex(String indexName) {
        log.info("Drop and init index: {}", indexName);
        var config = this.<OpenSearchIndexConfig> getDescriptors(XP_INDEX)
                         .stream()
                         .filter(c -> c.getName().equals(indexName))
                         .findFirst()
                         .orElseThrow(() -> new IllegalArgumentException("Index " + indexName + " not found"));
        initIndex(config, true);
    }

    protected record OpenSearchClientWithId(String id, OpenSearchClient client) {
    }
}
