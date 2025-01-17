package life.catalogue.parser;

import com.google.common.collect.Lists;
import life.catalogue.api.vocab.GeoTime;
import org.junit.Test;

import java.util.List;

public class GeoTimeParserTest extends ParserTestBase<GeoTime> {
  
  public GeoTimeParserTest() {
    super(GeoTimeParser.PARSER);
  }
  
  @Test
  public void
  parse() throws Exception {
    assertParse("Aalenian", "aalenian");
    assertParse("Aalenian", "AALENIAN");
    assertParse("Aalenian", "Aalenium");
    assertParse("Aalenian", "Aalenian Age");
    assertParse("Aalenian", "Aalénium");
    assertParse("Aalenian", "пален");
  
    assertParse("LowerDevonian", "Early/Lower Devonian");
    assertParse("LowerDevonian", "Early Devonian");
    assertParse("LowerDevonian", "Lower Devonian");
  
    assertParse("UpperCretaceous", "yngre/övre krita");
    assertParse("UpperCretaceous", "yngre krita");
    assertParse("UpperCretaceous", "OVRE KRITA");
    assertParse("UpperCretaceous", "Apatinė/Viršutinė Kreida");
    assertParse("UpperCretaceous", "Viršutinė Kreida");
    assertParse("UpperCretaceous", "Crétacé supérieur");
    assertParse("UpperCretaceous", "Späte Kreide/Oberkreide");
    assertParse("UpperCretaceous", "Späte Kreide");
    assertParse("UpperCretaceous", "Oberkreide");
    
    assertUnparsable("unknown");
    assertUnparsable("zz");
  }
  
  private void assertParse(String expected, String input) throws UnparsableException {
    assertParse(GeoTime.byName(expected), input);
  }
  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}