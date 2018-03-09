package org.col.admin.task.importer.dwca;

import com.google.common.collect.Lists;
import org.col.admin.task.importer.InsertMetadata;
import org.col.admin.task.importer.InterpreterBase;
import org.col.admin.task.importer.neo.ReferenceStore;
import org.col.admin.task.importer.neo.model.NeoTaxon;
import org.col.admin.task.importer.neo.model.UnescapedVerbatimRecord;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.col.parser.*;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Interprets a verbatim record and transforms it into a name, taxon and unique references.
 */
public class DwcInterpreter extends InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(DwcInterpreter.class);

  private final InsertMetadata insertMetadata;

  public DwcInterpreter(Dataset dataset, InsertMetadata insertMetadata, ReferenceStore refStore) {
    super(dataset, refStore);
    this.insertMetadata = insertMetadata;
  }

  public NeoTaxon interpret(UnescapedVerbatimRecord v) {
    NeoTaxon t = new NeoTaxon();
    // verbatim
    t.verbatim = v;
    // name
    t.name = interpretName(v);
    // acts
    t.acts = interpretActs(v);
    // flat classification
    t.classification = new Classification();
    for (DwcTerm dwc : DwcTerm.HIGHER_RANKS) {
      t.classification.setByTerm(dwc, v.getTerm(dwc));
    }
    // add taxon in any case - we can swap status of a synonym during normalization
    t.taxon = interpretTaxon(v);
    // a synonym by status?
    // we deal with relations via DwcTerm.acceptedNameUsageID and DwcTerm.acceptedNameUsage during relation insertion
    if(SafeParser.parse(SynonymStatusParser.PARSER, v.getTerm(DwcTerm.taxonomicStatus)).orElse(false)) {
      t.synonym = new NeoTaxon.Synonym();
    }

    return t;
  }

  private List<NameAct> interpretActs(VerbatimRecord v) {
    List<NameAct> acts = Lists.newArrayList();

    // publication of name
    if (v.hasTerm(DwcTerm.namePublishedInID) || v.hasTerm(DwcTerm.namePublishedIn)) {
      NameAct act = new NameAct();
      act.setType(NomActType.DESCRIPTION);
      act.setReferenceKey(
          lookupReferenceTitleID(
            v.getTerm(DwcTerm.namePublishedInID),
            v.getTerm(DwcTerm.namePublishedIn)
          ).getKey()
      );
      acts.add(act);
    }
    return acts;
  }

  void interpretBibliography(NeoTaxon t) {
    if (t.verbatim.hasExtension(GbifTerm.Reference)) {
      for (TermRecord rec : t.verbatim.getExtensionRecords(GbifTerm.Reference)) {
        //TODO: create / lookup references
        LOG.debug("Reference extension not implemented, but record found: {}", rec.getFirst(DcTerm.identifier, DcTerm.title, DcTerm.bibliographicCitation));
      }
    }
  }

  void interpretDistributions(NeoTaxon t) {
    if (t.verbatim.hasExtension(GbifTerm.Distribution)) {
      for (TermRecord rec : t.verbatim.getExtensionRecords(GbifTerm.Distribution)) {
        // try to figure out an area
        if (rec.hasTerm(DwcTerm.locationID)) {
          for (String loc : MULTIVAL.split(rec.get(DwcTerm.locationID))) {
            AreaParser.Area area = SafeParser.parse(AreaParser.PARSER, loc).orNull();
            if (area != null) {
              addDistribution(t, area.area, area.standard, rec);
            } else {
              t.addIssue(Issue.DISTRIBUTION_AREA_INVALID);
            }
          }

        } else if(rec.hasTerm(DwcTerm.countryCode) || rec.hasTerm(DwcTerm.country)) {
          for (String craw : MULTIVAL.split(rec.getFirst(DwcTerm.countryCode, DwcTerm.country))) {
            Country country = SafeParser.parse(CountryParser.PARSER, craw).orNull();
            if (country != null) {
              addDistribution(t, country.getIso2LetterCode(), Gazetteer.ISO, rec);
            } else {
              t.addIssue(Issue.DISTRIBUTION_COUNTRY_INVALID);
            }
          }

        } else if(rec.hasTerm(DwcTerm.locality)) {
          addDistribution(t, rec.get(DwcTerm.locality), Gazetteer.TEXT, rec);

        } else {
          t.addIssue(Issue.DISTRIBUTION_INVALID);
        }
      }
    }
  }

  void addDistribution(NeoTaxon t, String area, Gazetteer standard, TermRecord rec) {
    Distribution d = new Distribution();
    d.setArea(area);
    d.setGazetteer(standard);
    addReferences(d, rec);
    //TODO: parse status!!!
    d.setStatus(DistributionStatus.NATIVE);
    t.distributions.add(d);
  }

  private void addReferences(Referenced obj, TermRecord v) {
    if (v.hasTerm(DcTerm.source)) {
      //TODO: test for multiple
      obj.addReferenceKey(lookupReferenceTitleID(null, v.get(DcTerm.source)).getKey());
    }
  }

  void interpretVernacularNames(NeoTaxon t) {
    if (t.verbatim.hasExtension(GbifTerm.VernacularName)) {
      for (TermRecord rec : t.verbatim.getExtensionRecords(GbifTerm.VernacularName)) {
        VernacularName vn = new VernacularName();
        vn.setName(rec.get(DwcTerm.vernacularName));
        vn.setLanguage(SafeParser.parse(LanguageParser.PARSER, rec.get(DcTerm.language)).orNull());
        vn.setCountry(SafeParser.parse(CountryParser.PARSER, rec.getFirst(DwcTerm.countryCode, DwcTerm.country)).orNull());
        addReferences(vn, rec);
        addAndTransliterate(t, vn);
      }
    }
  }

  private Taxon interpretTaxon(VerbatimRecord v) {
    // and it keeps the taxonID for resolution of relations
    Taxon t = new Taxon();
    t.setId(v.getFirst(DwcTerm.taxonID, DwcaReader.DWCA_ID));

    t.setStatus(SafeParser.parse(TaxonomicStatusParser.PARSER, v.getTerm(DwcTerm.taxonomicStatus))
        .orElse(TaxonomicStatus.DOUBTFUL)
    );
    //TODO: interpret all of Taxon via new dwca extension
    t.setAccordingTo(v.getTerm(DwcTerm.nameAccordingTo));
    t.setAccordingToDate(null);
    t.setOrigin(Origin.SOURCE);
    t.setDatasetUrl(SafeParser.parse(UriParser.PARSER, v.getTerm(DcTerm.references)).orNull(Issue.URL_INVALID, t.getIssues()));
    t.setFossil(null);
    t.setRecent(null);
    //t.setLifezones();
    t.setSpeciesEstimate(null);
    t.setSpeciesEstimateReferenceKey(null);
    t.setRemarks(v.getTerm(DwcTerm.taxonRemarks));
    return t;
  }

  private Name interpretName(VerbatimRecord v) {
    //TODO: or use v.getID() ???
    //TODO: should we also get remarks through an extension, e.g. species profile or a nomenclature extension?
    return interpretName(v.getFirst(DwcTerm.scientificNameID, DwcTerm.taxonID, DwcaReader.DWCA_ID),
        v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank),
        v.getTerm(DwcTerm.scientificName),
        v.getTerm(DwcTerm.scientificNameAuthorship),
        v.getFirst(GbifTerm.genericName, DwcTerm.genus),
        v.getTerm(DwcTerm.subgenus),
        v.getTerm(DwcTerm.specificEpithet),
        v.getTerm(DwcTerm.infraspecificEpithet),
        v.getTerm(DwcTerm.nomenclaturalCode),
        v.getTerm(DwcTerm.nomenclaturalStatus),
        v.getTerm(DcTerm.references),
        v.getTerm(DwcTerm.nomenclaturalStatus)
    );
  }

}
