package life.catalogue.release;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.util.VocabularyUtils;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.collection.IterUtils;
import life.catalogue.common.id.IdConverter;
import life.catalogue.common.io.TabWriter;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.text.StringUtils;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.IdMapMapper;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.release.ReleasedIds.ReleasedId;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsLast;
import static life.catalogue.api.vocab.TaxonomicStatus.MISAPPLIED;

/**
 * Generates a usage id mapping table that maps all name usages from the project source
 * to some stable integer based identifiers.
 * The newly generated mapping table can be used in copy dataset commands
 * during the project release.
 *
 * Prerequisites:
 *     - names match up to date
 *
 * Basic steps:
 *
 * 1) Generate a ReleasedIds view (use interface to allow for different impls) on all previous releases,
 *    keyed on their usage id and names index id (nxId).
 *    For each id only use the version from its latest release.
 *    Include ALL ids, also deleted ones.
 *    Convert ids to their int representation to save memory and simplify comparison etc.
 *    Expose only properties needed for matching, i.e. id (int), nxId (int), status, parentID (int), ???
 *
 * 2) Process all name usages as groups by their canonical name index id, i.e. all usages that share the same name regardless of
 *    their authorship. Process groups by ranks from top down (allows to compare parentIds).
 *
 * 3) Match all usages in such a group ordered by their status:
 *    First assign ids for accepted, then prov accepted, synonyms, ambiguous syns and finally misapplied names
 */
public class IdProvider {
  protected final Logger LOG = LoggerFactory.getLogger(IdProvider.class);

  private final int projectKey;
  private final int attempt;
  private final int releaseDatasetKey;
  private final SqlSessionFactory factory;
  private final ReleaseConfig cfg;
  private final ReleasedIds ids = new ReleasedIds();
  private final Int2IntMap attempt2dataset = new Int2IntOpenHashMap();
  private final AtomicInteger keySequence = new AtomicInteger();
  private final File reportDir;
  // id changes in this release
  private int reused = 0;
  private IntSet resurrected = new IntOpenHashSet();
  private IntSet created = new IntOpenHashSet();
  private IntSet deleted = new IntOpenHashSet();
  private final SortedMap<String, List<InstableName>> unstable = new TreeMap<>();
  protected IdMapMapper idm;
  protected NameUsageMapper num;
  protected NameMatchMapper nmm;

  public IdProvider(int projectKey, int attempt, int releaseDatasetKey, ReleaseConfig cfg, SqlSessionFactory factory) {
    this.releaseDatasetKey = releaseDatasetKey;
    this.projectKey = projectKey;
    this.attempt = attempt;
    this.factory = factory;
    this.cfg = cfg;
    reportDir = cfg.reportDir(projectKey, attempt);
    reportDir.mkdirs();
  }

  public IdReport run() {
    prepare();
    mapIds();
    report();
    LOG.info("Reused {} stable IDs for project release {}-{} ({}), resurrected={}, newly created={}, deleted={}", reused, projectKey, attempt, releaseDatasetKey, resurrected.size(), created.size(), deleted.size());
    return getReport();
  }
  public static class InstableName implements DSID<String> {
    public final boolean del;
    public final int datasetKey;
    public final String id;
    public final String fullname;
    public final Rank rank;
    public final TaxonomicStatus status;
    public final String parent;

    public InstableName(boolean del, DSID<String> key, SimpleName sn) {
      this.del = del;
      this.datasetKey = key.getDatasetKey();
      this.id = key.getId();
      this.fullname = sn.getLabel();
      this.rank = sn.getRank();
      this.status = sn.getStatus();
      this.parent = sn.getParent();
    }

    public boolean isDel() {
      return del;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public void setId(String id) {
      throw new UnsupportedOperationException(getClass().getSimpleName() + " is final");
    }

    @Override
    public Integer getDatasetKey() {
      return datasetKey;
    }

    @Override
    public void setDatasetKey(Integer key) {
      throw new UnsupportedOperationException(getClass().getSimpleName() + " is final");
    }
  }

  public static class IdReport {
    public final IntSet created;
    public final IntSet deleted;
    public final IntSet resurrected;

    IdReport(IntSet created, IntSet deleted, IntSet resurrected) {
      this.created = created;
      this.deleted = deleted;
      this.resurrected = resurrected;
    }
  }

  public IdReport getReport() {
    return new IdReport(created, deleted, resurrected);
  }

  protected void report() {
    try {
      File dir = cfg.reportDir(projectKey, attempt);
      // read the following IDs from previous releases
      reportFile(dir,"deleted.tsv", deleted, true, true);
      reportFile(dir,"resurrected.tsv", resurrected, true, false);
      // read ID from this release & ID mapping
      reportFile(dir,"created.tsv", created, false, false);
      // clear instable names, removing the ones with just deletions
      unstable.entrySet().removeIf(entry -> entry.getValue().parallelStream().allMatch(n -> n.del));
      try (Writer writer = UTF8IoUtils.writerFromFile(new File(dir, "unstable.txt"));
          SqlSession session = factory.openSession(true)
      ) {
        nmm = session.getMapper(NameMatchMapper.class);
        for (var entry : unstable.entrySet()) {
          writer.write(entry.getKey() + "\n");
          entry.getValue().sort(Comparator.comparing(InstableName::isDel).reversed());
          entry.getValue().forEach(n -> writeInstableName(writer, n));
        }
      }
    } catch (IOException e) {
      LOG.error("Failed to write ID reports for project "+projectKey, e);
    }
  }

  private void writeInstableName(Writer writer, InstableName n) {
    try {
      writer.write(' ');
      writer.write(n.del ? '-' : '+');
      writer.write(' ');
      writer.write(n.fullname);
      writer.write(" [");
      writer.write(String.valueOf(n.status));
      writer.write(' ');
      writer.write(String.valueOf(n.rank));
      writer.write(' ');
      writer.write(String.valueOf(n.datasetKey));
      writer.write(':');
      writer.write(n.id);

      NameMatch match = nmm.get(n);
      writer.write(" nidx=");
      if (match != null) {
        writer.write(match.getName().getKey());
        writer.write('/');
        writer.write(match.getName().getCanonicalId());
        writer.write(' ');
        writer.write(String.valueOf(match.getType()));
      } else {
        writer.write("null");
      }

      if (n.parent != null && n.status.isSynonym()) {
        writer.write(" parent=");
        writer.write(n.parent);
      }
      writer.write(']');
      writer.write('\n');
    } catch (IOException e) {
      LOG.error("Failed to report unstable name {}", n.fullname, e);
    }
  }

  private void reportFile(File dir, String filename, IntSet ids, boolean previousReleases, boolean deletion) throws IOException {
    File f = new File(dir, filename);
    try(TabWriter tsv = TabWriter.fromFile(f);
        SqlSession session = factory.openSession(true)
    ) {
      num = session.getMapper(NameUsageMapper.class);
      LOG.info("Writing ID report for project release {}-{} of {} IDs to {}", projectKey, attempt, ids.size(), f);
      ids.stream()
        .sorted()
        .forEach(id -> reportId(id, previousReleases, tsv, deletion));
    }
  }

  /**
   * @param isOld if true lookup the if from older releases, otherwise from the project using the id map table
   * @param deletion
   */
  private void reportId(int id, boolean isOld, TabWriter tsv, boolean deletion){
    String ID = IdConverter.LATIN29.encode(id);
    SimpleName sn = null;
    DSID<String> key = null;
    try {
      int datasetKey = -1;
      if (isOld) {
        ReleasedId rid = this.ids.byId(id);
        datasetKey = attempt2dataset.get(rid.attempt);
        key = DSID.of(datasetKey, ID);
        sn = num.getSimple(key);
      } else {
        // usages do not exist yet in the release - we gotta use the id map and look them up in the project!
        sn = num.getSimpleByIdMap(DSID.of(projectKey, ID));
        if (sn != null) {
          key = DSID.of(releaseDatasetKey, ID);
        }
      }

      if (sn == null) {
        if (isOld) {
          LOG.warn("Old ID {}-{} [{}] reported without name usage", datasetKey, ID, id);
        } else {
          LOG.warn("ID {} [{}] reported without name usage", ID, id);
        }
        tsv.write(new String[]{
          ID,
          null,
          null,
          null,
          null
        });

      } else {
        // always use the new stable identifier, not the projects temporary one
        sn.setId(ID);
        tsv.write(new String[]{
          ID,
          VocabularyUtils.toString(sn.getRank()),
          VocabularyUtils.toString(sn.getStatus()),
          sn.getName(),
          sn.getAuthorship()
        });
        // populate unstable names report
        // expects deleted names to come first, so we can avoid adding many created ids for those which have not also been deleted
        if (deletion) {
          unstable.putIfAbsent(sn.getName(), new ArrayList<>());
        }
        if (unstable.containsKey(sn.getName())) {
          unstable.get(sn.getName()).add(new InstableName(deletion, key, sn));
        }
      }

    } catch (IOException | RuntimeException e) {
      LOG.error("Failed to report {}ID {}: {} [key={}, sn={}]", isOld ? "old ":"", id, ID, key, sn, e);
    }
  }

  private void prepare(){
    File dir = cfg.reportDir(projectKey, attempt);
    dir.mkdirs();
    if (cfg.restart) {
      LOG.info("Use ID provider with no previous IDs");
      // populate ids from db
    } else {
      LOG.info("Use ID provider with stable IDs since {}", cfg.since);
      loadPreviousReleaseIds();
      LOG.info("Last release attempt={} with {} IDs", ids.getMaxAttempt(), ids.maxAttemptIdCount());
    }
    keySequence.set(Math.max(cfg.start, ids.maxKey()));
    LOG.info("Max existing id = {}. Start ID sequence with {} ({})", ids.maxKey(), keySequence, encode(keySequence.get()));
  }

  static class LoadStats {
    AtomicInteger counter = new AtomicInteger();
    AtomicInteger nomatches = new AtomicInteger();
    AtomicInteger temporary = new AtomicInteger();

    @Override
    public String toString() {
      return String.format("%s usages with %s temporary ids and %s missing matches ", counter, nomatches, temporary);
    }
  }

  @VisibleForTesting
  protected void loadPreviousReleaseIds(){
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      DatasetSearchRequest dsr = new DatasetSearchRequest();
      dsr.setReleasedFrom(projectKey);
      List<Dataset> releases = dm.search(dsr, null, new Page(0, 1000));
      releases.sort(Comparator.comparing(Dataset::getAttempt).reversed());
      final LoadStats stats = new LoadStats();
      for (Dataset rel : releases) {
        if (cfg.since != null && rel.getCreated().isBefore(cfg.since)) {
          LOG.info("Ignore old release {} before {}", rel.getKey(), cfg.since);
          continue;
        }
        final int sizeBefore = ids.size();
        if (rel.getAttempt() == null) {
          throw new IllegalStateException("Release "+rel.getKey()+" of project "+projectKey+" has no importAttempt");
        }
        final int attempt = rel.getAttempt();
        session.getMapper(NameUsageMapper.class).processNxIds(rel.getKey())
          .forEach(sn -> addReleaseId(rel.getKey(), attempt, sn, stats));
        LOG.info("Read {} from previous release {} key={}. Adding {} new released ids to a total of {}",
          stats, rel.getAttempt(), rel.getKey(), ids.size() - sizeBefore, ids.size());
      }
    }
  }

  @VisibleForTesting
  protected void addReleaseId(int releaseDatasetKey, int attempt, SimpleNameWithNidx sn, LoadStats stats){
    stats.counter.incrementAndGet();
    if (sn.getNamesIndexId() == null) {
      stats.nomatches.incrementAndGet();
      LOG.info("Existing release id {}:{} without a names index id. Skip!", releaseDatasetKey, sn.getId());
    } else {
      try {
        var rl = ReleasedId.create(sn, attempt);
        attempt2dataset.putIfAbsent(attempt, releaseDatasetKey);
        ids.add(rl);
      } catch (IllegalArgumentException e) {
        // expected for temp UUID, swallow
        stats.temporary.incrementAndGet();
      }
    }
  }

  @VisibleForTesting
  protected void mapIds(){
    try (SqlSession readSession = factory.openSession(true)) {
      mapIds(readSession.getMapper(NameUsageMapper.class).processNxIds(projectKey));
    }
  }

  @VisibleForTesting
  protected void mapIds(Iterable<SimpleNameWithNidx> names){
    LOG.info("Map name usage IDs");
    final int lastRelIds = ids.maxAttemptIdCount();
    AtomicInteger counter = new AtomicInteger();
    try (SqlSession writeSession = factory.openSession(false);
         Writer nomatchWriter = UTF8IoUtils.writerFromFile(new File(reportDir, "nomatch.txt"))
    ) {
      idm = writeSession.getMapper(IdMapMapper.class);
      final int batchSize = 10000;
      Integer lastCanonID = null;
      List<SimpleNameWithNidx> group = new ArrayList<>();
      for (SimpleNameWithNidx u : names) {
        if (!Objects.equals(lastCanonID, u.getCanonicalId()) && !group.isEmpty()) {
          mapCanonicalGroup(group, nomatchWriter);
          int before = counter.get() / batchSize;
          int after = counter.addAndGet(group.size()) / batchSize;
          if (before != after) {
            writeSession.commit();
          }
          group.clear();
        }
        lastCanonID = u.getCanonicalId();
        group.add(u);
      }
      mapCanonicalGroup(group, nomatchWriter);
      writeSession.commit();

    } catch (IOException e) {
      LOG.error("Failed to write ID reports for project " + projectKey, e);
    }
    // ids remaining from the current attempt will be deleted
    deleted = ids.maxAttemptIds();
    reused = lastRelIds - deleted.size();
  }

  private void mapCanonicalGroup(List<SimpleNameWithNidx> group, Writer nomatchWriter) throws IOException {
    if (!group.isEmpty()) {
      // workaround for names index duplicates bug
      if (cfg.nidxDeduplication) {
        removeDuplicateIdxEntries(group);
      }

      // make sure we have the names sorted by their nidx
      group.sort(Comparator.comparing(SimpleNameWithNidx::getNamesIndexId, nullsLast(naturalOrder())));
      // now split the canonical group into subgroups for each nidx to match them individually
      for (List<SimpleNameWithNidx> idGroup : IterUtils.group(group, Comparator.comparing(SimpleNameWithNidx::getNamesIndexId, nullsLast(naturalOrder())))) {
        issueIDs(idGroup.get(0).getNamesIndexId(), idGroup, nomatchWriter);
      }
    }
  }

  /**
   * A temporary "hack" to remove the redundant names index entries that get created by the current NamesIndex implementation.
   * It reduces the names with the exact same name & authorship to a single index id (the lowest).
   */
  private void removeDuplicateIdxEntries(List<SimpleNameWithNidx> group) {
    // first determine which is the lowest nidx for each full name
    Set<Integer> originalIds = new HashSet<>();
    Object2IntMap<String> name2nidx = new Object2IntOpenHashMap<>();
    for (SimpleNameWithNidx n : group) {
      originalIds.add(n.getNamesIndexId());
      if (n.getNamesIndexId() == null) continue;
      String label = n.getLabel().toLowerCase();
      name2nidx.putIfAbsent(label, n.getNamesIndexId());
      if (name2nidx.get(label)>n.getNamesIndexId()) {
        name2nidx.put(label, n.getNamesIndexId());
      }
    }
    if (originalIds.size() != name2nidx.size()) {
      LOG.info("Reducing canonical group {} with {} distinct nidx values to {}", group.get(0).getName(), originalIds.size(), name2nidx.size());
      // now update redundant nidx
      for (SimpleNameWithNidx n : group) {
        String label = n.getLabel().toLowerCase();
        if (name2nidx.containsKey(label) && (n.getNamesIndexId() == null || name2nidx.getInt(label) != n.getNamesIndexId())) {
          LOG.debug("Updated names index match from {} to {} for {}", n.getNamesIndexId(), name2nidx.getInt(label), label);
          n.setNamesIndexId(name2nidx.getInt(label));
        }
      }
    }
  }

  /**
   * Populates sn.canonicalId with either an existing or new int based ID
   */
  private void issueIDs(Integer nidx, List<SimpleNameWithNidx> names, Writer nomatchWriter) throws IOException {
    if (nidx == null) {
      LOG.info("{} usages with no name match, e.g. {} - keep temporary ids", names.size(), names.get(0).getId());
      for (SimpleNameWithNidx n : names) {
        nomatchWriter.write(n.toStringBuilder().toString());
        nomatchWriter.write("\n");
      }

    } else {
      // convenient "hack": we keep the new identifiers as the canonicalID property of SimpleNameWithNidx
      names.forEach(n->n.setCanonicalId(null));
      // how many released ids do exist for this names index id?
      ReleasedId[] rids = ids.byNxId(nidx);
      if (rids != null) {
        IntSet ids = new IntOpenHashSet();
        ScoreMatrix scores = new ScoreMatrix(names, rids, this::matchScore);
        List<ScoreMatrix.ReleaseMatch> best = scores.highest();
        while (!best.isEmpty()) {
          // best is sorted, issue as they come but avoid already released ids
          for (ScoreMatrix.ReleaseMatch m : best) {
            if (!ids.contains(m.rid.id)) {
              release(m, scores);
              ids.add(m.rid.id);
            }
          }
          best = scores.highest();
        }
      }
      // persist mappings, issuing new ids for missing ones
      for (SimpleNameWithNidx sn : names) {
        if (sn.getCanonicalId() == null) {
          issueNewId(sn);
        }
        idm.mapUsage(projectKey, sn.getId(), encode(sn.getCanonicalId()));
      }
    }
  }

  private void release(ScoreMatrix.ReleaseMatch rm, ScoreMatrix scores){
    if (!ids.containsId(rm.rid.id)) {
      throw new IllegalArgumentException("Cannot release " + rm.rid.id + " which does not exist (anymore)");
    }
    ids.remove(rm.rid.id);
    rm.name.setCanonicalId(rm.rid.id);
    if (rm.rid.attempt < ids.getMaxAttempt()) {
      resurrected.add(rm.rid.id);
    }
    scores.remove(rm);
  }

  private void issueNewId(SimpleNameWithNidx n) {
    int id = keySequence.incrementAndGet();
    n.setCanonicalId(id);
    created.add(id);
  }

  /**
   * For homonyms or names very much alike we must provide a deterministic rule
   * that selects a stable id based on all previous releases.
   *
   * This can happen due to real homonyms, erroneous duplicates in the data
   * or potentially extensive pro parte synonyms as we have now for some genera like Achorutini Börner, C, 1901.
   *
   * We should avoid a major status change from accepted/synonym.
   * Require an ID change if the status changes even if the name is the same.
   *
   * For synonyms the ID is tied to the accepted name.
   * Change the synonym ID in case the accepted parent changes.
   * This helps to deal with ids for pro parte synonyms.
   *
   * @return zero for no match, positive for a match. The higher the better!
   */
  private int matchScore(SimpleNameWithNidx n, ReleasedId r) {
    var dsid = DSID.of(attempt2dataset.get(r.attempt), r.id());
    if (n.getStatus() != null && r.status != null && n.getStatus().isSynonym() != r.status.isSynonym()) {
      // major status difference, no match
      return 0;
    }
    // only one is a misapplied name - never match to anything else
    if (!Objects.equals(n.getStatus(), r.status) && (n.getStatus()==MISAPPLIED || r.status==MISAPPLIED) ) {
      return 0;
    }

    if (n.getStatus() != null && n.getStatus().isSynonym()) {
      // block synonyms with different accepted names aka parent
      if (!StringUtils.equalsIgnoreCase(n.getParent(), r.parent)) {
        return 0;
      }
    }

    int score = 1;
    // exact same status
    if (Objects.equals(n.getStatus(), r.status)) {
      score += 5;
    }
    // rank
    if (Objects.equals(n.getRank(), r.rank)) {
      score += 10;
    }
    // match type
    score += matchTypeScore(n.getNamesIndexMatchType());
    score += matchTypeScore(r.matchType);

    // exact same authorship
    if (StringUtils.equalsDigitOrAsciiLettersIgnoreCase(n.getAuthorship(), r.authorship)) {
      score += 6;
    }
    // name phrase is key for misapplied names!
    if (StringUtils.equalsDigitOrAsciiLettersIgnoreCase(n.getPhrase(), r.phrase)) {
      score += 5;
    } else if (n.getStatus() == MISAPPLIED) {
      return 0;
    }

    return score;
  }

  private int matchTypeScore(MatchType mt) {
    switch (mt) {
      case EXACT: return 3;
      case VARIANT: return 2;
      case CANONICAL: return 1;
      default: return 0;
    }
  }

  static String encode(int id) {
    return IdConverter.LATIN29.encode(id);
  }

}
