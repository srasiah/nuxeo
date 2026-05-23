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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.nuxeo.ecm.core.blob.BlobProvider;
import org.nuxeo.ecm.core.blob.BlobStore;
import org.nuxeo.ecm.core.blob.BlobStoreBlobProvider;
import org.nuxeo.ecm.core.blob.TestAbstractBlobStore;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.RandomBug;

/**
 * @since 2025.0
 */
@Features(S3BlobProviderFeature.class)
public abstract class TestAbstractS3BlobStoreClientEncryption extends TestAbstractBlobStore {

    @Override
    public boolean checkSizeOfGCedFiles() {
        // cannot check file size with client-side encryption
        return false;
    }

    @Test
    public void testFlags() {
        assertFalse(bp.isTransactional());
        assertFalse(bp.isRecordMode());
        assertTrue(bs.getKeyStrategy().useDeDuplication());
        assertTrue(((S3BlobProvider) bp).config.useClientSideEncryption);
        BlobProvider srcProvider = blobManager.getBlobProvider("other");
        BlobStore srcStore = ((BlobStoreBlobProvider) srcProvider).store; // no need for unwrap
        assertFalse(bs.copyBlobIsOptimized(srcStore));
    }

    @Test
    @Override
    @RandomBug.Repeat(issue = "NXP-32924", onFailure = 10, onSuccess = 30)
    public void testCopy() throws IOException {
        super.testCopy();
    }

    @Test
    @Override
    @RandomBug.Repeat(issue = "NXP-32924", onFailure = 10, onSuccess = 30)
    public void testMove() throws IOException {
        super.testMove();
    }
}
