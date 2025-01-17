package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeTestBase;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class ReferenceTest extends SerdeTestBase<Reference> {
  
  public ReferenceTest() {
    super(Reference.class);
  }
  
  @Override
  public Reference genTestValue() throws Exception {
    return TestEntityGenerator.newReference();
  }
  
  @Test
  public void testAbstract() throws Exception {
    String json = ApiModule.MAPPER.writeValueAsString(genTestValue());
    assertTrue(json.contains("\"abstract\""));
    assertFalse(json.contains("\"abstrct\""));
  }
  
  @Test
  public void testEquals() {
    Reference r1 = new Reference();
    Reference r2 = new Reference();
    assertEquals(r1, r2);
    
    r1=TestEntityGenerator.newReference("Bernd im Urlaub");
    r2=TestEntityGenerator.newReference("Franzi im Urlaub");
    assertNotEquals(r1, r2);
    
    r2=TestEntityGenerator.newReference("Bernd im Urlaub");
    r2.setId(r1.getId());
    TestEntityGenerator.nullifyUserDate(r1);
    TestEntityGenerator.nullifyUserDate(r2);
    assertEquals(r1, r2);
  }
}