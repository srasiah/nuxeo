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
package org.nuxeo.ecm.blob.azure;

import static org.nuxeo.ecm.core.blob.KeyStrategy.VER_SEP;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.nuxeo.ecm.core.blob.scroll.AbstractBlobScroll;

import com.azure.core.http.rest.PagedResponse;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobListDetails;
import com.azure.storage.blob.models.ListBlobsOptions;

/**
 * Scroll blobs of the Azure blob store of a {@link AzureBlobProvider}, the scroll query is the provider id.
 *
 * @since 2023.6
 */
public class AzureBlobScroll extends AbstractBlobScroll<AzureBlobProvider> {

    protected AzureBlobStore store;

    protected int prefixLength;

    protected String prefix;

    protected Iterator<PagedResponse<BlobItem>> iterator;

    @Override
    protected void init(AzureBlobProvider provider) {
        this.store = (AzureBlobStore) provider.store.unwrap();
        this.prefix = this.store.prefix;
        this.prefixLength = this.prefix.length();
        ListBlobsOptions options = new ListBlobsOptions().setPrefix(prefix).setMaxResultsPerPage(size);
        if (store.hasVersioning()) {
            var details = new BlobListDetails();
            details.setRetrieveVersions(true);
            options.setDetails(details);
        }
        this.iterator = this.store.client.listBlobsByHierarchy("/", options, null).iterableByPage().iterator();
    }

    @Override
    public boolean hasNext() {
        return this.iterator.hasNext();
    }

    @Override
    public List<String> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        List<String> result = new ArrayList<>(size);
        for (BlobItem blob : iterator.next().getElements()) {
            if (blob.isPrefix()) {
                // ignore sub directories
                continue;
            }
            var key = blob.getName().substring(prefixLength);
            if (store.hasVersioning()) {
                key += VER_SEP + blob.getVersionId();
            }
            addTo(result, key, () -> blob.getProperties().getContentLength());
        }
        return result;
    }

}
