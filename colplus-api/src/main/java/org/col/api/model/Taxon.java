package org.col.api.model;

import java.net.URI;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.base.Preconditions;
import org.col.api.vocab.Lifezone;
import org.col.api.vocab.TaxonomicStatus;

/**
 *
 */
public class Taxon extends NameUsageBase {
  
  private LocalDate accordingToDate;
  private Boolean fossil;
  private Boolean recent;
  private Set<Lifezone> lifezones = EnumSet.noneOf(Lifezone.class);
  private URI webpage;

  @Override
  public void setStatus(TaxonomicStatus status) {
    if (Preconditions.checkNotNull(status).isSynonym()) {
      throw new IllegalArgumentException("Taxa cannot have a synonym status");
    }
    super.setStatus(status);
  }
  
  @JsonIgnore
  public boolean isProvisional() {
    return getStatus() == TaxonomicStatus.PROVISIONALLY_ACCEPTED;
  }
  
  public LocalDate getAccordingToDate() {
    return accordingToDate;
  }
  
  public void setAccordingToDate(LocalDate accordingToDate) {
    this.accordingToDate = accordingToDate;
  }
  
  public Boolean isFossil() {
    return fossil;
  }
  
  public void setFossil(Boolean fossil) {
    this.fossil = fossil;
  }
  
  public Boolean isRecent() {
    return recent;
  }
  
  public void setRecent(Boolean recent) {
    this.recent = recent;
  }
  
  public Set<Lifezone> getLifezones() {
    return lifezones;
  }
  
  public void setLifezones(Set<Lifezone> lifezones) {
    this.lifezones = lifezones;
  }
  
  public URI getWebpage() {
    return webpage;
  }
  
  public void setWebpage(URI webpage) {
    this.webpage = webpage;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Taxon taxon = (Taxon) o;
    return Objects.equals(accordingToDate, taxon.accordingToDate) &&
        Objects.equals(fossil, taxon.fossil) &&
        Objects.equals(recent, taxon.recent) &&
        Objects.equals(lifezones, taxon.lifezones) &&
        Objects.equals(webpage, taxon.webpage);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), accordingToDate, fossil, recent, lifezones, webpage);
  }
}
