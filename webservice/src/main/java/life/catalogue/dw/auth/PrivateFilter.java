package life.catalogue.dw.auth;

import it.unimi.dsi.fastutil.ints.Int2BooleanMap;
import it.unimi.dsi.fastutil.ints.Int2BooleanMaps;
import it.unimi.dsi.fastutil.ints.Int2BooleanOpenHashMap;
import life.catalogue.api.model.User;
import life.catalogue.db.mapper.DatasetMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.annotation.Priority;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.function.IntPredicate;

/**
 * Avoids unpriveleged access to private datasets.
 * See https://github.com/CatalogueOfLife/backend/issues/659
 * <p>
 * To prevent performance penalties a low memory footprint cache is used.
 * Make sure that any dataset changing actions keep this filter up to date using {@link PrivateFilter#updateCache(int, boolean)}.
 * <p>
 * A SqlSessionFactory MUST be set before the service is used.
 */
@Priority(Priorities.AUTHORIZATION)
public class PrivateFilter implements ContainerRequestFilter {

  private SqlSessionFactory factory;
  private final Int2BooleanMap cache = Int2BooleanMaps.synchronize(new Int2BooleanOpenHashMap());

  @Override
  public void filter(ContainerRequestContext req) throws IOException {
    Integer datasetKey = AuthFilter.requestedDataset(req.getUriInfo());
    if (datasetKey != null) {
      // is this a private dataset?
      boolean priv = cache.computeIfAbsent(datasetKey, new IntPredicate() {
        @Override
        public boolean test(int value) {
          try (SqlSession session = factory.openSession()) {
            DatasetMapper dm = session.getMapper(DatasetMapper.class);
            return dm.isPrivate(datasetKey);
          }
        }
      });

      if (priv) {
        // check if users has permissions
        SecurityContext secCtxt = req.getSecurityContext();
        if (secCtxt != null && secCtxt.getUserPrincipal() != null && secCtxt.getUserPrincipal() instanceof User) {
          User user = (User) secCtxt.getUserPrincipal();
          if (user.isAuthorized(datasetKey)) {
            return;
          }
        }
        throw new ForbiddenException("Dataset " + datasetKey + " is private");
      }
    }
  }

  /**
   * Wires up the mybatis sqlfactory to be used.
   */
  public void setSqlSessionFactory(SqlSessionFactory factory) {
    this.factory = factory;
  }

  public void updateCache(int datasetKey, boolean privat) {
    cache.put(datasetKey, privat);
  }
}