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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;

import jakarta.inject.Inject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.blob.BlobContext;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.BlobStore;
import org.nuxeo.ecm.core.blob.BlobStoreBlobProvider;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.StorageClass;

/**
 * @since 2025.13
 */
@RunWith(FeaturesRunner.class)
@Features(S3BlobProviderFeature.class)
public abstract class TestAbstractS3StorageClass {

    @Inject
    protected BlobManager blobManager;

    protected BlobProvider bp;

    protected BlobStore bs;

    protected final StorageClass expectedStorageClass;

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    public TestAbstractS3StorageClass(StorageClass expectedStorageClass) {
        this.expectedStorageClass = expectedStorageClass;
    }

    @Before
    public void setUp() throws IOException {
        bp = blobManager.getBlobProvider("test");
        bs = ((BlobStoreBlobProvider) bp).store;
    }

    protected void assertStorageClass(String key) {
        S3BlobStoreConfiguration config = ((S3BlobProvider) bp).config;
        var s3Key = new S3BlobKey(config, key);
        HeadObjectResponse response = config.amazonS3.headObject(
                HeadObjectRequest.builder().bucket(config.bucketName).key(s3Key.bucketKey()).build());
        assertEquals(expectedStorageClass, response.storageClass());
    }

    @Test
    public void testStorageClass() throws IOException {
        // Write
        var value = "dummyContent" + System.currentTimeMillis();
        BlobContext blobContext = new BlobContext(new StringBlob(value), "key", "content");
        var key = bs.writeBlob(blobContext);
        assertStorageClass(key);
        // Read
        var tmpFile = temporary.newFile("tmp.txt").toPath();
        assertTrue(bs.readBlob(key, tmpFile));
        assertEquals(value, Files.readString(tmpFile));
        // Copy
        BlobProvider srcProvider = blobManager.getBlobProvider("other");
        BlobStore srcStore = ((BlobStoreBlobProvider) srcProvider).store;
        assertStorageClass(bs.copyOrMoveBlob(key, srcStore, key, true));
    }
}
