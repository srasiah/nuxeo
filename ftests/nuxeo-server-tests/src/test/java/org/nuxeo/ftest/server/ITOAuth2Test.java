/*
 * (C) Copyright 2017-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     Antoine Taillefer <ataillefer@nuxeo.com>
 */
package org.nuxeo.ftest.server;

import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.platform.oauth2.Constants.AUTHORIZATION_CODE_PARAM;
import static org.nuxeo.ecm.platform.oauth2.Constants.CLIENT_ID_PARAM;
import static org.nuxeo.ecm.platform.oauth2.Constants.CODE_CHALLENGE_METHODS_SUPPORTED;
import static org.nuxeo.ecm.platform.oauth2.Constants.CODE_CHALLENGE_METHOD_PARAM;
import static org.nuxeo.ecm.platform.oauth2.Constants.CODE_CHALLENGE_PARAM;
import static org.nuxeo.ecm.platform.oauth2.Constants.CODE_RESPONSE_TYPE;
import static org.nuxeo.ecm.platform.oauth2.Constants.REDIRECT_URI_PARAM;
import static org.nuxeo.ecm.platform.oauth2.Constants.RESPONSE_TYPE_PARAM;
import static org.nuxeo.ecm.platform.oauth2.Constants.STATE_PARAM;
import static org.nuxeo.ecm.platform.oauth2.NuxeoOAuth2Servlet.AUTHORIZE_SUBMIT_ENDPOINT;
import static org.nuxeo.ecm.platform.oauth2.NuxeoOAuth2Servlet.ERROR_DESCRIPTION_PARAM;
import static org.nuxeo.ecm.platform.oauth2.NuxeoOAuth2Servlet.ERROR_PARAM;
import static org.nuxeo.ecm.platform.oauth2.NuxeoOAuth2Servlet.TOKEN_ENDPOINT;
import static org.nuxeo.ecm.platform.oauth2.OAuth2Error.ACCESS_DENIED;
import static org.nuxeo.ecm.platform.oauth2.clients.OAuth2ClientService.OAUTH2CLIENT_DIRECTORY_NAME;
import static org.nuxeo.ecm.platform.oauth2.request.AuthorizationRequest.MISSING_REQUIRED_FIELD_MESSAGE;
import static org.nuxeo.ecm.platform.oauth2.tokens.OAuth2TokenStore.DIRECTORY_NAME;
import static org.nuxeo.ftest.server.OAuth2ErrorPage.getErrorPage;
import static org.nuxeo.ftest.server.OAuth2GrantPage.getDefaultGrantPage;
import static org.nuxeo.ftest.server.OAuth2GrantPage.getEmptyGrantPage;
import static org.nuxeo.ftest.server.OAuth2GrantPage.getOAuth2GrantPageBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.nuxeo.common.utils.URIUtils;
import org.nuxeo.ecm.platform.oauth2.NuxeoOAuth2Servlet;
import org.nuxeo.functionaltests.LogTestWatchman;
import org.nuxeo.functionaltests.RestTestRule;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;

import net.htmlparser.jericho.Source;

/**
 * Tests the OAuth2 authorization flow handled by the {@link NuxeoOAuth2Servlet}.
 *
 * @since 9.2
 */
public class ITOAuth2Test {

    public record OAuth2Token(String accessToken, String refreshToken) {
    }

    public static final String TEST_USERNAME = "jdoe";

    public static final String TEST_PASSWORD = "test";

    public static final String DOC_PATH = "/api/v1/path/";

    public static final String JSON_CMIS_PATH = "/json/cmis";

    public static final String ATOM_CMIS_PATH = "/atom/cmis";

    public static final String ATOM_CMIS10_PATH = "/atom/cmis10";

    protected String oauth2ClientDirectoryEntryId;

    @Rule
    public final HttpClientTestRule unauthenticatedClient = HttpClientTestRule.builder().build();

    @Rule
    public final HttpClientTestRule testUserClient = HttpClientTestRule.builder()
                                                                       .credentials(TEST_USERNAME, TEST_PASSWORD)
                                                                       .build();

    @Rule
    public final HttpClientTestRule testUserLocationClient = HttpClientTestRule.builder()
                                                                               .credentials(TEST_USERNAME,
                                                                                       TEST_PASSWORD)
                                                                               .redirectsEnabled(false)
                                                                               .build();

    @Rule
    public final HttpClientTestRule adminClient = HttpClientTestRule.builder().adminCredentials().build();

    @Rule
    public MethodRule watchman = new LogTestWatchman();

    @Rule
    public final RestTestRule restHelper = new RestTestRule();

    @Before
    public void before() {
        restHelper.createUser(TEST_USERNAME, TEST_PASSWORD);
        // Create a test OAuth2 client redirecting to localhost
        Map<String, String> properties = new HashMap<>();
        properties.put("name", "Test Client");
        properties.put("clientId", "test-client");
        properties.put("redirectURIs", "http://localhost:8080/nuxeo/home.html");
        oauth2ClientDirectoryEntryId = restHelper.createDirectoryEntry(OAUTH2CLIENT_DIRECTORY_NAME, properties);
    }

    @After
    public void after() {
        restHelper.deleteDirectoryEntries(DIRECTORY_NAME);
    }

    @Test
    public void testAuthorizationErrors() {
        // No client_id parameter
        OAuth2ErrorPage errorPage = getErrorPage(testUserClient, "/oauth2/authorize");
        errorPage.checkTitle("400");
        errorPage.checkH1("Bad Request");
        errorPage.checkDescription(String.format(MISSING_REQUIRED_FIELD_MESSAGE, CLIENT_ID_PARAM));

        // No response_type parameter
        errorPage = getErrorPage(testUserClient, "/oauth2/authorize?client_id=test-client");
        errorPage.checkDescription(String.format(MISSING_REQUIRED_FIELD_MESSAGE, RESPONSE_TYPE_PARAM));

        // Invalid response_type parameter
        errorPage = getErrorPage(testUserClient, "/oauth2/authorize?client_id=test-client&response_type=unknown");
        errorPage.checkDescription(String.format("Unknown %s: got \"unknown\", expecting \"%s\".", RESPONSE_TYPE_PARAM,
                CODE_RESPONSE_TYPE));

        // Invalid client_id parameter
        errorPage = getErrorPage(testUserClient, "/oauth2/authorize?client_id=unknown&response_type=code");
        errorPage.checkDescription(String.format("Invalid %s: unknown.", CLIENT_ID_PARAM));

        // Invalid redirect_uri parameter
        errorPage = getErrorPage(testUserClient,
                "/oauth2/authorize?client_id=test-client&response_type=code&redirect_uri=unknown");
        errorPage.checkDescription(String.format(
                "Invalid %s parameter: unknown. It must exactly match one of the redirect URIs configured for the app.",
                REDIRECT_URI_PARAM));

        // Invalid PKCE parameters
        errorPage = getErrorPage(testUserClient,
                "/oauth2/authorize?client_id=test-client&response_type=code&code_challenge=myCodeChallenge");
        errorPage.checkDescription(
                String.format("Invalid PKCE parameters: either both %s and %s parameters must be sent or none of them.",
                        CODE_CHALLENGE_PARAM, CODE_CHALLENGE_METHOD_PARAM));
        errorPage = getErrorPage(testUserClient,
                "/oauth2/authorize?client_id=test-client&response_type=code&code_challenge_method=S256");
        errorPage.checkDescription(
                String.format("Invalid PKCE parameters: either both %s and %s parameters must be sent or none of them.",
                        CODE_CHALLENGE_PARAM, CODE_CHALLENGE_METHOD_PARAM));
        errorPage = getErrorPage(testUserClient,
                "/oauth2/authorize?client_id=test-client&response_type=code&code_challenge=myCodeChallenge&code_challenge_method=unknown");
        errorPage.checkDescription(String.format(
                "Invalid %s parameter: transform algorithm unknown not supported. The server only supports %s.",
                CODE_CHALLENGE_METHOD_PARAM, CODE_CHALLENGE_METHODS_SUPPORTED));

        // Uncaught exception - NXP-31104
        // Create the same OAuth2 client to produce error
        Map<String, String> properties = new HashMap<>();
        properties.put("name", "Test Client");
        properties.put("clientId", "test-client");
        properties.put("redirectURIs", "http://localhost:8080/nuxeo/home.html");
        restHelper.createDirectoryEntry(OAUTH2CLIENT_DIRECTORY_NAME, properties);
        errorPage = getErrorPage(testUserClient, "/oauth2/authorize?client_id=test-client&response_type=code");
        errorPage.checkDescription("More than one client registered for the 'test-client' id");
    }

    @Test
    public void testOAuth2GrantPage() {
        // The grant page is behind the authentication filter
        OAuth2GrantPage grantPage = getEmptyGrantPage(testUserClient);
        // When called directly without going through the /oauth2/authorize endpoint the input fields are empty
        grantPage.checkClientName("null");
        grantPage.checkFieldCount(2);
        grantPage.checkResponseType("");
        grantPage.checkClientId("");

        // Get the grant page by going through the /oauth2/authorize endpoint
        // First send only the required parameters
        getDefaultGrantPage(testUserClient);

        // Then send extra parameters
        Map<String, String> extraParameters = new HashMap<>();
        extraParameters.put("redirect_uri", "http://localhost:8080/nuxeo/home.html");
        extraParameters.put("state", "1234");
        extraParameters.put("code_challenge", "myCodeChallenge");
        extraParameters.put("code_challenge_method", "plain");
        getOAuth2GrantPageBuilder(testUserClient).extraParameters(extraParameters).build();
    }

    @Test
    public void testAuthorizationSubmitErrors() {
        // Call a GET request on /oauth2/authorize_submit
        OAuth2ErrorPage errorPage = getErrorPage(testUserClient, "/oauth2/authorize_submit");
        errorPage.checkDescription("The /oauth2" + AUTHORIZE_SUBMIT_ENDPOINT + " endpoint only accepts POST requests.");

        // Simulate an empty client_id parameter
        OAuth2GrantPage grantPage = getDefaultGrantPage(testUserClient);
        grantPage.setClientId("");
        Source result = grantPage.grant();
        errorPage = new OAuth2ErrorPage(result);
        errorPage.checkDescription(String.format(MISSING_REQUIRED_FIELD_MESSAGE, CLIENT_ID_PARAM));

        // Simulate an empty response_type parameter
        grantPage = getDefaultGrantPage(testUserClient);
        grantPage.setResponseType("");
        result = grantPage.grant();
        errorPage = new OAuth2ErrorPage(result);
        errorPage.checkDescription(String.format(MISSING_REQUIRED_FIELD_MESSAGE, RESPONSE_TYPE_PARAM));

        // Simulate an invalid response_type parameter
        grantPage = getDefaultGrantPage(testUserClient);
        grantPage.setResponseType("unknown");
        result = grantPage.grant();
        errorPage = new OAuth2ErrorPage(result);
        errorPage.checkDescription(String.format("Unknown %s: got \"unknown\", expecting \"%s\".", RESPONSE_TYPE_PARAM,
                CODE_RESPONSE_TYPE));

        // Simulate an invalid client_id parameter
        grantPage = getDefaultGrantPage(testUserClient);
        grantPage.setClientId("unknown");
        result = grantPage.grant();
        errorPage = new OAuth2ErrorPage(result);
        errorPage.checkDescription(String.format("Invalid %s: unknown.", CLIENT_ID_PARAM));

        // Simulate an invalid redirect_uri parameter
        Map<String, String> extraParameters = new HashMap<>();
        extraParameters.put("redirect_uri", "http://localhost:8080/nuxeo/home.html");
        grantPage = getOAuth2GrantPageBuilder(testUserClient).extraParameters(extraParameters).build();
        grantPage.setRedirectURI("unknown");
        result = grantPage.grant();
        errorPage = new OAuth2ErrorPage(result);
        errorPage.checkDescription(String.format(
                "Invalid %s parameter: unknown. It must exactly match one of the redirect URIs configured for the app.",
                REDIRECT_URI_PARAM));

        // Simulate invalid PKCE parameters
        extraParameters.put("code_challenge", "myCodeChallenge");
        extraParameters.put("code_challenge_method", "S256");
        grantPage = getOAuth2GrantPageBuilder(testUserClient).extraParameters(extraParameters).build();
        grantPage.setCodeChallengeMethod(null);
        result = grantPage.grant();
        errorPage = new OAuth2ErrorPage(result);
        errorPage.checkDescription(
                String.format("Invalid PKCE parameters: either both %s and %s parameters must be sent or none of them.",
                        CODE_CHALLENGE_PARAM, CODE_CHALLENGE_METHOD_PARAM));

        grantPage = getOAuth2GrantPageBuilder(testUserClient).extraParameters(extraParameters).build();
        grantPage.setCodeChallenge(null);
        result = grantPage.grant();
        errorPage = new OAuth2ErrorPage(result);
        errorPage.checkDescription(
                String.format("Invalid PKCE parameters: either both %s and %s parameters must be sent or none of them.",
                        CODE_CHALLENGE_PARAM, CODE_CHALLENGE_METHOD_PARAM));

        grantPage = getOAuth2GrantPageBuilder(testUserClient).extraParameters(extraParameters).build();
        grantPage.setCodeChallengeMethod("unknown");
        result = grantPage.grant();
        errorPage = new OAuth2ErrorPage(result);
        errorPage.checkDescription(String.format(
                "Invalid %s parameter: transform algorithm unknown not supported. The server only supports %s.",
                CODE_CHALLENGE_METHOD_PARAM, CODE_CHALLENGE_METHODS_SUPPORTED));
    }

    @Test
    public void testAuthorizationDenied() {
        OAuth2GrantPage grantPage = getOAuth2GrantPageBuilder(testUserClient).extraParameters(Map.of("state", "1234"))
                                                                             .build();
        grantPage.setHttpClient(testUserLocationClient);
        String location = grantPage.getDenyLocation();
        assertEquals("http://localhost:8080/nuxeo/home.html", URIUtils.getURIPath(location));
        Map<String, String> expectedParameters = new HashMap<>();
        expectedParameters.put(ERROR_PARAM, ACCESS_DENIED);
        expectedParameters.put(ERROR_DESCRIPTION_PARAM, "Access denied by the user");
        expectedParameters.put(STATE_PARAM, "1234");
        assertEquals(expectedParameters, URIUtils.getRequestParameters(location));
    }

    @Test
    public void testAuthorizationGranted() {
        OAuth2GrantPage grantPage = getOAuth2GrantPageBuilder(testUserClient)
                                                                             .extraParameters(Collections.singletonMap(
                                                                                     "state", "1234"))
                                                                             .build();
        grantPage.setHttpClient(testUserLocationClient);
        String location = grantPage.getGrantLocation();
        assertEquals("http://localhost:8080/nuxeo/home.html", URIUtils.getURIPath(location));
        Map<String, String> parameters = URIUtils.getRequestParameters(location);
        assertEquals(2, parameters.size());
        assertEquals("1234", parameters.get(STATE_PARAM));
        assertTrue(parameters.containsKey(AUTHORIZATION_CODE_PARAM));
    }

    @Test
    public void testAuthorizationOnRestAPI() {
        OAuth2Token token = getOAuth2Token(adminClient);

        checkAuthorizationWithValidAccessToken(DOC_PATH, token.accessToken);

        // refresh the access token
        OAuth2Token refreshedToken = refreshOAuth2Token(token.refreshToken);

        checkAuthorizationWithInvalidAccessToken(DOC_PATH, token.accessToken);

        checkAuthorizationWithValidAccessToken(DOC_PATH, refreshedToken.accessToken);
    }

    @Test
    public void testAuthorizationOnCMIS() {
        OAuth2Token token = getOAuth2Token(adminClient);

        checkAuthorizationWithValidAccessToken(JSON_CMIS_PATH, token.accessToken);
        checkAuthorizationWithValidAccessToken(ATOM_CMIS_PATH, token.accessToken);
        checkAuthorizationWithValidAccessToken(ATOM_CMIS10_PATH, token.accessToken);

        // refresh the access token
        OAuth2Token refreshedToken = refreshOAuth2Token(token.refreshToken);

        checkAuthorizationWithInvalidAccessToken(JSON_CMIS_PATH, token.accessToken);
        checkAuthorizationWithInvalidAccessToken(ATOM_CMIS_PATH, token.accessToken);
        checkAuthorizationWithInvalidAccessToken(ATOM_CMIS10_PATH, token.accessToken);

        checkAuthorizationWithValidAccessToken(JSON_CMIS_PATH, refreshedToken.accessToken);
        checkAuthorizationWithValidAccessToken(ATOM_CMIS_PATH, refreshedToken.accessToken);
        checkAuthorizationWithValidAccessToken(ATOM_CMIS10_PATH, refreshedToken.accessToken);
    }

    @Test
    public void testTokenGetRequest() {
        // Call a GET request on /oauth2/token
        OAuth2ErrorPage errorPage = getErrorPage(unauthenticatedClient, "/oauth2/token");
        errorPage.checkDescription("The /oauth2" + TOKEN_ENDPOINT + " endpoint only accepts POST requests.");
    }

    @Test
    public void testAuthorizationWithExistingToken() {
        // Get an OAuth2 token
        OAuth2Token initialToken = getOAuth2Token(testUserClient);

        // Ask for authorization
        String location = getLocation("/oauth2/authorize?client_id=test-client&response_type=code");

        // Expecting to be redirected to the client's redirect_uri with a code parameter, bypassing the grant page
        assertEquals("http://localhost:8080/nuxeo/home.html", URIUtils.getURIPath(location));
        Map<String, String> queryParameters = URIUtils.getRequestParameters(location);
        assertEquals(1, queryParameters.size());
        String code = queryParameters.get("code");
        assertNotNull(code);

        // Ask for a token, expecting the initial access token
        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("client_id", "test-client");
        params.put("code", code);
        OAuth2Token token = getOAuth2Token(params);
        assertEquals(initialToken.accessToken, token.accessToken);
    }

    @Test
    public void testAuthorizationWithAutoGrant() {
        // Set auto-grant on the client
        setAutoGrant(true);

        // Ask for authorization
        String location = getLocation("/oauth2/authorize?client_id=test-client&response_type=code");

        // Expecting to be redirected to the client's redirect_uri with a code parameter, bypassing the grant page
        assertEquals("http://localhost:8080/nuxeo/home.html", URIUtils.getURIPath(location));
        Map<String, String> queryParameters = URIUtils.getRequestParameters(location);
        assertEquals(1, queryParameters.size());
        String code = queryParameters.get("code");
        assertNotNull(code);

        // Reset auto-grant on the client
        setAutoGrant(false);
    }

    protected String getLocation(String path) {
        return testUserLocationClient.buildGetRequest(path)
                                     .executeAndThen(response -> response.getLocation().toString());
    }

    protected void setAutoGrant(boolean autoGrant) {
        restHelper.updateDirectoryEntry(OAUTH2CLIENT_DIRECTORY_NAME, oauth2ClientDirectoryEntryId,
                Collections.singletonMap("autoGrant", autoGrant));
    }

    protected OAuth2Token getOAuth2Token(HttpClientTestRule client) {
        OAuth2GrantPage grantPage = getOAuth2GrantPageBuilder(client).build();
        String location = grantPage.getGrantLocation();
        Map<String, String> parameters = URIUtils.getRequestParameters(location);
        String code = parameters.get(AUTHORIZATION_CODE_PARAM);

        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("client_id", "test-client");
        params.put("code", code);
        return getOAuth2Token(params);
    }

    protected OAuth2Token getOAuth2Token(Map<String, String> params) {
        return unauthenticatedClient.buildPostRequest("/oauth2" + TOKEN_ENDPOINT)
                                    .entity(params)
                                    .executeAndThen(new JsonNodeHandler(),
                                            node -> new OAuth2Token(node.get("access_token").textValue(),
                                                    node.get("refresh_token").textValue()));
    }

    protected OAuth2Token refreshOAuth2Token(String refreshToken) {
        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("client_id", "test-client");
        params.put("refresh_token", refreshToken);
        return getOAuth2Token(params);
    }

    protected void checkAuthorizationWithValidAccessToken(String path, String accessToken) {
        unauthenticatedClient.buildGetRequest(path)
                             .executeAndConsume(new HttpStatusCodeHandler(),
                                     status -> assertEquals(SC_UNAUTHORIZED, status.intValue()));

        unauthenticatedClient.buildGetRequest(path)
                             .addQueryParameter("access_token", accessToken)
                             .executeAndConsume(new HttpStatusCodeHandler(),
                                     status -> assertEquals(SC_OK, status.intValue()));

        unauthenticatedClient.buildGetRequest(path)
                             .addHeader("Authorization", "Bearer " + accessToken)
                             .executeAndConsume(new HttpStatusCodeHandler(),
                                     status -> assertEquals(SC_OK, status.intValue()));
    }

    protected void checkAuthorizationWithInvalidAccessToken(String path, String accessToken) {
        unauthenticatedClient.buildGetRequest(path)
                             .addQueryParameter("access_token", accessToken)
                             .executeAndConsume(new HttpStatusCodeHandler(),
                                     status -> assertEquals(SC_UNAUTHORIZED, status.intValue()));
        unauthenticatedClient.buildGetRequest(path)
                             .addHeader("Authorization", "Bearer " + accessToken)
                             .executeAndConsume(new HttpStatusCodeHandler(),
                                     status -> assertEquals(SC_UNAUTHORIZED, status.intValue()));
    }

}
