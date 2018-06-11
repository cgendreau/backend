package org.col.db.dao;

import java.util.List;

import com.google.common.base.Preconditions;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Name;
import org.col.api.model.NameRelation;
import org.col.api.model.Page;
import org.col.api.model.ResultPage;
import org.col.api.vocab.NomRelType;
import org.col.api.vocab.TaxonomicStatus;
import org.col.db.NotFoundException;
import org.col.db.mapper.NameMapper;
import org.col.db.mapper.NameRelationMapper;
import org.col.db.mapper.SynonymMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NameDao {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameDao.class);

  private final SqlSession session;
  private final NameMapper nMapper;
  private final NameRelationMapper nrMapper;
  private final SynonymMapper sMapper;

  public NameDao(SqlSession sqlSession) {
    this.session = sqlSession;
    nMapper = session.getMapper(NameMapper.class);
    nrMapper = session.getMapper(NameRelationMapper.class);
    sMapper = session.getMapper(SynonymMapper.class);
  }

  public int count(int datasetKey) {
    return nMapper.count(datasetKey);
  }

  public ResultPage<Name> list(Integer datasetKey, Page page) {
    int total = nMapper.count(datasetKey);
    List<Name> result = nMapper.list(datasetKey, page);
    return new ResultPage<>(page, total, result);
  }

  public Integer lookupKey(String id, int datasetKey) throws NotFoundException {
    Integer key = nMapper.lookupKey(id, datasetKey);
    if (key == null) {
      throw NotFoundException.idNotFound(Name.class, datasetKey, id);
    }
    return key;
  }

  public Name get(Integer key) {
    Name result = nMapper.get(key);
    if (result == null) {
      throw NotFoundException.keyNotFound(Name.class, key);
    }
    return result;
  }

  public Name getBasionym(Integer key) {
    List<NameRelation> rels = nrMapper.listByType(key, NomRelType.BASIONYM);
    if (rels.size() == 1) {
      return nMapper.get(rels.get(0).getRelatedNameKey());
    } else if (rels.size() > 1) {
      throw new IllegalStateException("Multiple basionyms found for name " + key);
    }
    return null;
  }

  public Name get(String id, int datasetKey) {
    return get(lookupKey(id, datasetKey));
  }

  public void create(Name name) {
    nMapper.create(name);
    // this happens in the db but is not cascaded to the instance by the mapper
    // to avoid a reload of the instance from the db we do this manually here
    if (name.getHomotypicNameKey() == null) {
      name.setHomotypicNameKey(name.getKey());
    }
  }

  /**
   * Lists all homotypic synonyms based on the same homotypic group key
   */
  public List<Name> homotypicGroup(int key) {
    return nMapper.homotypicGroup(key);
  }

  /**
   * Lists all relations for a given name and type
   */
  public List<NameRelation> relations(int nameKey, NomRelType type) {
    return nrMapper.listByType(nameKey, type);
  }

  /**
   * Lists all relations for a given name
   */
  public List<NameRelation> relations(int nameKey) {
    return nrMapper.list(nameKey);
  }



  /**
   * Adds a new synonym link for an existing taxon and synonym name
   */
  public void addSynonym(int datasetKey, int nameKey, int taxonKey, TaxonomicStatus status, String accordingTo) {
    Preconditions.checkNotNull(status, "status must exist");
    sMapper.create(datasetKey, nameKey, taxonKey, status, accordingTo);
  }

}
