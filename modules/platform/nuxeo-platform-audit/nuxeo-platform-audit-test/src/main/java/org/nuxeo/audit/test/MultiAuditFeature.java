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
package org.nuxeo.audit.test;

import org.nuxeo.audit.service.AuditBackend;
import org.nuxeo.audit.service.AuditService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RunnerFeature;

import com.google.inject.Binder;
import com.google.inject.name.Names;

/**
 * @since 2025.16
 */
@Deploy("org.nuxeo.audit.test:OSGI-INF/multi-audit-test-contrib.xml")
@Features(AuditFeature.class)
public class MultiAuditFeature implements RunnerFeature {

    public static final String OTHER_AUDIT_BACKEND = "other";

    @Override
    public void configure(FeaturesRunner runner, Binder binder) {
        binder.bind(AuditBackend.class)
              .annotatedWith(Names.named(OTHER_AUDIT_BACKEND))
              .toProvider(() -> Framework.getService(AuditService.class).getAuditBackend(OTHER_AUDIT_BACKEND));
    }
}
