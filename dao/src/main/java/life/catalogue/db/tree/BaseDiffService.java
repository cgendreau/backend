package life.catalogue.db.tree;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.ImportAttempt;
import life.catalogue.common.io.InputStreamUtils;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.FileMetricsDao;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

public abstract class BaseDiffService<K> {
  private static final Logger LOG = LoggerFactory.getLogger(BaseDiffService.class);

  private final static Pattern ATTEMPTS = Pattern.compile("^(\\d+)\\.\\.(\\d+)$");
  protected final SqlSessionFactory factory;
  protected final FileMetricsDao<K> dao;

  public BaseDiffService(FileMetricsDao<K> dao, SqlSessionFactory factory) {
    this.factory = factory;
    this.dao = dao;
  }

  public Reader treeDiff(K key, String attempts) {
    int[] atts = parseAttempts(key, attempts);
    return udiff(key, atts, 2, a -> dao.treeFile(key, a));
  }

  public Reader namesDiff(K key, String attempts) {
    int[] atts = parseAttempts(key, attempts);
    return udiff(key, atts, 0, a -> dao.namesFile(key, a));
  }

  abstract int[] parseAttempts(K key, String attempts);

  private File[] attemptToFiles(K key, int[] attempts, Function<Integer, File> getFile) throws FileMetricsDao.AttemptMissingException{
    // verify that these exist!
    File[] files = new File[attempts.length];
    int idx = 0;
    for (int at : attempts) {
      File f = getFile.apply(at);
      assertExists(f, key, at);
      files[idx++]=f;
    }
    return files;
  }


  private File assertExists(File f, K key, int attempt) throws FileMetricsDao.AttemptMissingException {
    if (!f.exists() || f.isDirectory()) {
      throw new FileMetricsDao.AttemptMissingException(dao.getType(), key, attempt);
    }
    return f;
  }

  @VisibleForTesting
  protected int[] parseAttempts(String attempts, Supplier<List<? extends ImportAttempt>> importSupplier) {
    int a1;
    int a2;
    try {
      if (StringUtils.isBlank(attempts)) {
        List<? extends ImportAttempt> imports = importSupplier.get();
        if (imports.size()<2) {
          throw new NotFoundException("At least 2 successful imports must exist to provide a diff");
        }
        a1=imports.get(1).getAttempt();
        a2=imports.get(0).getAttempt();

      } else {
        Matcher m = ATTEMPTS.matcher(attempts);
        if (m.find()) {
          a1 = Integer.parseInt(m.group(1));
          a2 = Integer.parseInt(m.group(2));
          if (a1 >= a2) {
            throw new IllegalArgumentException("first attempt must be lower than second");
          }
        } else {
          throw new IllegalArgumentException("attempts must be separated by a two dots ..");
        }
      }
      return new int[]{a1, a2};

    } catch (IllegalArgumentException | NotFoundException e) {
      throw e;
    }
  }

  @VisibleForTesting
  protected NamesDiff namesDiff(K key, int[] atts, Function<Integer, File> getFile) {
    File[] files = attemptToFiles(key, atts, getFile);
    try {
      final NamesDiff diff = new NamesDiff(key, atts[0], atts[1]);
      Set<String> n1 = FileMetricsDao.readLines(files[0]);
      Set<String> n2 = FileMetricsDao.readLines(files[1]);

      diff.setDeleted(new HashSet<>(n1));
      diff.getDeleted().removeAll(n2);

      diff.setInserted(new HashSet<>(n2));
      diff.getInserted().removeAll(n1);
      return diff;

    } catch (IOException e) {
      throw new RuntimeException(String.format("Failed to read files for %s %s attempts %s-%s", dao.getType(), key, atts[0], atts[1]));
    }
  }

  public String diffBinaryVersion() throws IOException {
    Runtime rt = Runtime.getRuntime();
    Process ps = rt.exec("diff -v");
    return InputStreamUtils.readEntireStream(ps.getInputStream());
  }

  private String label(K key, int attempt) {
    return "dataset_" + key + "#" + attempt;
  }

  /**
   * Generates a unified diff from two gzipped files using a native unix process.
   * It allows a maximum of 10s before timing out.
   * @param atts
   * @param context number of lines of the context to include
   * @param getFile
   */
  @VisibleForTesting
  protected BufferedReader udiff(K key, int[] atts, int context, Function<Integer, File> getFile) {
    File[] files = attemptToFiles(key, atts, getFile);
    try {
      String cmd = String.format("export LC_CTYPE=en_US.UTF-8; diff --label %s --label %s -B -d -U %s <(gunzip -c %s) <(gunzip -c %s)",
        label(key,atts[0]), label(key,atts[1]), context, files[0].getAbsolutePath(), files[1].getAbsolutePath());
      // we write the diff to a temp file as we get into problems with the process not returning when streaming the results directly. Especially when http connections are aborted.
      File tmp = File.createTempFile("coldiff-"+key, ".diff");
      tmp.deleteOnExit();
      LOG.debug("Execute: {}", cmd);
      ProcessBuilder pb = new ProcessBuilder("bash", "-c", cmd + "; exit 0");
      pb.redirectOutput(tmp);

      Process ps = pb.start();
      // limit to 10s, see https://stackoverflow.com/questions/37043114/how-to-stop-a-command-being-executed-after-4-5-seconds-through-process-builder/37065167#37065167
      if (!ps.waitFor(10, TimeUnit.SECONDS)) {
        LOG.warn("Diff for {} attempts {} has timed out after 10s", key, atts);
        ps.destroy(); // make sure we leave no process behind
      }
      int status = ps.waitFor();
      if (status != 0) {
        String error = InputStreamUtils.readEntireStream(ps.getErrorStream());
        throw new RuntimeException("Unix diff failed with status " + status + ": " + error);
      }
      return UTF8IoUtils.readerFromFile(tmp);

    } catch (IOException e) {
      throw new RuntimeException("Unix diff failed", e);

    } catch (InterruptedException e) {
      throw new RuntimeException("Unix diff was interrupted", e);
    }
  }

}
