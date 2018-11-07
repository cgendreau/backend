package org.col.es.query;

import java.util.HashMap;
import java.util.Map;

public class TermQuery extends AbstractQuery {

  private final Map<String, TermValue> term;

  public TermQuery(String field, Object value) {
    this(field, value, null);
  }

  public TermQuery(String field, Object value, Float boost) {
    term = new HashMap<>();
    term.put(field, new TermValue(value, boost));
  }

  public Map<String, TermValue> getTerm() {
    return term;
  }

}
