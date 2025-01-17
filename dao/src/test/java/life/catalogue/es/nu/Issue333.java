package life.catalogue.es.nu;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchRequest.SearchContent;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.es.EsReadTestBase;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * https://github.com/Sp2000/colplus-backend/issues/333. This issue actually turns out to throw up a desgin flaw: you
 * can't just normalize the q, and then decide whether the strongly normalized version is different from the weakly
 * normalized version, because the weak normalization of a single word differs from the weak normalization of multiple
 * words. Therefore we should probably always (and only) store and query on the strongly normalized version. We are
 * going to ignore this issue for now and take it along in the redesign.
 */
@Ignore
public class Issue333 extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() {
    index(createTestObjects());
    NameUsageSearchRequest query = new NameUsageSearchRequest();
    // This was said to only return the binomial, but not the trinomial.
    query.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME));
    query.setQ("trilineatu");
    assertEquals(createTestObjects(), search(query).getResult());
  }

  private static List<NameUsageWrapper> createTestObjects() {
    Name n = new Name();
    n.setScientificName("Leptoiulus trilineatus nigra");
    Taxon t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(t);

    n = new Name();
    n.setScientificName("Leptoiulus trilineatus");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(t);

    return Arrays.asList(nuw1, nuw2);
  }

}
