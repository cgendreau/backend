package life.catalogue.resources;

import io.dropwizard.testing.ResourceHelpers;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.WsServerRule;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MatchingResourceTest {
  
  @ClassRule
  public static final WsServerRule RULE = new WsServerRule(ResourceHelpers.resourceFilePath("config-test.yaml"));
  
  @Test
  public void match() {
    NameMatch match = RULE.client().target(
        String.format("http://localhost:%d/name/matching", RULE.getLocalPort()))
        .queryParam("q", "Abies alba Mill.")
        .request().get(NameMatch.class);
    
    Name abies = new Name();
    abies.setGenus("Abies");
    abies.setSpecificEpithet("alba");
    abies.getCombinationAuthorship().getAuthors().add("Mill.");
    abies.setType(NameType.SCIENTIFIC);
    abies.setRank(Rank.SPECIES);
    abies.updateNameCache();
    
    assertNotNull(match);
    assertEquals(MatchType.NONE, match.getType());
  }
}