package life.catalogue.parser;

import com.google.common.collect.Lists;
import life.catalogue.api.vocab.SpeciesInteractionType;
import org.junit.Test;

import java.util.List;

public class SpeciesInteractionParserTest extends ParserTestBase<SpeciesInteractionType> {

  public SpeciesInteractionParserTest() {
    super(SpeciesInteractionParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(SpeciesInteractionType.POLLINATES, "pollinates");
    assertParse(SpeciesInteractionType.POLLINATED_BY, "PollinatedBy");
    assertParse(SpeciesInteractionType.POLLINATED_BY, "POLLINATED BY");
  }

  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList("a", "3d");
  }
}
