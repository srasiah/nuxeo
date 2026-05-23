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
package org.nuxeo.audit.provider;

import static org.nuxeo.audit.service.AuditComponent.DEFAULT_AUDIT_BACKEND;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.nuxeo.audit.api.AuditQueryBuilder;
import org.nuxeo.audit.api.LogEntry;
import org.nuxeo.audit.api.LogEntryList;
import org.nuxeo.audit.service.AuditBackend;
import org.nuxeo.audit.service.AuditService;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.query.sql.SQLQueryParser;
import org.nuxeo.ecm.core.query.sql.model.MultiExpression;
import org.nuxeo.ecm.core.query.sql.model.Operator;
import org.nuxeo.ecm.core.query.sql.model.OrderByExprs;
import org.nuxeo.ecm.core.query.sql.model.Predicate;
import org.nuxeo.ecm.core.query.sql.model.QueryBuilder;
import org.nuxeo.ecm.platform.query.api.AbstractPageProvider;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.WhereClauseDefinition;
import org.nuxeo.ecm.platform.query.nxql.NXQLQueryBuilder;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 2025.0
 */
public class AuditPageProvider extends AbstractPageProvider<LogEntry> implements PageProvider<LogEntry> {

    private static final long serialVersionUID = 1L;

    /** @since 2025.16 */
    public static final String BACKEND_NAME_PROPERTY = "backend";

    /** @since 2025.16 */
    public static final String CORE_SESSION_PROPERTY = "coreSession";

    protected String nxqlQuery;

    protected LogEntryList currentEntries;

    protected CoreSession getCoreSession() {
        if (getProperties().get(CORE_SESSION_PROPERTY) instanceof CoreSession session) {
            return session;
        }
        return null;
    }

    protected AuditBackend getAuditBackend() {
        String backendName = DEFAULT_AUDIT_BACKEND;
        if (getProperties().get(BACKEND_NAME_PROPERTY) instanceof String backendNameProperty) {
            backendName = backendNameProperty;
        }
        return Framework.getService(AuditService.class).getAuditBackend(backendName);
    }

    @Override
    public List<LogEntry> getCurrentPage() {
        long t0 = System.currentTimeMillis();
        if (currentEntries == null) {
            var query = buildQuery();
            currentEntries = getAuditBackend().queryLogs(query);
            // set total number of results
            setResultsCount(currentEntries.getTotalSize());

            var session = getCoreSession();
            if (session != null) {
                // send event for statistics !
                fireSearchEvent(session.getPrincipal(), nxqlQuery, currentEntries, System.currentTimeMillis() - t0);
            }
        }
        return currentEntries;
    }

    protected QueryBuilder buildQuery() {
        return new AuditQueryBuilder().filter(buildFilter())
                                      .orders(getSortInfos().stream().map(OrderByExprs::from).toList())
                                      .offset(offset)
                                      .limit(getMinMaxPageSize())
                                      .countTotal(true);
    }

    protected MultiExpression buildFilter() {
        if (nxqlQuery == null) {
            var ppDefinition = getDefinition().builder()
                                              .whereClause(this::remapWhereClause)
                                              .quickFilters(getQuickFilters())
                                              // remove sort infos as we're handling it at a different level
                                              .sortInfos(List.of())
                                              .build();
            // compute the audit query leveraging the NXQL syntax
            nxqlQuery = NXQLQueryBuilder.getQuery(ppDefinition, getParameters(), getSearchDocumentModel());
        }
        // we're only interested in the where clause as select/groupBy/having are not handled
        var sqlQuery = SQLQueryParser.parse(nxqlQuery);
        return Optional.ofNullable(sqlQuery.getWhereClause())
                       .map(clause -> clause.predicate)
                       .map(this::flattenPredicateToMultiExpression)
                       .orElseGet(() -> new MultiExpression(Operator.AND, List.of()));
    }

    protected WhereClauseDefinition remapWhereClause(WhereClauseDefinition whereClause) {
        if (whereClause != null) {
            var builder = whereClause.builder();
            if (StringUtils.isBlank(whereClause.getSelectStatement())) {
                builder.selectStatement("SELECT * FROM LogEntry");
            }
            builder.fixedPart(remapFixedPart(whereClause.getFixedPart()));
            return builder.build();
        }
        return null;
    }

    protected String remapFixedPart(String fixedPart) {
        return fixedPart;
    }

    protected MultiExpression flattenPredicateToMultiExpression(Predicate predicate) {
        if (predicate instanceof MultiExpression filter) {
            return filter;
        } else if (predicate.operator == Operator.OR || predicate.operator == Operator.AND) {
            return flattenPredicateToMultiExpression(predicate.operator, predicate);
        } else {
            return flattenPredicateToMultiExpression(Operator.AND, predicate);
        }
    }

    protected MultiExpression flattenPredicateToMultiExpression(Operator operator, Predicate predicate) {
        assert operator == Operator.AND || operator == Operator.OR;
        var predicates = new ArrayList<Predicate>();
        if (predicate.operator == operator) {
            flattenPredicate(predicates, operator, (Predicate) predicate.lvalue);
            flattenPredicate(predicates, operator, (Predicate) predicate.rvalue);
        } else {
            // simple predicate such as =
            predicates.add(predicate);
        }
        return new MultiExpression(operator, predicates);
    }

    protected void flattenPredicate(List<Predicate> predicates, Operator operator, Predicate predicate) {
        assert operator == Operator.AND || operator == Operator.OR;
        if (predicate.operator == operator) {
            flattenPredicate(predicates, operator, (Predicate) predicate.lvalue);
            flattenPredicate(predicates, operator, (Predicate) predicate.rvalue);
        } else if (predicate.operator == Operator.OR || predicate.operator == Operator.AND) {
            // here we have operator = AND + predicate.operator = OR or the opposite
            predicates.add(flattenPredicateToMultiExpression(predicate.operator, predicate));
        } else {
            // simple predicate such as =
            predicates.add(predicate);
        }
    }

    @Override
    protected void pageChanged() {
        currentEntries = null;
        super.pageChanged();
    }

    @Override
    public void refresh() {
        setCurrentPageOffset(0);
        nxqlQuery = null;
        currentEntries = null;
        super.refresh();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("name", name)
                                        .append("query", nxqlQuery)
                                        .append("sortInfos", getSortInfos())
                                        .append("offset", offset)
                                        .append("pageSize", getMinMaxPageSize())
                                        .toString();
    }
}
