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
package org.nuxeo.ecm.blob;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.lang3.reflect.TypeUtils;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.blob.BlobStoreBlobProvider;

/**
 * @since 2025.11
 */
public abstract class CloudBlobProvider<T extends CloudBlobStoreConfiguration> extends BlobStoreBlobProvider {

    public T config;

    @Override
    public void initialize(String blobProviderId, Map<String, String> properties) throws IOException {
        var typeArguments = TypeUtils.getTypeArguments(getClass(), CloudBlobProvider.class);
        var parameterType = typeArguments.get(CloudBlobProvider.class.getTypeParameters()[0]);
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) TypeUtils.getRawType(parameterType, null);
        try {
            config = clazz.getConstructor(Map.class).newInstance(properties);
        } catch (ReflectiveOperationException e) {
            throw new NuxeoException("Unable to initialize BlobProvider with id: " + blobProviderId, e);
        }
        super.initialize(blobProviderId, properties);
    }
}
