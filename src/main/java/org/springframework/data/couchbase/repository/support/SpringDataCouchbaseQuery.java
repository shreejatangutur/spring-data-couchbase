/*
 * Copyright 2012-2022 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.couchbase.repository.support;

import static com.couchbase.client.core.io.CollectionIdentifier.DEFAULT_COLLECTION;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Path;
import org.springframework.data.couchbase.core.CouchbaseOperations;
import org.springframework.data.couchbase.core.ExecutableFindByQueryOperation;
import org.springframework.data.couchbase.core.query.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.lang.Nullable;

import com.mysema.commons.lang.CloseableIterator;
import com.mysema.commons.lang.EmptyCloseableIterator;
import com.querydsl.core.Fetchable;
import com.querydsl.core.QueryMetadata;
import com.querydsl.core.QueryModifiers;
import com.querydsl.core.QueryResults;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;

/**
 * @author Michael Reiche
 */
public class SpringDataCouchbaseQuery<T> extends SpringDataCouchbaseQuerySupport<SpringDataCouchbaseQuery<T>>
		implements Fetchable<T> {

	private final CouchbaseOperations couchbaseOperations;
	private final Consumer<BasicQuery> queryCustomizer;
	private final ExecutableFindByQueryOperation.ExecutableFindByQuery<T> find;// ExecutableFindOperation.FindWithQuery<T>
																																							// find;
	private final Class<T> returnType;

	/**
	 * Creates a new {@link SpringDataCouchbaseQuery}.
	 *
	 * @param operations must not be {@literal null}.
	 * @param type must not be {@literal null}.
	 */
	public SpringDataCouchbaseQuery(CouchbaseOperations operations, Class<? extends T> type) {
		this(operations, type, DEFAULT_COLLECTION);
	}

	/**
	 * Creates a new {@link SpringDataCouchbaseQuery} to query the given collection.
	 *
	 * @param operations must not be {@literal null}.
	 * @param type must not be {@literal null}.
	 * @param collectionName must not be {@literal null} or empty.
	 */
	public SpringDataCouchbaseQuery(CouchbaseOperations operations, Class<? extends T> type, String collectionName) {
		this(operations, type, type, collectionName, it -> {});
	}

	/**
	 * Creates a new {@link SpringDataCouchbaseQuery}.
	 *
	 * @param operations must not be {@literal null}.
	 * @param domainType must not be {@literal null}.
	 * @param resultType must not be {@literal null}.
	 * @param collectionName must not be {@literal null} or empty.
	 * @since 3.3
	 */
	SpringDataCouchbaseQuery(CouchbaseOperations operations, Class<?> domainType, Class<? extends T> resultType,
			String collectionName, Consumer<BasicQuery> queryCustomizer) {
		super(new SpringDataCouchbaseSerializer(operations.getConverter()));

		Class<T> resultType1 = (Class<T>) resultType;
		this.couchbaseOperations = operations;
		this.queryCustomizer = queryCustomizer;
		this.find = (ExecutableFindByQueryOperation.ExecutableFindByQuery<T>) couchbaseOperations.findByQuery(domainType)
				.as(resultType1).inCollection(collectionName);
		this.returnType = resultType1;
	}

	/*
	 * (non-Javadoc)
	 * @see com.querydsl.core.Fetchable#iterable()
	 */
	@Override
	public CloseableIterator<T> iterate() {

		try {
			Stream<T> stream = stream();
			Iterator<T> iterator = stream.iterator();

			return new CloseableIterator<T>() {

				@Override
				public boolean hasNext() {
					return iterator.hasNext();
				}

				@Override
				public T next() {
					return iterator.next();
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException("Cannot remove from iterator while streaming data.");
				}

				@Override
				public void close() {
					stream.close();
				}
			};
		} catch (RuntimeException e) {
			return handleException(e, new EmptyCloseableIterator<>());
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.querydsl.core.Fetchable#iterable()
	 */
	@Override
	public Stream<T> stream() {

		try {
			return find.matching(createQuery()).stream();
		} catch (RuntimeException e) {
			return handleException(e, Stream.empty());
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.querydsl.core.Fetchable#fetch()
	 */
	@Override
	public List<T> fetch() {
		try {
			return find.matching(createQuery()).all();
		} catch (RuntimeException e) {
			return handleException(e, Collections.emptyList());
		}
	}

	/**
	 * Fetch a {@link Page}.
	 *
	 * @param pageable
	 * @return
	 */
	public Page<T> fetchPage(Pageable pageable) {

		try {

			List<T> content = find.matching(createQuery().with(pageable)).all();

			return PageableExecutionUtils.getPage(content, pageable, this::fetchCount);
		} catch (RuntimeException e) {
			return handleException(e, new PageImpl<>(Collections.emptyList(), pageable, 0));
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.querydsl.core.Fetchable#fetchFirst()
	 */
	@Override
	public T fetchFirst() {
		try {
			return find.matching(createQuery()).firstValue();
		} catch (RuntimeException e) {
			return handleException(e, null);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.querydsl.core.Fetchable#fetchOne()
	 */
	@Override
	public T fetchOne() {
		try {
			return find.matching(createQuery()).oneValue();
		} catch (RuntimeException e) {
			return handleException(e, null);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.querydsl.core.Fetchable#fetchResults()
	 */
	@Override
	public QueryResults<T> fetchResults() {

		long total = fetchCount();
		return total > 0L ? new QueryResults<>(fetch(), getQueryMixin().getMetadata().getModifiers(), total)
				: QueryResults.emptyResults();
	}

	/*
	 * (non-Javadoc)
	 * @see com.querydsl.core.Fetchable#fetchCount()
	 */
	@Override
	public long fetchCount() {
		try {
			return find.matching(createQuery().skip(-1).limit(-1)).count();
		} catch (RuntimeException e) {
			return handleException(e, 0L);
		}
	}

	protected org.springframework.data.couchbase.core.query.Query createQuery() {

		QueryMetadata metadata = getQueryMixin().getMetadata();

		return createQuery(createFilter(metadata), metadata.getProjection(), metadata.getModifiers(),
				metadata.getOrderBy());
	}

	@Override
	protected Predicate createFilter(QueryMetadata metadata) {
			Predicate filter;
			if (!metadata.getJoins().isEmpty()) {
				filter = ExpressionUtils.allOf(new Predicate[]{metadata.getWhere(), this.createJoinFilter(metadata)});
			} else {
				filter = metadata.getWhere();
			}
			return filter;
	}

	/**
	 * Fetch the list of ids matching a given condition.
	 *
	 * @param targetType must not be {@literal null}.
	 * @param condition must not be {@literal null}.
	 * @return empty {@link List} if none found.
	 */
	//@Override
	protected List<Object> getIds(Class<?> targetType, Predicate condition) {

		Query query = createQuery(condition, null, QueryModifiers.EMPTY, Collections.emptyList());
		List<String> entities = couchbaseOperations.findByQuery(returnType /*targetType*/).distinct(new String[]{"meta().id"}).as(String.class).matching(query).all();
		List<Object> objs = new LinkedList(entities);
		return objs;
	}

	@Override
	protected List<Object> getIds(Path<?> source, Path<?> target, Predicate condition) {
		Class<?> targetType = target.getType(); // unused??
		Query query = createQuery(condition, null, QueryModifiers.EMPTY, Collections.emptyList());
		List<String> entities = couchbaseOperations.findByQuery(source.getMetadata().getParent().getType() /*targetType*/).distinct(new String[]{target.getMetadata().getName()}).as(String.class).matching(query).all();
		List<Object> objs = new LinkedList(entities);
		return objs;
	}

	protected org.springframework.data.couchbase.core.query.Query createQuery(@Nullable Predicate filter,
			@Nullable Expression<?> projection, QueryModifiers modifiers, List<OrderSpecifier<?>> orderBy) {

		Map<String, String> fields = createProjection(projection);
		BasicQuery basicQuery = new BasicQuery(createCriteria(filter), fields);

		Integer limit = modifiers.getLimitAsInteger();
		Integer offset = modifiers.getOffsetAsInteger();

		if (limit != null) {
			basicQuery.limit(limit);
		}
		if (offset != null) {
			basicQuery.skip(offset);
		}
		if (orderBy.size() > 0) {
			basicQuery.setSort(createSort(orderBy));
		}
		queryCustomizer.accept(basicQuery);
		return basicQuery;
	}

	private static <T> T handleException(RuntimeException e, T defaultValue) {

		if (e.getClass().getName().endsWith("$NoResults")) {
			return defaultValue;
		}

		throw e;
	}

}
