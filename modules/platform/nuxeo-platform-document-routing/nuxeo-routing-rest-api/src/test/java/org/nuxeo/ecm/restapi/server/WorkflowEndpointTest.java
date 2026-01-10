/*
 * (C) Copyright 2014-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     <a href="mailto:grenard@nuxeo.com">Guillaume Renard</a>
 *     <a href="mailto:ncunha@nuxeo.com">Nuno Cunha</a>
 */
package org.nuxeo.ecm.restapi.server;

import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import jakarta.inject.Inject;

import org.apache.logging.log4j.core.LogEvent;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.audit.test.AuditFeature;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.NuxeoGroup;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.EventContextImpl;
import org.nuxeo.ecm.core.event.impl.EventImpl;
import org.nuxeo.ecm.core.io.registry.MarshallerHelper;
import org.nuxeo.ecm.core.io.registry.MarshallingConstants;
import org.nuxeo.ecm.core.io.registry.context.RenderingContext;
import org.nuxeo.ecm.core.schema.utils.DateParser;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.routing.core.io.DocumentRouteWriter;
import org.nuxeo.ecm.platform.routing.core.io.TaskWriter;
import org.nuxeo.ecm.platform.routing.core.io.enrichers.PendingTasksJsonEnricher;
import org.nuxeo.ecm.platform.routing.core.io.enrichers.RunnableWorkflowJsonEnricher;
import org.nuxeo.ecm.platform.routing.core.io.enrichers.RunningWorkflowJsonEnricher;
import org.nuxeo.ecm.platform.routing.core.listener.DocumentRoutingEscalationListener;
import org.nuxeo.ecm.platform.routing.core.listener.DocumentRoutingWorkflowInstancesCleanup;
import org.nuxeo.ecm.platform.routing.test.WorkflowFeature;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskService;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.restapi.io.RestConstants;
import org.nuxeo.ecm.restapi.server.routing.adapter.TaskAdapter;
import org.nuxeo.ecm.restapi.server.routing.adapter.WorkflowAdapter;
import org.nuxeo.ecm.restapi.test.RestServerFeature;
import org.nuxeo.ecm.restapi.test.RestServerInit;
import org.nuxeo.http.test.HttpClientTestRule;
import org.nuxeo.http.test.handler.HttpStatusCodeHandler;
import org.nuxeo.http.test.handler.JsonNodeHandler;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.BlacklistComponent;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LogCaptureFeature;
import org.nuxeo.runtime.test.runner.TransactionalFeature;
import org.nuxeo.runtime.test.runner.WithFrameworkProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * @since 7.2
 */
@RunWith(FeaturesRunner.class)
@Features({ AuditFeature.class, WorkflowFeature.class, RestServerFeature.class, LogCaptureFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, init = RestServerInit.class)
@Deploy("org.nuxeo.ecm.platform.restapi.server.routing")
@Deploy("org.nuxeo.ecm.platform.routing.default")
// needs NotificationService & MailService
@BlacklistComponent("org.nuxeo.ecm.platform.notification.document.routing.NotificationContrib")
public class WorkflowEndpointTest {

    protected static final int NB_WF = 5;

    protected static final String CANCELED_WORKFLOWS = "SELECT ecm:uuid FROM DocumentRoute WHERE ecm:currentLifeCycleState = 'canceled'";

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject
    protected CoreSession session;

    @Inject
    protected EventService eventService;

    @Inject
    protected LogCaptureFeature.Result logResult;

    @Inject
    protected RestServerFeature restServerFeature;

    @Inject
    protected TransactionalFeature txFeature;

    @Inject
    protected UserManager userManager;

    @Rule
    public final HttpClientTestRule httpClient = HttpClientTestRule.defaultClient(
            () -> restServerFeature.getRestApiUrl());

    @Test
    public void testAdapter() throws IOException {

        DocumentModel note = RestServerInit.getNote(0, session);
        // Check POST /api/id/{documentId}/@workflow/
        final String createdWorkflowInstanceId = httpClient.buildPostRequest(
                "/id/" + note.getId() + "/@" + WorkflowAdapter.NAME)
                                                           .entity(getCreateAndStartWorkflowBodyContent(
                                                                   "SerialDocumentReview"))
                                                           .execute(new JsonNodeHandler(SC_CREATED))
                                                           .get("id")
                                                           .textValue();

        // Check GET /api/id/{documentId}/@workflow/
        httpClient.buildGetRequest("/id/" + note.getId() + "/@" + WorkflowAdapter.NAME)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertEquals(1, node.get("entries").size());
                      assertEquals(createdWorkflowInstanceId,
                              node.get("entries").elements().next().get("id").textValue());
                  });

        // Check GET /api/id/{documentId}/@workflow/{workflowInstanceId}/task
        String taskUid = httpClient.buildGetRequest(
                "/id/" + note.getId() + "/@" + WorkflowAdapter.NAME + "/" + createdWorkflowInstanceId + "/task")
                                   .executeAndThen(new JsonNodeHandler(), node -> {
                                       assertEquals(1, node.get("entries").size());
                                       JsonNode taskNode = node.get("entries").elements().next();
                                       return taskNode.get("id").textValue();
                                   });

        // Check GET /api/id/{documentId}/@task/
        httpClient.buildGetRequest("/id/" + note.getId() + "/@" + TaskAdapter.NAME)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      assertEquals(1, node.get("entries").size());
                      JsonNode taskNode = node.get("entries").elements().next();
                      assertEquals(taskUid, taskNode.get("id").textValue());
                  });

        // Complete task via task adapter
        httpClient.buildPutRequest("/id/" + note.getId() + "/@" + TaskAdapter.NAME + "/" + taskUid + "/start_review")
                  .entity(getBodyForStartReviewTaskCompletion(taskUid))
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));
    }

    @Test
    public void testCreateGetAndCancelWorkflowEndpoint() throws IOException {
        // Check POST /workflow
        final String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                           .entity(getCreateAndStartWorkflowBodyContent(
                                                                   "SerialDocumentReview"))
                                                           .execute(new JsonNodeHandler(SC_CREATED))
                                                           .get("id")
                                                           .textValue();

        // Check GET /workflow/{workflowInstanceId}
        httpClient.buildGetRequest("/workflow/" + createdWorkflowInstanceId)
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(createdWorkflowInstanceId, node.get("id").textValue()));

        // Check GET /workflow .i.e get running workflow initialized by currentUser
        httpClient.buildGetRequest("/workflow").executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals(1, node.get("entries").size());
            Iterator<JsonNode> elements = node.get("entries").elements();
            String fetchedWorkflowInstanceId = elements.next().get("id").textValue();
            assertEquals(createdWorkflowInstanceId, fetchedWorkflowInstanceId);
        });

        // Check GET /task i.e. pending tasks for current user
        String taskId = httpClient.buildGetRequest("/task")
                                  .executeAndThen(new JsonNodeHandler(), this::assertActorIsAdministrator);

        // Check GET /task/{taskId}
        httpClient.buildGetRequest("/task/" + taskId)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        // Check GET /task?userId=Administrator i.e. pending tasks for Administrator
        httpClient.buildGetRequest("/task")
                  .addQueryParameter("userId", "Administrator")
                  .executeAndConsume(new JsonNodeHandler(), this::assertActorIsAdministrator);

        // Check GET /task?workflowInstanceId={workflowInstanceId} i.e. pending tasks for Administrator
        httpClient.buildGetRequest("/task")
                  .addQueryParameter("userId", "Administrator")
                  .addQueryParameter("workflowInstanceId", createdWorkflowInstanceId)
                  .executeAndConsume(new JsonNodeHandler(), this::assertActorIsAdministrator);

        // Check DELETE /workflow
        httpClient.buildDeleteRequest("/workflow/" + createdWorkflowInstanceId)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_NO_CONTENT, status.intValue()));

        // Check GET /workflow
        httpClient.buildGetRequest("/workflow")
                  .executeAndConsume(new JsonNodeHandler(),
                          // we cancel running workflow, we expect 0 running workflow
                          node -> assertEquals(0, node.get("entries").size()));

        // Check we have no opened tasks
        httpClient.buildGetRequest("/task")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(0, node.get("entries").size()));
    }

    protected String assertActorIsAdministrator(JsonNode node) {
        assertEquals(1, node.get("entries").size());
        Iterator<JsonNode> elements = node.get("entries").elements();
        JsonNode element = elements.next();
        String taskId = element.get("id").textValue();
        JsonNode actors = element.get("actors");
        assertEquals(1, actors.size());
        String actor = actors.elements().next().get("id").textValue();
        assertEquals("Administrator", actor);
        return taskId;
    }

    @Test
    public void testTasksPaginationOffset() throws IOException {
        // Create two dummy tasks
        httpClient.buildPostRequest("/workflow")
                  .entity(getCreateAndStartWorkflowBodyContent("SerialDocumentReview"))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));
        httpClient.buildPostRequest("/workflow")
                  .entity(getCreateAndStartWorkflowBodyContent("SerialDocumentReview"))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        // Check we get only one task due to offset parameter
        httpClient.buildGetRequest("/task")
                  .addQueryParameter("offset", "1")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(1, node.get("entries").size()));
    }

    @Test
    public void testWorkflowModelEndpoint() throws Exception {
        var jsonNodeHandler = new JsonNodeHandler();
        var statusCodeHandler = new HttpStatusCodeHandler();

        List<String> expectedNames = List.of("ParallelDocumentReview", "SerialDocumentReview");

        httpClient.buildGetRequest("/workflowModel").executeAndConsume(jsonNodeHandler, node -> {
            assertEquals(2, node.get("entries").size());

            Iterator<JsonNode> elements = node.get("entries").elements();

            List<String> realNames = new ArrayList<>();
            while (elements.hasNext()) {
                JsonNode element = elements.next();
                realNames.add(element.get("name").textValue());
            }
            Collections.sort(realNames);
            assertEquals(expectedNames, realNames);
        });

        httpClient.buildGetRequest("/workflowModel/SerialDocumentReview")
                  .executeAndConsume(jsonNodeHandler,
                          node -> assertEquals("SerialDocumentReview", node.get("name").textValue()));

        String graphModelPath = "/workflowModel/ParallelDocumentReview/graph";
        httpClient.buildGetRequest("/workflowModel/ParallelDocumentReview").executeAndConsume(jsonNodeHandler, node -> {
            assertEquals("ParallelDocumentReview", node.get("name").textValue());
            // Check graph resource
            String graphUrl = node.get("graphResource").textValue();
            assertTrue(graphUrl.endsWith(graphModelPath));
        });

        httpClient.buildGetRequest(graphModelPath)
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_OK, status.intValue()));

        // Instantiate a workflow and check it does not appear as a model
        httpClient.buildPostRequest("/workflow")
                  .entity(getCreateAndStartWorkflowBodyContent("SerialDocumentReview"))
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_CREATED, status.intValue()));

        httpClient.buildGetRequest("/workflowModel")
                  .executeAndConsume(jsonNodeHandler, node -> assertEquals(2, node.get("entries").size()));
    }

    @Test
    public void testInvalidNodeAction() throws JsonProcessingException {
        httpClient.buildPostRequest("/workflow")
                  .entity(getCreateAndStartWorkflowBodyContent("SerialDocumentReview"))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));
    }

    /**
     * Start and terminate ParallelDocumentReview workflow by completing all its tasks.
     */
    @Test
    public void testTerminateParallelDocumentReviewWorkflow() throws IOException {
        DocumentModel note = RestServerInit.getNote(0, session);

        httpClient.buildGetRequest("/id/" + note.getId())
                  .addHeader(MarshallingConstants.EMBED_ENRICHERS + ".document", RunnableWorkflowJsonEnricher.NAME)
                  .executeAndConsume(new JsonNodeHandler(),
                          // We can start both default workflow on the note
                          node -> assertEquals(2, getRunnableWorkflow(node).size()));

        // Start SerialDocumentReview on Note 0
        final String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                           .entity(getCreateAndStartWorkflowBodyContent(
                                                                   "ParallelDocumentReview", List.of(note.getId())))
                                                           .execute(new JsonNodeHandler(SC_CREATED))
                                                           .get("id")
                                                           .textValue();

        var statusCodeHandler = new HttpStatusCodeHandler();

        // Complete first task
        String taskId = getCurrentTaskId(createdWorkflowInstanceId);
        String out = getBodyForStartReviewTaskCompletion(taskId);
        // Missing required variables
        httpClient.buildPutRequest("/task/" + taskId + "/start_review")
                  .entity(out)
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_OK, status.intValue()));

        // Try to complete first task again
        httpClient.buildPutRequest("/task/" + taskId + "/start_review")
                  .entity(out)
                  .executeAndConsume(statusCodeHandler, status -> assertEquals(SC_CONFLICT, status.intValue()));

        String bodyForTaskCompletion = """
                {
                  "entity-type": "task",
                  "id": "%s"
                }
                """;

        // Complete second task
        taskId = getCurrentTaskId(createdWorkflowInstanceId);
        httpClient.buildPutRequest("/task/" + taskId + "/approve")
                  .entity(bodyForTaskCompletion.formatted(taskId))
                  .executeAndConsume(statusCodeHandler,
                          // Missing required variables
                          status -> assertEquals(SC_OK, status.intValue()));

        // Complete third task
        taskId = getCurrentTaskId(createdWorkflowInstanceId);
        httpClient.buildPutRequest("/task/" + taskId + "/validate")
                  .entity(bodyForTaskCompletion.formatted(taskId))
                  .executeAndConsume(statusCodeHandler,
                          // Missing required variables
                          status -> assertEquals(SC_OK, status.intValue()));

        // Workflow must be terminated now
        // Check there are no running workflow
        httpClient.buildGetRequest("/workflow")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(0, node.get("entries").size()));

        // Check we have no opened tasks
        httpClient.buildGetRequest("/task")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(0, node.get("entries").size()));

        httpClient.buildGetRequest("/id/" + note.getId())
                  .addHeader(MarshallingConstants.EMBED_ENRICHERS + ".document", RunnableWorkflowJsonEnricher.NAME)
                  .executeAndConsume(new JsonNodeHandler(),
                          // Cannot start default wf because of current lifecycle state of the note
                          node -> assertEquals(0, getRunnableWorkflow(node).size()));
    }

    private static JsonNode getRunnableWorkflow(JsonNode node) {
        return node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS).get(RunnableWorkflowJsonEnricher.NAME);
    }

    @Test
    public void testTerminateTaskPermissions() throws IOException {
        DocumentModel note = RestServerInit.getNote(0, session);

        httpClient.buildGetRequest("/id/" + note.getId())
                  .addHeader(MarshallingConstants.EMBED_ENRICHERS + ".document", RunnableWorkflowJsonEnricher.NAME)
                  .executeAndConsume(new JsonNodeHandler(),
                          // We can start both default workflow on the note
                          node -> assertEquals(2, getRunnableWorkflow(node).size()));

        // Start SerialDocumentReview on Note 0
        final String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                           .entity(getCreateAndStartWorkflowBodyContent(
                                                                   "ParallelDocumentReview", List.of(note.getId())))
                                                           .execute(new JsonNodeHandler(SC_CREATED))
                                                           .get("id")
                                                           .textValue();

        String taskId = getCurrentTaskId(createdWorkflowInstanceId);
        String out = getBodyForStartReviewTaskCompletion(taskId);

        // Try to complete task without permissions
        httpClient.buildPutRequest("/task/" + taskId + "/start_review")
                  .credentials("user1", "user1")
                  .entity(out)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Missing required variables
                          status -> assertEquals(SC_FORBIDDEN, status.intValue()));

        // Complete task
        httpClient.buildPutRequest("/task/" + taskId + "/start_review")
                  .entity(out)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Missing required variables
                          status -> assertEquals(SC_OK, status.intValue()));

        // Try to complete first task again
        httpClient.buildPutRequest("/task/" + taskId + "/start_review")
                  .entity(out)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CONFLICT, status.intValue()));
    }

    /**
     * Start ParallelDocumentReview workflow and try to set a global variable that you are not supposed to.
     */
    @Test
    @LogCaptureFeature.FilterOn(logLevel = "WARN")
    public void testSecurityCheckOnGlobalVariable() throws IOException {
        // Start SerialDocumentReview on Note 0
        DocumentModel note = RestServerInit.getNote(0, session);
        final String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                           .entity(getCreateAndStartWorkflowBodyContent(
                                                                   "ParallelDocumentReview", List.of(note.getId())))
                                                           .execute(new JsonNodeHandler(SC_CREATED))
                                                           .get("id")
                                                           .textValue();

        // Complete first task
        JsonNode node = httpClient.buildGetRequest("/task")
                                  .addQueryParameter("workflowInstanceId", createdWorkflowInstanceId)
                                  .execute(new JsonNodeHandler());
        assertEquals(1, node.get("entries").size());
        Iterator<JsonNode> elements = node.get("entries").elements();
        JsonNode task = elements.next();
        JsonNode variables = task.get("variables");
        // Check we don't see global variables we are not supposed to
        assertTrue(variables.has("end_date"));
        assertFalse(variables.has("review_result"));

        String taskId = task.get("id").textValue();

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 1);
        String bodyWithSecurityViolation = """
                {
                  "entity-type": "task",
                  "id": "%s",
                  "comment": "a comment",
                  "variables": {
                    "end_date": "%s",
                    "participants": [
                      "user:Administrator"
                    ],
                    "review_result": "blabablaa"
                  }
                }
                """.formatted(taskId, DateParser.formatW3CDateTime(calendar.getTime()));
        List<LogEvent> events = logResult.getCaughtEvents();
        assertTrue(events.isEmpty());
        httpClient.buildPutRequest("/task/" + taskId + "/start_review")
                  .entity(bodyWithSecurityViolation)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Security violation returns OK
                          status -> assertEquals(SC_OK, status.intValue()));
        // but a warn was logged
        events = logResult.getCaughtEvents();
        assertEquals(1, events.size());

        JsonNode workflowInstance = httpClient.buildGetRequest("/workflow/" + createdWorkflowInstanceId)
                                              .execute(new JsonNodeHandler());
        variables = workflowInstance.get("variables");
        // and the global variable has not been modified
        assertTrue(variables.get("review_result").isNull());
    }

    @Test
    public void testFilterByWorkflowModelName() throws IOException {
        // Initiate SerialDocumentReview workflow
        httpClient.buildPostRequest("/workflow")
                  .entity(getCreateAndStartWorkflowBodyContent("SerialDocumentReview"))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        // Initiate ParallelDocumentReview workflow
        httpClient.buildPostRequest("/workflow")
                  .entity(getCreateAndStartWorkflowBodyContent("ParallelDocumentReview"))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        // Check GET /task
        httpClient.buildGetRequest("/task")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(2, node.get("entries").size()));

        // Check GET /task?workflowModelName={workflowModelName} i.e. pending tasks for SerialDocumentReview
        String serialDocumentReviewTaskId = httpClient.buildGetRequest("/task")
                                                      .addQueryParameter("workflowModelName", "SerialDocumentReview")
                                                      .executeAndThen(new JsonNodeHandler(), node -> {
                                                          assertEquals(1, node.get("entries").size());
                                                          Iterator<JsonNode> elements = node.get("entries").elements();
                                                          JsonNode element = elements.next();
                                                          return element.get("id").textValue();
                                                      });

        // Check GET /task?workflowModelName={workflowModelName} i.e. pending tasks for ParallelDocumentReview
        String parallelDocumentReviewTaskId = httpClient.buildGetRequest("/task")
                                                        .addQueryParameter("workflowModelName",
                                                                "ParallelDocumentReview")
                                                        .executeAndThen(new JsonNodeHandler(), node -> {
                                                            assertEquals(1, node.get("entries").size());
                                                            Iterator<JsonNode> elements = node.get("entries")
                                                                                              .elements();
                                                            JsonNode element = elements.next();
                                                            return element.get("id").textValue();
                                                        });

        assertNotEquals(serialDocumentReviewTaskId, parallelDocumentReviewTaskId);
    }

    @Test
    public void testMultipleWorkflowInstanceCreation() throws IOException {
        // Initiate a first SerialDocumentReview workflow
        String workflowModelName1 = httpClient.buildPostRequest("/workflow")
                                              .entity(getCreateAndStartWorkflowBodyContent("SerialDocumentReview"))
                                              .execute(new JsonNodeHandler(SC_CREATED))
                                              .get("workflowModelName")
                                              .textValue();

        // Initiate a second SerialDocumentReview workflow
        String workflowModelName2 = httpClient.buildPostRequest("/workflow")
                                              .entity(getCreateAndStartWorkflowBodyContent("SerialDocumentReview"))
                                              .execute(new JsonNodeHandler(SC_CREATED))
                                              .get("workflowModelName")
                                              .textValue();

        assertEquals(workflowModelName1, workflowModelName2);

        // Check we have two pending tasks
        httpClient.buildGetRequest("/task")
                  .addQueryParameter("workflowModelName", "SerialDocumentReview")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(2, node.get("entries").size()));
    }

    @Test
    public void testMultipleWorkflowInstanceCreation2() throws IOException {
        // Initiate a SerialDocumentReview workflow
        httpClient.buildPostRequest("/workflow")
                  .entity(getCreateAndStartWorkflowBodyContent("SerialDocumentReview"))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        // Initiate a ParallelDocumentReview workflow
        httpClient.buildPostRequest("/workflow")
                  .entity(getCreateAndStartWorkflowBodyContent("ParallelDocumentReview"))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        // Check GET /workflow?workflowModelName=SerialDocumentReview
        httpClient.buildGetRequest("/workflow")
                  .addQueryParameter("workflowModelName", "SerialDocumentReview")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(1, node.get("entries").size()));

        // Check GET /workflow?workflowModelName=ParallelDocumentReview
        httpClient.buildGetRequest("/workflow")
                  .addQueryParameter("workflowModelName", "ParallelDocumentReview")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(1, node.get("entries").size()));
    }

    @Test
    public void testDelegateTask() throws IOException {
        // Start SerialDocumentReview on Note 0
        DocumentModel note = RestServerInit.getNote(0, session);
        String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                     .entity(getCreateAndStartWorkflowBodyContent(
                                                             "ParallelDocumentReview", List.of(note.getId())))
                                                     .executeAndThen(new JsonNodeHandler(SC_CREATED),
                                                             node -> node.get("id").textValue());

        // Complete first task
        String taskId = getCurrentTaskId(createdWorkflowInstanceId);
        String out = getBodyForStartReviewTaskCompletion(taskId);
        httpClient.buildPutRequest("/task/" + taskId + "/start_review")
                  .entity(out)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Missing required variables
                          status -> assertEquals(SC_OK, status.intValue()));

        // Delegate
        taskId = getCurrentTaskId(createdWorkflowInstanceId);
        httpClient.buildPutRequest("/task/" + taskId + "/delegate")
                  .addQueryParameter("delegatedActors", "members")
                  .addQueryParameter("delegatedActors", "Administrator")
                  .addQueryParameter("comment", "A comment")
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        httpClient.buildGetRequest("/task/" + taskId).executeAndConsume(new JsonNodeHandler(), node -> {
            JsonNode delegatedActorsNode = node.get("delegatedActors");
            assertNotNull(delegatedActorsNode);
            assertTrue(delegatedActorsNode.isArray());
            assertEquals(2, delegatedActorsNode.size());
            assertThatContainsActors(List.of("members", "Administrator"), delegatedActorsNode);
        });
    }

    /**
     * @since 9.1
     */
    @Test
    public void testTaskWithGroupAssignee() throws IOException {
        // Start SerialDocumentReview on Note 0
        DocumentModel note = RestServerInit.getNote(0, session);
        String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                     .entity(getCreateAndStartWorkflowBodyContent(
                                                             "ParallelDocumentReview", List.of(note.getId())))
                                                     .execute(new JsonNodeHandler(SC_CREATED))
                                                     .get("id")
                                                     .textValue();

        // Complete first task
        String taskId = getCurrentTaskId(createdWorkflowInstanceId);
        String out = getBodyForStartReviewTaskCompletion(taskId, "group:administrators");
        httpClient.buildPutRequest("/task/" + taskId + "/start_review")
                  .entity(out)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Missing required variables
                          status -> assertEquals(SC_OK, status.intValue()));

        // Check GET /task i.e. pending tasks for current user
        httpClient.buildGetRequest("/task")
                  .addQueryParameter("userId", "Administrator")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(1, node.get("entries").size()));
    }

    @Test
    public void testReassignTask() throws IOException {
        // Start SerialDocumentReview on Note 0
        DocumentModel note = RestServerInit.getNote(0, session);
        String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                     .entity(getCreateAndStartWorkflowBodyContent(
                                                             "ParallelDocumentReview", List.of(note.getId())))
                                                     .executeAndThen(new JsonNodeHandler(SC_CREATED),
                                                             node -> node.get("id").textValue());

        // Complete first task
        String taskId = getCurrentTaskId(createdWorkflowInstanceId);
        String out = getBodyForStartReviewTaskCompletion(taskId);
        httpClient.buildPutRequest("/task/" + taskId + "/start_review")
                  .entity(out)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Missing required variables
                          status -> assertEquals(SC_OK, status.intValue()));

        // Reassign
        taskId = httpClient.buildGetRequest("/task").executeAndThen(new JsonNodeHandler(), node -> {
            Iterator<JsonNode> elements = node.get("entries").elements();
            node = elements.next();
            assertThatContainsActors(List.of("user:Administrator"), node.get("actors"));
            return node.get("id").textValue();
        });

        httpClient.buildPutRequest("/task/" + taskId + "/reassign")
                  .addQueryParameter("actors", "members")
                  .addQueryParameter("comment", "A comment")
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        httpClient.buildGetRequest("/task").executeAndConsume(new JsonNodeHandler(), node -> {
            node = node.get("entries").elements().next();
            assertThatContainsActors(List.of("members"), node.get("actors"));
        });
    }

    protected static void assertThatContainsActors(List<String> expectedActors, JsonNode actorsNode) {
        Iterator<JsonNode> actorNode = actorsNode.elements();
        List<String> actors = new ArrayList<>();
        while (actorNode.hasNext()) {
            actors.add(actorNode.next().get("id").textValue());
        }
        assertEquals(expectedActors.size(), actors.size());
        assertTrue(actors.containsAll(expectedActors));
    }

    @Test
    public void testTaskActionUrls() throws IOException {
        // Check POST /workflow
        String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                     .entity(getCreateAndStartWorkflowBodyContent(
                                                             "SerialDocumentReview"))
                                                     .execute(new JsonNodeHandler(SC_CREATED))
                                                     .get("id")
                                                     .textValue();

        // Check GET /workflow/{workflowInstanceId}
        httpClient.buildGetRequest("/workflow/" + createdWorkflowInstanceId)
                  .executeAndConsume(new JsonNodeHandler(),
                          node -> assertEquals(createdWorkflowInstanceId, node.get("id").textValue()));

        // Check GET /workflow .i.e get running workflow initialized by currentUser
        httpClient.buildGetRequest("/workflow").executeAndConsume(new JsonNodeHandler(), node -> {
            // we expect to retrieve the one previously created
            assertEquals(1, node.get("entries").size());
            Iterator<JsonNode> elements = node.get("entries").elements();
            String fetchedWorkflowInstanceId = elements.next().get("id").textValue();
            assertEquals(createdWorkflowInstanceId, fetchedWorkflowInstanceId);
        });

        // Check GET /task i.e. pending tasks for current user
        httpClient.buildGetRequest("/task").executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals(1, node.get("entries").size());
            JsonNode element = node.get("entries").elements().next();
            assertNotNull(element);
            JsonNode taskInfo = element.get("taskInfo");
            assertNotNull(taskInfo);
            JsonNode taskActions = taskInfo.get("taskActions");
            assertEquals(2, taskActions.size());
            JsonNode taskAction = taskActions.elements().next();
            assertNotNull(taskAction);
            String expectedTaskUrl = restServerFeature.getRestApiUrl() + "/task/" + element.get("id").textValue()
                    + "/cancel";
            assertEquals(expectedTaskUrl, taskAction.get("url").textValue());
        });
    }

    /**
     * @since 8.3
     */
    @Test
    public void testFetchWfInitiator() throws IOException {
        String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                     .entity(getCreateAndStartWorkflowBodyContent(
                                                             "SerialDocumentReview"))
                                                     .execute(new JsonNodeHandler(SC_CREATED))
                                                     .get("id")
                                                     .textValue();

        JsonNode node = httpClient.buildGetRequest("/workflow/" + createdWorkflowInstanceId)
                                  .addQueryParameter("fetch." + DocumentRouteWriter.ENTITY_TYPE,
                                          DocumentRouteWriter.FETCH_INITATIOR)
                                  .execute(new JsonNodeHandler());
        JsonNode initiatorNode = node.get("initiator");
        assertEquals("Administrator", initiatorNode.get("id").textValue());
        JsonNode initiatorProps = initiatorNode.get("properties");
        assertEquals(1, initiatorProps.get("groups").size());
        assertEquals("administrators", initiatorProps.get("groups").get(0).textValue());
        // For the sake of security
        assertNull(initiatorNode.get("properties").get("password"));
    }

    /**
     * @since 8.3
     */
    @Test
    public void testFetchTaskActors() throws IOException {
        String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                     .entity(getCreateAndStartWorkflowBodyContent(
                                                             "SerialDocumentReview"))
                                                     .execute(new JsonNodeHandler(SC_CREATED))
                                                     .get("id")
                                                     .textValue();

        JsonNode task = getCurrentTask(createdWorkflowInstanceId,
                builder -> builder.addQueryParameter("fetch." + TaskWriter.ENTITY_TYPE, TaskWriter.FETCH_ACTORS));

        ArrayNode taskActors = (ArrayNode) task.get("actors");
        assertEquals(1, taskActors.size());
        assertEquals("Administrator", taskActors.get(0).get("id").textValue());
        // For the sake of security
        assertNull(taskActors.get(0).get("properties").get("password"));
    }

    /**
     * @since 8.3
     */
    @Test
    public void testTasksEnricher() throws IOException {
        DocumentModel note = RestServerInit.getNote(0, session);
        httpClient.buildPostRequest("/workflow")
                  .entity(getCreateAndStartWorkflowBodyContent("ParallelDocumentReview", List.of(note.getId())))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        JsonNode node = httpClient.buildGetRequest("/id/" + note.getId())
                                  .addHeader(MarshallingConstants.EMBED_ENRICHERS + ".document",
                                          PendingTasksJsonEnricher.NAME)
                                  .execute(new JsonNodeHandler());
        ArrayNode tasksNode = (ArrayNode) node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS)
                                              .get(PendingTasksJsonEnricher.NAME);
        assertEquals(1, tasksNode.size());
        ArrayNode targetDocumentIdsNode = (ArrayNode) tasksNode.get(0).get(TaskWriter.TARGET_DOCUMENT_IDS);
        assertEquals(1, targetDocumentIdsNode.size());
        assertEquals(note.getId(), targetDocumentIdsNode.get(0).get("id").textValue());
    }

    /**
     * @since 8.3
     */
    @Test
    public void testRunningWorkflowEnricher() throws IOException {
        DocumentModel note = RestServerInit.getNote(0, session);
        httpClient.buildPostRequest("/workflow")
                  .entity(getCreateAndStartWorkflowBodyContent("SerialDocumentReview", List.of(note.getId())))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          status -> assertEquals(SC_CREATED, status.intValue()));

        // Grant READ to user1 on the note and login as user1
        ACP acp = note.getACP();
        ACE ace = ACE.builder("user1", SecurityConstants.READ).build();
        acp.addACE(ACL.LOCAL_ACL, ace);

        note.setACP(acp, true);
        note = session.saveDocument(note);
        txFeature.nextTransaction();

        JsonNode node = httpClient.buildGetRequest("/id/" + note.getId())
                                  .credentials("user1", "user1")
                                  .addHeader(MarshallingConstants.EMBED_ENRICHERS + ".document",
                                          RunningWorkflowJsonEnricher.NAME)
                                  .execute(new JsonNodeHandler());
        ArrayNode workflowsNode = (ArrayNode) node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS)
                                                  .get(RunningWorkflowJsonEnricher.NAME);
        assertEquals(1, workflowsNode.size());
        ArrayNode attachedDocumentIdsNode = (ArrayNode) workflowsNode.get(0)
                                                                     .get(DocumentRouteWriter.ATTACHED_DOCUMENT_IDS);
        assertEquals(1, attachedDocumentIdsNode.size());
        assertEquals(note.getId(), attachedDocumentIdsNode.get(0).get("id").textValue());
    }

    /**
     * @since 8.3
     */
    @Test
    public void testFetchTaskTargetDocuments() throws IOException {
        DocumentModel note = RestServerInit.getNote(0, session);
        String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                     .entity(getCreateAndStartWorkflowBodyContent(
                                                             "SerialDocumentReview", List.of(note.getId())))
                                                     .execute(new JsonNodeHandler(SC_CREATED))
                                                     .get("id")
                                                     .textValue();

        JsonNode task = getCurrentTask(createdWorkflowInstanceId,
                builder -> builder.addQueryParameter("fetch." + TaskWriter.ENTITY_TYPE,
                        TaskWriter.FETCH_TARGET_DOCUMENT));

        ArrayNode taskTargetDocuments = (ArrayNode) task.get(TaskWriter.TARGET_DOCUMENT_IDS);
        assertEquals(1, taskTargetDocuments.size());
        assertEquals(note.getId(), taskTargetDocuments.get(0).get("uid").textValue());

        // Don't fetch the target documents and check that "targetDocumentIds" contains a list of document ids
        // instead of document objects
        task = getCurrentTask(createdWorkflowInstanceId);

        taskTargetDocuments = (ArrayNode) task.get(TaskWriter.TARGET_DOCUMENT_IDS);
        assertEquals(1, taskTargetDocuments.size());
        assertEquals(note.getId(), taskTargetDocuments.get(0).get("id").textValue());
    }

    /**
     * Same as {@link #testFetchTaskTargetDocuments()} with the {@code DeleteTaskForDeletedDocumentListener} disabled to
     * check the behavior when a task targeting a deleted document remains.
     *
     * @since 9.3
     */
    @Ignore(value = "NXP-29024")
    @Test
    @Deploy("org.nuxeo.ecm.platform.restapi.server.routing:test-disable-task-deletion-listener.xml")
    public void testFetchTaskTargetDocumentsDeleted() throws IOException {
        DocumentModel note = RestServerInit.getNote(0, session);
        String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                     .entity(getCreateAndStartWorkflowBodyContent(
                                                             "SerialDocumentReview", List.of(note.getId())))
                                                     .execute(new JsonNodeHandler(SC_CREATED))
                                                     .get("id")
                                                     .textValue();

        // Remove the task's target document
        session.removeDocument(note.getRef());
        txFeature.nextTransaction();

        JsonNode task = getCurrentTask(createdWorkflowInstanceId,
                builder -> builder.addQueryParameter("fetch." + TaskWriter.ENTITY_TYPE,
                        TaskWriter.FETCH_TARGET_DOCUMENT));

        ArrayNode taskTargetDocuments = (ArrayNode) task.get(TaskWriter.TARGET_DOCUMENT_IDS);
        assertEquals(0, taskTargetDocuments.size());

        // Don't fetch the target documents and check that "targetDocumentIds" still contains an empty list
        task = getCurrentTask(createdWorkflowInstanceId);

        taskTargetDocuments = (ArrayNode) task.get(TaskWriter.TARGET_DOCUMENT_IDS);
        assertEquals(0, taskTargetDocuments.size());
    }

    /**
     * @since 8.3
     */
    @Test
    public void testFetchWorkflowAttachedDocuments() throws IOException {
        DocumentModel note = RestServerInit.getNote(0, session);
        JsonNode node = httpClient.buildPostRequest("/workflow")
                                  .entity(getCreateAndStartWorkflowBodyContent("SerialDocumentReview",
                                          List.of(note.getId())))
                                  .addQueryParameter("fetch." + DocumentRouteWriter.ENTITY_TYPE,
                                          DocumentRouteWriter.FETCH_ATTACHED_DOCUMENTS)
                                  .execute(new JsonNodeHandler(SC_CREATED));

        ArrayNode wfAttachedDocuments = (ArrayNode) node.get(DocumentRouteWriter.ATTACHED_DOCUMENT_IDS);
        assertEquals(1, wfAttachedDocuments.size());
        assertEquals(note.getId(), wfAttachedDocuments.get(0).get("uid").textValue());
    }

    /**
     * Trigger the escalation rule that resumes a ParallelDocumentReview workflow instance of which all attached
     * documents have been deleted.
     * <p>
     * The expected behaviour is that workflow instance is canceled.
     *
     * @since 8.4
     */
    @Test
    public void testResumeWorkflowWithDeletedAttachedDoc() throws IOException {
        DocumentModel note = RestServerInit.getNote(0, session);
        // Start SerialDocumentReview on Note 0
        String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                     .entity(getCreateAndStartWorkflowBodyContent(
                                                             "ParallelDocumentReview", List.of(note.getId())))
                                                     .execute(new JsonNodeHandler(SC_CREATED))
                                                     .get("id")
                                                     .textValue();

        // Complete first task
        String taskId = getCurrentTaskId(createdWorkflowInstanceId);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -1);
        String out = getBodyForStartReviewTaskCompletion(taskId, calendar.getTime());
        httpClient.buildPutRequest("/task/" + taskId + "/start_review")
                  .entity(out)
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Missing required variables
                          status -> assertEquals(SC_OK, status.intValue()));

        // Let's remove the attached document.
        session.removeDocument(note.getRef());

        txFeature.nextTransaction();

        EventContext eventContext = new EventContextImpl();
        eventContext.setProperty("category", "escalation");
        Event event = new EventImpl(DocumentRoutingEscalationListener.EXECUTE_ESCALATION_RULE_EVENT, eventContext);
        eventService.fireEvent(event);

        txFeature.nextTransaction();

        // Check GET /workflow/{workflowInstanceId}
        httpClient.buildGetRequest("/workflow")
                  .executeAndConsume(new JsonNodeHandler(),
                          // we expect that the workflow has been canceled because of deleted documents
                          node -> assertEquals(0, node.get("entries").size()));
    }

    /**
     * @since 9.1
     */
    @Test
    public void testWorkflowCleanUp() throws Exception {
        createWorkflowsThenWaitForCleanup();
        DocumentModelList canceled = session.query(CANCELED_WORKFLOWS);
        assertTrue(canceled.isEmpty());
    }

    /**
     * @since 10.2
     */
    @Test
    @WithFrameworkProperty(name = DocumentRoutingWorkflowInstancesCleanup.CLEANUP_WORKFLOW_INSTANCES_PROPERTY, value = "true")
    public void testWorkflowCleanUpDisabling() throws Exception {
        createWorkflowsThenWaitForCleanup();
        DocumentModelList canceled = session.query(CANCELED_WORKFLOWS);
        assertEquals(NB_WF, canceled.size());
    }

    protected void createWorkflowsThenWaitForCleanup() throws Exception {
        DocumentModel note = RestServerInit.getNote(0, session);
        for (int i = 0; i < NB_WF; i++) {
            String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                         .entity(getCreateAndStartWorkflowBodyContent(
                                                                 "ParallelDocumentReview", List.of(note.getId())))
                                                         .execute(new JsonNodeHandler(SC_CREATED))
                                                         .get("id")
                                                         .textValue();
            // Cancel the workflow
            httpClient.buildDeleteRequest("/workflow/" + createdWorkflowInstanceId)
                      .executeAndConsume(new HttpStatusCodeHandler(),
                              status -> assertEquals(SC_NO_CONTENT, status.intValue()));
        }

        // Starts a new transaction for visibility on db that use repeatable read isolation (mysql, mariadb)
        txFeature.nextTransaction();

        DocumentModelList canceled = session.query(CANCELED_WORKFLOWS);
        assertEquals(NB_WF, canceled.size());

        EventContext eventContext = new EventContextImpl();
        eventContext.setProperty("category", "workflowInstancesCleanup");
        Event event = new EventImpl(DocumentRoutingWorkflowInstancesCleanup.CLEANUP_WORKFLOW_EVENT_NAME, eventContext);
        eventService.fireEvent(event);

        // TODO there's a code change here, an additional nextTransaction, to tackle
        txFeature.nextTransaction();
    }

    /**
     * @since 9.3
     */
    @Test
    public void testTaskWithoutWorkflowInstance() {
        DocumentModel note = RestServerInit.getNote(0, session);

        // Create a task not related to a workflow instance
        List<Task> tasks = Framework.getService(TaskService.class)
                                    .createTask(session, session.getPrincipal(), note, "testNoWorkflowTask",
                                            List.of("user:Administrator"), false, null, null, null, Map.of(), null);
        assertEquals(1, tasks.size());
        Task task = tasks.getFirst();
        txFeature.nextTransaction();

        JsonNode node = httpClient.buildGetRequest("/id/" + note.getId())
                                  .addHeader(MarshallingConstants.EMBED_ENRICHERS + ".document",
                                          PendingTasksJsonEnricher.NAME)
                                  .execute(new JsonNodeHandler());
        ArrayNode tasksNode = (ArrayNode) node.get(RestConstants.CONTRIBUTOR_CTX_PARAMETERS)
                                              .get(PendingTasksJsonEnricher.NAME);
        assertEquals(1, tasksNode.size());

        JsonNode taskNode = tasksNode.get(0);
        assertEquals(task.getId(), taskNode.get("id").textValue());
        assertEquals("testNoWorkflowTask", taskNode.get("name").textValue());
        assertTrue(taskNode.get("workflowInstanceId").isNull());
        ArrayNode targetDocumentIdsNode = (ArrayNode) taskNode.get(TaskWriter.TARGET_DOCUMENT_IDS);
        assertEquals(1, targetDocumentIdsNode.size());
        assertEquals(note.getId(), targetDocumentIdsNode.get(0).get("id").textValue());
        assertThatContainsActors(List.of("user:Administrator"), taskNode.get("actors"));
        assertEquals(0, taskNode.get("variables").size());
    }

    /**
     * @since 10.1
     */
    @Test
    public void testTaskWorkflowInfo() throws IOException {
        // Create a workflow
        String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                     .entity(getCreateAndStartWorkflowBodyContent(
                                                             "SerialDocumentReview"))
                                                     .execute(new JsonNodeHandler(SC_CREATED))
                                                     .get("id")
                                                     .textValue();

        // Fetch the user's tasks and check the workflow related info
        httpClient.buildGetRequest("/task").executeAndConsume(new JsonNodeHandler(), node -> {
            assertEquals(1, node.get("entries").size());
            JsonNode taskNode = node.get("entries").elements().next();
            assertEquals(createdWorkflowInstanceId, taskNode.get("workflowInstanceId").textValue());
            assertTrue(taskNode.has("taskInfo"));
            assertTrue(taskNode.get("taskInfo").has("allowTaskReassignment"));
            assertFalse(taskNode.get("taskInfo").get("allowTaskReassignment").booleanValue());

            JsonNode actorsNode = taskNode.get("actors");
            assertNotNull(actorsNode);
            assertTrue(actorsNode.isArray());
            assertEquals(1, actorsNode.size());
            assertThatContainsActors(List.of("Administrator"), actorsNode);

            JsonNode delegatedActorsNode = taskNode.get("delegatedActors");
            assertNotNull(delegatedActorsNode);
            assertTrue(delegatedActorsNode.isArray());
            assertEquals(0, delegatedActorsNode.size());

            assertEquals("SerialDocumentReview", taskNode.get("workflowModelName").textValue());
            assertEquals("Administrator", taskNode.get("workflowInitiator").textValue());
            assertEquals("wf.serialDocumentReview.SerialDocumentReview", taskNode.get("workflowTitle").textValue());
            assertEquals("running", taskNode.get("workflowLifeCycleState").textValue());
            assertEquals(
                    String.format("%s/workflow/%s/graph", restServerFeature.getRestApiUrl(), createdWorkflowInstanceId),
                    taskNode.get("graphResource").textValue());
        });

        // Check the workflowInitiator task fetch property
        httpClient.buildGetRequest("/task")
                  .addQueryParameter("fetch." + TaskWriter.ENTITY_TYPE, TaskWriter.FETCH_WORKFLOW_INITATIOR)
                  .executeAndConsume(new JsonNodeHandler(), node -> {
                      JsonNode taskNode = node.get("entries").elements().next();
                      JsonNode initiatorNode = taskNode.get("workflowInitiator");
                      assertEquals("user", initiatorNode.get("entity-type").textValue());
                      assertEquals("Administrator", initiatorNode.get("id").textValue());
                      JsonNode properties = initiatorNode.get("properties");
                      ArrayNode groups = (ArrayNode) properties.get("groups");
                      JsonNode isPartial = initiatorNode.get("isPartial");
                      if (isPartial != null && isPartial.booleanValue()) {
                          assertFalse(initiatorNode.get("isAdministrator").booleanValue());
                          assertTrue(groups.isEmpty());
                      } else {
                          assertTrue(initiatorNode.get("isAdministrator").booleanValue());
                          assertEquals(1, groups.size());
                          assertEquals("administrators", groups.get(0).textValue());

                      }
                  });
    }

    /**
     * @since 10.3
     */
    @Test
    public void testReassignableTaskWorkflowBasicInfo() throws IOException {
        // Create a workflow
        String createdWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                     .entity(getCreateAndStartWorkflowBodyContent(
                                                             "SerialDocumentReview"))
                                                     .execute(new JsonNodeHandler(SC_CREATED))
                                                     .get("id")
                                                     .textValue();

        // Fetch the user's tasks and check the workflow related info
        JsonNode node = httpClient.buildGetRequest("/task").execute(new JsonNodeHandler());
        assertEquals(1, node.get("entries").size());
        JsonNode taskNode = node.get("entries").elements().next();
        assertEquals(createdWorkflowInstanceId, taskNode.get("workflowInstanceId").textValue());
        assertNotNull(taskNode.get("id").textValue());

        String taskId = taskNode.get("id").textValue();

        // Complete Task
        String out = """
                {
                  "entity-type": "task",
                  "id": "%s",
                  "comment": "a comment",
                  "variables": {
                    "participants": [
                      "Administrator"
                    ],
                    "validationOrReview": "validation"
                  }
                }
                """.formatted(taskId);
        httpClient.buildPutRequest(String.format("/task/%s/start_review", taskId))
                  .entity(out)
                  .executeAndConsume(new HttpStatusCodeHandler(), status -> assertEquals(SC_OK, status.intValue()));

        node = httpClient.buildGetRequest("/task").execute(new JsonNodeHandler());
        taskNode = node.get("entries").elements().next();

        assertTrue(taskNode.has("entity-type"));
        assertEquals("task", taskNode.get("entity-type").textValue());

        assertTrue(taskNode.has("name"));
        assertEquals("wf.serialDocumentReview.DocumentValidation", taskNode.get("name").textValue());

        assertTrue(taskNode.has("workflowInstanceId"));
        assertEquals(createdWorkflowInstanceId, taskNode.get("workflowInstanceId").textValue());

        assertTrue(taskNode.has("workflowModelName"));
        assertEquals("SerialDocumentReview", taskNode.get("workflowModelName").textValue());

        assertTrue(taskNode.has("workflowInitiator"));
        assertEquals("Administrator", taskNode.get("workflowInitiator").textValue());

        assertTrue(taskNode.has("workflowTitle"));
        assertEquals("wf.serialDocumentReview.SerialDocumentReview", taskNode.get("workflowTitle").textValue());

        assertTrue(taskNode.has("workflowLifeCycleState"));
        assertEquals("running", taskNode.get("workflowLifeCycleState").textValue());

        assertTrue(taskNode.has("directive"));
        assertEquals("wf.serialDocumentReview.AcceptReject", taskNode.get("directive").textValue());

        assertTrue(taskNode.has("taskInfo"));
        assertTrue(taskNode.get("taskInfo").has("allowTaskReassignment"));
        assertTrue(taskNode.get("taskInfo").get("allowTaskReassignment").booleanValue());
    }

    /**
     * NXP-25029
     *
     * @since 10.2
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.restapi.server.routing.test:test-reject-task-in-sub-workflow.xml")
    public void testRejectTaskInSubWorkflow() throws IOException {

        DocumentModel note = RestServerInit.getNote(0, session);
        // create main workflow as Administrator
        String createdMainWorkflowInstanceId = httpClient.buildPostRequest("/workflow")
                                                         .entity(getCreateAndStartWorkflowBodyContent("MainWF",
                                                                 List.of(note.getId())))
                                                         .execute(new JsonNodeHandler(SC_CREATED))
                                                         .get("id")
                                                         .textValue();

        // check childWF has started GET /api/id/{documentId}/@workflow
        String createdChildWorkflowInstanceId = httpClient.buildGetRequest(
                "/id/" + note.getId() + "/@" + WorkflowAdapter.NAME).executeAndThen(new JsonNodeHandler(), node -> {
                    assertEquals(2, node.get("entries").size());
                    JsonNode firstWF = node.get("entries").get(0);
                    JsonNode secondWF = node.get("entries").get(1);
                    if (createdMainWorkflowInstanceId.equals(firstWF.get("id").textValue())) {
                        assertEquals("MainWF", firstWF.get("workflowModelName").textValue());
                        assertEquals("ChildWF", secondWF.get("workflowModelName").textValue());
                        return secondWF.get("id").textValue();
                    } else {
                        assertEquals("MainWF", secondWF.get("workflowModelName").textValue());
                        assertEquals("ChildWF", firstWF.get("workflowModelName").textValue());
                        return firstWF.get("id").textValue();
                    }
                });

        // get tasks for child WF
        String[] taskIds = httpClient.buildGetRequest(
                "/id/" + note.getId() + "/@" + WorkflowAdapter.NAME + "/" + createdChildWorkflowInstanceId + "/task")
                                     .executeAndThen(new JsonNodeHandler(), node -> {
                                         assertEquals(2, node.get("entries").size());
                                         JsonNode task1 = node.get("entries").get(0);
                                         JsonNode task2 = node.get("entries").get(1);
                                         String task1Id = task1.get("id").textValue();
                                         String task2Id = task2.get("id").textValue();
                                         if ("user1".equals(task1.get("actors").get(0).get("id").textValue())) {
                                             return new String[] { task1Id, task2Id };
                                         } else {
                                             return new String[] { task2Id, task1Id };
                                         }
                                     });
        String taskUser1Id = taskIds[0];
        String taskUser2Id = taskIds[1];

        // reject task for user1
        httpClient.buildPutRequest("/task/" + taskUser1Id + "/reject")
                  .credentials("user1", "user1")
                  .entity(getBodyForStartReviewTaskCompletion(taskUser1Id))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Missing required variables
                          status -> assertEquals(SC_OK, status.intValue()));

        // reject task for user2
        httpClient.buildPutRequest("/task/" + taskUser2Id + "/reject")
                  .credentials("user2", "user2")
                  .entity(getBodyForStartReviewTaskCompletion(taskUser2Id))
                  .executeAndConsume(new HttpStatusCodeHandler(),
                          // Missing required variables
                          status -> assertEquals(SC_OK, status.intValue()));

        // As both tasks have been rejected, administrator wouldn't have anything to do.
        // get tasks for child WF
        httpClient.buildGetRequest(
                "/id/" + note.getId() + "/@" + WorkflowAdapter.NAME + "/" + createdChildWorkflowInstanceId + "/task")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(0, node.get("entries").size()));

        // get tasks for main WF
        // there should be no entries, both reject leads to no tasks for administrator
        httpClient.buildGetRequest(
                "/id/" + note.getId() + "/@" + WorkflowAdapter.NAME + "/" + createdMainWorkflowInstanceId + "/task")
                  .executeAndConsume(new JsonNodeHandler(), node -> assertEquals(0, node.get("entries").size()));
    }

    /*
     * NXP-29171 NXP-30658
     */
    @Test
    @Deploy("org.nuxeo.ecm.platform.restapi.server.routing.test:test-specific-task-request-unmarshalling.xml")
    public void testSpecificTaskRequestWithFetchedGroup() throws IOException {
        DocumentModel note = RestServerInit.getNote(0, session);

        // create workflow as Administrator
        String workflowInstanceId = httpClient.buildPostRequest("/workflow")
                                              .entity(getCreateAndStartWorkflowBodyContent("confirm",
                                                      List.of(note.getId())))
                                              .execute(new JsonNodeHandler(SC_CREATED))
                                              .get("id")
                                              .textValue();

        // get tasks for child WF
        JsonNode tasksNode = httpClient.buildGetRequest(
                "/id/" + note.getId() + "/@" + WorkflowAdapter.NAME + "/" + workflowInstanceId + "/task")
                                       .execute(new JsonNodeHandler());
        assertEquals(1, tasksNode.get("entries").size());
        String taskId = tasksNode.get("entries").get(0).get("id").textValue();

        NuxeoGroup group = userManager.getGroup("members");

        String groupJson = MarshallerHelper.objectToJson(group, RenderingContext.CtxBuilder.get());
        String body = String.format("{\"entity-type\":\"task\", \"id\":\"%s\", \"variables\":{\"assignees\":[%s]}}",
                taskId, groupJson);
        // assign it with a fetched entity
        httpClient.buildPutRequest("/task/" + taskId + "/confirm")
                  .entity(body)
                  .executeAndConsume(new JsonNodeHandler(), node -> {

                      JsonNode nodeVariables = node.get("variables");
                      assertNotNull(nodeVariables);

                      JsonNode nodeAssignees = nodeVariables.get("assignees");
                      assertNotNull(nodeAssignees);
                      assertTrue(nodeAssignees.isArray());

                      ArrayNode arrayAssignees = (ArrayNode) nodeAssignees;
                      assertEquals("Number of assignees is wrong", 1, arrayAssignees.size());
                      assertEquals("group:members", arrayAssignees.get(0).textValue());
                  });
    }

    /**
     * NXP-29578
     */
    @Test
    public void testStartWorkflowWithVariables() throws IOException {
        Map<String, Serializable> variables = Map.of("initiatorComment", "workflow start", "validationOrReview",
                "review");
        DocumentModel note = RestServerInit.getNote(0, session);
        // with workflow adapter
        httpClient.buildPostRequest("/id/" + note.getId() + "/@" + WorkflowAdapter.NAME)
                  .entity(getCreateAndStartWorkflowBodyContent("SerialDocumentReview", null, variables))
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED),
                          node -> assertWorkflowCreatedWithVariables(variables, node));

        // with workflow endpoint
        httpClient.buildPostRequest("/workflow")
                  .entity(getCreateAndStartWorkflowBodyContent("ParallelDocumentReview", List.of(note.getId()),
                          variables))
                  .executeAndConsume(new JsonNodeHandler(SC_CREATED),
                          node -> assertWorkflowCreatedWithVariables(variables, node));
    }

    protected void assertWorkflowCreatedWithVariables(Map<String, Serializable> expectedVariables, JsonNode node) {
        JsonNode variablesNode = node.get("variables");
        expectedVariables.forEach((k, v) -> assertEquals(v, variablesNode.get(k).asText()));
    }

    protected String getCreateAndStartWorkflowBodyContent(String workflowName) throws JsonProcessingException {
        return getCreateAndStartWorkflowBodyContent(workflowName, null);
    }

    protected String getCreateAndStartWorkflowBodyContent(String workflowName, List<String> docIds)
            throws JsonProcessingException {
        return getCreateAndStartWorkflowBodyContent(workflowName, docIds, null);
    }

    protected String getCreateAndStartWorkflowBodyContent(String workflowName, List<String> docIds,
            Map<String, Serializable> variables) throws JsonProcessingException {
        StringBuilder result = new StringBuilder();
        result.append("{\"entity-type\": \"workflow\", ")
              .append("\"workflowModelName\": \"")
              .append(workflowName)
              .append("\"");
        if (docIds != null && !docIds.isEmpty()) {
            result.append(", \"attachedDocumentIds\": [");
            for (String docId : docIds) {
                result.append("\"").append(docId).append("\"");
            }
            result.append("]");
        }

        if (variables != null && !variables.isEmpty()) {
            result.append(", \"variables\": ");
            result.append(MAPPER.writeValueAsString(variables));
        }

        result.append("}");
        return result.toString();
    }

    protected String getBodyForStartReviewTaskCompletion(String taskId) {
        return getBodyForStartReviewTaskCompletion(taskId, "user:Administrator");
    }

    /**
     * @since 9.1
     */
    protected String getBodyForStartReviewTaskCompletion(String taskId, String assignee) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, 1);
        return getBodyForStartReviewTaskCompletion(taskId, calendar.getTime(), assignee);
    }

    protected String getBodyForStartReviewTaskCompletion(String taskId, Date dueDate) {
        return getBodyForStartReviewTaskCompletion(taskId, dueDate, "user:Administrator");
    }

    /**
     * @since 9.1
     */
    protected String getBodyForStartReviewTaskCompletion(String taskId, Date dueDate, String assignee) {
        return "{" + "\"id\": \"" + taskId + "\"," + "\"comment\": \"a comment\"," + "\"entity-type\": \"task\","
                + "\"variables\": {" + "\"end_date\": \"" + DateParser.formatW3CDateTime(dueDate) + "\","
                + "\"participants\": [\"" + assignee + "\"]," + "\"assignees\": [\"" + assignee + "\"]" + "}" + "}";
    }

    protected String getCurrentTaskId(String createdWorkflowInstanceId) {
        return getCurrentTask(createdWorkflowInstanceId).get("id").textValue();
    }

    /**
     * @since 10.2
     */
    protected JsonNode getCurrentTask(String createdWorkflowInstanceId) {
        return getCurrentTask(createdWorkflowInstanceId, UnaryOperator.identity());
    }

    /**
     * @since 10.2
     */
    protected JsonNode getCurrentTask(String createdWorkflowInstanceId,
            UnaryOperator<HttpClientTestRule.RequestBuilder> customizer) {
        return customizer.apply(httpClient.buildGetRequest("/task"))
                         .addQueryParameter("workflowInstanceId", createdWorkflowInstanceId)
                         .executeAndThen(new JsonNodeHandler(), node -> {
                             assertEquals(1, node.get("entries").size());
                             Iterator<JsonNode> elements = node.get("entries").elements();
                             return elements.next();
                         });
    }
}
