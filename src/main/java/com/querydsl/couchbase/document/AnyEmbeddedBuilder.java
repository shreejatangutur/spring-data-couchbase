//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.querydsl.couchbase.document;

import com.querydsl.core.support.QueryMixin;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Path;
import com.querydsl.core.types.Predicate;
import java.util.Collection;

public class AnyEmbeddedBuilder<Q extends AbstractCouchbaseQuery<Q>> {
  private final QueryMixin<Q> queryMixin;
  private final Path<? extends Collection<?>> collection;

  public AnyEmbeddedBuilder(QueryMixin<Q> queryMixin, Path<? extends Collection<?>> collection) {
    this.queryMixin = queryMixin;
    this.collection = collection;
  }

  public Q on(Predicate... conditions) {
    return this.queryMixin.where(ExpressionUtils.predicate(CouchbaseOps.ELEM_MATCH, new Expression[]{this.collection, ExpressionUtils.allOf(conditions)}));
  }
}
