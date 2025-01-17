package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.NomRelType;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 *
 */
@SuppressWarnings("static-method")
public class NameRelationMapperTest extends MapperTestBase<NameRelationMapper> {
  
  private NameRelationMapper nameRelationMapper;
  
  public NameRelationMapperTest() {
    super(NameRelationMapper.class);
  }
  
  @Before
  public void init() {
    nameRelationMapper = testDataRule.getMapper(NameRelationMapper.class);
  }
  
  @Test
  public void roundtrip() throws Exception {
    NameRelation in = nullifyDate(newNameRelation());
    nameRelationMapper.create(in);
    assertNotNull(in.getKey());
    commit();
    List<NameRelation> outs = nameRelationMapper.listByName(in.getNameKey());
    assertEquals(1, outs.size());
    assertEquals(in, nullifyDate(outs.get(0)));
  }

  @Test
  public void sectorProcessable() throws Exception {
    SectorProcessableTestComponent.test(mapper(), DSID.of(Datasets.COL, 1));
  }

  @Test
  public void testListByName() throws Exception {
    // NB We have one pre-inserted (apple.sql) NameAct record associated with NAME2 and 3
    assertEquals(0, nameRelationMapper.listByName(NAME1).size());
    assertEquals(1, nameRelationMapper.listByName(NAME2).size());
    
    nameRelationMapper.create(newNameRelation());
    nameRelationMapper.create(newNameRelation(NomRelType.BASED_ON));
    nameRelationMapper.create(newNameRelation(NomRelType.CONSERVED));
    commit();
    List<NameRelation> nas = nameRelationMapper.listByName(NAME1);
    
    assertEquals(3, nas.size());
    
    assertEquals(1, nameRelationMapper.listByName(NAME2).size());
    assertEquals(3, nameRelationMapper.listByRelatedName(NAME2).size());
  }
  
  private static NameRelation newNameRelation(NomRelType type) {
    NameRelation na = TestEntityGenerator.setUserDate(new NameRelation());
    na.setDatasetKey(DATASET11.getKey());
    na.setType(type);
    na.setNameId(NAME1.getId());
    na.setRelatedNameId(NAME2.getId());
    return na;
  }
  
  private static NameRelation newNameRelation() {
    return newNameRelation(NomRelType.REPLACEMENT_NAME);
  }
  
}
