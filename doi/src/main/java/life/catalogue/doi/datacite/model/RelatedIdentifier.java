package life.catalogue.doi.datacite.model;

import javax.validation.constraints.NotNull;

import java.util.Objects;

public class RelatedIdentifier {

  @NotNull
  private String relatedIdentifier;
  @NotNull
  private RelatedIdentifierType relatedIdentifierType;
  @NotNull
  private RelationType relationType;
  private ResourceType resourceTypeGeneral;

  public String getRelatedIdentifier() {
    return relatedIdentifier;
  }

  public void setRelatedIdentifier(String relatedIdentifier) {
    this.relatedIdentifier = relatedIdentifier;
  }

  public RelatedIdentifierType getRelatedIdentifierType() {
    return relatedIdentifierType;
  }

  public void setRelatedIdentifierType(RelatedIdentifierType relatedIdentifierType) {
    this.relatedIdentifierType = relatedIdentifierType;
  }

  public RelationType getRelationType() {
    return relationType;
  }

  public void setRelationType(RelationType relationType) {
    this.relationType = relationType;
  }

  public ResourceType getResourceTypeGeneral() {
    return resourceTypeGeneral;
  }

  public void setResourceTypeGeneral(ResourceType resourceTypeGeneral) {
    this.resourceTypeGeneral = resourceTypeGeneral;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RelatedIdentifier)) return false;
    RelatedIdentifier that = (RelatedIdentifier) o;
    return Objects.equals(relatedIdentifier, that.relatedIdentifier) && relatedIdentifierType == that.relatedIdentifierType && relationType == that.relationType && resourceTypeGeneral == that.resourceTypeGeneral;
  }

  @Override
  public int hashCode() {
    return Objects.hash(relatedIdentifier, relatedIdentifierType, relationType, resourceTypeGeneral);
  }
}
