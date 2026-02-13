/*
 * (C) Copyright 2024-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     Kevin Leturc <kevin.leturc@hyland.com>
 */
package org.nuxeo.ecm.core.convert.extension;

import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.BooleanUtils.toBooleanDefaultIfNull;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;

import java.nio.file.Path;
import java.time.Duration;

import org.nuxeo.common.Environment;
import org.nuxeo.common.utils.ByteSize;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.model.Descriptor;

/**
 * @since 2025.0
 */
@XObject("cache")
public class ConvertCacheDescriptor implements Descriptor {

    public static final boolean DEFAULT_CACHE_ENABLED = true;

    public static final String DEFAULT_CACHING_DIRECTORY = "convertcache";

    public static final Duration DEFAULT_GC_RATE = Duration.ofMinutes(10);

    public static final ByteSize DEFAULT_DISK_CACHE = ByteSize.ofMebibytes(10);

    /** @deprecated since 2025.11, use {@link #DEFAULT_DISK_CACHE} instead */
    @Deprecated(since = "2025.11", forRemoval = true)
    public static final long DEFAULT_DISK_CACHE_IN_KB = DEFAULT_DISK_CACHE.toKibibytes();

    @XNode("@enabled")
    protected Boolean enabled;

    @XNode("directory")
    protected String directory;

    /**
     * The rate to run the GC.
     */
    @XNode("gcRate")
    protected Duration gcRate;

    /**
     * The maximum byte size to reach to run the GC.
     * <p>
     * Use a negative value to clear the cache on each GC run.
     */
    @XNode("maxSize")
    protected ByteSize maxSize;

    @Override
    public String getId() {
        return UNIQUE_DESCRIPTOR_ID;
    }

    public boolean isEnabled() {
        return toBooleanDefaultIfNull(enabled, DEFAULT_CACHE_ENABLED);
    }

    public Path getDirectory() {
        if (directory == null) {
            return Environment.getDefault().getData().toPath().resolve(DEFAULT_CACHING_DIRECTORY).toAbsolutePath();
        }
        return Path.of(directory);
    }

    public Duration getGcRate() {
        return requireNonNullElse(gcRate, DEFAULT_GC_RATE);
    }

    /** @since 2025.11 */
    public ByteSize getMaxSize() {
        return requireNonNullElse(maxSize, DEFAULT_DISK_CACHE);
    }

    /** @deprecated since 2025.11, use {@link #getMaxSize()} instead */
    @Deprecated(since = "2025.11", forRemoval = true)
    public long getMaxSizeKB() {
        return getMaxSize().toKibibytes();
    }

    /**
     * @since 2025.11, for backward compatibility purpose
     * @deprecated since 2025.11
     */
    @XNode("maxSizeKB")
    protected void setMaxSizeKB(Long maxSizeKB) {
        this.maxSize = ByteSize.ofKibibytes(maxSizeKB);
    }

    @Override
    public Descriptor merge(Descriptor o) {
        var other = (ConvertCacheDescriptor) o;
        var merged = new ConvertCacheDescriptor();
        merged.enabled = getIfNull(other.enabled, enabled);
        merged.directory = getIfNull(other.directory, directory);
        merged.gcRate = getIfNull(other.gcRate, gcRate);
        merged.maxSize = getIfNull(other.maxSize, maxSize);
        return merged;
    }
}
