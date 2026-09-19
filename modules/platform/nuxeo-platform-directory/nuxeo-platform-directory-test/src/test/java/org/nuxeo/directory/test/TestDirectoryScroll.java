/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     Guillaume Renard
 */
package org.nuxeo.directory.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import jakarta.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.scroll.Scroll;
import org.nuxeo.ecm.core.api.scroll.ScrollRequest;
import org.nuxeo.ecm.core.api.scroll.ScrollService;
import org.nuxeo.ecm.core.query.scroll.QueryBuilderScrollRequest;
import org.nuxeo.ecm.core.scroll.GenericScrollRequest;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 2025.1
 */
@RunWith(FeaturesRunner.class)
@Features(DirectoryFeature.class)
@Deploy("org.nuxeo.ecm.platform.query.api")
@Deploy("org.nuxeo.ecm.directory.tests:test-directory-continent-config.xml")
public class TestDirectoryScroll {

    @Inject
    protected ScrollService scrollService;

    @Test
    public void testScroll() {
        var request = QueryBuilderScrollRequest.builder("directory", "SELECT * FROM continent").size(2).build();
        scrollAndAssert(request);
    }

    /**
     * @deprecated since 2025.18
     */
    @Test
    @Deprecated(since = "2025.18", forRemoval = true)
    public void testGenericScroll() {
        var request = GenericScrollRequest.builder("directory", "SELECT * FROM continent").size(2).build();
        scrollAndAssert(request);
    }

    protected void scrollAndAssert(ScrollRequest request) {
        List<String> expectedIds = List.of("africa", "antarctica", "asia", "europe", "north-america", "oceania",
                "south-america");
        assertTrue(scrollService.exists(request));
        try (Scroll scroll = scrollService.scroll(request)) {
            List<String> actualIds = new ArrayList<>();
            assertNotNull(scroll);
            int i = 0;
            do {
                assertTrue(scroll.hasNext());
                List<String> next = scroll.next();
                assertTrue("Unexpected scrolled entries", i + next.size() <= expectedIds.size());
                actualIds.addAll(next);
                i += next.size();
            } while (i < expectedIds.size());
            assertEquals("Unexpected scrolled entries", expectedIds, actualIds);
            assertFalse(scroll.hasNext());
            assertThrows("Should not be able to scroll beyond limit.", NoSuchElementException.class, scroll::next);
        }
    }
}
