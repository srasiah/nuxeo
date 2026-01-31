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

import org.nuxeo.ecm.core.blob.BlobManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.ConditionalIgnore;

/**
 * @since 2025.11
 */
public class IgnoreIfStorageRetentionDisabled implements ConditionalIgnore.Condition {

    @Override
    public boolean shouldIgnore() {
        var bp = Framework.getService(BlobManager.class).getBlobProvider("test");
        if (bp instanceof CloudBlobProvider<?> cloudBlobProvider) {
            return !cloudBlobProvider.config.retentionEnabled;
        }
        return true;
    }
}
