package life.catalogue.common;

/**
 * An interface for objects which need to be started and stopped as the application is started or
 * stopped.
 *
 * Copied from Dropwizard to have the interface available without the full DW dependencies.
 */
public interface Managed {
  /**
   * Starts the object. Called <i>before</i> the application becomes available.
   *
   * @throws Exception if something goes wrong; this will halt the application startup.
   */
  void start() throws Exception;

  /**
   * Stops the object. Called <i>after</i> the application is no longer accepting requests.
   *
   * @throws Exception if something goes wrong.
   */
  void stop() throws Exception;
}
