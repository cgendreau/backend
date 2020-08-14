package life.catalogue.api.model;

/**
 *
 */
public interface PageReferenced extends Referenced {

  String getPageReferenceId();

  void setPageReferenceId(String referenceId);
}
