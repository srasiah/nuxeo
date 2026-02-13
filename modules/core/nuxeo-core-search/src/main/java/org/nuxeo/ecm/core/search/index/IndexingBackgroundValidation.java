/*
 * (C) Copyright 2025 Nuxeo (http://nuxeo.com/) and others.
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

import static org.nuxeo.ecm.core.search.index.IndexingBackgroundAction.IndexingBackgroundComputation.INDEXES_PARAM;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.nuxeo.ecm.core.bulk.AbstractBulkActionValidation;
import org.nuxeo.ecm.core.bulk.message.BulkCommand;
import org.nuxeo.ecm.core.search.SearchIndex;
import org.nuxeo.ecm.core.search.SearchService;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 2025.11
 */
public class IndexingBackgroundValidation extends AbstractBulkActionValidation {

    @Override
    protected List<String> getParametersToValidate() {
        return List.of(INDEXES_PARAM);
    }

    @Override
    protected void validateCommand(BulkCommand command) throws IllegalArgumentException {
        validateList(INDEXES_PARAM, command);
        List<String> indexes = command.getParam(INDEXES_PARAM);
        if (CollectionUtils.isEmpty(indexes)) {
            return;
        }
        var searchService = Framework.getService(SearchService.class);
        var searchIndexes = indexes.stream().map(indexName -> {
            try {
                return searchService.getSearchIndex(indexName);
            } catch (NullPointerException e) {
                throw new IllegalArgumentException(
                        "Unknown index: %s in %s param: %s".formatted(indexName, INDEXES_PARAM, indexes));
            }
        }).toList();
        var uniqueRepositories = searchIndexes.stream().map(SearchIndex::repository).collect(Collectors.toSet());
        if (uniqueRepositories.size() > 1) {
            throw new IllegalArgumentException(
                    "All search indexes must point to the same repository: %s".formatted(searchIndexes));
        }
    }
}
