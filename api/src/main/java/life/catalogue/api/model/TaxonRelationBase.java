package life.catalogue.api.model;

import java.util.Objects;

/**
 * A taxon concept relation between two taxa.
 */
abstract class TaxonRelationBase<T extends Enum> extends DatasetScopedEntity<Integer> implements VerbatimEntity, PageReferenced {
  private Integer verbatimKey;
  private Integer datasetKey;
  private T type;
  private String taxonId;
  private String relatedTaxonId;
  private String referenceId;
  private String pageReferenceId;
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

  public T getType() {
    return type;
  }

  public void setType(T type) {
    this.type = type;
  }

  public String getTaxonId() {
    return taxonId;
  }

  public void setTaxonId(String taxonId) {
    this.taxonId = taxonId;
  }

  public String getRelatedTaxonId() {
    return relatedTaxonId;
  }

  public void setRelatedTaxonId(String relatedTaxonId) {
    this.relatedTaxonId = relatedTaxonId;
  }

  @Override
  public String getReferenceId() {
    return referenceId;
  }

  @Override
  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  @Override
  public String getPageReferenceId() {
    return pageReferenceId;
  }

  @Override
  public void setPageReferenceId(String pageReferenceId) {
    this.pageReferenceId = pageReferenceId;
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
    if (!(o instanceof TaxonRelationBase)) return false;
    if (!super.equals(o)) return false;
    TaxonRelationBase<?> that = (TaxonRelationBase<?>) o;
    return Objects.equals(verbatimKey, that.verbatimKey) &&
      Objects.equals(datasetKey, that.datasetKey) &&
      Objects.equals(type, that.type) &&
      Objects.equals(taxonId, that.taxonId) &&
      Objects.equals(relatedTaxonId, that.relatedTaxonId) &&
      Objects.equals(referenceId, that.referenceId) &&
      Objects.equals(pageReferenceId, that.pageReferenceId) &&
      Objects.equals(remarks, that.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), verbatimKey, datasetKey, type, taxonId, relatedTaxonId, referenceId, pageReferenceId, remarks);
  }
}
