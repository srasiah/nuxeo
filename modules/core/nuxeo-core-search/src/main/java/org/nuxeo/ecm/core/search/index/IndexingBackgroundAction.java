/*
 * (C) Copyright 2024 Nuxeo (http://nuxeo.com/) and others.
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
 *     bdelbosc
 */
package org.nuxeo.ecm.core.search.index;

import static org.nuxeo.ecm.core.bulk.BulkServiceImpl.STATUS_STREAM;
import static org.nuxeo.lib.stream.computation.AbstractComputation.INPUT_1;
import static org.nuxeo.lib.stream.computation.AbstractComputation.OUTPUT_1;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.lib.stream.computation.Topology;
import org.nuxeo.runtime.stream.StreamProcessorTopology;

/**
 * @since 2025.0
 */
public class IndexingBackgroundAction implements StreamProcessorTopology {

    public static final String ACTION_NAME = "indexingBackground";

    public static final String ACTION_FULL_NAME = "bulk/" + ACTION_NAME;

    @Override
    public Topology getTopology(Map<String, String> options) {
        return Topology.builder()
                       .addComputation(() -> new IndexingBackgroundComputation(ACTION_FULL_NAME),
                               List.of(INPUT_1 + ":" + ACTION_FULL_NAME, //
                                       OUTPUT_1 + ":" + STATUS_STREAM))
                       .build();

    }

    public static class IndexingBackgroundComputation extends IndexingAction.IndexingComputation {

        public IndexingBackgroundComputation(String name) {
            super(name);
        }

        /** @since 2025.11 */
        public static final String INDEXES_PARAM = "indexes";

        @Override
        public void startBucket(String bucketKey) {
            delete = false;
            BulkCommand command = getCurrentCommand();
            if (command.getParam(INDEXES_PARAM) instanceof List<?> objects) {
                filterIndexes = objects.stream()
                                       .filter(Objects::nonNull)
                                       .map(Object::toString)
                                       .collect(Collectors.toSet());
            }
        }
    }
}
