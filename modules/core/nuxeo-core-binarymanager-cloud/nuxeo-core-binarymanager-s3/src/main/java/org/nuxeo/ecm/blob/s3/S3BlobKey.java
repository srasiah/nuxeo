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
package org.nuxeo.ecm.blob.s3;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.nuxeo.ecm.core.blob.KeyStrategy.VER_SEP;

import org.nuxeo.ecm.blob.CloudBlobKey;

/**
 * @since 2025.8
 */
public record S3BlobKey(S3BlobStoreConfiguration config, String key) implements CloudBlobKey<S3BlobStoreConfiguration> {

    public S3BlobKey {
        if (isEmpty(config.bucketName)) {
            throw new IllegalArgumentException("Missing bucket name");
        }
        if (isEmpty(key)) {
            throw new IllegalArgumentException("Missing key");
        }
    }

    @Override
    public String bucketKey() {
        return config.bucketKey(isVersioned() ? key().substring(0, key().indexOf(VER_SEP)) : key());
    }

    @Override
    public String bucketPrefix() {
        return config.bucketPrefix;
    }

    @Override
    public String toString() {
        return isVersioned() ? bucketKey() + VER_SEP + versionId() : bucketKey();
    }

}
