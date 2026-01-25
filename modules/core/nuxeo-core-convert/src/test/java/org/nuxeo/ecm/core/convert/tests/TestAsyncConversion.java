/*
 * (C) Copyright 2015-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     Thomas Roger
 */
package org.nuxeo.ecm.core.convert.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.nuxeo.ecm.core.convert.service.ConversionServiceImpl.CONFIG_EP;
import static org.nuxeo.runtime.model.Descriptor.UNIQUE_DESCRIPTOR_ID;

import java.io.File;
import java.io.IOException;
import java.util.List;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.convert.ConvertFeature;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.convert.extension.ConvertCacheDescriptor;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.transientstore.TransientStoreFeature;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.impl.ComponentManagerImpl;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 7.4
 */
@RunWith(FeaturesRunner.class)
@Features({ ConvertFeature.class, TransientStoreFeature.class })
@Deploy("org.nuxeo.ecm.core.convert.tests:OSGI-INF/convert-service-no-cache-test-contrib.xml")
@Deploy("org.nuxeo.ecm.core.convert.tests:OSGI-INF/converters-test-contrib3.xml")
public class TestAsyncConversion {

    @Inject
    protected ConversionService conversionService;

    @Inject
    protected EventService eventService;

    @Test
    public void shouldDoAsyncConversionGivenDestinationMimeType() throws IOException {
        File file = FileUtils.getResourceFileFromContext("test-data/hello.doc");
        Blob blob = Blobs.createBlob(file, "application/msword", null, "hello.doc");
        BlobHolder bh = new SimpleBlobHolder(blob);

        String id = conversionService.scheduleConversionToMimeType("test/cache", bh, null);
        assertNotNull(id);

        eventService.waitForAsyncCompletion();

        BlobHolder result = conversionService.getConversionResult(id, true);
        assertNotNull(result);
        List<Blob> blobs = result.getBlobs();
        assertEquals(1, blobs.size());
        Blob resultBlob = blobs.get(0);
        assertEquals(blob.getFilename(), resultBlob.getFilename());
        assertEquals(blob.getMimeType(), resultBlob.getMimeType());
    }

    @Test
    public void shouldDoAsyncConversionGivenConverterName() throws IOException {
        File file = FileUtils.getResourceFileFromContext("test-data/hello.doc");
        Blob blob = Blobs.createBlob(file, "application/msword", null, "hello.doc");
        BlobHolder bh = new SimpleBlobHolder(blob);

        String id = conversionService.scheduleConversion("identity", bh, null);
        assertNotNull(id);

        eventService.waitForAsyncCompletion();

        BlobHolder result = conversionService.getConversionResult(id, true);
        assertNotNull(result);
        List<Blob> blobs = result.getBlobs();
        assertEquals(1, blobs.size());
        Blob resultBlob = blobs.get(0);
        assertEquals(blob.getFilename(), resultBlob.getFilename());
        assertEquals(blob.getMimeType(), resultBlob.getMimeType());
    }

    // NXP-33363
    // test is present here because it is the only place where we disable it
    @Test
    public void testCacheIsDisabled() {
        var componentManager = (ComponentManagerImpl) Framework.getRuntime().getComponentManager();
        ConvertCacheDescriptor cacheDescriptor = componentManager.getDescriptors()
                                                                 .getDescriptor(
                                                                         "org.nuxeo.ecm.core.convert.service.ConversionServiceImpl",
                                                                         CONFIG_EP, UNIQUE_DESCRIPTOR_ID);
        assertNotNull(cacheDescriptor);
        assertFalse(cacheDescriptor.isEnabled());
    }
}
