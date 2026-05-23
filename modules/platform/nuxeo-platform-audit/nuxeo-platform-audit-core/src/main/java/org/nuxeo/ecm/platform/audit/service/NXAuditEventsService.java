/*
 * (C) Copyright 2006-2018 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 */
package org.nuxeo.ecm.platform.audit.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.nuxeo.audit.service.AuditComponent.DEFAULT_AUDIT_BACKEND;
import static org.nuxeo.audit.service.extension.ExtendedInfoDescriptor.ALL_EVENTS;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.audit.service.AuditService;
import org.nuxeo.audit.service.extension.AdapterDescriptor;
import org.nuxeo.audit.service.extension.AuditRouteDescriptor;
import org.nuxeo.audit.service.extension.ExtendedInfoDescriptor;
import org.nuxeo.common.stream.MapMultis;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.AuditReader;
import org.nuxeo.ecm.platform.audit.api.AuditStorage;
import org.nuxeo.ecm.platform.audit.api.DocumentHistoryReader;
import org.nuxeo.ecm.platform.audit.api.Logs;
import org.nuxeo.ecm.platform.audit.api.document.DocumentHistoryReaderImpl;
import org.nuxeo.ecm.platform.audit.service.extension.AuditStorageDescriptor;
import org.nuxeo.runtime.RuntimeMessage;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.ComponentManager;
import org.nuxeo.runtime.model.ComponentManager.Listener;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Descriptor;
import org.nuxeo.runtime.model.Extension;

/**
 * Event service configuration.
 *
 * @author <a href="mailto:ja@nuxeo.com">Julien Anguenot</a>
 * @deprecated since 2025.0, use {@link org.nuxeo.audit.service.AuditService} instead
 */
@SuppressWarnings({ "removal", "rawtypes" })
@Deprecated(since = "2025.0", forRemoval = true)
public class NXAuditEventsService extends DefaultComponent {

    public static final ComponentName NAME = new ComponentName(
            "org.nuxeo.ecm.platform.audit.service.NXAuditEventsService");

    private static final String EVENT_EXT_POINT = "event";

    private static final String EXTENDED_INFO_EXT_POINT = "extendedInfo";

    private static final String ADAPTER_POINT = "adapter";

    /**
     * If passed as true on the event properties, event not logged
     *
     * @since 5.7
     */
    public static final String DISABLE_AUDIT_LOGGER = "disableAuditLogger";

    protected static final Logger log = LogManager.getLogger(NXAuditEventsService.class);

    protected static final String AUDIT_COMPONENT_NAME = "org.nuxeo.audit.service.AuditComponent";

    /** @since 2025.16 */
    protected static final String ROUTES_EXT_POINT = "routes";

    protected AuditBackend backend;

    protected Map<String, AuditStorageDescriptor> auditStorageDescriptors = new HashMap<>();

    protected Map<String, AuditStorage> auditStorages = new HashMap<>();

    @Override
    public void start(ComponentContext context) {
        // init storages after runtime was started (as we don't have started order for storages which are backends)
        Framework.getRuntime().getComponentManager().addListener(new Listener() {

            @Override
            public void afterStart(ComponentManager mgr, boolean isResume) {
                for (Entry<String, AuditStorageDescriptor> descriptor : auditStorageDescriptors.entrySet()) {
                    AuditStorage storage = descriptor.getValue().newInstance();
                    if (storage instanceof AuditBackend) {
                        ((AuditBackend) storage).onApplicationStarted();
                    }
                    auditStorages.put(descriptor.getKey(), storage);
                }
            }

            @Override
            public void afterStop(ComponentManager mgr, boolean isStandby) {
                uninstall();
            }

        });
    }

    @Override
    public void stop(ComponentContext context) {
        // clear storages
        auditStorages.values().forEach(storage -> {
            if (storage instanceof AuditBackend) {
                ((AuditBackend) storage).onApplicationStopped();
            }
        });
        auditStorages.clear();
    }

    @Override
    public <T> T getAdapter(Class<T> adapter) {
        if (adapter == NXAuditEventsService.class) {
            return adapter.cast(this);
        } else if (adapter.getCanonicalName().equals(DocumentHistoryReader.class.getCanonicalName())) {
            return adapter.cast(new DocumentHistoryReaderImpl());
        } else if (adapter.isAssignableFrom(AuditBackend.class) || adapter.isAssignableFrom(AuditLogger.class)
                || adapter.isAssignableFrom(AuditReader.class) || adapter.isAssignableFrom(Logs.class)) {
            log.atWarn()
               .withThrowable(log.isDebugEnabled() ? new Throwable("Debug stacktrace") : null)
               .log("Getting AuditBackend/AuditLogger/AuditReader/Logs from Framework.getService is deprecated, "
                       + "use AuditService.getAuditBackend instead, enable debug level for stacktrace");
            return adapter.cast(Framework.getService(org.nuxeo.audit.service.AuditBackend.class));
        } else {
            if (backend != null) {
                return adapter.cast(backend);
            } else {
                log.error("Can not provide service {} since backend is undefined", adapter::getCanonicalName);
                return null;
            }
        }
    }

    public Set<String> getAuditableEventNames() {
        return Framework.getService(AuditService.class).getAuditableEventNames();
    }

    public AuditBackend getBackend() {
        return Framework.getService(AuditService.class).getAuditBackend(DEFAULT_AUDIT_BACKEND);
    }

    public Set<AdapterDescriptor> getDocumentAdapters() {
        return new HashSet<>(getRegistry().getDescriptors(AUDIT_COMPONENT_NAME, ADAPTER_POINT));
    }

    /**
     * @since 7.4
     */
    public Map<String, List<ExtendedInfoDescriptor>> getEventExtendedInfoDescriptors() {
        var extendedInfoDescriptors = this.getRegistry()
                                          .<ExtendedInfoDescriptor> getDescriptors(AUDIT_COMPONENT_NAME,
                                                  EXTENDED_INFO_EXT_POINT)
                                          .stream()
                                          .collect(groupingBy(ExtendedInfoDescriptor::getEvent, toList()));
        return getRegistry().<AuditRouteDescriptor> getDescriptors(AUDIT_COMPONENT_NAME, ROUTES_EXT_POINT)
                            .stream()
                            .filter(route -> DEFAULT_AUDIT_BACKEND.equals(route.getBackendName()))
                            .mapMulti(MapMultis.each(AuditRouteDescriptor::getEvents))
                            .collect(Collectors.groupingBy(AuditRouteDescriptor.EventDescriptor::getName,
                                    Collectors.flatMapping(
                                            desc -> extendedInfoDescriptors.getOrDefault(desc.getName(), List.of())
                                                                           .stream(),
                                            Collectors.collectingAndThen(
                                                    Collectors.toMap(ExtendedInfoDescriptor::getKey,
                                                            Function.identity(), ExtendedInfoDescriptor::merge),
                                                    map -> map.values()
                                                              .stream()
                                                              .filter(ExtendedInfoDescriptor::isEnabled)
                                                              .collect(Collectors.toList())))));
    }

    public Set<ExtendedInfoDescriptor> getExtendedInfoDescriptors() {
        return getRegistry().<ExtendedInfoDescriptor> getDescriptors(AUDIT_COMPONENT_NAME, EXTENDED_INFO_EXT_POINT)
                            .stream()
                            .filter(descriptor -> ALL_EVENTS.equals(descriptor.getEvent()))
                            .collect(Collectors.toSet());
    }

    @Override
    public void registerExtension(Extension extension) {
        if (!"storage".equals(extension.getExtensionPoint())) {
            String message = """
                    Component: %s contribute to extension point: %s:%s which is deprecated \
                    You should contribute to org.nuxeo.audit.service.AuditComponent:%s instead\
                    """.formatted(extension.getComponent().getName().getName(),
                    extension.getTargetComponent().getName(), extension.getExtensionPoint(),
                    extension.getExtensionPoint());
            addRuntimeMessage(RuntimeMessage.Level.WARNING, message, RuntimeMessage.Source.EXTENSION,
                    extension.getComponent().getName().getName());

        }
        super.registerExtension(extension);
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (contribution instanceof AuditStorageDescriptor auditStorageDesc) {
            auditStorageDescriptors.put(auditStorageDesc.getId(), auditStorageDesc);
        } else if (contribution instanceof Descriptor) {
            ((DefaultComponent) Framework.getRuntime().getComponent(AUDIT_COMPONENT_NAME)).registerContribution(
                    contribution, extensionPoint, contributor);
        }
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (contribution instanceof Descriptor) {
            ((DefaultComponent) Framework.getRuntime().getComponent(AUDIT_COMPONENT_NAME)).unregisterContribution(
                    contribution, extensionPoint, contributor);
        }
    }

    /**
     * @since 9.3
     */
    public AuditStorage getAuditStorage(String id) {
        return auditStorages.get(id);
    }

}
