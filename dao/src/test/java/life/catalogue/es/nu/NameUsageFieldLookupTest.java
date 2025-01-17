package life.catalogue.es.nu;

import life.catalogue.api.search.NameUsageSearchParameter;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NameUsageFieldLookupTest {

  @Test
  public void lookup() {
    for (NameUsageSearchParameter p : NameUsageSearchParameter.values()) {
      assertNotNull(NameUsageFieldLookup.INSTANCE.lookupSingle(p));
      assertEquals(1, NameUsageFieldLookup.INSTANCE.lookup(p).length);
    }
  }
}