/*
 * (C) Copyright 2021-2024 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.search.index.IndexingBackgroundAction.ACTION_NAME;

import java.io.Serializable;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.GregorianCalendar;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.commons.collections4.CollectionUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.SortInfo;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;
import org.nuxeo.ecm.core.bulk.BulkService;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.bulk.message.BulkCommand.Builder;
import org.nuxeo.ecm.core.bulk.message.BulkStatus;
import org.nuxeo.ecm.core.test.CoreSearchFeature;
import org.nuxeo.runtime.test.runner.ConditionalIgnore;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.TransactionalFeature;

@RunWith(FeaturesRunner.class)
@Features(CoreSearchFeature.class)
@ConditionalIgnore(condition = IgnoreIfSearchClientDoesNotHaveIndexingCapability.class)
public class TestSearchBulkIndex {

    // field bigger than a record
    protected static final int BIG_FIELD_SIZE = 1_200_000;

    @Inject
    protected CoreSession session;

    @Inject
    protected BulkService bulkService;

    @Inject
    protected SearchService searchService;

    @Inject
    protected CoreSearchFeature coreSearchFeature;

    @Inject
    protected TransactionalFeature txFeature;

    @Before
    public void initWorkingDocuments() {
        for (int i = 0; i < 20; i++) {
            String name = "file" + i;
            String title = String.format("File%02d", i);
            DocumentModel doc = session.createDocumentModel("/", name, "File");
            doc.setPropertyValue("dc:title", title);
            if (i == 0) {
                // create a huge field to make the doc bigger than a record
                doc.setPropertyValue("dc:source", new String(new char[BIG_FIELD_SIZE]).replace('\0', 'X'));
            }
            doc = session.createDocument(doc);
            if (i == 1) {
                // create a document with a broken ACE with end date before the beginning date
                ACP acp = new ACPImpl();
                ACL acl = ACPImpl.newACL(ACL.LOCAL_ACL);
                ACE brokenAce = new ACE("toto", SecurityConstants.READ, true);
                var now = ZonedDateTime.now();
                brokenAce.setEnd(GregorianCalendar.from(now.minusWeeks(1)));
                brokenAce.setBegin(GregorianCalendar.from(now));
                acl.add(brokenAce);
                acp.addACL(acl);
                try {
                    session.setACP(doc.getRef(), acp, true);
                } catch (IllegalArgumentException e) {
                    // expected during notification but the ACP is set
                }
            }
        }
        txFeature.nextTransaction();
    }

    @Test
    public void testIndexAction() throws InterruptedException {
        checkSearchOrder();

        BulkCommand command = new Builder(ACTION_NAME, "SELECT * FROM Document", "Administrator").batch(2)
                                                                                                 .bucket(2)
                                                                                                 .build();
        String commandId = bulkService.submit(command);
        assertTrue("command timeout", bulkService.await(commandId, Duration.ofSeconds(60)));
        BulkStatus status = bulkService.getStatus(commandId);
        assertEquals(BulkStatus.State.COMPLETED, status.getState());
        assertNotNull("Processing start time is null, status: " + status, status.getProcessingStartTime());
        assertNotNull("Processing end time is null, status: " + status, status.getProcessingEndTime());
        assertTrue("Processing duration is 0, status: " + status, status.getProcessingDurationMillis() > 0);
    }

    @Test
    public void testIndexValidationAction() throws InterruptedException {
        checkSearchOrder();
        // invalid param
        final BulkCommand invalidCommand = new Builder(ACTION_NAME, "SELECT * FROM Document", "Administrator").batch(
                2).bucket(2).param("indexes", "some-index").build();
        // expecting a list of indexes, not a string
        assertThrows(IllegalArgumentException.class, () -> bulkService.submit(invalidCommand));

        final BulkCommand invalidCommand2 = new Builder(ACTION_NAME, "SELECT * FROM Document", "Administrator").batch(
                2).bucket(2).param("indexes", (Serializable) List.of("enhanced", "unexisting-index")).build();
        // expecting valid indexes
        assertThrows(IllegalArgumentException.class, () -> bulkService.submit(invalidCommand2));

        BulkCommand command = new Builder(ACTION_NAME, "SELECT * FROM Document", "Administrator").batch(
                2).bucket(2).param("indexes", (Serializable) List.of("enhanced")).build();
        String commandId = bulkService.submit(command);
        assertTrue("command timeout", bulkService.await(commandId, Duration.ofSeconds(60)));
        BulkStatus status = bulkService.getStatus(commandId);
        assertEquals(BulkStatus.State.COMPLETED, status.getState());
    }

    protected void checkSearchOrder() {
        SearchQuery query = SearchQuery.builder("SELECT * FROM File", session)
                                       .addSort(new SortInfo("dc:title", false))
                                       .build();
        var response = searchService.search(query);
        var documents = response.loadDocuments(session);
        assertTrue(CollectionUtils.isNotEmpty(documents));
        List<String> ids = documents.stream().map(DocumentModel::getTitle).toList();
        List<String> ordered = new ArrayList<>(ids);
        ordered.sort(Comparator.reverseOrder());
        assertEquals(ordered, ids);
    }
}
