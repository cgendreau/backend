package life.catalogue.match;

import life.catalogue.api.model.DSID;

import java.util.Objects;

public class RematchRequest implements DSID<Integer> {
  private Integer id;
  private Integer datasetKey; // project
  private boolean broken;

  public RematchRequest() {
  }

  public RematchRequest(Integer id) {
    this.id = id;
  }

  public RematchRequest(int datasetKey, boolean broken) {
    this.datasetKey = datasetKey;
    this.broken = broken;
  }


  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public Integer getId() {
    return id;
  }

  public void setId(Integer id) {
    this.id = id;
  }

  public boolean isBroken() {
    return broken;
  }

  public void setBroken(boolean broken) {
    this.broken = broken;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RematchRequest)) return false;
    RematchRequest that = (RematchRequest) o;
    return datasetKey == that.datasetKey &&
      broken == that.broken &&
      Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, id, broken);
  }
}
