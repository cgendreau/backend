package org.col.api;

import org.col.api.model.Name;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 */
public class NameTest {

  @Test
  public void isConsistent() throws Exception {
    Name n = new Name();
    assertTrue(n.isConsistent());

    n.setUninomial("Asteraceae");
    n.setRank(Rank.FAMILY);
    assertTrue(n.isConsistent());
    for (Rank r : Rank.values()) {
      if (r.isSuprageneric()) {
        n.setRank(r);
        assertTrue(n.isConsistent());
      }
    }

    n.setRank(Rank.GENUS);
    assertTrue(n.isConsistent());

    n.setUninomial("Abies");
    assertTrue(n.isConsistent());

    n.getCombinationAuthorship().getAuthors().add("Mill.");
    assertTrue(n.isConsistent());

    n.setRank(Rank.SPECIES);
    assertFalse(n.isConsistent());

    n.setInfragenericEpithet("Pinoideae");
    assertFalse(n.isConsistent());

    n.setRank(Rank.SUBGENUS);
    // uninomial is not allowed!
    assertFalse(n.isConsistent());

    n.setUninomial(null);
    n.setGenus("Abies");
    assertTrue(n.isConsistent());

    n.setSpecificEpithet("alba");
    assertFalse(n.isConsistent());

    n.setRank(Rank.SPECIES);
    assertTrue(n.isConsistent());

    n.setInfragenericEpithet(null);
    assertTrue(n.isConsistent());

    n.setRank(Rank.VARIETY);
    assertFalse(n.isConsistent());

    n.setInfraspecificEpithet("alpina");
    assertTrue(n.isConsistent());

    n.setRank(Rank.SPECIES);
    assertFalse(n.isConsistent());

    n.setRank(Rank.UNRANKED);
    assertTrue(n.isConsistent());

    n.setSpecificEpithet(null);
    assertFalse(n.isConsistent());
  }

  /**
   * logger_name:org.col.admin.task.importer.acef.AcefInterpreter
   * message:Inconsistent name W-Msc-1005056: null/W-Msc-1005056[SCIENTIFIC] G:Marmorana IG:Ambigua S:saxetana R:SUBSPECIES IS:forsythi A:null BA:null
   */
  @Test
  public void isConsistent2() throws Exception {
    Name n = new Name();
    n.setType(NameType.SCIENTIFIC);
    n.setGenus("Marmorana");
    n.setInfragenericEpithet("Ambigua");
    n.setSpecificEpithet("saxetana");
    n.setInfraspecificEpithet("forsythi");
    n.setRank(Rank.SUBSPECIES);

    assertTrue(n.isConsistent());
  }

  @Test
  public void conversionAndFormatting() throws Exception {
    Name n = new Name();
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setNotho(NamePart.SPECIFIC);
    n.setRank(Rank.SUBSPECIES);
    assertEquals("Abies × alba subsp.", n.canonicalNameComplete());

    n.setInfraspecificEpithet("alpina");
    n.getCombinationAuthorship().setYear("1999");
    n.getCombinationAuthorship().getAuthors().add("L.");
    n.getCombinationAuthorship().getAuthors().add("DC.");
    n.getBasionymAuthorship().setYear("1899");
    n.getBasionymAuthorship().getAuthors().add("Lin.");
    n.getBasionymAuthorship().getAuthors().add("Deca.");
    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999", n.canonicalNameComplete());
  }
}