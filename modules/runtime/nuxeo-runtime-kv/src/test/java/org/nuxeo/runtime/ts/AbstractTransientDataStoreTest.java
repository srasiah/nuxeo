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
package org.nuxeo.runtime.ts;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.nuxeo.runtime.ts.TransientDataServiceImpl.DEFAULT_STORE_ID;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import jakarta.inject.Inject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.ReflectUtils;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 2025.8
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeTransientDataStoreFeature.class)
@Deploy("org.nuxeo.runtime.kv.tests:OSGI-INF/test-transientdatastore.xml")
public abstract class AbstractTransientDataStoreTest<T extends TransientDataStoreProvider> {

    protected static final String KEY = "key";

    protected static final String PARAMETER1 = "foo1";

    protected static final String PARAMETER2 = "foo2";

    protected static final String PARAMETER3 = "foo3";

    protected static final String VALUE1 = "bar1";

    protected static final String VALUE2 = "bar2";

    protected static final String VALUE3 = "bar3";

    @Inject
    protected TransientDataService transientDataService;

    protected T store;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        var store = transientDataService.getStore(DEFAULT_STORE_ID);
        var storeType = ReflectUtils.retrieveFirstParameterType(this.getClass(), AbstractTransientDataStoreTest.class);
        assertEquals("Implementation is not the expected one", storeType.getTypeName(), store.getClass().getTypeName());
        this.store = (T) store;
    }

    protected boolean hasSlowTTLExpiration() {
        return false;
    }

    @Test
    public void testNotNull() {
        var list = new ArrayList<String>();
        list.add(null);
        var map = new HashMap<String, Object>();
        map.put(PARAMETER2, null);

        assertThrows(NullPointerException.class, () -> store.put(null, PARAMETER1, VALUE1));
        assertThrows(NullPointerException.class, () -> store.put(KEY, null, VALUE1));
        assertThrows(NullPointerException.class, () -> store.put(KEY, PARAMETER1, null));
        assertThrows(NullPointerException.class, () -> store.put(KEY, PARAMETER1, new ArrayList<>()));
        assertThrows(NullPointerException.class, () -> store.put(KEY, PARAMETER1, list));
        assertThrows(NullPointerException.class, () -> store.put(KEY, PARAMETER1, new HashMap<>()));
        assertThrows(NullPointerException.class, () -> store.put(KEY, PARAMETER1, map));

        assertThrows(NullPointerException.class, () -> store.putAll(null, Map.of(PARAMETER1, VALUE1)));
        assertThrows(NullPointerException.class, () -> store.putAll(KEY, null));
        assertThrows(NullPointerException.class, () -> store.putAll(KEY, Map.of()));

        assertThrows(NullPointerException.class, () -> store.exists(null));

        assertThrows(NullPointerException.class, () -> store.get(null, PARAMETER1));
        assertThrows(NullPointerException.class, () -> store.get(KEY, null));

        assertThrows(NullPointerException.class, () -> store.getAll(null));

        assertThrows(NullPointerException.class, () -> store.remove(null, PARAMETER1));
        assertThrows(NullPointerException.class, () -> store.remove(KEY, null));

        assertThrows(NullPointerException.class, () -> store.removeAll(null));
    }

    @Test
    public void testPut() {
        assertFalse(store.exists(KEY));
        assertNull(store.get(KEY, PARAMETER1));
        assertNull(store.getAll(KEY));

        store.put(KEY, PARAMETER1, VALUE1);

        assertTrue(store.exists(KEY));
        assertEquals(VALUE1, store.get(KEY, PARAMETER1));
        assertEquals(Map.of(PARAMETER1, VALUE1), store.getAll(KEY));
    }

    @Test
    public void testPutAll() {
        assertFalse(store.exists(KEY));
        assertNull(store.get(KEY, PARAMETER1));
        assertNull(store.getAll(KEY));

        store.putAll(KEY, Map.of(PARAMETER1, VALUE1));

        assertTrue(store.exists(KEY));
        assertEquals(VALUE1, store.get(KEY, PARAMETER1));
        assertEquals(Map.of(PARAMETER1, VALUE1), store.getAll(KEY));
    }

    @Test
    public void testPutAllIgnoreNull() {
        assertFalse(store.exists(KEY));
        assertNull(store.get(KEY, PARAMETER1));
        assertNull(store.getAll(KEY));

        var map = new HashMap<String, String>();
        map.put(PARAMETER1, VALUE1);
        map.put(PARAMETER2, null);
        store.putAll(KEY, map);

        assertTrue(store.exists(KEY));
        assertEquals(VALUE1, store.get(KEY, PARAMETER1));
        assertNull(store.get(KEY, PARAMETER2));
        assertEquals(Map.of(PARAMETER1, VALUE1), store.getAll(KEY));

    }

    @Test
    public void testPutDifferentParameterDoesNotOverridePreviousOne() {
        assertFalse(store.exists(KEY));
        assertNull(store.get(KEY, PARAMETER1));
        assertNull(store.get(KEY, PARAMETER2));
        assertNull(store.getAll(KEY));

        store.put(KEY, PARAMETER1, VALUE1);
        store.put(KEY, PARAMETER2, VALUE2);

        assertTrue(store.exists(KEY));
        assertEquals(VALUE1, store.get(KEY, PARAMETER1));
        assertEquals(VALUE2, store.get(KEY, PARAMETER2));
        assertEquals(Map.of(PARAMETER1, VALUE1, PARAMETER2, VALUE2), store.getAll(KEY));
    }

    @Test
    public void testPutAllDifferentParametersDoesNotOverridePreviousOnes() {
        assertFalse(store.exists(KEY));
        assertNull(store.get(KEY, PARAMETER1));
        assertNull(store.get(KEY, PARAMETER2));
        assertNull(store.getAll(KEY));

        store.putAll(KEY, Map.of(PARAMETER1, VALUE1));
        store.putAll(KEY, Map.of(PARAMETER2, VALUE2));

        assertTrue(store.exists(KEY));
        assertEquals(VALUE1, store.get(KEY, PARAMETER1));
        assertEquals(VALUE2, store.get(KEY, PARAMETER2));
        assertEquals(Map.of(PARAMETER1, VALUE1, PARAMETER2, VALUE2), store.getAll(KEY));
    }

    @Test
    public void testPutAllParametersWithOverride() {
        assertFalse(store.exists(KEY));
        assertNull(store.get(KEY, PARAMETER1));
        assertNull(store.get(KEY, PARAMETER2));
        assertNull(store.get(KEY, PARAMETER3));
        assertNull(store.getAll(KEY));

        store.putAll(KEY, Map.of(PARAMETER1, VALUE1, PARAMETER2, VALUE2));
        store.putAll(KEY, Map.of(PARAMETER2, VALUE2 + "_bis", PARAMETER3, VALUE3));

        assertTrue(store.exists(KEY));
        assertEquals(VALUE1, store.get(KEY, PARAMETER1));
        assertEquals(VALUE2 + "_bis", store.get(KEY, PARAMETER2));
        assertEquals(VALUE3, store.get(KEY, PARAMETER3));
        assertEquals(Map.of(PARAMETER1, VALUE1, PARAMETER2, VALUE2 + "_bis", PARAMETER3, VALUE3), store.getAll(KEY));
    }

    @Test
    public void testRemove() {
        assertFalse(store.exists(KEY));
        assertNull(store.get(KEY, PARAMETER1));
        assertNull(store.getAll(KEY));
        assertNull(store.remove(KEY, PARAMETER1));

        store.put(KEY, PARAMETER1, VALUE1);

        var previousValue = store.remove(KEY, PARAMETER1);

        assertEquals(VALUE1, previousValue);
        assertFalse(store.exists(KEY));
        assertNull(store.get(KEY, PARAMETER1));
        assertNull(store.getAll(KEY));
    }

    @Test
    public void testRemoveAll() {
        assertFalse(store.exists(KEY));
        assertNull(store.get(KEY, PARAMETER1));
        assertNull(store.getAll(KEY));
        assertNull(store.removeAll(KEY));

        store.put(KEY, PARAMETER1, VALUE1);

        var previousValue = store.removeAll(KEY);

        assertEquals(Map.of(PARAMETER1, VALUE1), previousValue);
        assertFalse(store.exists(KEY));
        assertNull(store.get(KEY, PARAMETER1));
        assertNull(store.getAll(KEY));
    }

    @Test
    public void testShortAccessedTtlGet() throws InterruptedException {
        assumeFalse("Ignored because of slow TTL expiration", hasSlowTTLExpiration());
        var shortAccessedStore = transientDataService.getStore("short-accessed");
        assertFalse(shortAccessedStore.exists(KEY));
        assertNull(shortAccessedStore.get(KEY, PARAMETER1));

        shortAccessedStore.put(KEY, PARAMETER1, VALUE1);
        shortAccessedStore.put(KEY + "_bis", PARAMETER1, VALUE1);

        // can't use awaitility here
        Thread.sleep(500);
        shortAccessedStore.get(KEY, PARAMETER1);
        Thread.sleep(600); // exceed ttl

        assertTrue(shortAccessedStore.exists(KEY));
        assertNotNull(shortAccessedStore.get(KEY, PARAMETER1));
        assertFalse(shortAccessedStore.exists(KEY + "_bis"));
        assertNull(shortAccessedStore.get(KEY + "_bis", PARAMETER1));
    }

    @Test
    public void testShortAccessedTtlGetAll() throws InterruptedException {
        assumeFalse("Ignored because of slow TTL expiration", hasSlowTTLExpiration());
        var shortAccessedStore = transientDataService.getStore("short-accessed");
        assertFalse(shortAccessedStore.exists(KEY));
        assertNull(shortAccessedStore.getAll(KEY));

        shortAccessedStore.put(KEY, PARAMETER1, VALUE1);
        shortAccessedStore.put(KEY + "_bis", PARAMETER1, VALUE1);

        // can't use awaitility here
        Thread.sleep(500);
        shortAccessedStore.getAll(KEY);
        Thread.sleep(600); // exceed ttl

        assertTrue(shortAccessedStore.exists(KEY));
        assertNotNull(shortAccessedStore.getAll(KEY));
        assertFalse(shortAccessedStore.exists(KEY + "_bis"));
        assertNull(shortAccessedStore.getAll(KEY + "_bis"));
    }

    @Test
    public void testShortCreatedTtlPut() {
        assumeFalse("Ignored because of slow TTL expiration", hasSlowTTLExpiration());
        var shortCreatedStore = transientDataService.getStore("short-created");
        assertFalse(shortCreatedStore.exists(KEY));
        assertNull(shortCreatedStore.get(KEY, PARAMETER1));

        shortCreatedStore.put(KEY, PARAMETER1, VALUE1);

        await().pollDelay(Duration.ofSeconds(1))
               .atMost(Duration.ofSeconds(2))
               .until(() -> shortCreatedStore.get(KEY, PARAMETER1) == null);
    }

    @Test
    public void testShortCreatedTtlPutAll() {
        assumeFalse("Ignored because of slow TTL expiration", hasSlowTTLExpiration());
        var shortCreatedStore = transientDataService.getStore("short-created");
        assertFalse(shortCreatedStore.exists(KEY));
        assertNull(shortCreatedStore.getAll(KEY));

        shortCreatedStore.putAll(KEY, Map.of(PARAMETER1, VALUE1));

        await().pollDelay(Duration.ofSeconds(1))
               .atMost(Duration.ofSeconds(2))
               .until(() -> shortCreatedStore.getAll(KEY) == null);
    }
}
