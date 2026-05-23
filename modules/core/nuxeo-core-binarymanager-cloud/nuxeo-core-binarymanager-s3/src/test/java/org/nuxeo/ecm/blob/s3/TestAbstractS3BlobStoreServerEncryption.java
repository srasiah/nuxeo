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
import static org.junit.Assert.assertNotNull;

import java.io.IOException;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.impl.blob.StringBlob;
import org.nuxeo.ecm.core.blob.BlobContext;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import software.amazon.awssdk.services.s3.model.ServerSideEncryption;

/**
 * @since 2025.13
 */
@RunWith(FeaturesRunner.class)
@Features(S3BlobProviderFeature.class)
public abstract class TestAbstractS3BlobStoreServerEncryption {

    @Inject
    protected BlobManager blobManager;

    protected abstract ServerSideEncryption expectedServerSideEncryption();

    @Test
    public void assertServerSideEncryption() throws IOException {
        S3BlobProvider s3BlobProvider = (S3BlobProvider) blobManager.getBlobProvider("test");
        BlobContext blobContext = new BlobContext(new StringBlob("dummyContent"), "key", "content");
        String key = s3BlobProvider.writeBlob(blobContext);
        assertNotNull(key);
        var config = s3BlobProvider.config;
        var headObject = config.amazonS3.headObject(b -> b.bucket(config.bucketName).key(config.bucketPrefix + key));
        assertEquals(expectedServerSideEncryption(), headObject.serverSideEncryption());
    }

}
