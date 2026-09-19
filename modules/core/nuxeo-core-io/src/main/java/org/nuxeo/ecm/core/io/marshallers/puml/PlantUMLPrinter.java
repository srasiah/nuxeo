/*
 * (C) Copyright 2025-2026 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.io.marshallers.puml;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @since 2025.12
 */
@SuppressWarnings("RedundantThrows") // IOException is not always necessary but still interesting for future change
public class PlantUMLPrinter implements Closeable, Flushable {

    protected final PrintWriter writer;

    protected Deque<Type> typeDeque = new ArrayDeque<>();

    public PlantUMLPrinter(Writer writer) {
        this.writer = (writer instanceof PrintWriter printWriter) ? printWriter : new PrintWriter(writer);
    }

    public void writeStartDocument() throws IOException {
        writer.println("@startuml");
    }

    public void writeEndDocument() throws IOException {
        writer.println("@enduml");
    }

    public void writeTitle(String title) throws IOException {
        writer.print("title ");
        writer.println(title);
        writer.println();
    }

    /** @since 2025.16 */
    public void writeHide(String hide) throws IOException {
        writer.print("hide ");
        writer.println(hide);
        writer.println();
    }

    public void writeSkinparam(String key, String value) throws IOException {
        writer.print("skinparam ");
        writer.print(key);
        writer.print(" ");
        writer.println(value);
    }

    protected void writeStartSkinparam(String parentKey) throws IOException {
        pushTypeIfNeeded(Type.UNKNOWN);
        indent();
        writer.print("skinparam ");
        writer.print(parentKey);
        pushTypeIfNeeded(Type.CONTAINER);
    }

    protected void writeSkinparamKeyValue(String key, String value) throws IOException {
        indent();
        writer.print("  ");
        writer.print(key);
        writer.print(" ");
        writer.println(value);
    }

    protected void writeEndSkinparam() throws IOException {
        popType();
    }

    /** @since 2025.16 */
    public void writeSkinparam(String parentKey, String key1, String value1) throws IOException {
        writeStartSkinparam(parentKey);
        writeSkinparamKeyValue(key1, value1);
        writeEndSkinparam();
    }

    public void writeSkinparam(String parentKey, String key1, String value1, String key2, String value2)
            throws IOException {
        writeStartSkinparam(parentKey);
        writeSkinparamKeyValue(key1, value1);
        writeSkinparamKeyValue(key2, value2);
        writeEndSkinparam();
    }

    /** @since 2025.16 */
    public void writeSkinparam(String parentKey, String key1, String value1, String key2, String value2, String key3,
            String value3) throws IOException {
        writeStartSkinparam(parentKey);
        writeSkinparamKeyValue(key1, value1);
        writeSkinparamKeyValue(key2, value2);
        writeSkinparamKeyValue(key3, value3);
        writeEndSkinparam();
    }

    /** @since 2025.16 */
    public void writeStartClass(String identifier, String title) throws IOException {
        writeStartElement(ElementType.CLASS, identifier, title, null);
    }

    /** @since 2025.16 */
    public void writeEndClass() throws IOException {
        popType();
    }

    public void writeStartComponent(String identifier) throws IOException {
        writeStartComponent(identifier, null);
    }

    public void writeStartComponent(String identifier, String title) throws IOException {
        writeStartComponent(identifier, title, null);
    }

    /** @since 2025.16 */
    public void writeStartComponent(String identifier, @Nullable String title, @Nullable String stereo)
            throws IOException {
        writeStartElement(ElementType.COMPONENT, identifier, title, stereo);
    }

    public void writeEndComponent() throws IOException {
        popType();
    }

    /** @since 2025.16 */
    public void writeStartDatabase(String identifier, String title) throws IOException {
        writeStartElement(ElementType.DATABASE, identifier, title, null);
    }

    /** @since 2025.16 */
    public void writeEndDatabase() throws IOException {
        popType();
    }

    /** @since 2025.16 */
    public void writeStartPackage(String identifier, String title) throws IOException {
        writeStartElement(ElementType.PACKAGE, identifier, title, null);
    }

    /** @since 2025.16 */
    public void writeEndPackage() throws IOException {
        popType();
    }

    /** @since 2025.16 */
    public void writeStartQueue(String identifier) throws IOException {
        writeStartElement(ElementType.QUEUE, identifier, null, null);
    }

    /** @since 2025.16 */
    public void writeStartQueue(String identifier, String title) throws IOException {
        writeStartElement(ElementType.QUEUE, identifier, title, null);
    }

    /** @since 2025.16 */
    public void writeEndQueue() throws IOException {
        popType();
    }

    protected void writeStartElement(ElementType type, String identifier, @Nullable String title,
            @Nullable String stereo) throws IOException {
        pushTypeIfNeeded(Type.UNKNOWN);
        indent();
        writer.print(type.type);
        writer.print(" ");
        writer.print(formatIdentifier(identifier));
        if (isNotBlank(stereo)) {
            writer.print(" <<");
            writer.print(stereo);
            writer.print(">>");
        }
        if (isNotBlank(title)) {
            writer.print(" as \"");
            writer.print(title);
            writer.print("\"");
            pushTypeIfNeeded(Type.CONTAINER);
        }
    }

    public void writeArrow(String identifier1, String identifier2) throws IOException {
        writeArrow(identifier1, identifier2, ArrowStyle.DEFAULT);
    }

    /** since 2025.16 */
    public void writeArrow(String identifier1, String identifier2, @Nullable ArrowStyle style) throws IOException {
        writeArrow(identifier1, identifier2, null, style);
    }

    public void writeArrow(String identifier1, String identifier2, @Nullable String comment) throws IOException {
        writeArrow(identifier1, identifier2, comment, ArrowStyle.DEFAULT);
    }

    /** since 2025.16 */
    public void writeArrow(String identifier1, String identifier2, @Nullable String comment,
            @Nullable ArrowStyle style) {
        style = Objects.requireNonNullElse(style, ArrowStyle.DEFAULT);
        pushTypeIfNeeded(Type.UNKNOWN);
        indent();
        writer.print(formatIdentifier(identifier1));
        if (isNotBlank(style.startFormat)) {
            writer.print(style.startFormat);
        }
        writer.print(style.lineFormat);
        if (isNotBlank(style.direction)) {
            writer.print(style.direction);
        }
        if (!style.flavors.isEmpty()) {
            writer.print(style.flavors.stream().collect(Collectors.joining(";", "[", "]")));
        }
        writer.print(style.lineFormat);
        if (isNotBlank(style.endFormat)) {
            writer.print(style.endFormat);
        }
        writer.print(formatIdentifier(identifier2));
        if (isNotBlank(comment)) {
            writer.print(": ");
            writer.print(comment);
        }
        popType();
    }

    public void writeFreeText(String text) throws IOException {
        pushTypeIfNeeded(Type.FREE_TEXT);
        indent();
        writer.println(text);
    }

    public void writeTextSeparator() throws IOException {
        pushTypeIfNeeded(Type.FREE_TEXT);
        indent();
        writer.println("----");
    }

    protected String formatIdentifier(String identifier) {
        return identifier.replaceAll("[^a-zA-Z0-9_]", ".");
    }

    protected void pushTypeIfNeeded(@Nonnull Type type) {
        var previousType = typeDeque.peek();
        if (previousType == null && type == Type.FREE_TEXT) {
            // writing free text at root is forbidden
            throw new IllegalStateException("Cannot write free text outside of an element");
        } else if (previousType == Type.CONTAINER && type == Type.FREE_TEXT) {
            throw new IllegalStateException("Cannot write free text inside a container");
        } else if (previousType == Type.FREE_TEXT && type != Type.FREE_TEXT) {
            throw new IllegalStateException("Cannot write element inside free text");
        }
        if (previousType == Type.UNKNOWN) {
            // current type element is not known yet, deduce it from given type
            typeDeque.removeFirst();
            if (type == Type.UNKNOWN) {
                // currently creating element, so parent will be a container
                previousType = Type.CONTAINER;
                typeDeque.push(previousType);
                writer.println(" {");
            }
        }
        if (previousType != type) {
            typeDeque.push(type);
            // check if we need to write the opening character
            switch (type) {
                case CONTAINER -> writer.println(" {");
                case FREE_TEXT -> writer.println(" [");
            }
        }
    }

    protected void popType() {
        if (typeDeque.isEmpty()) {
            writer.println();
        } else {
            if (typeDeque.peek() != Type.UNKNOWN) {
                indent();
            }
            switch (typeDeque.removeFirst()) {
                case CONTAINER -> writer.println("}");
                case FREE_TEXT -> writer.println("]");
                case UNKNOWN -> writer.println();
            }
        }
    }

    protected void indent() {
        if (!typeDeque.isEmpty()) {
            writer.print("  ".repeat(typeDeque.size() - 1));
        }
    }

    protected Writer getWriter() {
        return writer;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    @Override
    public void flush() throws IOException {
        writer.flush();
    }

    public enum ElementType {
        CLASS("class"), //
        COMPONENT("component"), //
        DATABASE("database"), //
        PACKAGE("package"), //
        QUEUE("queue"), //
        ;

        protected final String type;

        ElementType(String type) {
            this.type = type;
        }
    }

    protected enum Type {
        CONTAINER, // the element is a container, written with "{" and "}"
        FREE_TEXT, // the element contains free text, written with "[" and "]"
        UNKNOWN, // the element could be both
    }

    /**
     * The style to apply on an arrow, like dashed, dotted, or changing color.
     *
     * @since 2025.16
     */
    public static class ArrowStyle {

        public static final ArrowStyle DEFAULT = new Builder().build();

        public static final ArrowStyle HIDDEN = new Builder().flavor("hidden").build();

        protected final String lineFormat;

        protected final String startFormat;

        protected final String direction;

        protected final List<String> flavors;

        protected final String endFormat;

        protected ArrowStyle(Builder builder) {
            this.lineFormat = builder.lineFormat;
            this.startFormat = builder.startFormat;
            this.direction = builder.direction;
            this.flavors = List.copyOf(builder.flavors);
            this.endFormat = builder.endFormat;
        }

        public static class Builder {

            protected String lineFormat = "=";

            protected String startFormat = "";

            protected String direction = "";

            protected List<String> flavors = new ArrayList<>();

            protected String endFormat = ">";

            public Builder() {
            }

            public Builder lineFormat(String lineFormat) {
                this.lineFormat = lineFormat;
                return this;
            }

            public Builder startFormat(String startFormat) {
                this.startFormat = startFormat;
                return this;
            }

            public Builder direction(String direction) {
                this.direction = direction;
                return this;
            }

            public Builder endFormat(String endFormat) {
                this.endFormat = endFormat;
                return this;
            }

            public Builder flavor(String flavor) {
                this.flavors.add(flavor);
                return this;
            }

            public ArrowStyle build() {
                return new ArrowStyle(this);
            }
        }
    }
}
