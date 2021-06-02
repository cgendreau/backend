package life.catalogue.dao;

import life.catalogue.db.TestDataRule;
import org.junit.Test;

public class DatasetProjectSourceDaoTest extends DaoTestBase {

  public DatasetProjectSourceDaoTest(){
    super(TestDataRule.fish());
  }

  @Test
  public void list() {
    DatasetProjectSourceDao dao = new DatasetProjectSourceDao(factory());
    dao.list(TestDataRule.FISH.key, null, true).forEach(d -> {
      System.out.println(d.getTitle());
    });
  }
}