package life.catalogue.config;

/**
 * DataCite DOI configuration.
 */
public class DoiConfig {

  public String api = "https://api.test.datacite.org";

  public String username;

  public String password;

  /**
   * DOI prefix to be used for COL DOIs.
   * Defaults to the test system.
   */
  public String prefix = "10.80631";
  
}
