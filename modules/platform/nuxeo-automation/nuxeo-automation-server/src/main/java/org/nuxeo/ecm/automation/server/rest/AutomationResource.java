/*
 * (C) Copyright 2014-2025 Nuxeo (http://nuxeo.com/) and others.
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
 *     vpasquier <vpasquier@nuxeo.com>
 */
package org.nuxeo.ecm.automation.server.rest;

import java.io.IOException;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.OperationNotFoundException;
import org.nuxeo.ecm.automation.OperationType;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.io.LoginInfo;
import org.nuxeo.ecm.automation.io.rest.operations.AutomationInfo;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.exceptions.WebResourceNotFoundException;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;
import org.nuxeo.ecm.webengine.rest.session.SessionFactory;
import org.nuxeo.runtime.api.Framework;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@Path("/automation")
@WebObject(type = "automation")
public class AutomationResource extends ModuleRoot {

    protected AutomationService service;

    public AutomationResource() {
        service = Framework.getService(AutomationService.class);
    }

    /**
     * Gets the content of the blob or blobs (multipart/mixed) located by the given doc uid and property path.
     */
    @SuppressWarnings("unchecked")
    @GET
    @Path("/files/{uid}")
    public Response getFile(@Context HttpServletRequest request, @PathParam("uid") String uid,
            @QueryParam("path") String path) {
        try {
            CoreSession session = SessionFactory.getSession(request);
            DocumentModel doc = session.getDocument(new IdRef(uid));
            Object obj;
            try {
                obj = doc.getPropertyValue(path);
            } catch (PropertyException e) {
                return ResponseHelper.notFound();
            }
            return switch (obj) {
                // a list of blobs -> use multipart/mixed
                case List<?> list when !list.isEmpty() && list.getFirst() instanceof Blob ->
                    ResponseHelper.blobs((List<Blob>) list);
                // BlobWriter will do all the processing and call the DownloadService
                case Blob blob -> Response.ok(blob).build();
                case null, default -> ResponseHelper.notFound();
            };
        } catch (IOException e) {
            throw new NuxeoException(e);
        }
    }

    @GET
    public AutomationInfo doGet() throws OperationException {
        return new AutomationInfo(service);
    }

    @POST
    @Path("/login")
    public Response login(@Context HttpServletRequest request) {
        Principal p = request.getUserPrincipal();
        if (p instanceof NuxeoPrincipal np) {
            List<String> groups = np.getAllGroups();
            Set<String> set = new HashSet<>(groups);
            return Response.ok(new LoginInfo(np.getId(), np.getName(), set, np.isAdministrator())).build();
        } else {
            return Response.status(401).build();
        }
    }

    @Path("/{oid}")
    public Object getExecutable(@PathParam("oid") String oid) {
        if (oid.startsWith(Constants.CHAIN_ID_PREFIX)) {
            oid = oid.substring(6);
        }
        try {
            OperationType op = service.getOperation(oid);
            return newObject("operation", op);
        } catch (OperationNotFoundException cause) {
            return new WebResourceNotFoundException("Failed to invoke operation: " + oid, cause);
        }
    }

}
