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
 *     bstefanescu
 */
package org.nuxeo.ecm.automation.io.rest;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationDocumentation;
import org.nuxeo.ecm.automation.OperationDocumentation.Param;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.io.LoginInfo;
import org.nuxeo.ecm.automation.io.rest.operations.AutomationInfo;
import org.nuxeo.ecm.automation.io.services.codec.ObjectCodec;
import org.nuxeo.ecm.automation.io.services.codec.ObjectCodecService;
import org.nuxeo.ecm.webengine.JsonFactoryManager;
import org.nuxeo.runtime.api.Framework;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

/**
 * Json writer for operations export.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 * @author <a href="mailto:grenard@nuxeo.com">Guillaume Renard</a>
 */
public class JsonWriter {

    private static JsonFactory getFactory() {
        return Framework.getService(JsonFactoryManager.class).getJsonFactory();
    }

    private static JsonGenerator createGenerator(OutputStream out) throws IOException {
        return getFactory().createGenerator(out, JsonEncoding.UTF8);
    }

    public static void writeAutomationInfo(OutputStream out, AutomationInfo info, boolean prettyPrint)
            throws IOException {
        writeAutomationInfo(createGenerator(out), info, prettyPrint);
    }

    public static void writeAutomationInfo(JsonGenerator jg, AutomationInfo info, boolean prettyPrint)
            throws IOException {
        if (prettyPrint) {
            jg.useDefaultPrettyPrinter();
        }
        jg.writeStartObject();
        writePaths(jg);
        writeCodecs(jg);
        writeOperations(jg, info);
        writeChains(jg, info);
        jg.writeEndObject();
        jg.flush();
    }

    private static void writePaths(JsonGenerator jg) throws IOException {
        jg.writeObjectFieldStart("paths");
        jg.writeStringField("login", "login");
        jg.writeEndObject();
    }

    private static void writeCodecs(JsonGenerator jg) throws IOException {
        jg.writeArrayFieldStart("codecs");
        ObjectCodecService codecs = Framework.getService(ObjectCodecService.class);
        for (ObjectCodec<?> codec : codecs.getCodecs()) {
            if (!codec.isBuiltin()) {
                jg.writeString(codec.getClass().getName());
            }
        }
        jg.writeEndArray();
    }

    /**
     * Used to export operations to studio.
     */
    public static String exportOperations() throws IOException, OperationException {
        return exportOperations(false);
    }

    /**
     * Used to export operations to studio.
     *
     * @param filterNotInStudio if true, operation types not exposed in Studio will be filtered.
     * @since 5.9.1
     */
    public static String exportOperations(boolean filterNotInStudio) throws IOException, OperationException {
        List<OperationDocumentation> ops = Framework.getService(AutomationService.class).getDocumentation();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (JsonGenerator jg = getFactory().createGenerator(out)) {
            jg.useDefaultPrettyPrinter();
            jg.writeStartObject();
            jg.writeArrayFieldStart("operations");
            for (OperationDocumentation op : ops) {
                if (filterNotInStudio) {
                    if (op.addToStudio && !Constants.CAT_CHAIN.equals(op.category)) {
                        writeOperation(jg, op);
                    }
                } else {
                    writeOperation(jg, op);
                }
            }
            jg.writeEndArray();
            jg.writeEndObject();
        }
        return out.toString(UTF_8);
    }

    private static void writeOperations(JsonGenerator jg, AutomationInfo info) throws IOException {
        jg.writeArrayFieldStart("operations");
        for (OperationDocumentation op : info.getOperations()) {
            writeOperation(jg, op);
        }
        jg.writeEndArray();
    }

    private static void writeChains(JsonGenerator jg, AutomationInfo info) throws IOException {
        jg.writeArrayFieldStart("chains");
        for (OperationDocumentation op : info.getChains()) {
            writeOperation(jg, op, Constants.CHAIN_ID_PREFIX + op.id);
        }
        jg.writeEndArray();
    }

    public static void writeOperation(OutputStream out, OperationDocumentation op) throws IOException {
        writeOperation(out, op, false);
    }

    /**
     * @since 5.9.4
     */
    public static void writeOperation(OutputStream out, OperationDocumentation op, boolean prettyPrint)
            throws IOException {
        writeOperation(createGenerator(out), op, prettyPrint);
    }

    public static void writeOperation(JsonGenerator jg, OperationDocumentation op) throws IOException {
        writeOperation(jg, op, false);
    }

    /**
     * @since 5.9.4
     */
    public static void writeOperation(JsonGenerator jg, OperationDocumentation op, boolean prettyPrint)
            throws IOException {
        writeOperation(jg, op, op.url, prettyPrint);
    }

    public static void writeOperation(JsonGenerator jg, OperationDocumentation op, String url) throws IOException {
        writeOperation(jg, op, url, false);
    }

    /**
     * @since 5.9.4
     */
    public static void writeOperation(JsonGenerator jg, OperationDocumentation op, String url, boolean prettyPrint)
            throws IOException {
        if (prettyPrint) {
            jg.useDefaultPrettyPrinter();
        }
        jg.writeStartObject();
        jg.writeStringField("id", op.id);
        if (op.getAliases() != null && op.getAliases().length > 0) {
            jg.writeArrayFieldStart("aliases");
            for (String alias : op.getAliases()) {
                jg.writeString(alias);
            }
            jg.writeEndArray();
        }
        jg.writeStringField("label", op.label);
        jg.writeStringField("category", op.category);
        jg.writeStringField("requires", op.requires);
        jg.writeStringField("description", op.description);
        if (StringUtils.isNotEmpty(op.since)) {
            jg.writeStringField("since", op.since);
        }
        jg.writeStringField("url", url);
        jg.writeArrayFieldStart("signature");
        for (String s : op.signature) {
            jg.writeString(s);
        }
        jg.writeEndArray();
        writeParams(jg, Arrays.asList(op.params));
        jg.writeEndObject();
        jg.flush();
    }

    private static void writeParams(JsonGenerator jg, List<Param> params) throws IOException {
        jg.writeArrayFieldStart("params");
        for (Param p : params) {
            jg.writeStartObject();
            jg.writeStringField("name", p.name);
            jg.writeStringField("description", p.description);
            jg.writeStringField("type", p.type);
            jg.writeBooleanField("required", p.required);

            jg.writeStringField("widget", p.widget);
            jg.writeNumberField("order", p.order);
            jg.writeArrayFieldStart("values");
            for (String value : p.values) {
                jg.writeString(value);
            }
            jg.writeEndArray();
            jg.writeEndObject();
        }
        jg.writeEndArray();
    }

    public static void writeLogin(OutputStream out, LoginInfo login) throws IOException {
        writeLogin(createGenerator(out), login);
    }

    public static void writeLogin(JsonGenerator jg, LoginInfo login) throws IOException {
        jg.writeStartObject();
        jg.writeStringField("entity-type", "login");
        jg.writeStringField("id", login.getId());
        jg.writeStringField("name", login.getUsername());
        jg.writeStringField("username", login.getUsername());
        jg.writeBooleanField("isAdministrator", login.isAdministrator());
        jg.writeArrayFieldStart("groups");
        for (String group : login.getGroups()) {
            jg.writeString(group);
        }
        jg.writeEndArray();
        jg.writeEndObject();
        jg.flush();
    }

    public static void writePrimitive(OutputStream out, Object value) throws IOException {
        writePrimitive(createGenerator(out), value);
    }

    public static void writePrimitive(JsonGenerator jg, Object value) throws IOException {
        jg.writeStartObject();
        jg.writeStringField("entity-type", "primitive");
        switch (value) {
            case String string -> jg.writeStringField("value", string);
            case Boolean booleanValue -> jg.writeBooleanField("value", booleanValue);
            case Long longValue -> jg.writeNumberField("value", longValue);
            case Double doubleValue -> jg.writeNumberField("value", doubleValue);
            case Integer integerValue -> jg.writeNumberField("value", integerValue);
            case Float floatValue -> jg.writeNumberField("value", floatValue);
            case null -> jg.writeNullField("value");
            default -> {
                // nothing
            }
        }
        jg.writeEndObject();
        jg.flush();
    }

}
