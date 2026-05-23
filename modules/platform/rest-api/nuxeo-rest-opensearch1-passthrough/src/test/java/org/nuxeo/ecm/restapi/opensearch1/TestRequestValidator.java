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
 *     Benoit Delbosc
 */
package org.nuxeo.ecm.restapi.opensearch1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.restapi.opensearch1.filter.RequestValidator;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

@RunWith(FeaturesRunner.class)
@Features(OpenSearchPassthroughFeature.class)
public class TestRequestValidator {

    private RequestValidator validator;

    @Before
    public void initValidator() {
        validator = new RequestValidator();
    }

    @Test
    public void testCheckValidDocumentId() {
        validator.checkValidDocumentId("123");
        assertThrows(IllegalArgumentException.class, () -> validator.checkValidDocumentId(null));
    }

    @Test
    public void testGetIndices() {
        validator.getIndices("nxutest");
        validator.getIndices("nxutest,nxutest");
        String indices = validator.getIndices(null);
        assertEquals("nxutest", indices);
        indices = validator.getIndices("*");
        assertEquals("nxutest", indices);
        indices = validator.getIndices("_all");
        assertEquals("nxutest", indices);
    }

    @Test
    public void testGetInvalidIndices1() {
        assertThrows(IllegalArgumentException.class, () -> validator.getIndices("unexisting"));
    }

    @Test
    public void testGetInvalidIndices2() {
        assertThrows(IllegalArgumentException.class, () -> validator.getIndices("nxutest,unexisting"));
    }

    @Test
    public void testGetInvalidIndices3() {
        assertThrows(IllegalArgumentException.class, () -> validator.getIndices("?"));
    }

    @Test
    public void testHasAccessAllowed() throws JSONException {
        validator.checkAccess(TestSearchRequestFilter.getNonAdminPrincipal(),
                "{\"_index\":\"nuxeo\",\"_type\":\"doc\",\"_id\":\"f1714dd9-ba3e-4c1a-845f-0cd2f7defd7c\",\"_version\":1,\"found\":true,\"fields\":{\"ecm:acl\":[\"Administrator\",\"members\"]}}");
    }

    @Test
    public void testHasAccessDenied() throws JSONException {
        assertThrows(SecurityException.class, () -> validator.checkAccess(
                TestSearchRequestFilter.getNonAdminPrincipal(),
                "{\"_index\":\"nuxeo\",\"_type\":\"doc\",\"_id\":\"f1714dd9-ba3e-4c1a-845f-0cd2f7defd7c\",\"_version\":1,\"found\":true,\"fields\":{\"ecm:acl\":[\"Administrator\"]}}"));
    }

    @Test
    public void testHasAccessNotFound() throws JSONException {
        assertThrows(SecurityException.class, () -> validator.checkAccess(
                TestSearchRequestFilter.getNonAdminPrincipal(),
                "{\"_index\":\"nuxeo\",\"_type\":\"doc\",\"_id\":\"f1714dd9-ba3e-4c1a-845f-e0cd2f7defd7c\",\"found\":false}"));
    }

}
