package org.col.admin.importer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.col.admin.config.NormalizerConfig;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NeoDbFactory;
import org.col.admin.importer.neo.NotUniqueRuntimeException;
import org.col.admin.importer.neo.model.Labels;
import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.model.NeoTaxon;
import org.col.admin.importer.neo.model.RankedName;
import org.col.admin.importer.neo.model.RelType;
import org.col.admin.importer.neo.printer.GraphFormat;
import org.col.admin.importer.neo.printer.PrinterUtils;
import org.col.admin.matching.NameIndexFactory;
import org.col.api.model.Dataset;
import org.col.api.model.Distribution;
import org.col.api.model.NameRelation;
import org.col.api.model.Reference;
import org.col.api.model.VerbatimRecord;
import org.col.api.model.VernacularName;
import org.col.api.vocab.DataFormat;
import org.col.api.vocab.DistributionStatus;
import org.col.api.vocab.Gazetteer;
import org.col.api.vocab.Issue;
import org.col.api.vocab.Language;
import org.col.api.vocab.NomRelType;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.schema.IndexDefinition;
import org.neo4j.graphdb.schema.Schema;
import org.neo4j.helpers.collection.Iterables;
import org.neo4j.helpers.collection.Iterators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NormalizerDwcaIT {

  private NeoDb store;
  private NormalizerConfig cfg;
  private Path dwca;

  @Before
  public void initCfg() throws Exception {
    cfg = new NormalizerConfig();
    cfg.archiveDir = Files.createTempDir();
    cfg.scratchDir = Files.createTempDir();
  }

  @After
  public void cleanup() throws Exception {
    if (store != null) {
      store.closeAndDelete();
    }
    FileUtils.deleteQuietly(cfg.archiveDir);
    FileUtils.deleteQuietly(cfg.scratchDir);
  }

  /**
   * Normalizes a dwca from the dwca test resources and checks its printed txt tree against the
   * expected tree
   *
   * @param datasetKey
   * @return
   * @throws Exception
   */
  private void normalize(int datasetKey) throws Exception {
    URL dwcaUrl = getClass().getResource("/dwca/" + datasetKey);
    normalize(Paths.get(dwcaUrl.toURI()));
  }

  private void normalize(URI url) throws Exception {
    // download an decompress
    ExternalSourceUtil.consumeSource(url, this::normalize);
  }

  private void normalize(Path dwca) {
    try {
      store = NeoDbFactory.create(1, cfg);
      Dataset d = new Dataset();
      d.setKey(1);
      d.setDataFormat(DataFormat.DWCA);
      store.put(d);
      Normalizer norm = new Normalizer(store, dwca, NameIndexFactory.passThru());
      norm.call();

      // reopen
      store = NeoDbFactory.open(1, cfg);

    } catch (IOException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  VerbatimRecord vByID(String id) {
    NeoTaxon t = byID(id);
    return store.getVerbatim(t.name.getVerbatimKey());
  }

  NeoTaxon byID(String id) {
    Node n = store.byID(id);
    return store.get(n);
  }

  NeoTaxon byName(String name) {
    return byName(name, null);
  }

  NeoTaxon byName(String name, @Nullable String author) {
    List<Node> nodes = store.byScientificName(name);
    // filter by author
    if (author != null) {
      nodes.removeIf(n -> !author.equalsIgnoreCase(NeoProperties.getAuthorship(n)));
    }

    if (nodes.isEmpty()) {
      throw new NotFoundException();
    }
    if (nodes.size() > 1) {
      throw new NotUniqueRuntimeException("scientificName", name);
    }
    return store.get(nodes.get(0));
  }

  @Test
  public void testBdjCsv() throws Exception {
    normalize(17);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon t = byID("1099-sp16");
      assertFalse(t.isSynonym());
      assertEquals("Pinus palustris Mill.", t.name.canonicalNameComplete());
      assertEquals(URI.create("http://dx.doi.org/10.3897/BDJ.2.e1099"), t.taxon.getDatasetUrl());
    }
  }

  @Test
  public void testPublishedIn() throws Exception {
    normalize(0);

    for (Reference r : store.refList()) {
      System.out.println(r);
    }

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon trametes_modesta = byID("324805");
      assertFalse(trametes_modesta.isSynonym());

      Reference pubIn = store.refById(trametes_modesta.name.getPublishedInId());
      assertEquals("Norw. Jl Bot. 19: 236 (1972)", pubIn.getCitation());
      assertNotNull(pubIn.getId());

      NeoTaxon Polystictus_substipitatus = byID("140283");
      assertTrue(Polystictus_substipitatus.isSynonym());
      assertTrue(Polystictus_substipitatus.synonym.getStatus().isSynonym());
      pubIn = store.refById(Polystictus_substipitatus.name.getPublishedInId());
      assertEquals("Syll. fung. (Abellini) 21: 318 (1912)", pubIn.getCitation());

      NeoTaxon Polyporus_modestus = byID("198666");
      assertTrue(Polyporus_modestus.isSynonym());
      assertTrue(Polyporus_modestus.synonym.getStatus().isSynonym());
      pubIn = store.refById(Polyporus_modestus.name.getPublishedInId());
      assertEquals("Linnaea 5: 519 (1830)", pubIn.getCitation());
    }
  }

  @Test
  public void testSupplementary() throws Exception {
    normalize(24);

    // verify results
    try (Transaction tx = store.getNeo().beginTx()) {
      // check species name
      NeoTaxon t = byID("1000");
      assertEquals("Crepis pulchra", t.name.getScientificName());

      // check vernaculars
      Map<Language, String> expV = jersey.repackaged.com.google.common.collect.Maps.newHashMap();
      expV.put(Language.GERMAN, "Schöner Pippau");
      expV.put(Language.ENGLISH, "smallflower hawksbeard");
      assertEquals(expV.size(), t.vernacularNames.size());
      for (VernacularName vn : t.vernacularNames) {
        assertEquals(expV.remove(vn.getLanguage()), vn.getName());
      }
      assertTrue(expV.isEmpty());

      // check distributions
      Set<Distribution> expD = Sets.newHashSet();
      expD.add(dist(Gazetteer.TEXT, "All of Austria and the alps", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "DE", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "FR", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "DK", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "GB", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "NG", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "KE", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.TDWG, "AGS", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.FAO, "37.4.1", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.TDWG, "MOR-MO", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.TDWG, "MOR-CE", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.TDWG, "MOR-ME", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.TDWG, "CPP", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.TDWG, "NAM", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "IT-82", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "ES-CN", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "FR-H", DistributionStatus.NATIVE));
      expD.add(dist(Gazetteer.ISO, "FM-PNI", DistributionStatus.NATIVE));

      assertEquals(expD.size(), t.distributions.size());
      // remove keys before we check equality
      t.distributions.forEach(d -> {
        d.setKey(null);
        d.setVerbatimKey(null);
      });
      Set<Distribution> imported = Sets.newHashSet(t.distributions);

      Sets.SetView<Distribution> diff = Sets.difference(expD, imported);
      for (Distribution d : diff) {
        System.out.println(d);
      }
      assertEquals(expD, imported);
    }
  }

  @Test
  public void chainedBasionyms() throws Exception {
    normalize(28);
    debug();
    // verify results
    try (Transaction tx = store.getNeo().beginTx()) {
      // 1->2->1
      // should be: 1->2
      NeoTaxon t1 = byID("1");
      NeoTaxon t2 = byID("2");

      assertEquals(1, t1.node.getDegree(RelType.HAS_BASIONYM));
      assertEquals(1, t2.node.getDegree(RelType.HAS_BASIONYM));
      assertEquals(t2.node,
          t1.node.getSingleRelationship(RelType.HAS_BASIONYM, Direction.OUTGOING).getEndNode());
      assertNotNull(t1.name.getHomotypicNameId());
      assertEquals(t2.name.getHomotypicNameId(), t1.name.getHomotypicNameId());

      // 10->11->12->10, 13->11
      // should be: 10,13->11 12
      NeoTaxon t10 = byID("10");
      NeoTaxon t11 = byID("11");
      NeoTaxon t12 = byID("12");
      NeoTaxon t13 = byID("13");

      assertEquals(1, t10.node.getDegree(RelType.HAS_BASIONYM));
      assertEquals(2, t11.node.getDegree(RelType.HAS_BASIONYM));
      assertEquals(0, t12.node.getDegree(RelType.HAS_BASIONYM));
      assertEquals(1, t13.node.getDegree(RelType.HAS_BASIONYM));
      assertEquals(t11.node, t10.node
          .getSingleRelationship(RelType.HAS_BASIONYM, Direction.OUTGOING).getOtherNode(t10.node));
      assertEquals(t11.node, t13.node
          .getSingleRelationship(RelType.HAS_BASIONYM, Direction.OUTGOING).getOtherNode(t13.node));
      assertNull(t12.name.getHomotypicNameId());
      assertEquals(t10.name.getId(), t11.name.getHomotypicNameId());
      assertEquals(t10.name.getId(), t10.name.getHomotypicNameId());
      assertEquals(t10.name.getId(), t13.name.getHomotypicNameId());
    }
  }

  /**
   * https://github.com/Sp2000/colplus-backend/issues/69
   */
  @Test
  public void testIcznLists() throws Exception {
    normalize(26);

    // verify results
    try (Transaction tx = store.getNeo().beginTx()) {
      // check species name
      NeoTaxon t = byID("10156");
      assertEquals("'Prosthète'", t.name.getScientificName());
    }
  }

  private Distribution dist(Gazetteer standard, String area, DistributionStatus status) {
    Distribution d = new Distribution();
    d.setArea(area);
    d.setGazetteer(standard);
    d.setStatus(status);
    return d;
  }

  @Test
  public void testNeoIndices() throws Exception {
    normalize(1);

    Set<String> taxonIndices = Sets.newHashSet();
    taxonIndices.add(NeoProperties.ID);
    taxonIndices.add(NeoProperties.SCIENTIFIC_NAME);
    try (Transaction tx = store.getNeo().beginTx()) {
      Schema schema = store.getNeo().schema();
      for (IndexDefinition idf : schema.getIndexes(Labels.TAXON)) {
        List<String> idxProps = Iterables.asList(idf.getPropertyKeys());
        assertTrue(idxProps.size() == 1);
        assertTrue(taxonIndices.remove(idxProps.get(0)));
      }

      // 1001, Crepis bakeri Greene
      assertNotNull(
          Iterators.singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.ID, "1001")));
      assertNotNull(Iterators.singleOrNull(
          store.getNeo().findNodes(Labels.TAXON, NeoProperties.SCIENTIFIC_NAME, "Crepis bakeri")));

      assertNull(Iterators
          .singleOrNull(store.getNeo().findNodes(Labels.TAXON, NeoProperties.ID, "x1001")));
      assertNull(Iterators.singleOrNull(
          store.getNeo().findNodes(Labels.TAXON, NeoProperties.SCIENTIFIC_NAME, "xCrepis bakeri")));
    }
  }

  @Test
  public void testIdRels() throws Exception {
    normalize(1);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon u1 = byID("1006");
      NeoTaxon u2 = byName("Leontodon taraxacoides", "(Vill.) Mérat");

      assertEquals(u1, u2);

      NeoTaxon bas = byName("Leonida taraxacoida");
      assertEquals(u2.name.getHomotypicNameId(), bas.name.getHomotypicNameId());

      NeoTaxon syn = byName("Leontodon leysseri");
      assertTrue(syn.synonym.getStatus().isSynonym());
    }
  }

  private void debug() throws Exception {
    PrinterUtils.printTree(store.getNeo(), new PrintWriter(System.out), GraphFormat.TEXT, true);

    // dump graph as DOT file for debugging
    File dotFile = new File("graphs/dbugtree.dot");
    Files.createParentDirs(dotFile);
    Writer writer = new FileWriter(dotFile);
    PrinterUtils.printTree(store.getNeo(), writer, GraphFormat.DOT, true);
    writer.close();
    System.out.println("Wrote graph to " + dotFile.getAbsolutePath());
  }

  @Test
  public void testProParte() throws Exception {
    normalize(8);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon syn = byID("1001");
      assertNotNull(syn.synonym);

      Map<String, String> expectedAccepted = Maps.newHashMap();
      expectedAccepted.put("1000", "Calendula arvensis");
      expectedAccepted.put("10000", "Calendula incana subsp. incana");
      expectedAccepted.put("10002", "Calendula incana subsp. maderensis");

      for (RankedName acc : store.accepted(syn.node)) {
        assertEquals(expectedAccepted.remove(NeoProperties.getID(acc.node)), acc.name);
      }
      assertTrue(expectedAccepted.isEmpty());
    }
  }

  @Test
  public void testHomotypic() throws Exception {
    normalize(29);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon annua1 = byID("4");
      NeoTaxon annua2 = byID("5");
      assertEquals(annua1.name.getHomotypicNameId(), annua2.name.getHomotypicNameId());
      NeoTaxon reptans1 = byID("7");
      NeoTaxon reptans2 = byID("8");
      assertEquals(reptans1.name.getHomotypicNameId(), reptans2.name.getHomotypicNameId());
      assertFalse(annua1.name.getHomotypicNameId().equals(reptans1.name.getHomotypicNameId()));

      List<String> homos = Lists.newArrayList("4", "5", "7", "8");
      store.all().forEach(t -> {
        if (!homos.contains(t.name.getId())) {
          assertNull(t.name.getHomotypicNameId());
        }
      });
    }
  }

  @Test
  public void testNameRelations() throws Exception {
    normalize(30);

    try (Transaction tx = store.getNeo().beginTx()) {
      NeoTaxon t10 = byID("10");
      NeoTaxon t11 = byID("11");
      assertEquals(t10.name.getHomotypicNameId(), t11.name.getHomotypicNameId());
      List<NameRelation> rels = store.relations(t10.node);
      assertEquals(1, rels.size());
      assertEquals(NomRelType.BASED_ON, rels.get(0).getType());
    }
  }

  @Test
  public void testIssueFlagging() throws Exception {
    normalize(31);

    try (Transaction tx = store.getNeo().beginTx()) {
      VerbatimRecord t9 = vByID("9");
      // TODO: fix https://github.com/Sp2000/colplus-backend/issues/118
      // assertTrue(t9.hasIssue(Issue.PUBLISHED_BEFORE_GENUS));
      assertFalse(t9.hasIssue(Issue.PARENT_NAME_MISMATCH));

      VerbatimRecord t11 = vByID("11");
      assertTrue(t11.hasIssue(Issue.PARENT_NAME_MISMATCH));

      VerbatimRecord t103 = vByID("103");
      assertFalse(t103.hasIssue(Issue.PUBLISHED_BEFORE_GENUS));
      assertFalse(t103.hasIssue(Issue.PARENT_NAME_MISMATCH));

      VerbatimRecord t104 = vByID("104");
      assertTrue(t104.hasIssue(Issue.PUBLISHED_BEFORE_GENUS));
    }
  }

  @Test
  @Ignore("No testing yet")
  public void testWormsParents() throws Exception {
    normalize(32);
    print("worms", GraphFormat.DOT, false);

    try (Transaction tx = store.getNeo().beginTx()) {
    }
  }

  @Test
  @Ignore
  public void testExternal() throws Exception {
    //normalize(URI.create("http://www.marinespecies.org/dwca/WoRMS_DwC-A.zip"));
    normalize(Paths.get("/Users/markus/Downloads/ipni-dwca"));
    //normalize(URI.create("http://www.marinespecies.org/dwca/WoRMS_DwC-A.zip"));
    // print("Diversity", GraphFormat.TEXT, false);
  }

  void print(String id, GraphFormat format, boolean file) throws Exception {
    // dump graph as DOT file for debugging
    File dotFile = new File("graphs/tree-dwca-" + id + "." + format.suffix);
    Files.createParentDirs(dotFile);
    Writer writer;
    if (file) {
      writer = new FileWriter(dotFile);
    } else {
      writer = new StringWriter();
    }
    PrinterUtils.printTree(store.getNeo(), writer, format);
    writer.close();

    if (file) {
      System.out.println("Wrote graph to " + dotFile.getAbsolutePath());
    } else {
      System.out.println(writer.toString());
    }
  }

}
