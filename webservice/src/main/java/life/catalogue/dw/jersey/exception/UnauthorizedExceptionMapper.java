package life.catalogue.dw.jersey.exception;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Converts a BadRequestException into a http 400 bad request.
 */
@Provider
public class UnauthorizedExceptionMapper extends JsonExceptionMapperBase<BadRequestException> {

  public UnauthorizedExceptionMapper() {
    super(Response.Status.BAD_REQUEST, false, false, null);
  }
}
