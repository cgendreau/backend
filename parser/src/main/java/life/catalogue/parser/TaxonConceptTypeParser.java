package life.catalogue.parser;

import life.catalogue.api.vocab.TaxonConceptRelType;

/**
 *
 */
public class TaxonConceptTypeParser extends EnumParser<TaxonConceptRelType> {
  public static final TaxonConceptTypeParser PARSER = new TaxonConceptTypeParser();

  public TaxonConceptTypeParser() {
    super("taxonconcepttype.csv", TaxonConceptRelType.class);
  }
  
}
