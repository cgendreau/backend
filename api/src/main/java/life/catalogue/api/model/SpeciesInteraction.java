package life.catalogue.api.model;

import life.catalogue.api.vocab.SpeciesInteractionType;

import java.util.Objects;

/**
 * A species interaction between two taxa or one taxon and just a name.
 */
public class SpeciesInteraction extends TaxonRelationBase<SpeciesInteractionType> {
  private String relatedScientificName;

  public String getRelatedScientificName() {
    return relatedScientificName;
  }

  public void setRelatedScientificName(String relatedScientificName) {
    this.relatedScientificName = relatedScientificName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SpeciesInteraction)) return false;
    if (!super.equals(o)) return false;
    SpeciesInteraction that = (SpeciesInteraction) o;
    return Objects.equals(relatedScientificName, that.relatedScientificName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), relatedScientificName);
  }
}
