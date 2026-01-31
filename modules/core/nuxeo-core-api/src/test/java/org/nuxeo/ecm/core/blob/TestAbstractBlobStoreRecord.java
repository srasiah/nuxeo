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
package org.nuxeo.ecm.core.blob;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.junit.Test;
import org.nuxeo.common.function.ThrowableRunnable;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.TransactionalConfig;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * @since 2025.8
 */
@Features(TransactionalFeature.class)
@TransactionalConfig(autoStart = false)
public abstract class TestAbstractBlobStoreRecord extends TestAbstractBlobStoreWithOptimizedCopy {

    @Test
    public void testFlags() {
        assertTrue(bp.isTransactional());
        assertTrue(bp.isRecordMode());
        assertFalse(bs.getKeyStrategy().useDeDuplication());
    }

    @Test
    public void testCRUDInTransaction() {
        TransactionHelper.runInTransaction(ThrowableRunnable.asRunnable(this::testCRUD));
    }

    @Test
    public void testWriteAndDeleteManyVersions() throws IOException {
        // store 3 blob versions
        String key1 = bs.writeBlob(blobContext(ID1, FOO));
        assertKey(ID1, key1);
        String key2 = bs.writeBlob(blobContext(ID1, BAR));
        assertKey(ID1, key2);
        String key3 = bs.writeBlob(blobContext(ID1, FOO + BAR));
        assertKey(ID1, key3);

        bs.deleteBlob(key1);
        assertNoBlob(key1);
        assertBlob(key2, BAR);
        assertBlob(key3, FOO + BAR);

        bs.deleteBlob(key3);
        assertNoBlob(key1);
        assertBlob(key2, BAR);
        assertNoBlob(key3);
    }
}
