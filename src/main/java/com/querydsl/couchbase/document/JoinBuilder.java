//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//
package com.querydsl.couchbase.document;

import com.couchbase.client.core.error.CouchbaseException;
import com.querydsl.core.JoinType;
import com.querydsl.core.support.QueryMixin;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Predicate;

public class JoinBuilder<Q extends AbstractCouchbaseQuery<Q>, T> {
  private final QueryMixin<Q> queryMixin;
  private final Path<?> ref;
  private final Path<T> target;

  public JoinBuilder(QueryMixin<Q> queryMixin, Path<?> ref, Path<T> target) {
    this.queryMixin = queryMixin;
    this.ref = ref;
    this.target = target;
    //throw new CouchbaseException("querydsl join not implemented for Couchbase");
  }

  public Q on(Predicate... conditions) {
    this.queryMixin.addJoin(JoinType.JOIN, ExpressionUtils.as(( Path<T> )this.ref, this.target));
    this.queryMixin.on(conditions);
    return (Q)this.queryMixin.getSelf();
  }
}
