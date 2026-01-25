/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
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

import java.nio.file.Path;
import java.time.Duration;

import org.nuxeo.common.Environment;
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

    public static final long DEFAULT_DISK_CACHE_IN_KB = 10 * 1024;

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
     * The maximum size (in KB) to reach to run the GC.
     * <p>
     * Use a negative value to clear the cache on each GC run.
     */
    @XNode("maxSizeKB")
    protected Long maxSizeKB;

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

    public long getMaxSizeKB() {
        return requireNonNullElse(maxSizeKB, DEFAULT_DISK_CACHE_IN_KB);
    }

    @Override
    public Descriptor merge(Descriptor o) {
        var other = (ConvertCacheDescriptor) o;
        var merged = new ConvertCacheDescriptor();
        merged.enabled = other.enabled != null ? other.enabled : enabled;
        merged.directory = other.directory != null ? other.directory : directory;
        merged.gcRate = other.gcRate != null ? other.gcRate : gcRate;
        merged.maxSizeKB = other.maxSizeKB != null ? other.maxSizeKB : maxSizeKB;
        return merged;
    }
}
