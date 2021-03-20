package life.catalogue.release;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.es.NameUsageIndexService;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Job to duplicate a managed project with all its data, decisions and metadata
 */
public class ProjectDuplication extends AbstractProjectCopy {

  ProjectDuplication(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, DatasetDao dDao,
                     int datasetKey, int userKey) {
    super("duplicating", factory, diDao, dDao, indexService, userKey, datasetKey, false);
  }
}
