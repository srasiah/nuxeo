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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.sql;

import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.ObjectUtils.getIfNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.utils.ByteSize;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XNodeMap;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.ecm.core.api.repository.PoolConfiguration;
import org.nuxeo.ecm.core.storage.FulltextDescriptor;
import org.nuxeo.ecm.core.storage.FulltextDescriptor.FulltextIndexDescriptor;
import org.nuxeo.runtime.model.Descriptor;

/**
 * Low-level VCS Repository Descriptor.
 */
@XObject(value = "repository", order = { "@name" })
public class RepositoryDescriptor implements Descriptor {

    private static final Logger log = LogManager.getLogger(RepositoryDescriptor.class);

    /** @deprecated since 11.1, was PostgreSQL-specific */
    @Deprecated
    public static final int DEFAULT_READ_ACL_MAX_SIZE = 4096;

    public static final int DEFAULT_PATH_OPTIM_VERSION = 2;

    /** At startup, DDL changes are not detected. */
    public static final String DDL_MODE_IGNORE = "ignore";

    /** At startup, DDL changes are detected and if not empty they are dumped. */
    public static final String DDL_MODE_DUMP = "dump";

    /** At startup, DDL changes are detected and executed. */
    public static final String DDL_MODE_EXECUTE = "execute";

    /** At startup, DDL changes are detected and if not empty Nuxeo startup is aborted. */
    public static final String DDL_MODE_ABORT = "abort";

    /** Specifies that stored procedure detection must be compatible with previous Nuxeo versions. */
    public static final String DDL_MODE_COMPAT = "compat";

    @XObject(value = "field")
    public static class FieldDescriptor {

        // empty constructor needed by XMap
        public FieldDescriptor() {
        }

        /** Copy constructor. */
        public FieldDescriptor(FieldDescriptor other) {
            type = other.type;
            field = other.field;
            table = other.table;
            column = other.column;
        }

        public static List<FieldDescriptor> copyList(List<FieldDescriptor> other) {
            List<FieldDescriptor> copy = new ArrayList<>(other.size());
            for (FieldDescriptor fd : other) {
                copy.add(new FieldDescriptor(fd));
            }
            return copy;
        }

        public void merge(FieldDescriptor other) {
            if (other.field != null) {
                field = other.field;
            }
            if (other.type != null) {
                type = other.type;
            }
            if (other.table != null) {
                table = other.table;
            }
            if (other.column != null) {
                column = other.column;
            }
        }

        @XNode("@type")
        public String type;

        public String field;

        @XNode("@name")
        public void setName(String name) {
            if (!StringUtils.isBlank(name) && field == null) {
                field = name;
            }
        }

        // compat with older syntax
        @XNode
        public void setXNodeContent(String name) {
            setName(name);
        }

        @XNode("@table")
        public String table;

        @XNode("@column")
        public String column;

        @Override
        public String toString() {
            return this.getClass().getSimpleName() + '(' + field + ",type=" + type + ",table=" + table + ",column="
                    + column + ")";
        }
    }

    /** False if the boolean is null or FALSE, true otherwise. */
    private static boolean defaultFalse(Boolean bool) {
        return Boolean.TRUE.equals(bool);
    }

    /** True if the boolean is null or TRUE, false otherwise. */
    private static boolean defaultTrue(Boolean bool) {
        return !Boolean.FALSE.equals(bool);
    }

    public String name;

    @Override
    public String getId() {
        return name;
    }

    @XNode("@name")
    public void setName(String name) {
        this.name = name;
    }

    @XNode("@label")
    public String label;

    @XNode("@isDefault")
    private Boolean isDefault;

    public Boolean isDefault() {
        return isDefault;
    }

    @XNode("@headless")
    private Boolean headless;

    /** @since 11.2 */
    public Boolean isHeadless() {
        return headless;
    }

    // compat, when used with old-style extension point syntax
    // and nested repository
    @XNode("repository")
    public RepositoryDescriptor repositoryDescriptor;

    @XNode("pool")
    public PoolConfiguration pool;

    @XNode("clusterInvalidatorClass")
    public Class<? extends VCSClusterInvalidator> clusterInvalidatorClass;

    @XNode("cachingMapper@class")
    public Class<? extends CachingMapper> cachingMapperClass;

    @XNode("cachingMapper@enabled")
    private Boolean cachingMapperEnabled;

    public boolean getCachingMapperEnabled() {
        return defaultTrue(cachingMapperEnabled);
    }

    @XNodeMap(value = "cachingMapper/property", key = "@name", type = HashMap.class, componentType = String.class)
    public Map<String, String> cachingMapperProperties = new HashMap<>();

    @XNode("ddlMode")
    private String ddlMode;

    public String getDDLMode() {
        return ddlMode;
    }

    @XNode("noDDL")
    private Boolean noDDL;

    public boolean getNoDDL() {
        return defaultFalse(noDDL);
    }

    @XNodeList(value = "sqlInitFile", type = ArrayList.class, componentType = String.class)
    public List<String> sqlInitFiles = new ArrayList<>(0);

    @XNode("softDelete@enabled")
    private Boolean softDeleteEnabled;

    public boolean getSoftDeleteEnabled() {
        return defaultFalse(softDeleteEnabled);
    }

    protected void setSoftDeleteEnabled(boolean enabled) {
        softDeleteEnabled = Boolean.valueOf(enabled);
    }

    @XNode("proxies@enabled")
    private Boolean proxiesEnabled;

    public boolean getProxiesEnabled() {
        return defaultTrue(proxiesEnabled);
    }

    protected void setProxiesEnabled(boolean enabled) {
        proxiesEnabled = Boolean.valueOf(enabled);
    }

    @XNode("idType")
    public String idType; // "varchar", "uuid", "sequence"

    @XNodeList(value = "schema/field", type = ArrayList.class, componentType = FieldDescriptor.class)
    public List<FieldDescriptor> schemaFields = new ArrayList<>(0);

    @XNode("schema/arrayColumns")
    private Boolean arrayColumns;

    public boolean getArrayColumns() {
        return defaultFalse(arrayColumns);
    }

    public void setArrayColumns(boolean enabled) {
        arrayColumns = Boolean.valueOf(enabled);
    }

    @XNode("childNameUniqueConstraintEnabled")
    private Boolean childNameUniqueConstraintEnabled;

    public boolean getChildNameUniqueConstraintEnabled() {
        return defaultTrue(childNameUniqueConstraintEnabled);
    }

    @XNode("collectionUniqueConstraintEnabled")
    private Boolean collectionUniqueConstraintEnabled;

    public boolean getCollectionUniqueConstraintEnabled() {
        return defaultTrue(collectionUniqueConstraintEnabled);
    }

    @XNode("indexing/queryMaker@class")
    public void setQueryMakerDeprecated(String klass) {
        log.warn("Setting queryMaker from repository configuration is now deprecated");
    }

    // VCS-specific fulltext indexing options
    private String fulltextAnalyzer;

    public String getFulltextAnalyzer() {
        return fulltextAnalyzer;
    }

    @XNode("indexing/fulltext@analyzer")
    public void setFulltextAnalyzer(String fulltextAnalyzer) {
        this.fulltextAnalyzer = fulltextAnalyzer;
    }

    private String fulltextCatalog;

    public String getFulltextCatalog() {
        return fulltextCatalog;
    }

    @XNode("indexing/fulltext@catalog")
    public void setFulltextCatalog(String fulltextCatalog) {
        this.fulltextCatalog = fulltextCatalog;
    }

    private FulltextDescriptor fulltextDescriptor = new FulltextDescriptor();

    public FulltextDescriptor getFulltextDescriptor() {
        return fulltextDescriptor;
    }

    /** @since 2025.11 */
    @XNode("indexing/fulltext@fieldSizeLimit")
    public void setFulltextFieldSizeLimit(ByteSize fieldByteSizeLimit) {
        fulltextDescriptor.setFulltextFieldByteSizeLimit(fieldByteSizeLimit);
    }

    /** @deprecated since 2025.11, use {@link #setFulltextFieldSizeLimit(ByteSize)} instead */
    @Deprecated(since = "2025.11", forRemoval = true)
    public void setFulltextFieldSizeLimit(int fieldSizeLimit) {
        setFulltextFieldSizeLimit(ByteSize.ofBytes(fieldSizeLimit));
    }

    @XNode("indexing/fulltext@disabled")
    public void setFulltextDisabled(boolean disabled) {
        fulltextDescriptor.setFulltextDisabled(disabled);
    }

    /** @since 11.1 */
    @XNode("indexing/fulltext@storedInBlob")
    public void setFulltextStoredInBlob(boolean storedInBlob) {
        fulltextDescriptor.setFulltextStoredInBlob(storedInBlob);
    }

    @XNode("indexing/fulltext@searchDisabled")
    public void setFulltextSearchDisabled(boolean disabled) {
        fulltextDescriptor.setFulltextSearchDisabled(disabled);
    }

    @XNodeList(value = "indexing/fulltext/index", type = ArrayList.class, componentType = FulltextIndexDescriptor.class)
    public void setFulltextIndexes(List<FulltextIndexDescriptor> fulltextIndexes) {
        fulltextDescriptor.setFulltextIndexes(fulltextIndexes);
    }

    @XNodeList(value = "indexing/excludedTypes/type", type = HashSet.class, componentType = String.class)
    public void setFulltextExcludedTypes(Set<String> fulltextExcludedTypes) {
        fulltextDescriptor.setFulltextExcludedTypes(fulltextExcludedTypes);
    }

    @XNodeList(value = "indexing/includedTypes/type", type = HashSet.class, componentType = String.class)
    public void setFulltextIncludedTypes(Set<String> fulltextIncludedTypes) {
        fulltextDescriptor.setFulltextIncludedTypes(fulltextIncludedTypes);
    }

    // compat
    @XNodeList(value = "indexing/neverPerDocumentFacets/facet", type = HashSet.class, componentType = String.class)
    public Set<String> neverPerInstanceMixins = new HashSet<>(0);

    @XNode("pathOptimizations@enabled")
    private Boolean pathOptimizationsEnabled;

    public boolean getPathOptimizationsEnabled() {
        return defaultTrue(pathOptimizationsEnabled);
    }

    protected void setPathOptimizationsEnabled(boolean enabled) {
        pathOptimizationsEnabled = Boolean.valueOf(enabled);
    }

    /* @since 5.7 */
    @XNode("pathOptimizations@version")
    private Integer pathOptimizationsVersion;

    public int getPathOptimizationsVersion() {
        return pathOptimizationsVersion == null ? DEFAULT_PATH_OPTIM_VERSION : pathOptimizationsVersion.intValue();
    }

    @XNode("aclOptimizations@enabled")
    private Boolean aclOptimizationsEnabled;

    public boolean getAclOptimizationsEnabled() {
        return defaultTrue(aclOptimizationsEnabled);
    }

    protected void setAclOptimizationsEnabled(boolean enabled) {
        aclOptimizationsEnabled = Boolean.valueOf(enabled);
    }

    /* @since 5.4.2 */
    @XNode("aclOptimizations@readAclMaxSize")
    private Integer readAclMaxSize;

    public int getReadAclMaxSize() {
        return readAclMaxSize == null ? 0 : readAclMaxSize.intValue();
    }

    @XNode("usersSeparator@key")
    public String usersSeparatorKey;

    /** @since 9.1 */
    @XNode("changeTokenEnabled")
    private Boolean changeTokenEnabled;

    /** @since 9.1 */
    public boolean isChangeTokenEnabled() {
        return defaultFalse(changeTokenEnabled);
    }

    /** @since 9.1 */
    public void setChangeTokenEnabled(boolean enabled) {
        this.changeTokenEnabled = Boolean.valueOf(enabled);
    }

    public RepositoryDescriptor() {
    }

    /** Copy constructor. */
    public RepositoryDescriptor(RepositoryDescriptor other) {
        name = other.name;
        label = other.label;
        isDefault = other.isDefault;
        headless = other.headless;
        pool = other.pool == null ? null : new PoolConfiguration(other.pool);
        clusterInvalidatorClass = other.clusterInvalidatorClass;
        cachingMapperClass = other.cachingMapperClass;
        cachingMapperEnabled = other.cachingMapperEnabled;
        cachingMapperProperties = new HashMap<>(other.cachingMapperProperties);
        noDDL = other.noDDL;
        ddlMode = other.ddlMode;
        sqlInitFiles = new ArrayList<>(other.sqlInitFiles);
        softDeleteEnabled = other.softDeleteEnabled;
        proxiesEnabled = other.proxiesEnabled;
        schemaFields = FieldDescriptor.copyList(other.schemaFields);
        arrayColumns = other.arrayColumns;
        childNameUniqueConstraintEnabled = other.childNameUniqueConstraintEnabled;
        collectionUniqueConstraintEnabled = other.collectionUniqueConstraintEnabled;
        idType = other.idType;
        fulltextAnalyzer = other.fulltextAnalyzer;
        fulltextCatalog = other.fulltextCatalog;
        fulltextDescriptor = new FulltextDescriptor(other.fulltextDescriptor);
        neverPerInstanceMixins = other.neverPerInstanceMixins;
        pathOptimizationsEnabled = other.pathOptimizationsEnabled;
        pathOptimizationsVersion = other.pathOptimizationsVersion;
        aclOptimizationsEnabled = other.aclOptimizationsEnabled;
        readAclMaxSize = other.readAclMaxSize;
        usersSeparatorKey = other.usersSeparatorKey;
        changeTokenEnabled = other.changeTokenEnabled;
    }

    @Override
    public RepositoryDescriptor merge(Descriptor o) {
        var other = (RepositoryDescriptor) o;
        var merged = new RepositoryDescriptor(this);
        merged.name = getIfNull(other.name, merged.name);
        merged.label = getIfNull(other.label, merged.label);
        merged.isDefault = getIfNull(other.isDefault, merged.isDefault);
        merged.headless = getIfNull(other.headless, merged.headless);
        if (other.pool != null) {
            if (merged.pool == null) {
                merged.pool = new PoolConfiguration(other.pool);
            } else {
                merged.pool.merge(other.pool);
            }
        }
        merged.clusterInvalidatorClass = getIfNull(other.clusterInvalidatorClass, merged.clusterInvalidatorClass);
        merged.cachingMapperClass = getIfNull(other.cachingMapperClass, merged.cachingMapperClass);
        merged.cachingMapperEnabled = getIfNull(other.cachingMapperEnabled, merged.cachingMapperEnabled);
        merged.cachingMapperProperties.putAll(other.cachingMapperProperties);
        merged.noDDL = getIfNull(other.noDDL, merged.noDDL);
        merged.ddlMode = getIfNull(other.ddlMode, merged.ddlMode);
        merged.sqlInitFiles.addAll(other.sqlInitFiles);
        merged.softDeleteEnabled = getIfNull(other.softDeleteEnabled, merged.softDeleteEnabled);
        merged.proxiesEnabled = getIfNull(other.proxiesEnabled, merged.proxiesEnabled);
        merged.idType = getIfNull(other.idType, merged.idType);
        Map<String, FieldDescriptor> mappedFields = merged.schemaFields.stream().collect(toMap(f -> f.field, f -> f));
        for (FieldDescriptor of : other.schemaFields) {
            FieldDescriptor f = mappedFields.get(of.field);
            if (f != null) {
                f.merge(of);
            } else {
                merged.schemaFields.add(of);
            }
        }
        merged.arrayColumns = getIfNull(other.arrayColumns, merged.arrayColumns);
        merged.childNameUniqueConstraintEnabled = getIfNull(other.childNameUniqueConstraintEnabled,
                merged.childNameUniqueConstraintEnabled);
        merged.collectionUniqueConstraintEnabled = getIfNull(other.collectionUniqueConstraintEnabled,
                merged.collectionUniqueConstraintEnabled);
        merged.fulltextAnalyzer = getIfNull(other.fulltextAnalyzer, merged.fulltextAnalyzer);
        merged.fulltextCatalog = getIfNull(other.fulltextCatalog, merged.fulltextCatalog);
        merged.fulltextDescriptor.merge(other.fulltextDescriptor);
        merged.neverPerInstanceMixins.addAll(other.neverPerInstanceMixins);
        merged.pathOptimizationsEnabled = getIfNull(other.pathOptimizationsEnabled, merged.pathOptimizationsEnabled);

        merged.pathOptimizationsVersion = getIfNull(other.pathOptimizationsVersion, merged.pathOptimizationsVersion);
        merged.aclOptimizationsEnabled = getIfNull(other.aclOptimizationsEnabled, merged.aclOptimizationsEnabled);
        merged.readAclMaxSize = getIfNull(other.readAclMaxSize, merged.readAclMaxSize);
        merged.usersSeparatorKey = getIfNull(other.usersSeparatorKey, merged.usersSeparatorKey);
        merged.changeTokenEnabled = getIfNull(other.changeTokenEnabled, merged.changeTokenEnabled);
        return merged;
    }

}
