package org.col.admin.importer.dwca;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.Lists;
import org.col.admin.importer.InterpreterBase;
import org.col.admin.importer.MappingFlags;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.model.NeoNameRel;
import org.col.admin.importer.neo.model.NeoUsage;
import org.col.admin.importer.neo.model.RelType;
import org.col.admin.importer.reference.ReferenceFactory;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.col.parser.EnumNote;
import org.col.parser.NomRelTypeParser;
import org.col.parser.SafeParser;
import org.col.parser.TaxonomicStatusParser;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interprets a verbatim record and transforms it into a name, taxon and unique references.
 */
public class DwcInterpreter extends InterpreterBase {
  private static final Logger LOG = LoggerFactory.getLogger(DwcInterpreter.class);
  private static final EnumNote<TaxonomicStatus> NO_STATUS = new EnumNote<>(TaxonomicStatus.PROVISIONALLY_ACCEPTED, null);

  private final MappingFlags mappingFlags;

  public DwcInterpreter(Dataset dataset, MappingFlags mappingFlags, ReferenceFactory refFactory, NeoDb store) {
    super(dataset, refFactory, store);
    this.mappingFlags = mappingFlags;
  }

  public Optional<NeoUsage> interpret(VerbatimRecord v) {
    // name
    Optional<NameAccordingTo> nat = interpretName(v);
    if (nat.isPresent()) {
      EnumNote<TaxonomicStatus> status = SafeParser.parse(TaxonomicStatusParser.PARSER, v.get(DwcTerm.taxonomicStatus)).orElse(NO_STATUS);
      // usage
      NeoUsage u;
      // a synonym by status?
      // we deal with relations via DwcTerm.acceptedNameUsageID and DwcTerm.acceptedNameUsage
      // during relation insertion
      if (status.val.isSynonym()) {
        u = NeoUsage.createSynonym(Origin.SOURCE, nat.get().getName(), status.val);
      } else {
        u = NeoUsage.createTaxon(Origin.SOURCE, nat.get().getName(), false);
        interpretTaxon(u, v, status);
      }
  
      // shared usage props
      u.setId(v.getFirstRaw(DwcTerm.taxonID, DwcaReader.DWCA_ID));
      u.setVerbatimKey(v.getKey());
      u.usage.setAccordingTo(v.get(DwcTerm.nameAccordingTo));
      u.usage.addAccordingTo(nat.get().getAccordingTo());
      u.homotypic = TaxonomicStatusParser.isHomotypic(status);

      // flat classification
      u.classification = new Classification();
      for (DwcTerm dwc : DwcTerm.HIGHER_RANKS) {
        u.classification.setByTerm(dwc, v.get(dwc));
      }
      return Optional.of(u);
    }
    return Optional.empty();
  }
  
  Optional<NeoNameRel> interpretNameRelations(VerbatimRecord rec) {
    NeoNameRel rel = new NeoNameRel();
    SafeParser<NomRelType> type = SafeParser.parse(NomRelTypeParser.PARSER, rec.get(ColDwcTerm.relationType));
    if (type.isPresent()) {
      rel.setType(RelType.from(type.get()));
      rel.setNote(rec.get(ColDwcTerm.relationRemarks));
      if (rec.hasTerm(ColDwcTerm.publishedIn)) {
        Reference ref = refFactory.fromDWC(rec.get(ColDwcTerm.publishedInID), rec.get(ColDwcTerm.publishedIn), null, rec);
        rel.setReferenceId(ref.getId());
      }
      return Optional.of(rel);
    }
    return Optional.empty();
  }
  
  List<Reference> interpretReference(VerbatimRecord rec) {
    return Lists.newArrayList(refFactory.fromDC(rec.getRaw(DcTerm.identifier),
        rec.get(DcTerm.bibliographicCitation),
        rec.get(DcTerm.creator),
        rec.get(DcTerm.date),
        rec.get(DcTerm.title),
        rec.get(DcTerm.source),
        rec
    ));
  }
  
  List<Distribution> interpretDistribution(VerbatimRecord rec) {
    // try to figure out an area
    if (rec.hasTerm(DwcTerm.locationID)) {
      return createDistributions(Gazetteer.ISO,
          rec.getRaw(DwcTerm.locationID),
          rec.get(DwcTerm.occurrenceStatus),
          rec, this::setReference);
      
    } else if (rec.hasTerm(DwcTerm.countryCode) || rec.hasTerm(DwcTerm.country)) {
      return createDistributions(Gazetteer.ISO,
          rec.getFirst(DwcTerm.countryCode, DwcTerm.country),
          rec.get(DwcTerm.occurrenceStatus),
          rec, this::setReference);
      
    } else if (rec.hasTerm(DwcTerm.locality)) {
      return createDistributions(Gazetteer.TEXT,
          rec.get(DwcTerm.locality),
          rec.get(DwcTerm.occurrenceStatus),
          rec, this::setReference);
      
    } else {
      rec.addIssue(Issue.DISTRIBUTION_INVALID);
      return Collections.emptyList();
    }
  }
  
  List<VernacularName> interpretVernacularName(VerbatimRecord rec) {
    return super.interpretVernacular(rec,
        this::setReference,
        DwcTerm.vernacularName,
        null,
        DcTerm.language,
        DwcTerm.countryCode, DwcTerm.country
    );
  }
  
  List<Description> interpretDescription(VerbatimRecord rec) {
    return interpretDescription(rec, this::setReference,
        DcTerm.description,
        DcTerm.type,
        DcTerm.language);
  }
  
  List<Media> interpretMedia(VerbatimRecord rec) {
    return interpretMedia(rec, this::setReference,
        DcTerm.type,
        DcTerm.identifier,
        DcTerm.references,
        DcTerm.license,
        DcTerm.creator,
        DcTerm.created,
        DcTerm.title,
        DcTerm.format);
  }
  
  /**
   * Reads the dc:source citation string and looks up or creates a new reference.
   * Sets the reference id of the referenced object.
   * @param obj
   * @param v
   */
  private void setReference(Referenced obj, VerbatimRecord v) {
    if (v.hasTerm(DcTerm.source)) {
      Reference ref = refFactory.fromCitation(null, v.get(DcTerm.source), v);
      if (ref != null) {
        if (ref.getVerbatimKey() == null) {
          // create new reference with verbatim key, we've never seen this before!
          ref.setVerbatimKey(v.getKey());
          store.create(ref);
        }
        obj.setReferenceId(ref.getId());
      }
    }
  }

  private void interpretTaxon(NeoUsage u, VerbatimRecord v, EnumNote<TaxonomicStatus> status) {
    Taxon tax = u.getTaxon();
    // this can be a synonym at this stage which the class does not accept
    tax.setProvisional(TaxonomicStatus.PROVISIONALLY_ACCEPTED == status.val || status.val.isSynonym());
    tax.setDatasetUrl(uri(v, Issue.URL_INVALID, DcTerm.references));
    tax.setFossil(null);
    tax.setRecent(null);
    // t.setLifezones();
    if (v.hasTerm(ColDwcTerm.speciesEstimate)) {
      Integer est = v.getInt(ColDwcTerm.speciesEstimate, Issue.ESTIMATES_INVALID);
      if (est != null && est != 0) {
        if (est < 0) {
          v.addIssue(Issue.ESTIMATES_INVALID);
        } else {
          tax.setSpeciesEstimate(est);
          if (v.hasTerm(ColDwcTerm.speciesEstimateReference)) {
            Reference ref = refFactory.fromCitation(null, v.get(ColDwcTerm.speciesEstimateReference), v);
            if (ref != null) {
              tax.setSpeciesEstimateReferenceId(ref.getId());
            }
          }
        }
      }
    }
    tax.setRemarks(v.get(DwcTerm.taxonRemarks));
  }
  
  private Optional<NameAccordingTo> interpretName(VerbatimRecord v) {
    Optional<NameAccordingTo> opt = interpretName(v.getFirstRaw(DwcTerm.taxonID, DwcaReader.DWCA_ID),
        v.getFirst(DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank), v.get(DwcTerm.scientificName),
        v.get(DwcTerm.scientificNameAuthorship),
        v.getFirst(GbifTerm.genericName, DwcTerm.genus), v.get(DwcTerm.subgenus),
        v.get(DwcTerm.specificEpithet), v.get(DwcTerm.infraspecificEpithet),
        v.get(DwcTerm.nomenclaturalCode), v.get(DwcTerm.nomenclaturalStatus),
        v.getRaw(DcTerm.references), v.get(DwcTerm.nomenclaturalStatus), v);
    
    // publishedIn
    if (opt.isPresent()) {
      Name n = opt.get().getName();
      if (v.hasTerm(DwcTerm.namePublishedInID) || v.hasTerm(DwcTerm.namePublishedIn)) {
        Reference ref = refFactory.fromDWC(v.getRaw(DwcTerm.namePublishedInID), v.get(DwcTerm.namePublishedIn), v.get(DwcTerm.namePublishedInYear), v);
        if (ref != null) {
          if (ref.getVerbatimKey() == null) {
            // create new reference with verbatim key, we've never seen this before!
            ref.setVerbatimKey(v.getKey());
            store.create(ref);
          }
          n.setPublishedInId(ref.getId());
          n.setPublishedInPage(ref.getPage());
        }
      }
    }
    return opt;
  }
  
}
