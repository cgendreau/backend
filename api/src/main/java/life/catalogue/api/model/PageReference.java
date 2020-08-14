package life.catalogue.api.model;

import java.net.URI;
import java.util.Objects;

/**
 * A taxon concept or species interaction relation between two taxa.
 */
public class PageReference extends DatasetScopedEntity<String> implements VerbatimEntity, Referenced {
  private Integer verbatimKey;
  private Integer datasetKey;
  private String referenceId;
  private String page;
  private URI link;
  private String remarks;
  
  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }
  
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public String getReferenceId() {
    return referenceId;
  }

  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  public String getPage() {
    return page;
  }

  public void setPage(String page) {
    this.page = page;
  }

  public URI getLink() {
    return link;
  }

  public void setLink(URI link) {
    this.link = link;
  }

  public String getRemarks() {
    return remarks;
  }
  
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PageReference)) return false;
    if (!super.equals(o)) return false;
    PageReference that = (PageReference) o;
    return Objects.equals(verbatimKey, that.verbatimKey) &&
      Objects.equals(datasetKey, that.datasetKey) &&
      Objects.equals(referenceId, that.referenceId) &&
      Objects.equals(page, that.page) &&
      Objects.equals(link, that.link) &&
      Objects.equals(remarks, that.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), verbatimKey, datasetKey, referenceId, page, link, remarks);
  }
}
