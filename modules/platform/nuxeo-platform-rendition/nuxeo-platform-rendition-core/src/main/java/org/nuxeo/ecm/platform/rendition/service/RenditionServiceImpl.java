/*
 * (C) Copyright 2010-2024 Nuxeo (http://nuxeo.com/) and others.
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
 *   Nuxeo - initial API and implementation
 */
package org.nuxeo.ecm.platform.rendition.service;

import static org.nuxeo.ecm.core.api.security.SecurityConstants.ADD_CHILDREN;
import static org.nuxeo.ecm.platform.rendition.Constants.RENDITION_SOURCE_ID_PROPERTY;
import static org.nuxeo.ecm.platform.rendition.Constants.RENDITION_SOURCE_VERSIONABLE_ID_PROPERTY;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.query.sql.NXQL;
import org.nuxeo.ecm.platform.actions.ActionContext;
import org.nuxeo.ecm.platform.actions.ELActionContext;
import org.nuxeo.ecm.platform.actions.ejb.ActionManager;
import org.nuxeo.ecm.platform.query.nxql.NXQLQueryBuilder;
import org.nuxeo.ecm.platform.rendition.Constants;
import org.nuxeo.ecm.platform.rendition.Rendition;
import org.nuxeo.ecm.platform.rendition.extension.DefaultAutomationRenditionProvider;
import org.nuxeo.ecm.platform.rendition.impl.LazyRendition;
import org.nuxeo.ecm.platform.rendition.impl.LiveRendition;
import org.nuxeo.ecm.platform.rendition.impl.StoredRendition;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.transaction.TransactionHelper;

/**
 * Default implementation of {@link RenditionService}.
 *
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @since 5.4.1
 */
public class RenditionServiceImpl extends DefaultComponent implements RenditionService {

    public static final String RENDITION_DEFINITIONS_EP = "renditionDefinitions";

    public static final String RENDITON_DEFINION_PROVIDERS_EP = "renditionDefinitionProviders";

    public static final String DEFAULT_RENDITION_EP = "defaultRendition";

    /**
     * @since 2025.13
     */
    public static final String RENDITION_TARGET_DOC_TYPES_EP = "targetDocTypes";

    /**
     * @since 8.1
     */
    public static final String STORED_RENDITION_MANAGERS_EP = "storedRenditionManagers";

    private static final Logger log = LogManager.getLogger(RenditionServiceImpl.class);

    protected Map<String, RenditionDefinition> renditionDefinitions;

    protected Map<String, RenditionDefinitionProviderDescriptor> renditionDefinitionProviders;

    protected List<DefaultRenditionDescriptor> defaultRenditionDescriptors;

    protected static final StoredRenditionManager DEFAULT_STORED_RENDITION_MANAGER = new DefaultStoredRenditionManager();

    /**
     * @since 8.1
     */
    protected Deque<StoredRenditionManagerDescriptor> storedRenditionManagerDescriptors = new LinkedList<>();

    protected final ScriptEngineManager scriptEngineManager;

    public RenditionServiceImpl() {
        scriptEngineManager = new ScriptEngineManager();
    }

    /**
     * @since 8.1
     */
    public StoredRenditionManager getStoredRenditionManager() {
        StoredRenditionManagerDescriptor descr = storedRenditionManagerDescriptors.peekLast();
        return descr == null ? DEFAULT_STORED_RENDITION_MANAGER : descr.getStoredRenditionManager();
    }

    @Override
    public void start(ComponentContext context) {
        renditionDefinitions = this.<RenditionDefinition> getDescriptors(RENDITION_DEFINITIONS_EP)
                                   .stream()
                                   .filter(RenditionDefinition::isEnabled)
                                   .collect(Collectors.toMap(RenditionDefinition::getName, Function.identity()));
        renditionDefinitionProviders = this.<RenditionDefinitionProviderDescriptor> getDescriptors(
                RENDITON_DEFINION_PROVIDERS_EP)
                                           .stream()
                                           .filter(RenditionDefinitionProviderDescriptor::isEnabled)
                                           .collect(Collectors.toMap(RenditionDefinitionProviderDescriptor::getName,
                                                   Function.identity()));
        defaultRenditionDescriptors = getDescriptors(DEFAULT_RENDITION_EP);
        storedRenditionManagerDescriptors = new LinkedList<>(getDescriptors(STORED_RENDITION_MANAGERS_EP));

        renditionDefinitions.values().forEach(this::setupProvider);
    }

    protected void setupProvider(RenditionDefinition definition) {
        if (definition.getProviderClass() == null) {
            definition.setProvider(new DefaultAutomationRenditionProvider());
        } else {
            try {
                var provider = definition.getProviderClass().getDeclaredConstructor().newInstance();
                definition.setProvider(provider);
            } catch (Exception e) {
                log.error("Unable to create RenditionProvider", e);
            }
        }
    }

    @Override
    public void stop(ComponentContext context) {
        renditionDefinitions = null;
        renditionDefinitionProviders = null;
        defaultRenditionDescriptors = null;
        storedRenditionManagerDescriptors = null;
    }

    @Override
    public List<RenditionDefinition> getDeclaredRenditionDefinitions() {
        return new ArrayList<>(renditionDefinitions.values());
    }

    @Override
    public List<RenditionDefinition> getAvailableRenditionDefinitions(DocumentModel doc) {

        List<RenditionDefinition> defs = new ArrayList<>();
        defs.addAll(getRenditionDefinitionsFromDefinition(doc));
        defs.addAll(getRenditionDefinitionsFromProvider(doc));

        // XXX what about "lost renditions" ?
        return defs;
    }

    protected List<RenditionDefinition> getRenditionDefinitionsFromDefinition(DocumentModel doc) {
        List<RenditionDefinition> renditionDefinitions = new ArrayList<>();
        for (var descriptor : this.renditionDefinitions.values()) {
            if (canUseRenditionDefinition(descriptor, doc) && descriptor.getProvider().isAvailable(doc, descriptor)) {
                renditionDefinitions.add(descriptor);
            }
        }
        return renditionDefinitions;
    }

    protected boolean canUseRenditionDefinition(RenditionDefinition renditionDefinition, DocumentModel doc) {
        var actionService = Framework.getService(ActionManager.class);
        return actionService.checkFilters(renditionDefinition.getFilterIds(), createActionContext(doc));
    }

    protected List<RenditionDefinition> getRenditionDefinitionsFromProvider(DocumentModel doc) {
        List<RenditionDefinition> renditionDefinitions = new ArrayList<>();
        for (var descriptor : this.renditionDefinitionProviders.values()) {
            if (canUseRenditionDefinitionProvider(descriptor, doc)) {
                RenditionDefinitionProvider provider = descriptor.getProvider();
                renditionDefinitions.addAll(provider.getRenditionDefinitions(doc));
            }
        }
        return renditionDefinitions;
    }

    protected boolean canUseRenditionDefinitionProvider(
            RenditionDefinitionProviderDescriptor renditionDefinitionProviderDescriptor, DocumentModel doc) {
        ActionManager actionService = Framework.getService(ActionManager.class);
        return actionService.checkFilters(renditionDefinitionProviderDescriptor.getFilterIds(),
                createActionContext(doc));
    }

    protected ActionContext createActionContext(DocumentModel doc) {
        var actionContext = new ELActionContext();
        actionContext.setCurrentDocument(doc);
        CoreSession coreSession = doc.getCoreSession();
        actionContext.setDocumentManager(coreSession);
        if (coreSession != null) {
            actionContext.setCurrentPrincipal(coreSession.getPrincipal());
        }
        return actionContext;
    }

    @Override
    public DocumentRef storeRendition(DocumentModel source, String renditionDefinitionName) {
        Rendition rendition = getRendition(source, renditionDefinitionName, true);
        return rendition == null ? null : rendition.getHostDocument().getRef();
    }

    /**
     * @since 10.10
     */
    protected StoredRendition storeRendition(DocumentModel sourceDocument, Rendition rendition,
            RenditionDefinition renditionDefinition) {
        if (!rendition.isCompleted()) {
            log.debug("Incomplete rendition for source document {}.", sourceDocument);
            return null;
        }
        List<Blob> renderedBlobs = rendition.getBlobs();
        if (CollectionUtils.isEmpty(renderedBlobs)) {
            log.debug("No rendition blobs for source document {}.", sourceDocument);
            return null;
        }
        Blob renderedBlob = renderedBlobs.getFirst();
        String mimeType = renderedBlob.getMimeType();
        if (mimeType != null
                && (mimeType.contains(LazyRendition.ERROR_MARKER) || mimeType.contains(LazyRendition.STALE_MARKER))) {
            log.debug("Rendition has MIME type {} for source document {}.", mimeType, sourceDocument);
            return null;
        }

        CoreSession session = sourceDocument.getCoreSession();
        DocumentModel version = null;
        boolean isVersionable = sourceDocument.isVersionable();
        if (sourceDocument.isVersion()) {
            version = sourceDocument;
            sourceDocument = session.getDocument(new IdRef(version.getSourceId()));
        } else if (isVersionable) {
            DocumentRef versionRef = createVersionIfNeeded(sourceDocument, session);
            version = session.getDocument(versionRef);
        }

        log.debug("Creating stored rendition for source document {}.", sourceDocument);
        return getStoredRenditionManager().createStoredRendition(sourceDocument, version, renderedBlob,
                renditionDefinition);
    }

    protected DocumentRef createVersionIfNeeded(DocumentModel source, CoreSession session) {
        if (source.isVersionable()) {
            if (source.isVersion()) {
                return source.getRef();
            } else if (source.isCheckedOut()) {
                DocumentRef versionRef = session.checkIn(source.getRef(), VersioningOption.MINOR, null);
                source.refresh(DocumentModel.REFRESH_STATE, null);
                return versionRef;
            } else {
                return session.getLastDocumentVersionRef(source.getRef());
            }
        }
        return null;
    }

    @Override
    public Rendition getRendition(DocumentModel doc, String renditionName) {
        RenditionDefinition renditionDefinition = getAvailableRenditionDefinition(doc, renditionName);
        return getRendition(doc, renditionDefinition, renditionDefinition.isStoreByDefault());
    }

    @Override
    public Rendition getRendition(DocumentModel doc, String renditionName, boolean store) {
        RenditionDefinition renditionDefinition = getAvailableRenditionDefinition(doc, renditionName);
        return getRendition(doc, renditionDefinition, store);
    }

    @Override
    public String getRenditionTargetDocType(DocumentModel doc) {
        return Optional.ofNullable(getDescriptor(RENDITION_TARGET_DOC_TYPES_EP, doc.getType()))
                       .map(d -> ((RenditionTargetDocTypeDescriptor) d).to)
                       .orElse("File");
    }

    /**
     * @since 11.1
     */
    protected Optional<Rendition> getRenditionSafe(DocumentModel doc, String defaultRenditionName, boolean store) {
        try {
            return Optional.of(getRendition(doc, defaultRenditionName, store));
        } catch (NuxeoException e) {
            log.error("Unable to use default rendition: {}", defaultRenditionName, e);
            return Optional.empty();
        }
    }

    protected Rendition getRendition(DocumentModel doc, RenditionDefinition renditionDefinition, boolean store) {

        Rendition rendition;
        boolean isVersionable = doc.isVersionable();
        if (!isVersionable || !doc.isCheckedOut()) {
            log.debug("Document {} is not versionable nor checked out, trying to find a stored rendition.", doc);
            // stored renditions are only done against a non-versionable doc
            // or a versionable doc that is not checkedout
            rendition = getStoredRenditionManager().findStoredRendition(doc, renditionDefinition);
            if (rendition != null) {
                log.debug("Found and returning a stored rendition for document {}.", doc);
                return rendition;
            } else {
                log.debug("Found no stored rendition for document {}.", doc);
            }
        } else {
            log.debug("Document {} is versionable and checked out, not trying to find any stored rendition.", doc);
        }

        rendition = new LiveRendition(doc, renditionDefinition);

        if (store) {
            StoredRendition storedRendition = storeRendition(doc, rendition, renditionDefinition);
            if (storedRendition != null) {
                log.debug("Returning new stored rendition for document {}.", doc);
                return storedRendition;
            } else {
                log.debug("No rendition stored for document {}, returning live rendition.", doc);
            }
        } else {
            log.debug("Returning live rendition for document {}.", doc);
        }
        return rendition;
    }

    @Override
    public RenditionDefinition getAvailableRenditionDefinition(DocumentModel doc, String renditionName) {
        RenditionDefinition renditionDefinition = getRenditionDefinitionFromDefinition(renditionName);
        if (renditionDefinition == null) {
            renditionDefinition = getRenditionDefinitionFromProvider(renditionName, doc);
            if (renditionDefinition == null) {
                String message = "The rendition definition '%s' is not registered";
                throw new NuxeoException(String.format(message, renditionName));
            }
        } else {
            // we have a rendition definition but we must check that it can be used for this doc
            if (!canUseRenditionDefinition(renditionDefinition, doc)) {
                throw new NuxeoException("Rendition " + renditionName + " cannot be used for this doc " + doc.getId());
            }
        }
        if (renditionDefinition.getProvider() == null) {
            throw new NuxeoException(
                    String.format("Rendition definition %s isn't bound to any rendition provider", renditionName));
        }
        if (!renditionDefinition.getProvider().isAvailable(doc, renditionDefinition)) {
            throw new NuxeoException(
                    String.format("Rendition %s not available for this doc %s", renditionName, doc.getPathAsString()));
        }
        return renditionDefinition;
    }

    protected RenditionDefinition getRenditionDefinitionFromDefinition(String name) {
        var renditionDefinition = renditionDefinitions.get(name);
        if (renditionDefinition == null) {
            // could be the CMIS name
            for (var rd : renditionDefinitions.values()) {
                if (name.equals(rd.getCmisName())) {
                    renditionDefinition = rd;
                    break;
                }
            }
        }
        return renditionDefinition;
    }

    protected RenditionDefinition getRenditionDefinitionFromProvider(String name, DocumentModel doc) {
        List<RenditionDefinition> renditionDefinitions = getRenditionDefinitionsFromProvider(doc);
        for (var renditionDefinition : renditionDefinitions) {
            if (renditionDefinition.getName().equals(name)) {
                return renditionDefinition;
            }
        }
        return null;
    }

    @Override
    public List<Rendition> getAvailableRenditions(DocumentModel doc) {
        return getAvailableRenditions(doc, false);
    }

    @Override
    public List<Rendition> getAvailableRenditions(DocumentModel doc, boolean onlyVisible) {
        List<Rendition> renditions = new ArrayList<>();

        List<RenditionDefinition> defs = getAvailableRenditionDefinitions(doc);
        if (defs != null) {
            for (RenditionDefinition def : defs) {
                if (!onlyVisible || def.isVisible()) {
                    Rendition rendition = getRendition(doc, def.getName(), false);
                    if (rendition != null) {
                        renditions.add(rendition);
                    }
                }
            }
        }

        return renditions;
    }

    @Override
    public void deleteStoredRenditions(String repositoryName) {
        StoredRenditionsCleaner cleaner = new StoredRenditionsCleaner(repositoryName);
        cleaner.runUnrestricted();
    }

    private static final class StoredRenditionsCleaner extends UnrestrictedSessionRunner {

        private static final int BATCH_SIZE = 100;

        private StoredRenditionsCleaner(String repositoryName) {
            super(repositoryName);
        }

        @Override
        public void run() {
            Map<String, List<String>> sourceIdToRenditionRefs = computeLiveDocumentRefsToRenditionRefs();
            removeStoredRenditions(sourceIdToRenditionRefs);
        }

        /**
         * Computes only live documents renditions, the related versions will be deleted by Nuxeo.
         */
        private Map<String, List<String>> computeLiveDocumentRefsToRenditionRefs() {
            Map<String, List<String>> liveDocumentRefsToRenditionRefs = new HashMap<>();
            String query = String.format("SELECT %s, %s, %s FROM Document WHERE %s IS NOT NULL AND ecm:isVersion = 0",
                    NXQL.ECM_UUID, RENDITION_SOURCE_ID_PROPERTY, RENDITION_SOURCE_VERSIONABLE_ID_PROPERTY,
                    RENDITION_SOURCE_ID_PROPERTY);
            try (IterableQueryResult result = session.queryAndFetch(query, NXQL.NXQL)) {
                for (Map<String, Serializable> res : result) {
                    String renditionRef = res.get(NXQL.ECM_UUID).toString();
                    String sourceId = res.get(RENDITION_SOURCE_ID_PROPERTY).toString();
                    Serializable sourceVersionableId = res.get(RENDITION_SOURCE_VERSIONABLE_ID_PROPERTY);

                    String key = sourceVersionableId != null ? sourceVersionableId.toString() : sourceId;
                    liveDocumentRefsToRenditionRefs.computeIfAbsent(key, k -> new ArrayList<>()).add(renditionRef);
                }
            }
            return liveDocumentRefsToRenditionRefs;
        }

        private void removeStoredRenditions(Map<String, List<String>> liveDocumentRefsToRenditionRefs) {
            List<String> liveDocumentRefs = new ArrayList<>(liveDocumentRefsToRenditionRefs.keySet());
            if (liveDocumentRefs.isEmpty()) {
                // no more document to check
                return;
            }

            int processedSourceIds = 0;
            while (processedSourceIds < liveDocumentRefs.size()) {
                // compute the batch of source ids to check for existence
                int limit = Math.min(processedSourceIds + BATCH_SIZE, liveDocumentRefs.size());
                List<String> batchSourceIds = liveDocumentRefs.subList(processedSourceIds, limit);

                // retrieve still existing documents
                List<String> existingSourceIds = new ArrayList<>();
                String query = NXQLQueryBuilder.getQuery("SELECT ecm:uuid FROM Document WHERE ecm:uuid IN ?",
                        new Object[] { batchSourceIds }, true, true, null);
                try (IterableQueryResult result = session.queryAndFetch(query, NXQL.NXQL)) {
                    result.forEach(res -> existingSourceIds.add(res.get(NXQL.ECM_UUID).toString()));
                }
                batchSourceIds.removeAll(existingSourceIds);

                List<String> renditionRefsToDelete = batchSourceIds.stream()
                                                                   .map(liveDocumentRefsToRenditionRefs::get)
                                                                   .reduce(new ArrayList<>(), (allRefs, refs) -> {
                                                                       allRefs.addAll(refs);
                                                                       return allRefs;
                                                                   });

                if (!renditionRefsToDelete.isEmpty()) {
                    session.removeDocuments(renditionRefsToDelete.stream().map(IdRef::new).toArray(DocumentRef[]::new));
                }

                if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
                    TransactionHelper.commitOrRollbackTransaction();
                    TransactionHelper.startTransaction();
                }

                // next batch
                processedSourceIds += BATCH_SIZE;
            }
        }
    }

    @Override
    public Rendition getDefaultRendition(DocumentModel doc, String reason, Map<String, Serializable> extendedInfos) {
        return getDefaultRendition(doc, reason, false, extendedInfos);
    }

    @Override
    public Rendition getDefaultRendition(DocumentModel doc, String reason, boolean store,
            Map<String, Serializable> extendedInfos) {
        Map<String, Object> context = new HashMap<>();
        Map<String, Serializable> ei = extendedInfos == null ? Collections.emptyMap() : extendedInfos;
        NuxeoPrincipal currentUser = NuxeoPrincipal.getCurrent();
        context.put("Document", doc);
        context.put("Infos", ei);
        context.put("CurrentUser", currentUser);
        ScriptEngine engine = null;
        for (int i = defaultRenditionDescriptors.size() - 1; i >= 0; i--) {
            DefaultRenditionDescriptor desc = defaultRenditionDescriptors.get(i);
            if ((StringUtils.isEmpty(reason) && StringUtils.isEmpty(desc.reason))
                    || (reason != null && reason.equals(desc.reason))) {
                String scriptLanguage = desc.getScriptLanguage();
                if (engine == null || !engine.getFactory().getNames().contains(scriptLanguage)) {
                    // Instantiating an engine may be costly, let's keep previous one if same language
                    engine = scriptEngineManager.getEngineByName(scriptLanguage);
                    if (engine == null) {
                        throw new NuxeoException("Engine not found for language: " + scriptLanguage);
                    }
                }
                if (!(engine instanceof Invocable)) {
                    throw new NuxeoException(
                            "Engine " + engine.getClass().getName() + " not Invocable for language: " + scriptLanguage);
                }
                try {
                    engine.eval(desc.getScript());
                    engine.getBindings(ScriptContext.ENGINE_SCOPE).putAll(context);
                    Object result = ((Invocable) engine).invokeFunction("run");
                    if (result == null && desc.override) {
                        break;
                    } else {
                        String defaultRenditionName = (String) result;
                        if (!StringUtils.isBlank(defaultRenditionName)) {
                            Optional<Rendition> rendition = getRenditionSafe(doc, defaultRenditionName, store);
                            if (rendition.isPresent()) {
                                return rendition.get();
                            }
                        }
                    }

                } catch (NoSuchMethodException e) {
                    throw new NuxeoException("Script does not contain function: run() in defaultRendition: ", e);
                } catch (ScriptException e) {
                    log.error("Failed to evaluate script: ", e);
                }
            }
        }
        log.warn("Failed to get rendition name for reason {}", reason);
        return null;
    }

    @Override
    public DocumentModel publishRendition(DocumentModel doc, DocumentModel target, String renditionName,
            boolean override) {
        CoreSession session = doc.getCoreSession();
        if (!session.hasPermission(target.getRef(), ADD_CHILDREN)) {
            log.error("Permission '{}' is not granted to '{}' on document '{}'", ADD_CHILDREN,
                    session.getPrincipal().getName(), target.getPath());
            throw new DocumentSecurityException(
                    "Privilege '" + ADD_CHILDREN + "' is not granted to '" + session.getPrincipal().getName() + "'");
        }
        Rendition rendition = StringUtils.isEmpty(renditionName)
                ? getDefaultRendition(doc, Constants.DEFAULT_RENDTION_PUBLISH_REASON, true, null)
                : getRendition(doc, renditionName, true);
        if (rendition == null) {
            throw new NuxeoException("Unable to render the document");
        }
        DocumentModel renditionDocument = rendition.getHostDocument();
        /*
         * We've checked above that the current user is allowed to add new documents in the target. We need the
         * privileged session to publish the rendition which is a placeless document.
         */
        DocumentRef publishedDocumentRef = CoreInstance.doPrivileged(session,
                (CoreSession s) -> s.publishDocument(renditionDocument, target, override).getRef());
        DocumentModel publishedDocument = session.getDocument(publishedDocumentRef);
        if (override) {
            RenditionsRemover remover = new RenditionsRemover(publishedDocument);
            remover.runUnrestricted();
        }
        return publishedDocument;
    }

}
