/*
 * (C) Copyright 2020-2024 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.runtime.opensearch1;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.launcher.config.ConfigurationException;
import org.nuxeo.launcher.config.ConfigurationHolder;
import org.nuxeo.launcher.config.backingservices.BackingChecker;
import org.nuxeo.runtime.opensearch1.client.OpenSearchClientConfig;
import org.nuxeo.runtime.opensearch1.client.OpenSearchRestClientFactory;

/**
 * @since 11.3
 */
public class OpenSearchChecker implements BackingChecker {

    private static final Logger log = LogManager.getLogger(OpenSearchChecker.class);

    protected static final String CONFIG_NAME = "opensearch1-search-client-config.xml";

    @Override
    public boolean accepts(ConfigurationHolder configHolder) {
        log.debug("Checker accepted");
        return true;
    }

    @Override
    public void check(ConfigurationHolder configHolder) throws ConfigurationException {
        OpenSearchClientConfig config;
        try {
            config = getDescriptor(configHolder, CONFIG_NAME, OpenSearchClientConfig.class,
                    // avoid XMap to fail when trying to load class value by removing class attribute
                    content -> content.replace("class=", "ignore="), OpenSearchClientConfig.Store.class);
        } catch (ConfigurationException e) {
            if (e.getMessage() != null && e.getMessage().contains("No configuration found")) {
                // An empty configuration is expected during a migration from another search client
                log.warn("Skip OpenSearch1 check, because {} is empty", CONFIG_NAME);
                return;
            }
            throw e;
        }
        if (config.getEmbedServer().isPresent()) {
            log.debug("OpenSearch config check skipped on embedded configuration");
            return;
        }
        log.debug("Check OpenSearch config: {}", config);
        try (var client = new OpenSearchRestClientFactory().create(config)) {
            if (!client.isReady()) {
                throw new ConfigurationException("OpenSearch cluster is not healthy");
            }
        } catch (Exception e) {
            throw new ConfigurationException("Unable to connect to OpenSearch: " + config.getServers(), e);
        }
    }
}
