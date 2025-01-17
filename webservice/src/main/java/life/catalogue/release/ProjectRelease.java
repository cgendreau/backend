package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.*;
import life.catalogue.cache.VarnishUtils;
import life.catalogue.common.date.FuzzyDate;
import life.catalogue.common.text.CitationUtils;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.db.mapper.CitationMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetSourceMapper;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import javax.ws.rs.core.UriBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ProjectRelease extends AbstractProjectCopy {
  private static final String DEFAULT_TITLE_TEMPLATE = "{title}, {date}";
  private static final String DEFAULT_ALIAS_TEMPLATE = "{aliasOrTitle}-{date}";

  private final ImageService imageService;
  private final WsServerConfig cfg;
  private final UriBuilder datasetApiBuilder;
  private final CloseableHttpClient client;
  private final ExportManager exportManager;
  private final DoiService doiService;
  private final DoiUpdater doiUpdater;

  ProjectRelease(SqlSessionFactory factory, NameUsageIndexService indexService, DatasetImportDao diDao, DatasetDao dDao, ImageService imageService,
                 int datasetKey, int userKey, WsServerConfig cfg, CloseableHttpClient client, ExportManager exportManager,
                 DoiService doiService, DoiUpdater doiUpdater) {
    super("releasing", factory, diDao, dDao, indexService, userKey, datasetKey, true);
    this.imageService = imageService;
    this.doiService = doiService;
    this.cfg = cfg;
    this.datasetApiBuilder = cfg.apiURI == null ? null : UriBuilder.fromUri(cfg.apiURI).path("dataset/{key}LR");
    this.client = client;
    this.exportManager = exportManager;
    this.doiUpdater = doiUpdater;
  }

  @Override
  protected void modifyDataset(Dataset d, DatasetSettings ds) {
    super.modifyDataset(d, ds);
    d.setOrigin(DatasetOrigin.RELEASED);

    final FuzzyDate today = FuzzyDate.now();
    d.setIssued(today);
    d.setVersion(today.toString());

    String alias = CitationUtils.fromTemplate(d, ds, Setting.RELEASE_ALIAS_TEMPLATE, DEFAULT_ALIAS_TEMPLATE);
    d.setAlias(alias);

    String title = CitationUtils.fromTemplate(d, ds, Setting.RELEASE_TITLE_TEMPLATE, DEFAULT_TITLE_TEMPLATE);
    d.setTitle(title);

    // append authors for release?
    final List<Agent> authors = new ArrayList<>();
    if (ds.isEnabled(Setting.RELEASE_ADD_SOURCE_AUTHORS)) {
      srcDao.list(datasetKey, null, false).forEach(src -> {
        if (src.getCreator() != null) {
          authors.addAll(src.getCreator());
        }
        if (src.getEditor() != null) {
          authors.addAll(src.getEditor());
        }
      });
    }
    if (ds.isEnabled(Setting.RELEASE_ADD_CONTRIBUTORS) && d.getContributor() != null) {
      authors.addAll(d.getContributor());
    }
    // remove authors without a family name and distinct them based in the generated name alone!
    Map<String, Agent> uniq = new HashMap<>();
    for (Agent a : authors) {
      if (a != null) {
        String name = a.getName();
        if (name != null) {
          uniq.put(name.toLowerCase(), a);
        }
      }
    }
    // sort them alphabetically
    if (!uniq.isEmpty()) {
      authors.clear();
      authors.addAll(uniq.values());
      Collections.sort(authors);
      // now append them to already existing creators
      if (d.getCreator() == null) {
        d.setCreator(new ArrayList<>());
      }
      d.getCreator().addAll(authors);
    }

    d.setPrivat(true); // all releases are private candidate releases first
  }


  @Override
  void prepWork() throws Exception {
    try (SqlSession session = factory.openSession(true)) {
      // find previous public release needed for DOI management
      final Integer prevReleaseKey = findPreviousRelease(datasetKey, session);
      LOG.info("Last public release was {}", prevReleaseKey);

      // assign DOIs?
      if (cfg.doi != null) {
        newDataset.setDoi(cfg.doi.datasetDOI(newDatasetKey));
        updateDataset(newDataset);

        var attr = doiUpdater.buildReleaseMetadata(datasetKey, false, newDataset, prevReleaseKey);
        LOG.info("Creating new DOI {} for release {}", newDataset.getDoi(), newDatasetKey);
        doiService.createSilently(attr);
      }

      // treat source. Archive dataset metadata & logos & assign a potentially new DOI
      updateState(ImportState.ARCHIVING);
      DatasetSourceDao dao = new DatasetSourceDao(factory);

      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
      var cm = session.getMapper(CitationMapper.class);
      final AtomicInteger counter = new AtomicInteger(0);
      dao.list(datasetKey, newDataset, true).forEach(d -> {
        if (cfg.doi != null) {
          // can we reuse a previous DOI for the source?
          DOI srcDOI = findSourceDOI(prevReleaseKey, d.getKey(), session);
          if (srcDOI == null) {
            srcDOI = cfg.doi.datasetSourceDOI(newDatasetKey, d.getKey());
            d.setDoi(srcDOI);
            LOG.info("Creating new DOI {} for modified source {} of release {}", srcDOI, d.getKey(), newDatasetKey);
            var srcAttr = doiUpdater.buildSourceMetadata(d, newDataset, true);
            doiService.createSilently(srcAttr);
          }
          d.setDoi(srcDOI);
        }

        LOG.info("Archive dataset {}#{} for release {}", d.getKey(), attempt, newDatasetKey);
        psm.create(newDatasetKey, d);
        cm.createRelease(d.getKey(), newDatasetKey, attempt);
        // archive logos
        try {
          imageService.archiveDatasetLogo(newDatasetKey, d.getKey());
        } catch (IOException e) {
          LOG.warn("Failed to archive logo for source dataset {} of release {}", d.getKey(), newDatasetKey, e);
        }
        counter.incrementAndGet();
      });
      LOG.info("Archived metadata for {} source datasets of release {}", counter.get(), newDatasetKey);
    }

    // map ids
    updateState(ImportState.MATCHING);
    IdProvider idProvider = new IdProvider(datasetKey, attempt, newDatasetKey, cfg.release, factory);
    idProvider.run();
  }

  /**
   * This looks up the previous release by ignoring the latest releases and ignoring the very latest one.
   * Private flags do not matter.
   * @param datasetKey
   * @param session
   */
  public static Integer findPreviousRelease(int datasetKey, SqlSession session){
    DatasetMapper dm = session.getMapper(DatasetMapper.class);
    DatasetSearchRequest req = new DatasetSearchRequest();
    req.setPrivat(true);
    req.setReleasedFrom(datasetKey);
    req.setSortBy(DatasetSearchRequest.SortBy.CREATED);

    var releases = dm.search(req, DatasetMapper.MAGIC_ADMIN_USER_KEY, new Page(0, 2));
    return releases.size() < 2 ? null : releases.get(1).getKey();
  }

  /**
   * Looks up a previous DOI and verifies the core metrics have not changed since.
   * Otherwise NULL is returned and a new DOI should be issued.
   * @param prevReleaseKey the datasetKey of the previous release or NULL if never released before
   * @param sourceKey
   */
  private DOI findSourceDOI(Integer prevReleaseKey, int sourceKey, SqlSession session) {
    if (prevReleaseKey != null) {
      DatasetSourceMapper psm = session.getMapper(DatasetSourceMapper.class);
      var prevSrc = psm.getReleaseSource(sourceKey, prevReleaseKey);
      if (prevSrc != null && prevSrc.getDoi() != null) {
        // compare basic metrics
        var metrics = srcDao.projectSourceMetrics(datasetKey, sourceKey);
        var prevMetrics = srcDao.projectSourceMetrics(prevReleaseKey, sourceKey);
        if (Objects.equals(metrics.getTaxaByRankCount(), prevMetrics.getTaxaByRankCount())
            && Objects.equals(metrics.getSynonymsByRankCount(), prevMetrics.getSynonymsByRankCount())
            && Objects.equals(metrics.getVernacularsByLanguageCount(), prevMetrics.getVernacularsByLanguageCount())
            && Objects.equals(metrics.getUsagesByStatusCount(), prevMetrics.getUsagesByStatusCount())
        ) {
          return prevSrc.getDoi();
        }
      }
    }
    return null;
  }

  @Override
  void finalWork() throws Exception {
    // update both the projects and release datasets import attempt pointer
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.updateLastImport(datasetKey, attempt);
      dm.updateLastImport(newDatasetKey, attempt);
    }
    // flush varnish cache for dataset/3LR and LRC
    if (client != null && datasetApiBuilder != null) {
      URI api = datasetApiBuilder.build(datasetKey);
      VarnishUtils.ban(client, api);
    }
    // kick off exports
    for (DataFormat df : DataFormat.values()) {
      if (df.isExportable()) {
        ExportRequest req = new ExportRequest();
        req.setDatasetKey(newDatasetKey);
        req.setFormat(df);
        exportManager.submit(req, user);
      }
    }
  }

  @Override
  void onError() {
    // remove reports
    File dir = cfg.release.reportDir(datasetKey, attempt);
    if (dir.exists()) {
      LOG.debug("Remove release report {}-{} for failed dataset {}", datasetKey, metrics.attempt(), newDatasetKey);
      FileUtils.deleteQuietly(dir);
    }
  }
}
