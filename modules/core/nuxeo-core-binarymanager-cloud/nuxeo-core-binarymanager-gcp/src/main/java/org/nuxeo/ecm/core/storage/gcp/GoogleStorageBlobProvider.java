/*
 * (C) Copyright 2023 Nuxeo (http://nuxeo.com/) and others.
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
 *     Guillaume Renard
 */
package org.nuxeo.ecm.core.storage.gcp;

import static org.nuxeo.ecm.core.storage.gcp.GoogleStorageBlobStoreConfiguration.SYSTEM_PROPERTY_PREFIX;

import java.io.IOException;
import java.util.Map;

import org.nuxeo.ecm.blob.CloudBlobProvider;
import org.nuxeo.ecm.core.blob.BlobStore;
import org.nuxeo.ecm.core.blob.CachingBlobStore;
import org.nuxeo.ecm.core.blob.CachingConfiguration;
import org.nuxeo.ecm.core.blob.KeyStrategy;
import org.nuxeo.ecm.core.blob.KeyStrategyDigest;
import org.nuxeo.ecm.core.blob.TransactionalBlobStore;

/**
 * Blob provider that stores files in Google Cloud Storage.
 * <p>
 * This implementation only supports {@link KeyStrategyDigest} which is the legacy strategy.
 * <p>
 * This implementation does not support transactional mode.
 *
 * @since 2023.5
 */
public class GoogleStorageBlobProvider extends CloudBlobProvider<GoogleStorageBlobStoreConfiguration> {

    public static final String STORE_SCROLL_NAME = "googleStorageBlobScroll";

    @Override
    public void close() {
        // Do nothing
    }

    @Override
    protected BlobStore getBlobStore(String blobProviderId, Map<String, String> properties) throws IOException {
        KeyStrategy keyStrategy = getKeyStrategy();
        BlobStore store = new GoogleStorageBlobStore(blobProviderId, "googleStorage", config, keyStrategy);
        boolean caching = !config.getBooleanProperty("nocache");
        if (caching) {
            CachingConfiguration cachingConfiguration = new CachingConfiguration(SYSTEM_PROPERTY_PREFIX, properties);
            store = new CachingBlobStore(blobProviderId, "Cache", store, cachingConfiguration);
        }

        // maybe wrap into a transactional store
        if (isTransactional()) {
            BlobStore transientStore;
            if (store.hasVersioning()) {
                // if versioning is used, we don't need a separate transient store for transactions
                transientStore = store;
            } else {
                // transient store is another S3 blob store wrapped in a caching store
                GoogleStorageBlobStoreConfiguration transientConfig = config.withNamespace("tx");
                transientStore = new GoogleStorageBlobStore(blobProviderId, "GCP_tmp", transientConfig, keyStrategy);
                if (caching) {
                    transientStore = new CachingBlobStore(blobProviderId, "Cache_tmp", transientStore,
                            config.cachingConfiguration);
                }
            }
            // transactional store
            store = new TransactionalBlobStore(blobProviderId, store, transientStore);
        }
        return store;
    }

    @Override
    public String getStoreScrollName() {
        return STORE_SCROLL_NAME;
    }

}
