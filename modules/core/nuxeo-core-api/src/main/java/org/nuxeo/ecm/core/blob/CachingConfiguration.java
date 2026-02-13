/*
 * (C) Copyright 2019-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.blob;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.nuxeo.common.utils.ByteSize;
import org.nuxeo.runtime.api.Framework;

/**
 * Configuration for a cache.
 *
 * @since 11.1
 */
public class CachingConfiguration extends PropertyBasedConfiguration {

    public static final String CACHE_SIZE_PROPERTY = "cachesize";

    public static final String CACHE_COUNT_PROPERTY = "cachecount";

    public static final String CACHE_MIN_AGE_PROPERTY = "cacheminage";

    public static final ByteSize DEFAULT_CACHE_BYTE_SIZE = ByteSize.ofMebibytes(100);

    /** @deprecated since 2025.11, use {@link #DEFAULT_CACHE_BYTE_SIZE} instead. */
    @Deprecated(since = "2025.11", forRemoval = true)
    public static final String DEFAULT_CACHE_SIZE = "100 mb";

    public static final long DEFAULT_CACHE_COUNT_LONG = 10000L;

    @Deprecated(since = "2025.11", forRemoval = true)
    public static final String DEFAULT_CACHE_COUNT = String.valueOf(DEFAULT_CACHE_COUNT_LONG);

    public static final Duration DEFAULT_CACHE_MIN_AGE_DURATION = Duration.ofHours(1);

    @Deprecated(since = "2025.11", forRemoval = true)
    public static final String DEFAULT_CACHE_MIN_AGE = String.valueOf(DEFAULT_CACHE_MIN_AGE_DURATION.toSeconds());

    public final Path dir;

    public final long maxSize;

    public final long maxCount;

    public final long minAge;

    public CachingConfiguration(String systemPropertyPrefix, Map<String, String> properties) throws IOException {
        super(systemPropertyPrefix, properties);
        dir = Framework.createTempDirectory("nxbincache.");
        maxSize = getOptionalByteSizeProperty(CACHE_SIZE_PROPERTY).orElse(DEFAULT_CACHE_BYTE_SIZE).toBytes();
        maxCount = getOptionalLongProperty(CACHE_COUNT_PROPERTY).orElse(DEFAULT_CACHE_COUNT_LONG);
        minAge = getOptionalLongProperty(CACHE_MIN_AGE_PROPERTY).orElseGet(DEFAULT_CACHE_MIN_AGE_DURATION::toSeconds);
    }

    public CachingConfiguration(Path dir, long maxSize, long maxCount, long minAge) {
        super(null, null);
        this.dir = dir;
        this.maxSize = maxSize;
        this.maxCount = maxCount;
        this.minAge = minAge;
    }

}
