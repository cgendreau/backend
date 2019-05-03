package org.col.assembly;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableSet;
import org.apache.ibatis.session.*;
import org.col.api.model.*;
import org.col.api.vocab.*;
import org.col.dao.*;
import org.col.db.mapper.*;
import org.col.es.NameUsageIndexService;
import org.gbif.nameparser.api.NameType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.col.dao.DatasetImportDao.countMap;

/**
 * Syncs/imports source data for a given sector into the assembled catalogue
 */
public class SectorSync extends SectorRunnable {
  private static final Logger LOG = LoggerFactory.getLogger(SectorSync.class);
  private static Set<EntityType> COPY_DATA = ImmutableSet.of(
      EntityType.REFERENCE,
      EntityType.VERNACULAR,
      EntityType.DISTRIBUTION
  );
  private NamesTreeDao treeDao;
  
  public SectorSync(int sectorKey, SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao,
                    Consumer<SectorRunnable> successCallback,
                    BiConsumer<SectorRunnable, Exception> errorCallback, ColUser user) {
    super(sectorKey, factory, indexService, successCallback, errorCallback, user);
    treeDao = diDao.getTreeDao();
  }
  
  @Override
  void doWork() throws Exception {
    sync();
    metrics();
  }
  
  private void metrics() {
    try (SqlSession session = factory.openSession(true)) {
      SectorImportMapper mapper = session.getMapper(SectorImportMapper.class);
      final int key = sector.getKey();
      state.setDescriptionCount(mapper.countDescription(catalogueKey, key));
      state.setDistributionCount(mapper.countDistribution(catalogueKey, key));
      state.setMediaCount(mapper.countMedia(catalogueKey, key));
      state.setNameCount(mapper.countName(catalogueKey, key));
      state.setReferenceCount(mapper.countReference(catalogueKey, key));
      state.setTaxonCount(mapper.countTaxon(catalogueKey, key));
      state.setSynonymCount(mapper.countSynonym(catalogueKey, key));
      state.setVernacularCount(mapper.countVernacular(catalogueKey, key));
      state.setIssuesCount(countMap(Issue.class, mapper.countIssues(catalogueKey, key)));
  
      state.setDistributionsByGazetteerCount(countMap(Gazetteer.class, mapper.countDistributionsByGazetteer(catalogueKey, key)));
      state.setIssuesCount(countMap(Issue.class, mapper.countIssues(catalogueKey, key)));
      state.setMediaByTypeCount(countMap(MediaType.class, mapper.countMediaByType(catalogueKey, key)));
      state.setNameRelationsByTypeCount(countMap(NomRelType.class, mapper.countNameRelationsByType(catalogueKey, key)));
      state.setNamesByOriginCount(countMap(Origin.class, mapper.countNamesByOrigin(catalogueKey, key)));
      state.setNamesByRankCount(countMap(DatasetImportDao::parseRank, mapper.countNamesByRank(catalogueKey, key)));
      state.setNamesByStatusCount(countMap(NomStatus.class, mapper.countNamesByStatus(catalogueKey, key)));
      state.setNamesByTypeCount(countMap(NameType.class, mapper.countNamesByType(catalogueKey, key)));
      state.setTaxaByRankCount(countMap(DatasetImportDao::parseRank, mapper.countTaxaByRank(catalogueKey, key)));
      state.setUsagesByStatusCount(countMap(TaxonomicStatus.class, mapper.countUsagesByStatus(catalogueKey, key)));
      state.setVernacularsByLanguageCount(countMap(Language::fromIsoCode, mapper.countVernacularsByLanguage(catalogueKey, key)));

      try {
        treeDao.updateSectorTree(sector.getKey(), state.getAttempt());
        treeDao.updateSectorNames(sector.getKey(), state.getAttempt());
      } catch (IOException e) {
        LOG.error("Failed to print sector {} of catalogue {}", sector.getKey(), catalogueKey, e);
      }
    }
  }
  
  private void sync() throws InterruptedException {

    state.setState( SectorImport.State.DELETING);
    deleteOld();
    checkIfCancelled();

    state.setState( SectorImport.State.COPYING);
    processTree();
    checkIfCancelled();
  
    state.setState( SectorImport.State.RELINKING);
    relinkForeignChildren();
    relinkAttachedSectors();
  
    state.setState( SectorImport.State.INDEXING);
    indexService.indexSector(sector.getKey());
  
    state.setState( SectorImport.State.FINISHED);
  }
  
  private void relinkForeignChildren() {
    try (SqlSession session = factory.openSession(false)) {
      TaxonMapper tm = session.getMapper(TaxonMapper.class);
      MatchingDao mdao = new MatchingDao(session);
      for (Taxon t : foreignChildren) {
        List<Taxon> matches = mdao.matchSector(t.getName(), sector.getKey());
        if (matches.isEmpty()) {
          LOG.warn("{} with parent in sector {} cannot be rematched - becomes new root", t.getName(), sector.getKey());
          t.setParentId(null);
        } else {
          if (matches.size() > 1) {
            LOG.warn("{} with parent in sector {} matches multiple - pick first {}", t.getName(), sector.getKey());
          }
          t.setParentId(matches.get(0).getId());
        }
        tm.update(t);
      }
      session.commit();
    }
    
  }
  
  private void relinkAttachedSectors() {
    try (SqlSession session = factory.openSession(false)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      MatchingDao mdao = new MatchingDao(session);
      for (Sector s : childSectors) {
        List<Taxon> matches = mdao.matchSector(s.getTarget(), sector.getKey());
        if (matches.isEmpty()) {
          LOG.warn("Child sector {} cannot be rematched to synced sector {} - lost {}", s.getKey(), sector.getKey(), s.getTarget());
          s.getTarget().setId(null);
          //TODO: warn in sync status !!!
        } else if (matches.size() > 1) {
          LOG.warn("Child sector {} cannot be rematched to synced sector {} - multiple names like {}", s.getKey(), sector.getKey(), s.getTarget());
          s.getTarget().setId(null);
          //TODO: warn in sync status !!!
        } else {
          s.getTarget().setId(matches.get(0).getId());
        }
        sm.update(s);
      }
      session.commit();
    }
  }

  private void processTree() {
    try (SqlSession session = factory.openSession(ExecutorType.BATCH,false)) {
      NameUsageMapper um = session.getMapper(NameUsageMapper.class);
      final Set<String> blockedIds = decisions.values().stream()
          .filter(ed -> ed.getMode().equals(EditorialDecision.Mode.BLOCK))
          .map(ed -> ed.getSubject().getId())
          .collect(Collectors.toSet());
      LOG.info("Traverse taxon tree, blocking {} nodes", blockedIds.size());
      final TreeCopyHandler treeHandler = new TreeCopyHandler(session);
      um.processTree(datasetKey, null, sector.getSubject().getId(), blockedIds, true, false, treeHandler);
      session.commit();
    }
  }
  
  class TreeCopyHandler implements ResultHandler<NameUsageBase> {
    final SqlSession session;
    final SynonymMapper sMapper;
    int counter = 0;
    final Map<String, String> ids = new HashMap<>();
  
    TreeCopyHandler(SqlSession session) {
      this.session = session;
      sMapper = session.getMapper(SynonymMapper.class);
    }
    
    @Override
    public void handleResult(ResultContext<? extends NameUsageBase> ctxt) {
      NameUsageBase u = ctxt.getResultObject();
      u.setSectorKey(sector.getKey());
      u.getName().setSectorKey(sector.getKey());
  
      if (decisions.containsKey(u.getId())) {
        if (applyDecision(u, decisions.get(u.getId()))) {
          // skip this taxon, but include children
          // use taxons parent also as the parentID for this so children link one level up
          ids.put(u.getId(), ids.get(u.getParentId()));
          return;
        }
      }
      
      String parentID;
      // treat root node according to sector mode
      if (sector.getSubject().getId().equals(u.getId())) {
        if (sector.getMode() == Sector.Mode.MERGE) {
          // in merge mode the root node itself is not copied
          // but all child taxa should be linked to the sector target, so remember ID:
          ids.put(u.getId(), sector.getTarget().getId());
          return;
        }
        // we want to attach the root node under the sector target
        parentID = sector.getTarget().getId();
      } else {
        // all non root nodes have newly created parents
        parentID = ids.get(u.getParentId());
      }
      DatasetID parent = new DatasetID(catalogueKey, parentID);

      // copy usage with all associated information. This assigns a new id !!!
      DatasetID orig;
      if (u.isTaxon()) {
        orig = TaxonDao.copyTaxon(session, (Taxon) u, parent, user.getKey(), COPY_DATA, this::lookupReference);
      } else {
        orig = SynonymDao.copySynonym(session, (Synonym) u, parent, user.getKey(), this::lookupReference);
      }
      // remember old to new id mapping
      ids.put(orig.getId(), u.getId());
      
      // commit in batches
      if (counter++ % 1000 == 0) {
        session.commit();
      }
      state.setTaxonCount(counter);
    }
  
    /**
     * @return true if the taxon should be skipped
     */
    private boolean applyDecision(NameUsageBase u, EditorialDecision ed) {
      switch (ed.getMode()) {
        case BLOCK:
          throw new IllegalStateException("Blocked usage "+u.getId()+" should not have been traversed");
        case CHRESONYM:
          return true;
        case UPDATE:
          if (ed.getName() != null) {
            //TODO: update name
          }
          if (ed.getStatus() != null) {
            try {
              u.setStatus(ed.getStatus());
            } catch (IllegalArgumentException e) {
              LOG.warn("Cannot convert {} {} {} into {}", u.getName().getRank(), u.getStatus(), u.getName().canonicalNameComplete(), ed.getStatus(), e);
            }
          }
          if (u.isTaxon()) {
            Taxon t = (Taxon) u;
            if (ed.getLifezones() != null) {
              t.setLifezones(ed.getLifezones());
            }
            if (ed.getFossil() != null) {
              t.setFossil(ed.getFossil());
            }
            if (ed.getRecent() != null) {
              t.setRecent(ed.getRecent());
            }
          }
      }
      return false;
    }

    private String lookupReference(Reference ref) {
      if (ref != null) {
        //TODO: lookup existing refs from other sectors
        if (1 == 2) {
          ref.setDatasetKey(catalogueKey);
          ref.applyUser(user);
          // TODO: Create ref
        }
      }
      return null;
    }
  }
  
  private void deleteOld() {
    int count;
    try (SqlSession session = factory.openSession(true)) {
      NameUsageMapper m = session.getMapper(NameUsageMapper.class);
      count = m.deleteBySector(catalogueKey, sector.getKey());
      LOG.info("Deleted {} existing taxa with their synonyms and related information from sector {}", count, sector.getKey());
    }
    
    // TODO: delete orphaned names
    count = 0;
    LOG.info("Deleted {} orphaned names from sector {}", count, sector.getKey());
  }
  
}
