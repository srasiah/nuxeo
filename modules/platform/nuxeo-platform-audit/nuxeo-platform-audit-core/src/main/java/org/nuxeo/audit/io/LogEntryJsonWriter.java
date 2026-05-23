/*
 * (C) Copyright 2015-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nicolas Chapurlat <nchapurlat@nuxeo.com>
 */
package org.nuxeo.audit.io;

import static org.nuxeo.audit.api.LogEntryConstants.LOG_CATEGORY;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_COMMENT;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_DOC_LIFE_CYCLE;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_DOC_PATH;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_DOC_TYPE;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_DOC_UUID;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_EVENT_DATE;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_EVENT_ID;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_EXTENDED;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_ID;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_LOG_DATE;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_PRINCIPAL;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_PRINCIPAL_NAME;
import static org.nuxeo.audit.api.LogEntryConstants.LOG_REPOSITORY_ID;
import static org.nuxeo.common.utils.DateUtils.formatISODateTime;
import static org.nuxeo.common.utils.DateUtils.nowIfNull;
import static org.nuxeo.ecm.core.io.registry.reflect.Instantiations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.io.marshallers.json.ExtensibleEntityJsonWriter;
import org.nuxeo.ecm.core.io.marshallers.json.enrichers.AbstractJsonEnricher;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Convert {@link LogEntry} to Json.
 * <p>
 * This marshaller is enrichable: register class implementing {@link AbstractJsonEnricher} and managing {@link LogEntry}
 * .
 * <p>
 * This marshaller is also extensible: extend it and simply override
 * {@link ExtensibleEntityJsonWriter#extend(Object, JsonGenerator)}.
 * <p>
 * Format is:
 *
 * <pre>
 * {@code
 * {
 *   "entity-type":"logEntry",
 *   "category": "LOG_ENTRY_CATEGORY",
 *   "principalName": "LOG_ENTRY_PRINCIPAL",
 *   "comment": "LOG_ENTRY_COMMENT",
 *   "docLifeCycle": "DOC_LIFECYCLE",
 *   "docPath": "DOC_PATH",
 *   "docType": "DOC_TYPE",
 *   "docUUID": "DOC_UUID",
 *   "eventId": "EVENT_ID",
 *   "repositoryId": "REPO_ID",
 *   "eventDate": "LOG_EVENT_DATE",
 *   "logDate": "LOG_DATE"
 *             <-- contextParameters if there are enrichers activated
 *             <-- additional property provided by extend() method
 * }
 * }
 * </pre>
 *
 * @since 7.2
 */
@Setup(mode = SINGLETON, priority = REFERENCE)
public class LogEntryJsonWriter extends ExtensibleEntityJsonWriter<LogEntry> {

    public static final String ENTITY_TYPE = "logEntry";

    /**
     * {@link org.nuxeo.ecm.core.io.registry.context.RenderingContext} parameter to serialize JSON like extended infos
     * as JSON and not String.
     *
     * @since 2025.0
     */
    public static final String EXTENDED_INFO_JSON_STRING_AS_JSON = "extendedInfoJsonStringAsJson";

    /** @since 2025.9 */
    public static final String FETCH_PRINCIPAL = "principal";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public LogEntryJsonWriter() {
        super(ENTITY_TYPE);
    }

    @Override
    protected void writeEntityBody(LogEntry logEntry, JsonGenerator jg) throws IOException {
        jg.writeNumberField(LOG_ID, logEntry.getId());
        jg.writeStringField(LOG_CATEGORY, logEntry.getCategory());
        jg.writeStringField(LOG_PRINCIPAL_NAME, logEntry.getPrincipalName());
        if (ctx.getFetched(ENTITY_TYPE).contains(FETCH_PRINCIPAL)
                && getPrincipal(logEntry.getPrincipalName()) instanceof NuxeoPrincipal principal) {
            writeEntityField(LOG_PRINCIPAL, principal, jg);
        }
        jg.writeStringField(LOG_COMMENT, logEntry.getComment());
        jg.writeStringField(LOG_DOC_LIFE_CYCLE, logEntry.getDocLifeCycle());
        jg.writeStringField(LOG_DOC_PATH, logEntry.getDocPath());
        jg.writeStringField(LOG_DOC_TYPE, logEntry.getDocType());
        jg.writeStringField(LOG_DOC_UUID, logEntry.getDocUUID());
        jg.writeStringField(LOG_EVENT_ID, logEntry.getEventId());
        jg.writeStringField(LOG_REPOSITORY_ID, logEntry.getRepositoryId());
        jg.writeStringField(LOG_EVENT_DATE, formatISODateTime(nowIfNull(logEntry.getEventDate())));
        jg.writeStringField(LOG_LOG_DATE, formatISODateTime(nowIfNull(logEntry.getLogDate())));
        writeExtendedInfos(jg, logEntry);
    }

    protected NuxeoPrincipal getPrincipal(String principalName) {
        var userManager = Framework.getService(UserManager.class);
        // allows tests to not deploy userManager to use this writer
        if (Framework.isTestModeSet() && userManager == null) {
            return null;
        }
        return userManager.getPrincipal(principalName);
    }

    protected void writeExtendedInfos(JsonGenerator jg, LogEntry logEntry) throws IOException {
        Map<String, Object> extended = logEntry.getExtended();
        jg.writeObjectFieldStart(LOG_EXTENDED);
        for (String key : extended.keySet()) {
            var value = extended.get(key);
            if (value != null) {
                writeExtendedInfo(jg, key, (Serializable) value);
            } else {
                jg.writeNullField(key);
            }
        }
        jg.writeEndObject();
    }

    protected void writeExtendedInfo(JsonGenerator jg, String key, Serializable value) throws IOException {
        Class<?> clazz = value.getClass();
        if (Long.class.isAssignableFrom(clazz)) {
            jg.writeNumberField(key, (Long) value);
        } else if (Integer.class.isAssignableFrom(clazz)) {
            jg.writeNumberField(key, (Integer) value);
        } else if (Double.class.isAssignableFrom(clazz)) {
            jg.writeNumberField(key, (Double) value);
        } else if (Date.class.isAssignableFrom(clazz)) {
            jg.writeStringField(key, formatISODateTime((Date) value));
        } else if (String.class.isAssignableFrom(clazz)) {
            var string = (String) value;
            if (ctx.getBooleanParameter(EXTENDED_INFO_JSON_STRING_AS_JSON) && isJsonContent(string)) {
                jg.writeFieldName(key);
                try {
                    MAPPER.readTree(string);
                    jg.writeRawValue(string);
                } catch (IOException e) {
                    // the extended info value is not a valid JSON content
                    // send a null value to avoid mapping errors on backend that has such constraint (ie: OpenSearch)
                    jg.writeObject(null);
                }
            } else {
                jg.writeStringField(key, string);
            }
        } else if (Boolean.class.isAssignableFrom(clazz)) {
            jg.writeBooleanField(key, (Boolean) value);
        } else if (clazz.isArray() || List.class.isAssignableFrom(clazz)) {
            writeValues(jg, key, value);
        } else if (Map.class.isAssignableFrom(clazz)) {
            @SuppressWarnings("unchecked")
            Map<String, Serializable> map = (Map<String, Serializable>) value;
            jg.writeObjectFieldStart(key);
            for (Entry<String, Serializable> entry : map.entrySet()) {
                Serializable v = entry.getValue();
                if (v != null && !(v instanceof Blob)) {
                    writeExtendedInfo(jg, entry.getKey(), v);
                }
            }
            jg.writeEndObject();
        } else {
            // mainly blobs
            jg.writeStringField(key, value.toString());
        }
    }

    protected void writeValues(JsonGenerator jg, String key, Serializable value) throws IOException {
        if (value.getClass().isArray()) {
            Object[] values = (Object[]) value;
            if (values.length == 0 || !(values[0] instanceof Blob)) {
                jg.writeObjectField(key, value);
            }
        } else if (List.class.isAssignableFrom(value.getClass())) {
            List<?> values = (List<?>) value;
            if (values.isEmpty() || !(values.getFirst() instanceof Blob)) {
                jg.writeObjectField(key, value);
            }
        }
    }

    /**
     * @since 2025.0
     */
    public static boolean isJsonContent(String value) {
        value = StringUtils.trimToEmpty(value);
        return (value.startsWith("{") && value.endsWith("}")) || (value.startsWith("[") && value.endsWith("]"));
    }
}
