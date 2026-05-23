/*
 * (C) Copyright 2021 Nuxeo (http://nuxeo.com/) and others.
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
 *     bdelbosc
 */
package org.nuxeo.ecm.core.bulk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.bulk.S3SetBlobLengthAction.ACTION_NAME;
import static org.nuxeo.ecm.core.bulk.message.BulkStatus.State.COMPLETED;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;

import jakarta.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.blob.s3.S3BlobProviderFeature;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.api.impl.blob.URLBlob;
import org.nuxeo.ecm.core.bulk.computation.BulkScrollerComputation;
import org.nuxeo.ecm.core.bulk.message.BulkCommand.Builder;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@RunWith(FeaturesRunner.class)
@Features({ CoreFeature.class, S3BlobProviderFeature.class })
@Deploy("org.nuxeo.ecm.core.storage.binarymanager.s3.tests:OSGI-INF/test-bulk-contrib.xml")
public class TestSetBlobLengthAction {

    private static final Logger log = LogManager.getLogger(BulkScrollerComputation.class);

    @Inject
    public BulkService service;

    @Inject
    public CoreSession session;

    @Inject
    public TransactionalFeature txFeature;

    protected long expectedUrlBlobSize;

    protected long expectedMissingBlobSize;

    @Before
    public void populate() throws URISyntaxException {
        DocumentModel folder = session.createDocumentModel("/", "test", "Folder");
        folder = session.createDocument(folder);

        DocumentModel doc = session.createDocumentModel(folder.getPathAsString(), "file1", "File");
        Blob blob = Blobs.createBlob("A blob content");
        blob.setFilename("test_content_ok.doc");
        doc.setProperty("file", "content", blob);
        doc = session.createDocument(doc);

        doc = session.createDocumentModel(folder.getPathAsString(), "file2", "File");
        blob = Blobs.createBlob("Another blob content");
        blob.setFilename("test_content_ok2.doc");
        doc.setProperty("file", "content", blob);
        doc = session.createDocument(doc);

        doc.setPropertyValue("dc:title", "new title");
        // create versions
        session.checkIn(doc.getRef(), VersioningOption.MINOR, "testing version");
        session.checkOut(doc.getRef());
        session.checkIn(doc.getRef(), VersioningOption.MINOR, "another version");

        doc = session.createDocumentModel(folder.getPathAsString(), "file-missing-length", "File");
        blob = new MissingLengthBlob("A blob with a missing length");
        blob.setFilename("test_file_missing_length.doc");
        doc.setProperty("file", "content", blob);
        doc = session.createDocument(doc);

        // create proxy
        DocumentModel folder2 = session.createDocumentModel("/", "folder", "Folder");
        folder2 = session.createDocument(folder2);
        DocumentModel proxy = session.createProxy(doc.getRef(), folder2.getRef());
        proxy = session.saveDocument(proxy);

        doc = session.createDocumentModel(folder.getPathAsString(), "file-zero-length", "File");
        blob = new ZeroLengthBlob("A blob with an invalid length of 0");
        blob.setFilename("test_file_missing_length.doc");
        doc.setProperty("file", "content", blob);
        doc = session.createDocument(doc);

        doc = session.createDocumentModel(folder.getPathAsString(), "file-without-blob", "File");
        doc = session.createDocument(doc);

        doc = session.createDocumentModel(folder.getPathAsString(), "url-blob-file", "File");
        URL url = Thread.currentThread().getContextClassLoader().getResource("test.blob");
        File file = new File(url.toURI());
        expectedUrlBlobSize = file.length();
        blob = new URLBlob(url);
        blob.setFilename("test_url_missing_length.doc");
        doc.setProperty("file", "content", blob);
        doc = session.createDocument(doc);
        txFeature.nextTransaction();

    }

    protected int getDocsWithoutBlobLength() {
        return session.query("SELECT * FROM File WHERE file:content/length IS NULL").size();
    }

    @Test
    public void testSetBlobLength() throws InterruptedException {
        String nxql = "SELECT * from File";
        dumpDocs("BEFORE", nxql);
        // file-missing-length + file-missing-length proxy + file-without-blob
        assertEquals(3, getDocsWithoutBlobLength());
        String commandId = service.submit(
                new Builder(ACTION_NAME, nxql, "Administrator").repository(session.getRepositoryName())
                                                               .param("force", true)
                                                               .param("xpath", "content")
                                                               .build());
        assertTrue("Bulk action didn't finish", service.await(Duration.ofSeconds(60)));
        BulkStatus status = service.getStatus(commandId);
        log.info(status);
        assertNotNull(status);
        assertEquals(COMPLETED, status.getState());
        txFeature.nextTransaction();
        dumpDocs("AFTER", nxql);
        // file-without-blob
        assertEquals(1, getDocsWithoutBlobLength());
    }

    protected void dumpDocs(String title, String nxql) {
        log.info("---------- " + title);
        for (DocumentModel child : session.query(nxql)) {
            Long length = null;
            var blob = ((Blob) child.getPropertyValue("file:content"));
            if (blob != null) {
                length = blob.getLength();
            }
            log.info("{}: content/length: {}", child.getName(), length);
        }
    }
}
