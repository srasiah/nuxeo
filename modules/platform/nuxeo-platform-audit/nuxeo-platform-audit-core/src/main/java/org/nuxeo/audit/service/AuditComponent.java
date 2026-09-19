/*
 * (C) Copyright 2024-2026 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.audit.service;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.nuxeo.audit.impl.StreamAuditWriter.COMPUTATION_NAME;
import static org.nuxeo.audit.listener.StreamAuditEventListener.STREAM_NAME;
import static org.nuxeo.audit.service.extension.ExtendedInfoDescriptor.ALL_EVENTS;
import static org.nuxeo.common.stream.MapMultis.eachIf;
import static org.nuxeo.common.stream.MapMultis.instanceOf;
import static org.nuxeo.ecm.core.schema.FacetNames.SYSTEM_DOCUMENT;

import java.io.Serializable;
import java.security.Principal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Nullable;
import jakarta.el.ELException;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.el.ExpressionFactoryImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.audit.api.AuditRouterIntrospection;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.audit.api.LogEntryBuilder;
import org.nuxeo.audit.api.Route;
import org.nuxeo.audit.mem.MemAuditBackendFactory;
import org.nuxeo.audit.service.extension.AdapterDescriptor;
import org.nuxeo.audit.service.extension.AuditBackendFactoryDescriptor;
import org.nuxeo.audit.service.extension.AuditRouteDescriptor;
import org.nuxeo.audit.service.extension.EventDescriptor;
import org.nuxeo.audit.service.extension.ExtendedInfoDescriptor;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentNotFoundException;
import org.nuxeo.ecm.core.api.LifeCycleConstants;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.PropertyException;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.DeletedDocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.uidgen.UIDGeneratorService;
import org.nuxeo.ecm.platform.el.ExpressionContext;
import org.nuxeo.ecm.platform.el.ExpressionEvaluator;
import org.nuxeo.lib.stream.log.Name;
import org.nuxeo.runtime.RuntimeMessage;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.ComponentStartOrders;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.stream.StreamService;

/**
 * @since 2025.0
 */
public class AuditComponent extends DefaultComponent implements AuditRouter, AuditService {

    private static final Logger log = LogManager.getLogger(AuditComponent.class);

    /**
     * The default audit backend {@link AuditBackendFactoryDescriptor#getName() name}.
     */
    public static final String DEFAULT_AUDIT_BACKEND = "default";

    /**
     * If passed as true on the event properties, event not logged
     */
    public static final String DISABLE_AUDIT_LOGGER = "disableAuditLogger";

    public static final String FORCE_AUDIT_FACET = "ForceAudit";

    /** @since 2025.16 */
    public static final String STREAM_AUDIT_VIRTUAL_EVENTS_ENABLED_PROP = "nuxeo.stream.audit.virtual.events.enabled";

    protected static final String SEQUENCE_NAME = "audit";

    protected static final String VIRTUAL_EVENT = "virtualEventCreated";

    protected static final String ADAPTER_EXT_POINT = "adapter";

    protected static final String BACKEND_FACTORY_EXT_POINT = "backendFactory";

    /** @deprecated since 2025.16, use {@link #ROUTES_EXT_POINT} instead */
    @Deprecated(since = "2025.16", forRemoval = true)
    protected static final String EVENT_EXT_POINT = "event";

    protected static final String EXTENDED_INFO_EXT_POINT = "extendedInfo";

    /** @since 2025.16 */
    protected static final String ROUTES_EXT_POINT = "routes";

    protected final ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator(new ExpressionFactoryImpl());

    protected Map<String, AuditBackendFactory<?>> auditBackendFactories;

    protected Map<String, List<ExtendedInfoMapper>> eventExtendedInfoMappers;

    /** @since 2025.16 */
    protected List<Route> routes;

    protected Boolean handleVirtualEvents;

    // --------------
    // Component APIs
    // --------------

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter.isAssignableFrom(AuditBackend.class)) {
            log.atWarn()
               .withThrowable(
                       log.isDebugEnabled() || Framework.isDevModeSet() ? new Throwable("Debug stacktrace") : null)
               .log("Getting AuditBackend from Framework.getService is deprecated, "
                       + "use AuditService.getAuditBackend instead, enable debug level for stacktrace");
            return adapter.cast(getAuditBackend(DEFAULT_AUDIT_BACKEND));
        }
        return super.getAdapter(adapter);
    }

    @Override
    @SuppressWarnings("removal")
    @Deprecated(since = "2025.16", forRemoval = true) // method will be removed when EventDescriptor will be removed
    public void registerContribution(Object contribution, String xp, ComponentInstance component) {
        if (EVENT_EXT_POINT.equals(xp)) {
            var eventDescriptor = (EventDescriptor) contribution;
            register(ROUTES_EXT_POINT, eventDescriptor.toAuditRoute());
            eventDescriptor.toExtendedInfos().forEach(extendedInfo -> register(EXTENDED_INFO_EXT_POINT, extendedInfo));
            Framework.getRuntime()
                     .getMessageHandler()
                     .addMessage(new RuntimeMessage(RuntimeMessage.Level.WARNING,
                             "The extension point: %s is deprecated, use extension points: %s and %s instead".formatted(
                                     EVENT_EXT_POINT, ROUTES_EXT_POINT, EXTENDED_INFO_EXT_POINT),
                             RuntimeMessage.Source.EXTENSION, component.getName().getName()));
        } else {
            super.registerContribution(contribution, xp, component);
        }
    }

    @Override
    @SuppressWarnings("removal")
    @Deprecated(since = "2025.16", forRemoval = true) // method will be removed when EventDescriptor will be removed
    public void unregisterContribution(Object contribution, String xp, ComponentInstance component) {
        if (EVENT_EXT_POINT.equals(xp)) {
            var eventDescriptor = (EventDescriptor) contribution;
            unregister(ROUTES_EXT_POINT, eventDescriptor.toAuditRoute());
            eventDescriptor.toExtendedInfos()
                           .forEach(extendedInfo -> unregister(EXTENDED_INFO_EXT_POINT, extendedInfo));
        } else {
            super.unregisterContribution(contribution, xp, component);
        }
    }

    @Override
    public int getApplicationStartedOrder() {
        return ComponentStartOrders.AUDIT;
    }

    @Override
    public void start(ComponentContext context) {
        // pre-compute global extendedInfo mappers
        var extendedInfoDescriptors = this.<ExtendedInfoDescriptor> getDescriptors(EXTENDED_INFO_EXT_POINT)
                                          .stream()
                                          .collect(groupingBy(ExtendedInfoDescriptor::getEvent, toList()));
        // register auditable event names and their specific extendedInfo mappers
        eventExtendedInfoMappers = //
                this.<AuditRouteDescriptor> getDescriptors(ROUTES_EXT_POINT)
                    .stream()
                    .mapMulti(eachIf(AuditRouteDescriptor::getEvents, AuditRouteDescriptor.EventDescriptor::isEnabled))
                    .distinct()
                    .peek(eventDescriptor -> log.debug("Registered event: {}", eventDescriptor::getName))
                    .collect(toMap(AuditRouteDescriptor.EventDescriptor::getName,
                            // merge all extended info mappers with specific ones
                            eventDescriptor -> Stream.concat(
                                    extendedInfoDescriptors.getOrDefault(ALL_EVENTS, List.of()).stream(),
                                    extendedInfoDescriptors.getOrDefault(eventDescriptor.getName(), List.of()).stream())
                                                     .collect(toMap(ExtendedInfoDescriptor::getKey, Function.identity(),
                                                             ExtendedInfoDescriptor::merge))
                                                     .values()
                                                     .stream()
                                                     // specific mapper can disable global one
                                                     .filter(ExtendedInfoDescriptor::isEnabled)
                                                     .map(descriptor -> new ExtendedInfoMapper(descriptor.getKey(),
                                                             descriptor.getExpression()))
                                                     .toList(),
                            (a, b) -> b, LinkedHashMap::new));
        // register audit routes
        routes = this.<AuditRouteDescriptor> getDescriptors(ROUTES_EXT_POINT).stream().map(descriptor -> {
            // compute the route based on contribution: predicates AND event names
            // predicates are ORed between them and default to true if none
            var contributedPredicate = descriptor.streamPredicates()
                                                 .map(AuditRouteDescriptor.PredicateDescriptor::instantiatePredicate)
                                                 .reduce(Predicate::or)
                                                 .orElse(logEntry -> true);
            // event names are ORed between them and default to all if none
            var eventNamePredicate = descriptor.streamEvents()
                                               .filter(AuditRouteDescriptor.EventDescriptor::isEnabled)
                                               .map(AuditRouteDescriptor.EventDescriptor::getName)
                                               .collect(collectingAndThen(toSet(),
                                                       eventNames -> (Predicate<LogEntry>) logEntry -> eventNames.isEmpty()
                                                               || eventNames.contains(logEntry.getEventId())));
            return Route.of(descriptor.getName(), descriptor.getBackendName(),
                    contributedPredicate.and(eventNamePredicate));
        }).toList();
        // register auditBackendFactories
        auditBackendFactories = //
                this.<AuditBackendFactoryDescriptor> getDescriptors(BACKEND_FACTORY_EXT_POINT)
                    .stream()
                    .peek(descriptor -> {
                        if (!Framework.isTestModeSet()
                                && descriptor.getFactory().isAssignableFrom(MemAuditBackendFactory.class)) {
                            String message = ("In-Memory implementation for audit backend \"%s\" is ONLY for testing purpose."
                                    + " Use a dedicated implementation for production.").formatted(
                                            descriptor.getName());
                            log.warn(message);
                            addRuntimeMessage(RuntimeMessage.Level.WARNING, message);
                        }
                    })
                    .collect(toMap(AuditBackendFactoryDescriptor::getName,
                            descriptor -> Framework.getService(descriptor.getFactory())));
    }

    @Override
    public void stop(ComponentContext context) throws InterruptedException {
        eventExtendedInfoMappers = null;
        auditBackendFactories = null;
        routes = null;
    }

    // ----------------
    // AuditRouter APIs
    // ----------------

    /** @since 2025.16 */
    @Override
    public List<LogEntry> computeLogEntries(Event event) {
        if (eventExtendedInfoMappers.containsKey(event.getName())) {
            LogEntry logEntry = buildEntryFromEvent(event);
            return logEntry == null ? List.of() : List.of(logEntry);
        } else if (handleVirtualEvents() && VIRTUAL_EVENT.equals(event.getName())) {
            return extractVirtualEvents(event.getContext().getArguments());
        }
        return List.of();
    }

    protected boolean handleVirtualEvents() {
        if (handleVirtualEvents == null) {
            handleVirtualEvents = Framework.isBooleanPropertyTrue(STREAM_AUDIT_VIRTUAL_EVENTS_ENABLED_PROP);
        }
        return handleVirtualEvents;
    }

    protected List<LogEntry> extractVirtualEvents(Object[] args) {
        if (ArrayUtils.isEmpty(args)) {
            return List.of();
        }
        return Arrays.stream(args).mapMulti(instanceOf(LogEntry.class)).toList();
    }

    @Override
    public void routeToBackends(List<LogEntry> logEntries) {
        routeToBackends(logEntries, routes);
    }

    @Override
    public void routeToBackends(List<LogEntry> logEntries, List<Route> routes) {
        if (logEntries.isEmpty()) {
            return;
        }
        log.debug("Writing {} log entries to audit backend(s).", logEntries::size);
        var sequencer = Framework.getService(UIDGeneratorService.class).getSequencer();
        List<Long> block = sequencer.getNextBlock(SEQUENCE_NAME, logEntries.size());
        Map<String, Set<LogEntry>> logEntriesByBackend = new HashMap<>();
        for (int i = 0; i < logEntries.size(); i++) {
            // first fill entry id and log date
            LogEntry entry = logEntries.get(i).builder().id(block.get(i)).logDate(new Date()).build();
            // second evaluate the routes
            for (var route : routes) {
                if (route.test(entry)) {
                    logEntriesByBackend.computeIfAbsent(route.getBackendName(), k -> new LinkedHashSet<>()).add(entry);
                }
            }
        }
        // finally insert to backends
        for (var mapEntry : logEntriesByBackend.entrySet()) {
            var backendName = mapEntry.getKey();
            var entries = mapEntry.getValue();
            log.debug("Inserting to backend: {} following log entries: {}", () -> backendName,
                    () -> entries.stream()
                                 .map(entry -> "{id=%s,logDate=%s,eventId=%s}".formatted(entry.getId(),
                                         entry.getLogDate(), entry.getEventId()))
                                 .collect(Collectors.joining(", ", "[", "]")));
            getAuditBackend(backendName).insertLogs(entries);
        }
    }

    @Override
    public AuditRouterIntrospection getIntrospection() {
        var routes = this.routes.stream()
                                .map(route -> new AuditRouterIntrospection.RouteIntrospection(route.getName(),
                                        route.getBackendName()))
                                .toList();
        var backends = auditBackendFactories.entrySet()
                                            .stream()
                                            .map(entry -> new AuditRouterIntrospection.BackendIntrospection(
                                                    entry.getKey(),
                                                    entry.getValue().getAuditBackend(entry.getKey()).getClass()))
                                            .toList();
        return new AuditRouterIntrospection(eventExtendedInfoMappers.keySet(), routes, backends);
    }

    // -----------------
    // AuditService APIs
    // -----------------

    @Override
    @SuppressWarnings("removal") // remove on deprecation removal
    public Set<String> getAuditableEventNames() {
        return Collections.unmodifiableSet(eventExtendedInfoMappers.keySet());
    }

    @Override
    @SuppressWarnings("removal") // remove on deprecation removal
    public List<ExtendedInfoMapper> getExtendedInfoMappers(String eventName) {
        return eventExtendedInfoMappers.getOrDefault(eventName, List.of());
    }

    @Override
    @SuppressWarnings({ "unchecked", "removal" })
    public <B extends AuditBackend> B getAuditBackend(String name) {
        var factory = auditBackendFactories.get(name);
        if (factory == null) {
            throw new NuxeoException("No AuditBackendFactory configured for name: " + name);
        }
        var backend = (B) factory.getAuditBackend(name);
        if (backend == null) {
            throw new NuxeoException("No AuditBackend configured for name: " + name);
        }
        if (backend instanceof AbstractAuditBackend abstractBackend) {
            abstractBackend.setName(name);
        }
        return backend;
    }

    @Override
    @Nullable
    @SuppressWarnings("removal") // turn into protected on deprecation removal
    public LogEntry buildEntryFromEvent(Event event) {
        EventContext ctx = event.getContext();
        String eventName = event.getName();

        Date eventDate = new Date(event.getTime());

        var builder = LogEntry.builder(eventName, eventDate);

        if (ctx instanceof DocumentEventContext docCtx) {
            DocumentModel document = docCtx.getSourceDocument();
            if (document.hasFacet(SYSTEM_DOCUMENT) && !document.hasFacet(FORCE_AUDIT_FACET)) {
                // do not log event on System documents
                // unless it has the FORCE_AUDIT_FACET facet
                return null;
            }

            Boolean disabled = (Boolean) docCtx.getProperty(DISABLE_AUDIT_LOGGER);
            if (BooleanUtils.isTrue(disabled)) {
                // don't log events with this flag
                return null;
            }
            NuxeoPrincipal principal = docCtx.getPrincipal();
            Map<String, Serializable> properties = docCtx.getProperties();

            builder.docUUID(document.getId());
            builder.docPath(document.getPathAsString());
            builder.docType(document.getType());
            builder.repositoryId(document.getRepositoryName());
            if (principal != null) {
                builder.principalName(principal);
            } else {
                log.warn("received event {} with null principal", eventName);
            }
            builder.comment((String) properties.get("comment"));
            if (document instanceof DeletedDocumentModel) {
                builder.comment("Document does not exist anymore!");
            } else {
                if (document.isLifeCycleLoaded()) {
                    builder.docLifeCycle(document.getCurrentLifeCycleState());
                }
            }
            if (LifeCycleConstants.TRANSITION_EVENT.equals(eventName)) {
                builder.docLifeCycle((String) docCtx.getProperty(LifeCycleConstants.TRANSTION_EVENT_OPTION_TO));
            }
            String category = (String) properties.get("category");
            builder.category(Objects.requireNonNullElse(category, "eventDocumentCategory"));

            doPutExtendedInfos(builder, docCtx, document, principal);

        } else {
            NuxeoPrincipal principal = ctx.getPrincipal();
            Map<String, Serializable> properties = ctx.getProperties();

            if (principal != null) {
                builder.principalName(principal);
            }
            builder.comment((String) properties.get("comment"));

            String category = (String) properties.get("category");
            builder.category(category);

            doPutExtendedInfos(builder, ctx, null, principal);

        }

        return builder.build();
    }

    protected void doPutExtendedInfos(LogEntryBuilder builder, EventContext eventContext, DocumentModel source,
            Principal principal) {
        ExpressionContext context = new ExpressionContext();
        if (eventContext != null) {
            expressionEvaluator.bindValue(context, "message", eventContext);
        }
        if (source != null) {
            expressionEvaluator.bindValue(context, "source", source);
            // inject now the adapters
            for (var ad : this.<AdapterDescriptor> getDescriptors(ADAPTER_EXT_POINT)) {
                if (source instanceof DeletedDocumentModel) {
                    continue; // skip
                }
                Object adapter = source.getAdapter(ad.getKlass());
                if (adapter != null) {
                    expressionEvaluator.bindValue(context, ad.getName(), adapter);
                }
            }
        }
        if (principal != null) {
            expressionEvaluator.bindValue(context, "principal", principal);
        }

        populateExtendedInfo(builder, source, context,
                eventExtendedInfoMappers.getOrDefault(builder.eventId(), List.of()));

        if (eventContext != null) {
            @SuppressWarnings("unchecked")
            var map = (Map<String, Serializable>) eventContext.getProperty("extendedInfos");
            if (map != null) {
                for (Map.Entry<String, Serializable> en : map.entrySet()) {
                    Serializable value = en.getValue();
                    if (value != null) {
                        builder.extended(en.getKey(), value);
                    }
                }
            }
        }
    }

    protected void populateExtendedInfo(LogEntryBuilder builder, DocumentModel source, ExpressionContext context,
            List<ExtendedInfoMapper> mappers) {
        for (var mapper : mappers) {
            String exp = mapper.expression();
            Serializable value;
            try {
                value = expressionEvaluator.evaluateExpression(context, exp, Serializable.class);
            } catch (PropertyException | UnsupportedOperationException e) {
                if (source instanceof DeletedDocumentModel) {
                    log.debug("Can not evaluate the expression: {} on a DeletedDocumentModel, skipping.", exp);
                }
                continue;
            } catch (DocumentNotFoundException e) {
                if (!DocumentEventTypes.DOCUMENT_REMOVED.equals(builder.eventId())) {
                    log.error("Document not found while populating extendedInfos into log entry: {}", builder::build,
                            () -> e);
                }
                continue;
            } catch (ELException e) {
                if (Framework.isDevModeSet() || log.isInfoEnabled()) {
                    if (source == null && !exp.startsWith("${source.")) { // global mapper on source registered
                        log.warn("Unable to evaluate expression: {} for log entry: {}", () -> exp, builder::build,
                                () -> e);
                    }
                }
                continue;
            }
            if (value == null) {
                continue;
            }
            builder.extended(mapper.key(), value);
        }
    }

    @Override
    public boolean await(Duration duration) throws InterruptedException {
        return Framework.getService(StreamService.class)
                        .await(Name.ofUrn(STREAM_NAME), Name.ofUrn(COMPUTATION_NAME), duration);
    }

    /**
     * INTERNAL METHOD FOR TESTS, DO NOT USE.
     */
    protected void clearEntriesFromBackends() {
        auditBackendFactories.entrySet()
                             .stream()
                             .map(entry -> entry.getValue().getAuditBackend(entry.getKey()))
                             .filter(AbstractAuditBackend.class::isInstance)
                             .map(AbstractAuditBackend.class::cast)
                             .forEach(AbstractAuditBackend::clearEntries);
    }
}
