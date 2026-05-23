/*
 * (C) Copyright 2016-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     Guillaume Renard <grenard@nuxeo.com>
 */
package org.nuxeo.ecm.core.io.marshallers.json.aggregate;

import static org.nuxeo.common.utils.DateUtils.formatISODateTime;
import static org.nuxeo.common.utils.DateUtils.nowIfNull;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.FETCH_PROPERTIES;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.MAX_DEPTH_PARAM;
import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.TRANSLATE_PROPERTIES;
import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_AVG;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_CARDINALITY;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_COUNT;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_MAX;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_MIN;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_MISSING;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_SUM;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_TYPE_SIGNIFICANT_TERMS;
import static org.nuxeo.ecm.platform.query.api.AggregateConstants.AGG_TYPE_TERMS;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;

import jakarta.inject.Inject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.DocumentPartImpl;
import org.nuxeo.ecm.core.api.model.impl.PropertyFactory;
import org.nuxeo.ecm.core.io.marshallers.json.ExtensibleEntityJsonWriter;
import org.nuxeo.ecm.core.io.marshallers.json.document.DocumentModelJsonWriter;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.core.schema.types.Field;
import org.nuxeo.ecm.core.schema.types.ListType;
import org.nuxeo.ecm.core.schema.types.ListTypeImpl;
import org.nuxeo.ecm.core.schema.types.Schema;
import org.nuxeo.ecm.core.schema.types.SchemaImpl;
import org.nuxeo.ecm.core.schema.types.primitives.BooleanType;
import org.nuxeo.ecm.core.schema.types.primitives.DateType;
import org.nuxeo.ecm.core.schema.types.primitives.StringType;
import org.nuxeo.ecm.platform.query.api.Aggregate;
import org.nuxeo.ecm.platform.query.api.Bucket;
import org.nuxeo.ecm.platform.query.core.BucketDouble;
import org.nuxeo.ecm.platform.query.core.BucketRange;
import org.nuxeo.ecm.platform.query.core.BucketRangeDate;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.core.JsonGenerator;

/**
 * @since 8.4
 */
@SuppressWarnings("rawtypes")
@Setup(mode = SINGLETON, priority = REFERENCE)
public class AggregateJsonWriter extends ExtensibleEntityJsonWriter<Aggregate> {

    public static final String ENTITY_TYPE = "aggregate";

    public static final String FETCH_KEY = "key";

    private static final Logger log = LogManager.getLogger(AggregateJsonWriter.class);

    /** Fake schema for system properties usable as a page provider aggregate */
    protected static final Schema SYSTEM_SCHEMA = new SchemaImpl("system", null);

    /** @since 2025.0 */
    protected static final String DIRECTORY_ENTITY_TYPE = "directoryEntry";

    static {
        SYSTEM_SCHEMA.addField("ecm:mixinType", new ListTypeImpl("system", "", StringType.INSTANCE), null, 0, null);
        SYSTEM_SCHEMA.addField("ecm:tag", new ListTypeImpl("system", "", StringType.INSTANCE), null, 0, null);
        SYSTEM_SCHEMA.addField("ecm:primaryType", StringType.INSTANCE, null, 0, null);
        SYSTEM_SCHEMA.addField("ecm:currentLifeCycleState", StringType.INSTANCE, null, 0, null);
        SYSTEM_SCHEMA.addField("ecm:versionLabel", StringType.INSTANCE, null, 0, null);
        SYSTEM_SCHEMA.addField("ecm:isProxy", BooleanType.INSTANCE, null, 0, null);
        SYSTEM_SCHEMA.addField("ecm:isVersion", BooleanType.INSTANCE, null, 0, null);
        SYSTEM_SCHEMA.addField("ecm:isRecord", BooleanType.INSTANCE, null, 0, null);
        SYSTEM_SCHEMA.addField("ecm:retainUntil", DateType.INSTANCE, null, 0, null);
        SYSTEM_SCHEMA.addField("ecm:hasLegalHold", BooleanType.INSTANCE, null, 0, null);
        SYSTEM_SCHEMA.addField("ecm:isTrashed", BooleanType.INSTANCE, null, 0, null);
    }

    /**
     * @since 10.3
     */
    protected Field getSystemField(String name) {
        Field result = SYSTEM_SCHEMA.getField(name);
        if (result == null && name.startsWith("ecm:path@level")) {
            SYSTEM_SCHEMA.addField(name, StringType.INSTANCE, null, 0, null);
            return SYSTEM_SCHEMA.getField(name);
        }
        return result;
    }

    @Inject
    private SchemaManager schemaManager;

    public AggregateJsonWriter() {
        super(ENTITY_TYPE);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void writeEntityBody(Aggregate agg, JsonGenerator jg) throws IOException {
        boolean fetch = ctx.getFetched(ENTITY_TYPE).contains(FETCH_KEY);
        String fieldName = agg.getField();
        Field field;
        if (fieldName.startsWith("ecm:")) {
            field = getSystemField(fieldName);
            if (field == null) {
                log.warn("Field: {} is not a valid field for aggregates", fieldName);
                return;
            }
        } else {
            field = schemaManager.getField(fieldName);
        }
        jg.writeObjectField("id", agg.getId());
        jg.writeObjectField("field", agg.getField());
        jg.writeObjectField("properties", agg.getProperties());
        jg.writeObjectField("ranges", agg.getRanges());
        jg.writeObjectField("selection", agg.getSelection());
        jg.writeObjectField("type", agg.getType());
        switch (agg.getType()) {
            // single value bucket
            case AGG_AVG, AGG_MAX, AGG_MIN, AGG_COUNT, AGG_SUM, AGG_CARDINALITY, AGG_MISSING -> {
                if (agg.getBuckets().getFirst() instanceof BucketDouble bucket) {
                    jg.writeObjectField("value", Double.isFinite(bucket.getValue()) ? bucket.getValue() : null);
                }
            }
            default -> {
                if (!fetch || !(AGG_TYPE_TERMS.equals(agg.getType())
                        || AGG_TYPE_SIGNIFICANT_TERMS.equals(agg.getType()))) {
                    jg.writeObjectField("buckets", agg.getBuckets());
                    jg.writeObjectField("extendedBuckets", agg.getExtendedBuckets());
                } else {
                    if (field != null) {
                        try (Closeable ignored = ctx.wrap()
                                                    .with(FETCH_PROPERTIES + "." + DocumentModelJsonWriter.ENTITY_TYPE,
                                                            "properties")
                                                    .with(FETCH_PROPERTIES + "." + DIRECTORY_ENTITY_TYPE, "parent")
                                                    .with(TRANSLATE_PROPERTIES + "." + DIRECTORY_ENTITY_TYPE, "label")
                                                    .with(MAX_DEPTH_PARAM, "max")
                                                    .open()) {
                            // write buckets with privilege because we create a property to leverage marshallers
                            Framework.doPrivilegedThrowing(() -> {
                                writeBuckets("buckets", agg.getBuckets(), field, jg);
                                writeBuckets("extendedBuckets", agg.getExtendedBuckets(), field, jg);
                            });
                        }
                    } else {
                        log.warn("Could not resolve field: {} for aggregate: {}", fieldName, agg.getId());
                        jg.writeObjectField("buckets", agg.getBuckets());
                        jg.writeObjectField("extendedBuckets", agg.getExtendedBuckets());
                    }
                }
            }
        }
    }

    protected void writeBuckets(String fieldName, List<Bucket> buckets, Field field, JsonGenerator jg)
            throws IOException {
        // prepare document part in order to use property
        Schema schema = field.getDeclaringType().getSchema();
        DocumentPartImpl part = new DocumentPartImpl(schema);
        // write data
        jg.writeArrayFieldStart(fieldName);
        for (Bucket bucket : buckets) {
            jg.writeStartObject();

            jg.writeObjectField("key", bucket.getKey());

            Property prop = PropertyFactory.createProperty(part, field, Property.NONE);
            if (prop.isList()) {
                ListType t = (ListType) prop.getType();
                t.getField();
                prop = PropertyFactory.createProperty(part, t.getField(), Property.NONE);
            }
            log.debug("Writing value: {} for field: {} resolved to: {}", fieldName, field.getName(), prop.getName());
            prop.setValue(bucket.getKey());

            writeEntityField("fetchedKey", prop, jg);
            jg.writeNumberField("docCount", bucket.getDocCount());
            jg.writeEndObject();

            if (bucket instanceof BucketRange bucketRange) {
                jg.writeNumberField("from", bucketRange.getFrom());
                jg.writeNumberField("to", bucketRange.getTo());
            }

            if (bucket instanceof BucketRangeDate bucketRange) {
                jg.writeStringField("fromAsDate", formatISODateTime(nowIfNull(bucketRange.getFromAsDate())));
                jg.writeStringField("toAsDate", formatISODateTime(nowIfNull(bucketRange.getToAsDate())));
            }
        }
        jg.writeEndArray();
    }

}
