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
package org.nuxeo.ecm.core.bulk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.action.GarbageCollectOrphanBlobsAction.RECORDS_PARAM;
import static org.nuxeo.ecm.core.action.GarbageCollectOrphanBlobsAction.RESULT_DELETED_SIZE_KEY;
import static org.nuxeo.ecm.core.action.GarbageCollectOrphanBlobsAction.RESULT_TOTAL_SIZE_KEY;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.COMPLETED;

import org.junit.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.ecm.core.blob.BlobStoreBlobProvider;
import org.nuxeo.ecm.core.blob.KeyStrategy;
import org.nuxeo.ecm.core.blob.KeyStrategyDocId;
import org.nuxeo.ecm.core.blob.ManagedBlob;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;

/**
 * @since 2023.5
 */
@Features(CoreFeature.class)
public abstract class AbstractTestFullGCOrphanBlobsRecord extends AbstractTestFullGCOrphanBlobs {

    @Override
    public int getNbFiles() {
        return 1;
    }

    @Test
    public void testGCBlobsAction() {
        DocumentModelList docList = session.query("SELECT * From Document");
        assertEquals(1, docList.size());
        DocumentModel doc = docList.getFirst();
        ManagedBlob blob = (ManagedBlob) doc.getPropertyValue("file:content");
        assertNotNull(blob);
        BlobStoreBlobProvider blobProvider = (BlobStoreBlobProvider) Framework.getService(BlobManager.class)
                                                                              .getBlobProvider(blob);
        assertTrue(blobProvider.getKeyStrategy() instanceof KeyStrategyDocId);
        String blobKey = blob.getKey();
        assertTrue(blobProvider.store.exists(doc.getId()));

        // We are testing record provider
        // blob versioning is enabled and blob key has the ${docId}@{versionId} pattern
        assertTrue("Unexpected blobKey: %s".formatted(blobKey),
                blobKey.startsWith(blobProvider.blobProviderId + ":" + doc.getId() + KeyStrategy.VER_SEP));

        BulkStatus status = triggerAndWaitGC(RECORDS_PARAM);
        assertNotNull(status);
        assertEquals(COMPLETED, status.getState());
        assertFalse(status.hasError());

        // nothing was deleted
        assertTrue(blobProvider.store.exists(doc.getId()));

        assertEquals(1, status.getProcessed());
        assertEquals(0, status.getErrorCount());
        assertEquals(1, status.getTotal());
        assertEquals(1, status.getSkipCount());
        assertEquals(0, ((Number) status.getResult().get(RESULT_DELETED_SIZE_KEY)).longValue());
        assertEquals(sizeOfBinaries, ((Number) status.getResult().get(RESULT_TOTAL_SIZE_KEY)).longValue());
    }

    @Test
    public void testGCBlobsActionSkipRecords() {
        BulkStatus status = triggerAndWaitGC();
        assertNotNull(status);
        assertEquals(COMPLETED, status.getState());
        assertFalse(status.hasError());

        assertEquals(0, status.getProcessed());
        assertEquals(0, status.getErrorCount());
        assertEquals(0, status.getTotal());
        assertEquals(0, status.getSkipCount());
        assertNull(status.getResult().get(RESULT_DELETED_SIZE_KEY));
        assertNull(status.getResult().get(RESULT_TOTAL_SIZE_KEY));
    }

}
