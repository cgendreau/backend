package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Duplicate;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.*;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.db.PgSetupRule;
import life.catalogue.postgres.AuthorshipNormFunc;
import life.catalogue.postgres.PgCopyUtils;

import org.gbif.nameparser.api.Rank;

import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.ibatis.session.SqlSession;
import org.javers.common.collections.Lists;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.*;
import org.postgresql.jdbc.PgConnection;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import static org.junit.Assert.*;

public class DuplicateMapperTest {
  
  final static int datasetKey = 1000;
  DuplicateMapper mapper;
  SqlSession session;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @BeforeClass
  public static void setup() throws Exception {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      final DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
      pm.create(datasetKey, DatasetOrigin.EXTERNAL);
      pm.attach(datasetKey, DatasetOrigin.EXTERNAL);
      pm.createManagedSequences(datasetKey);
      pm.createManagedSequences(Datasets.COL);
      session.commit();
    }
  
    final AuthorshipNormFunc aFunc = new AuthorshipNormFunc(17);
    try (Connection c = pgSetupRule.connect()) {
      PgConnection pgc = (PgConnection) c;
      
      PgCopyUtils.copy(pgc, "dataset", "/duplicates/dataset.csv");
      PgCopyUtils.copy(pgc, "verbatim_1000", "/duplicates/verbatim.csv");
      PgCopyUtils.copy(pgc, "name_1000", "/duplicates/name.csv", null, ImmutableMap.<String, Function<String[], String>>of(
          "scientific_name_normalized", row -> SciNameNormalizer.normalize(row[5]),
          "authorship_normalized", aFunc::normAuthorship
          )
      );
      PgCopyUtils.copy(pgc, "name_usage_1000", "/duplicates/name_usage.csv");
      
      c.commit();
    }
  }
  
  @Before
  public void init() {
    session = PgSetupRule.getSqlSessionFactory().openSession(true);
    mapper = session.getMapper(DuplicateMapper.class);
  }
  
  @After
  public void destroy() {
    session.close();
  }
  
  @Test
  public void usagesWithDecisions() {
    List<Duplicate.UsageDecision> res = mapper.usagesByIds(datasetKey, Datasets.COL, Lists.immutableListOf("45", "46"));
    assertEquals(2, res.size());
    for (Duplicate.UsageDecision u : res) {
      assertFalse(u.getClassification().isEmpty());
      assertNull(u.getDecision());
    }
  
    // now with a decision
    DecisionMapper dm = session.getMapper(DecisionMapper.class);
  
    EditorialDecision d1 = TestEntityGenerator.setUser(new EditorialDecision());
    d1.setDatasetKey(Datasets.COL);
    d1.setSubjectDatasetKey(datasetKey);
    d1.setSubject(TreeMapperTest.nameref("45"));
    d1.setMode(EditorialDecision.Mode.UPDATE);
    dm.create(d1);
    
    res = mapper.usagesByIds(datasetKey, Datasets.COL, Lists.immutableListOf("45", "46"));
    assertEquals(2, res.size());
    for (Duplicate.UsageDecision u : res) {
      assertFalse(u.getClassification().isEmpty());
    }
    EditorialDecision d = res.get(0).getDecision();
    assertNotNull(d);
    assertNotNull(d.getKey());
    printDiff(DecisionMapperTest.removeCreatedProps(d), d1);
    assertEquals(DecisionMapperTest.removeCreatedProps(d), d1);
  }

  void printDiff(Object o1, Object o2) {
    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(o1, o2);
    System.out.println(diff);
  }

  @Test
  public void usagesByIds() {
    List<Duplicate.UsageDecision> res = mapper.usagesByIds(datasetKey, Datasets.COL, Lists.immutableListOf("55", "46"));
    assertEquals(2, res.size());
    for (Duplicate.UsageDecision u : res) {
      assertFalse(u.getClassification().isEmpty());
      assertNull(u.getDecision());
      assertNotNull(u.getUsage());
    }
  }
  
  @Test
  public void duplicates() {
    Set<TaxonomicStatus> status = new HashSet<>();
    status.add(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    List<Duplicate.Mybatis> dups = mapper.duplicates(MatchingMode.STRICT, 2, datasetKey, null, null, NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), status, false, null, null, null, false, Datasets.COL,
        new Page(0, 2));
    assertEquals(2, dups.size());
    for (Duplicate.Mybatis d : dups) {
      assertFalse(d.getUsages().isEmpty());
      assertNotNull(d.getKey());
    }
  
    // all accepted, so not different
    // https://github.com/Sp2000/colplus-backend/issues/456
    dups = mapper.duplicates(MatchingMode.STRICT, 2, datasetKey, null, null, NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), status, false, true, null, null, false, Datasets.COL,
        new Page(0, 2));
    assertEquals(2, dups.size());
    dups = mapper.duplicates(MatchingMode.STRICT, 2, datasetKey, null, null, NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), status, false, false, null, null, false, Datasets.COL,
        new Page(0, 2));
    assertEquals(0, dups.size());
    dups = mapper.duplicates(MatchingMode.STRICT, 2, datasetKey, null, null, NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), null, null, false, null, null, null, Datasets.COL,
        new Page(0, 2));
    assertEquals(1, dups.size());
    assertEquals("achillea nigra", dups.get(0).getKey());
    
    // https://github.com/Sp2000/colplus-backend/issues/457
    // Aspidoscelis deppii subsp. schizophorus
    dups = mapper.duplicates(MatchingMode.STRICT, 3, datasetKey, null, null, NameCategory.TRINOMIAL,
        Sets.newHashSet(Rank.SUBSPECIES), null, true, null, null, null, null, Datasets.COL,
        new Page(0, 5));
    assertEquals(1, dups.size());

    dups = mapper.duplicates(MatchingMode.FUZZY, 2, datasetKey, 999, null, null,
      null, null, true, null, null, null, null, null,
      new Page(0, 5));
    assertEquals(0, dups.size());

    dups = mapper.duplicates(MatchingMode.FUZZY, 2, datasetKey, null, 999, null,
      null, null, true, null, null, null, null, null,
      new Page(0, 5));
    assertEquals(0, dups.size());

    dups = mapper.duplicates(MatchingMode.FUZZY, 2, datasetKey, 999, 999, null,
      null, null, true, null, null, null, null, null,
      new Page(0, 5));
    assertEquals(0, dups.size());  }
  
  @Test
  public void duplicateNames() {
    List<Duplicate.Mybatis> dups = mapper.duplicateNames(MatchingMode.STRICT, 2, datasetKey,  NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), false, false, false, new Page(0, 2));
    assertEquals(2, dups.size());
    for (Duplicate.Mybatis d : dups) {
      assertFalse(d.getUsages().isEmpty());
      assertNotNull(d.getKey());
    }
  
    dups = mapper.duplicateNames(MatchingMode.STRICT, 2, datasetKey,  NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), false, true, true, new Page(0, 2));
    assertEquals(0, dups.size());
  
    // https://github.com/Sp2000/colplus-backend/issues/457
    // Achillea asplenifolia
    dups = mapper.duplicates(MatchingMode.STRICT, 2, datasetKey, null, null, NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES_AGGREGATE), null, true, null, null, null, null, Datasets.COL,
        new Page(0, 5));
    assertEquals(1, dups.size());
  }
  
}