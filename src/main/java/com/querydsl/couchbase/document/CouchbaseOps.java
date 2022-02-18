package com.querydsl.couchbase.document;

import com.querydsl.core.types.Operator;

public enum CouchbaseOps implements Operator {
  NEAR(Boolean.class),
  GEO_WITHIN_BOX(Boolean.class),
  ELEM_MATCH(Boolean.class),
  NO_MATCH(Boolean.class),
  NEAR_SPHERE(Boolean.class);

  private final Class<?> type;

  private CouchbaseOps(Class<?> type) {
    this.type = type;
  }

  public Class<?> getType() {
    return this.type;
  }
}
