/*
 * (C) Copyright 2006-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     Thierry Delprat
 *     Stephane Lacoin
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.convert.extension;

import static java.util.Objects.requireNonNullElse;
import static org.apache.commons.lang3.BooleanUtils.toBooleanDefaultIfNull;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import org.apache.commons.io.FileUtils;
import org.nuxeo.common.Environment;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.NuxeoException;

/**
 * XMap Descriptor for the {@link org.nuxeo.ecm.core.convert.api.ConversionService} configuration.
 *
 * @deprecated since 2025.0, use {@link ConvertCacheDescriptor} instead
 */
@XObject("configuration")
@Deprecated(since = "2025.0", forRemoval = true)
public class GlobalConfigDescriptor {

    public static final boolean DEFAULT_CACHE_ENABLED = true;

    public static final long DEFAULT_GC_INTERVAL_IN_MIN = 10;

    public static final int DEFAULT_DISK_CACHE_IN_KB = 10 * 1024;

    public static final String DEFAULT_CACHING_DIRECTORY = "convertcache";

    @XNode("enableCache")
    protected Boolean enableCache;

    @XNode("cachingDirectory")
    protected String cachingDirectory;

    protected Long gcInterval;

    protected Integer diskCacheSize;

    public boolean isCacheEnabled() {
        return toBooleanDefaultIfNull(enableCache, DEFAULT_CACHE_ENABLED);
    }

    public String getCachingDirectory() {
        return cachingDirectory == null ? getDefaultCachingDirectory() : cachingDirectory;
    }

    protected String getDefaultCachingDirectory() {
        File cache = new File(Environment.getDefault().getData(), DEFAULT_CACHING_DIRECTORY);
        return cache.getAbsolutePath();
    }

    /** @since 9.1 */
    public void clearCachingDirectory() {
        File cache = new File(getCachingDirectory());
        if (cache.exists()) {
            try {
                FileUtils.deleteDirectory(cache);
            } catch (IOException e) {
                throw new NuxeoException("Cannot create cache dir " + cache, e);
            }
        }
        cache.mkdirs();
    }

    @XNode("gcInterval")
    public void setGCInterval(long value) {
        gcInterval = value == 0 ? null : Long.valueOf(value);
    }

    public long getGCInterval() {
        return requireNonNullElse(gcInterval, DEFAULT_GC_INTERVAL_IN_MIN);
    }

    @XNode("diskCacheSize")
    public void setDiskCacheSize(int size) {
        diskCacheSize = size == 0 ? null : Integer.valueOf(size);
    }

    public int getDiskCacheSize() {
        return requireNonNullElse(diskCacheSize, DEFAULT_DISK_CACHE_IN_KB);
    }

    public void update(GlobalConfigDescriptor other) {
        if (other.enableCache != null) {
            enableCache = other.enableCache;
        }
        if (other.gcInterval != null) {
            gcInterval = other.gcInterval;
        }
        if (other.diskCacheSize != null) {
            diskCacheSize = other.diskCacheSize;
        }
        if (other.cachingDirectory != null) {
            cachingDirectory = other.cachingDirectory;
        }
    }

    /**
     * @since 2025.0
     */
    public ConvertCacheDescriptor toConvertCacheDescriptor() {
        var descriptor = new ConvertCacheDescriptor();
        descriptor.enabled = enableCache;
        descriptor.directory = cachingDirectory;
        if (gcInterval != null) {
            if (gcInterval < 0) {
                // keep unclear behavior for test backward compatibility
                descriptor.gcRate = Duration.ofMillis(-gcInterval);
            } else {
                descriptor.gcRate = Duration.ofMinutes(gcInterval);
            }
        }
        if (diskCacheSize != null) {
            descriptor.setMaxSizeKB(diskCacheSize.longValue());
        }
        return descriptor;
    }
}
