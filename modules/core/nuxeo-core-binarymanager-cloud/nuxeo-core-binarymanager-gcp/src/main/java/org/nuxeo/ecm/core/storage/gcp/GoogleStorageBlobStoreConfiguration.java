/*
 * (C) Copyright 2023-2025 Nuxeo (http://nuxeo.com/) and others.
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

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.nuxeo.ecm.core.blob.BlobProviderDescriptor.ALLOW_BYTE_RANGE;
import static org.nuxeo.ecm.core.blob.BlobProviderDescriptor.RECORD;
import static org.nuxeo.ecm.core.model.BaseSession.isRetentionStrictMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.Environment;
import org.nuxeo.common.utils.ByteSize;
import org.nuxeo.ecm.blob.CloudBlobStoreConfiguration;
import org.nuxeo.ecm.core.api.NuxeoException;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

/**
 * Blob storage configuration in Google Storage.
 *
 * @since 2023.5
 */
public class GoogleStorageBlobStoreConfiguration extends CloudBlobStoreConfiguration {

    protected static final Logger log = LogManager.getLogger(GoogleStorageBlobStoreConfiguration.class);

    public static final String BUCKET_NAME_PROPERTY = "bucket";

    public static final String BUCKET_PREFIX_PROPERTY = "bucket_prefix";

    public static final String UPLOAD_CHUNK_SIZE_PROPERTY = "storage.upload.chunk.size";

    /**
     * Default is taken from {@link com.google.cloud.BaseWriteChannel}.
     */
    public static final ByteSize DEFAULT_UPLOAD_CHUNK_BYTE_SIZE = ByteSize.ofMebibytes(2);

    /**
     * Default is taken from {@link com.google.cloud.BaseWriteChannel}.
     *
     * @deprecated since 2025.11, use {@link #DEFAULT_UPLOAD_CHUNK_BYTE_SIZE} instead
     */
    @Deprecated(since = "2025.11", forRemoval = true)
    public static final int DEFAULT_UPLOAD_CHUNK_SIZE = (int) DEFAULT_UPLOAD_CHUNK_BYTE_SIZE.toBytes();

    public static final String SYSTEM_PROPERTY_PREFIX = "nuxeo.gcp";

    public static final String GOOGLE_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

    public static final String GOOGLE_STORAGE_SCOPE = "https://www.googleapis.com/auth/devstorage.full_control";

    public static final String GOOGLE_APPLICATION_CREDENTIALS = "credentials";

    public static final String GCP_JSON_FILE = "gcp-credentials.json";

    public static final String DELIMITER = "/";

    public static final String PROJECT_ID_PROPERTY = "project";

    protected final Storage storage;

    protected final String bucketName;

    protected final String bucketPrefix;

    protected final Bucket bucket;

    protected final boolean allowByteRange;

    protected final ByteSize chunkSize;

    public final BlobInfo.Retention.Mode retentionMode;

    public final boolean isBucketVersioningEnabled;

    public GoogleStorageBlobStoreConfiguration(Map<String, String> properties) throws IOException {
        super(SYSTEM_PROPERTY_PREFIX, properties);
        String projectId = getProperty(PROJECT_ID_PROPERTY);
        Path credentialsPath = Path.of(getOptionalProperty(GOOGLE_APPLICATION_CREDENTIALS).orElse(GCP_JSON_FILE));
        if (!credentialsPath.isAbsolute()) {
            credentialsPath = Environment.getDefault().getConfig().toPath().resolve(credentialsPath);
        }
        GoogleCredentials credentials;
        try {
            credentials = GoogleCredentials.fromStream(Files.newInputStream(credentialsPath))
                                           .createScoped(GOOGLE_PLATFORM_SCOPE, GOOGLE_STORAGE_SCOPE);
            credentials.refreshIfExpired();
        } catch (IOException e) {
            throw new NuxeoException(e);
        }

        storage = StorageOptions.newBuilder().setCredentials(credentials).setProjectId(projectId).build().getService();
        bucketName = getProperty(BUCKET_NAME_PROPERTY);
        // Get all fields, such as Storage.BucketField.RETENTION_POLICY
        Bucket b = storage.get(bucketName, Storage.BucketGetOption.fields(Storage.BucketField.values()));
        if (b == null) {
            log.debug("Creating a new bucket: {}", bucketName);
            b = storage.create(BucketInfo.of(bucketName));
        }
        bucket = b;
        chunkSize = getOptionalByteSizeProperty(UPLOAD_CHUNK_SIZE_PROPERTY).orElse(DEFAULT_UPLOAD_CHUNK_BYTE_SIZE);
        allowByteRange = getBooleanProperty(ALLOW_BYTE_RANGE);

        String bp = getOptionalProperty(BUCKET_PREFIX_PROPERTY).orElse(EMPTY);
        if (!isBlank(bp) && !bp.endsWith(DELIMITER)) {
            log.warn("Google bucket prefix ({}): {} should end with '{}': added automatically.", BUCKET_PREFIX_PROPERTY,
                    bp, DELIMITER);
            bp += DELIMITER;
        }
        if (isNotBlank(namespace)) {
            // use namespace as an additional prefix
            bp += namespace;
            if (!bp.endsWith(DELIMITER)) {
                bp += DELIMITER;
            }
        }
        bucketPrefix = bp;
        if (Boolean.parseBoolean(properties.get(RECORD))) {
            retentionEnabled = isRetentionEnabled();
            if (!retentionEnabled) {
                log.warn(
                        "Blob provider is configured for records but retention is not enabled on Google Storage bucket {}",
                        bucketName);
                retentionMode = null;
            } else {
                // Google storage does not have a default object retention policy unlike s3
                // we can only rely on Nuxeo platform setting
                retentionMode = isRetentionStrictMode() ? BlobInfo.Retention.Mode.LOCKED
                        : BlobInfo.Retention.Mode.UNLOCKED;
            }
        } else {
            retentionEnabled = false;
            retentionMode = null;
        }
        isBucketVersioningEnabled = Boolean.TRUE.equals(bucket.versioningEnabled());

    }

    protected boolean isRetentionEnabled() {
        var objectRetention = bucket.getObjectRetention();
        if (objectRetention != null) {
            return bucket.getObjectRetention().getMode().equals(BucketInfo.ObjectRetention.Mode.ENABLED);
        }
        return false;
    }

    @Override
    protected boolean isVersioningEnabled() {
        return isBucketVersioningEnabled;
    }

    /**
     * Returns a copy of the GoogleStorageBlobStoreConfiguration with a different namespace.
     */
    public GoogleStorageBlobStoreConfiguration withNamespace(String ns) throws IOException {
        return new GoogleStorageBlobStoreConfiguration(propertiesWithNamespace(ns));
    }

}
