/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.blob;

import static org.nuxeo.ecm.core.blob.BlobProviderDescriptor.RECORD;
import static org.nuxeo.ecm.core.blob.BlobStoreBlobProvider.DIGEST_KEY_STRATEGY;
import static org.nuxeo.ecm.core.blob.BlobStoreBlobProvider.KEY_STRATEGY_PROPERTY;
import static org.nuxeo.ecm.core.blob.BlobStoreBlobProvider.MANAGED_KEY_STRATEGY;

import java.io.IOException;
import java.util.Map;

import org.nuxeo.ecm.core.blob.AbstractBlobStoreConfiguration;
import org.nuxeo.ecm.core.blob.BlobProviderDescriptor;
import org.nuxeo.ecm.core.blob.CachingConfiguration;
import org.nuxeo.ecm.core.blob.DigestConfiguration;
import org.nuxeo.ecm.core.blob.KeyStrategy;
import org.nuxeo.ecm.core.blob.KeyStrategyDigest;
import org.nuxeo.ecm.core.blob.KeyStrategyDocId;
import org.nuxeo.ecm.core.blob.KeyStrategyManaged;

/**
 * Abstract blob store configuration for cloud providers.
 *
 * @since 11.1
 */
public abstract class CloudBlobStoreConfiguration extends AbstractBlobStoreConfiguration {

    /**
     * @deprecated since 2023.7, use {@link BlobProviderDescriptor#DIRECTDOWNLOAD_PROPERTY} instead.
     */
    @Deprecated
    public static final String DIRECTDOWNLOAD_PROPERTY = BlobProviderDescriptor.DIRECTDOWNLOAD_PROPERTY;

    /**
     * @deprecated since 2023.7, use {@link BlobProviderDescriptor#DIRECTDOWNLOAD_EXPIRE_PROPERTY} instead.
     */
    @Deprecated
    public static final String DIRECTDOWNLOAD_EXPIRE_PROPERTY = BlobProviderDescriptor.DIRECTDOWNLOAD_EXPIRE_PROPERTY;

    public static final long DEFAULT_DIRECTDOWNLOAD_EXPIRE = 60L * 60L; // 1h

    public static final String DIGEST_ALGORITHM_PROPERTY = "digest";

    public final DigestConfiguration digestConfiguration;

    public final CachingConfiguration cachingConfiguration;

    public final boolean directDownload;

    public final long directDownloadExpire;

    protected final KeyStrategy keyStrategy;

    /**
     * Is Object Lock/Hold feature enabled by the storage.
     *
     * @since 2025.8
     */
    public boolean retentionEnabled;

    protected Boolean useVersion;

    public CloudBlobStoreConfiguration(String systemPropertyPrefix, Map<String, String> properties) throws IOException {
        super(systemPropertyPrefix, properties);

        digestConfiguration = new DigestConfiguration(systemPropertyPrefix, properties);
        cachingConfiguration = new CachingConfiguration(systemPropertyPrefix, properties);

        directDownload = parseDirectDownload();
        directDownloadExpire = parseDirectDownloadExpire();

        boolean hasDigest = properties.get(DIGEST_ALGORITHM_PROPERTY) != null;
        if (Boolean.parseBoolean(properties.get(RECORD)) && !hasDigest) {
            keyStrategy = KeyStrategyDocId.instance();
        } else {
            String strKeyStrategy = properties.getOrDefault(KEY_STRATEGY_PROPERTY, DIGEST_KEY_STRATEGY);
            var tmpKeyStrategy = new KeyStrategyDigest(digestConfiguration.digestAlgorithm);
            if (MANAGED_KEY_STRATEGY.equals(strKeyStrategy)) {
                keyStrategy = new KeyStrategyManaged(tmpKeyStrategy);
            } else {
                keyStrategy = tmpKeyStrategy;
            }
        }
    }

    /**
     * @since 2025.15
     */
    public KeyStrategy getKeyStrategy() {
        return keyStrategy;
    }

    /**
     * @since 2025.15
     */
    public boolean useVersion() {
        if (useVersion == null) {
            useVersion = keyStrategy instanceof KeyStrategyDocId && isVersioningEnabled();
        }
        return useVersion;
    }

    /**
     * @since 2025.15
     */
    protected abstract boolean isVersioningEnabled();

    protected boolean parseDirectDownload() {
        return Boolean.parseBoolean(getProperty(BlobProviderDescriptor.DIRECTDOWNLOAD_PROPERTY));
    }

    protected long parseDirectDownloadExpire() {
        long expire = getLongProperty(BlobProviderDescriptor.DIRECTDOWNLOAD_EXPIRE_PROPERTY);
        if (expire < 0) {
            expire = DEFAULT_DIRECTDOWNLOAD_EXPIRE;
        }
        return expire;
    }

}
