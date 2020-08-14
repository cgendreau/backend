package life.catalogue.parser;

import life.catalogue.api.vocab.SpeciesInteractionType;

/**
 *
 */
public class SpeciesInteractionParser extends EnumParser<SpeciesInteractionType> {
  public static final SpeciesInteractionParser PARSER = new SpeciesInteractionParser();

  public SpeciesInteractionParser() {
    super("speciesinteraction.csv", SpeciesInteractionType.class);
  }
  
}
