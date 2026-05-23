/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.blob;

import static org.nuxeo.ecm.core.blob.KeyStrategy.VER_SEP;

/**
 * Interface to map Nuxeo blob keys to Cloud Storage Provider object keys with convenient methods to extract the object
 * version id if any.
 *
 * @since 2025.8
 */
public interface CloudBlobKey<T extends CloudBlobStoreConfiguration> {

    /**
     * Gets the bucket object key interpretable by the cloud storage provider.
     */
    default String bucketKey() {
        return bucketPrefix() + (isVersioned() ? key().substring(0, key().indexOf(VER_SEP)) : key());
    }

    /**
     * Gets the cloud storage prefix.
     */
    String bucketPrefix();

    /**
     * Gets the cloud storage nuxeo configuration.
     */
    T config();

    /**
     * Is the Nuxeo blob key versioned.
     */
    default boolean isVersioned() {
        return config().useVersion() && key().indexOf(VER_SEP) > 0;
    }

    /**
     * Gets the Nuxeo blob key.
     */
    String key();

    /**
     * Gets the version id part of the Nuxeo blob key.
     */
    default String versionId() {
        return isVersioned() ? key().substring(key().indexOf(VER_SEP) + 1) : null;
    }

}
