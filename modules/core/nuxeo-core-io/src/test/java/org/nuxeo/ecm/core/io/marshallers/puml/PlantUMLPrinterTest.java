/*
 * (C) Copyright 2026 Nuxeo (http://nuxeo.com/) and others.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Test;
import org.nuxeo.ecm.core.io.marshallers.puml.PlantUMLPrinter.ArrowStyle;

/**
 * @since 2025.16
 */
public class PlantUMLPrinterTest {

    @Test
    public void testBasic() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeTitle("Test title");
        pumlPrinter.writeHide("empty members");
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                title Test title

                hide empty members

                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteSkinParamInline() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeSkinparam("nodeBackgroundColor", "#EEEEEE");
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                skinparam nodeBackgroundColor #EEEEEE
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteSkinParam1() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeSkinparam("node", "BackgroundColor", "#EEEEEE");
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                skinparam node {
                  BackgroundColor #EEEEEE
                }
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteSkinParam2() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeSkinparam("node", "BackgroundColor", "#EEEEEE", "ArrowColor", "#CC6655");
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                skinparam node {
                  BackgroundColor #EEEEEE
                  ArrowColor #CC6655
                }
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteSkinParam4() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeSkinparam("node", "BackgroundColor", "#EEEEEE", "BorderColor", "#CCCCCC", "ArrowColor",
                "#CC6655");
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                skinparam node {
                  BackgroundColor #EEEEEE
                  BorderColor #CCCCCC
                  ArrowColor #CC6655
                }
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteClass() throws IOException {
        var writer = new StringWriter();
        var printer = new PlantUMLPrinter(writer);

        printer.writeStartDocument();
        printer.writeStartClass("some_class", "Class Title");
        printer.writeEndClass();
        printer.writeEndDocument();

        assertEquals("""
                @startuml
                class some_class as "Class Title" {
                }
                @enduml
                """, writer.toString());
    }

    @Test
    public void testWriteComponent() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeStartComponent("some_component");
        pumlPrinter.writeEndComponent();
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                component some_component
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteComponentWithTitle() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeStartComponent("some_component", "Some component");
        pumlPrinter.writeEndComponent();
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                component some_component as "Some component" {
                }
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteComponentWithStereo() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeStartComponent("some_component", null, "failure");
        pumlPrinter.writeEndComponent();
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                component some_component <<failure>>
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteDatabase() throws IOException {
        var writer = new StringWriter();
        var printer = new PlantUMLPrinter(writer);

        printer.writeStartDocument();
        printer.writeStartDatabase("some_database", "Database Title");
        printer.writeEndDatabase();
        printer.writeEndDocument();

        assertEquals("""
                @startuml
                database some_database as "Database Title" {
                }
                @enduml
                """, writer.toString());
    }

    @Test
    public void testWritePackage() throws IOException {
        var writer = new StringWriter();
        var printer = new PlantUMLPrinter(writer);

        printer.writeStartDocument();
        printer.writeStartPackage("some_package", "Package Title");
        printer.writeEndPackage();
        printer.writeEndDocument();

        assertEquals("""
                @startuml
                package some_package as "Package Title" {
                }
                @enduml
                """, writer.toString());
    }

    @Test
    public void testWriteQueue() throws IOException {
        var writer = new StringWriter();
        var printer = new PlantUMLPrinter(writer);

        printer.writeStartDocument();
        printer.writeStartQueue("some_queue", "Queue Title");
        printer.writeEndQueue();
        printer.writeEndDocument();

        assertEquals("""
                @startuml
                queue some_queue as "Queue Title" {
                }
                @enduml
                """, writer.toString());
    }

    @Test
    public void testWriteFreeText() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeStartComponent("some_component");
        pumlPrinter.writeFreeText("This is a free text block.");
        pumlPrinter.writeEndComponent();
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                component some_component [
                This is a free text block.
                ]
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteTextSeparator() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeStartComponent("some_component");
        pumlPrinter.writeTextSeparator();
        pumlPrinter.writeEndComponent();
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                component some_component [
                ----
                ]
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteComplexFreeTex() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeStartComponent("some_component");
        pumlPrinter.writeFreeText("This is a free text block.");
        pumlPrinter.writeTextSeparator();
        pumlPrinter.writeFreeText("Just after the separator.");
        pumlPrinter.writeEndComponent();
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                component some_component [
                This is a free text block.
                ----
                Just after the separator.
                ]
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testNestedElements() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeStartComponent("parent_component");
        pumlPrinter.writeStartQueue("child_queue");
        pumlPrinter.writeFreeText("parent_component/child_queue");
        pumlPrinter.writeEndQueue();
        pumlPrinter.writeEndComponent();
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                component parent_component {
                  queue child_queue [
                  parent_component/child_queue
                  ]
                }
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteArrow() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeArrow("A", "B");
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                A==>B
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteArrowWithStyle() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeArrow("A", "B", ArrowStyle.HIDDEN);
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                A=[hidden]=>B
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteArrowWithComment() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeArrow("A", "B", "A to B");
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                A==>B: A to B
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testWriteArrowWithCommentAndStyle() throws IOException {
        var stringWriter = new StringWriter();
        var pumlPrinter = new PlantUMLPrinter(stringWriter);

        pumlPrinter.writeStartDocument();
        pumlPrinter.writeArrow("A", "B", "A to B",
                new ArrowStyle.Builder().lineFormat("-")
                                        .startFormat("<")
                                        .endFormat("*")
                                        .direction("left")
                                        .flavor("#CCCCCC")
                                        .build());
        pumlPrinter.writeEndDocument();

        assertEquals("""
                @startuml
                A<-left[#CCCCCC]-*B: A to B
                @enduml
                """, stringWriter.toString());
    }

    @Test
    public void testInvalidFreeTextAtRoot() {
        // can not write free text at root level
        var pumlPrinter = new PlantUMLPrinter(new StringWriter());
        var exception = assertThrows(IllegalStateException.class, () -> {
            pumlPrinter.writeStartDocument();
            pumlPrinter.writeFreeText("something");
        });
        assertEquals("Cannot write free text outside of an element", exception.getMessage());
    }

    @Test
    public void testInvalidFreeTextWithinContainer() {
        // can not write free text within a container
        var pumlPrinter = new PlantUMLPrinter(new StringWriter());
        var exception = assertThrows(IllegalStateException.class, () -> {
            pumlPrinter.writeStartDocument();
            pumlPrinter.writeStartComponent("some_component");
            // makes some_component a container by writing another_component inside it
            pumlPrinter.writeStartComponent("another_component");
            pumlPrinter.writeEndComponent();
            // already in a container, can not write free text
            pumlPrinter.writeFreeText("something");
        });
        assertEquals("Cannot write free text inside a container", exception.getMessage());
    }

    @Test
    public void testInvalidContainerWithinFreeText() {
        // can not write container within a free text block
        var pumlPrinter = new PlantUMLPrinter(new StringWriter());
        var exception = assertThrows(IllegalStateException.class, () -> {
            pumlPrinter.writeStartDocument();
            pumlPrinter.writeStartComponent("some_component");
            // makes some_component a free text by writing free text inside it
            pumlPrinter.writeFreeText("something");
            // already a free text block, can not write a container
            pumlPrinter.writeStartComponent("another_component");
        });
        assertEquals("Cannot write element inside free text", exception.getMessage());
    }
}
