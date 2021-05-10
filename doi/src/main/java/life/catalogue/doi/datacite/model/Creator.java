package life.catalogue.doi.datacite.model;

import java.util.List;
import java.util.Objects;

import javax.validation.constraints.NotNull;

public class Creator {

  @NotNull
  protected String name;
  protected NameType nameType;
  protected String givenName;
  protected String familyName;
  protected List<NameIdentifier> nameIdentifier;
  protected List<Affiliation> affiliation;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public NameType getNameType() {
    return nameType;
  }

  public void setNameType(NameType nameType) {
    this.nameType = nameType;
  }

  public String getGivenName() {
    return givenName;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  public String getFamilyName() {
    return familyName;
  }

  public void setFamilyName(String familyName) {
    this.familyName = familyName;
  }

  public List<NameIdentifier> getNameIdentifier() {
    return nameIdentifier;
  }

  public void setNameIdentifier(List<NameIdentifier> nameIdentifier) {
    this.nameIdentifier = nameIdentifier;
  }

  public List<Affiliation> getAffiliation() {
    return affiliation;
  }

  public void setAffiliation(List<Affiliation> affiliation) {
    this.affiliation = affiliation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Creator)) return false;
    Creator creator = (Creator) o;
    return Objects.equals(name, creator.name) && nameType == creator.nameType && Objects.equals(givenName, creator.givenName) && Objects.equals(familyName, creator.familyName) && Objects.equals(nameIdentifier, creator.nameIdentifier) && Objects.equals(affiliation, creator.affiliation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, nameType, givenName, familyName, nameIdentifier, affiliation);
  }
}
