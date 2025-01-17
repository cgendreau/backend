package life.catalogue.config;

import life.catalogue.api.vocab.DataFormat;

import javax.validation.constraints.NotNull;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 *
 */
public class ReleaseConfig {
  public boolean restart = false;
  // the date we first deployed stable ids in releases - we ignore older ids than this date
  public LocalDateTime since;
  // id start
  public int start = 0;
  // nidx deduplication workaround - should be fixed by now so not enabled by default
  public boolean nidxDeduplication = false;

  @NotNull
  public File reportDir = new File("/tmp/col/release");

  // the monthly COL download directory
  public File colDownloadDir = new File("/tmp/col/monthly");

  public File reportDir(int datasetKey, int attempt) {
    return new File(reportDir, String.valueOf(datasetKey) + "/" + String.valueOf(attempt));
  }

  /**
   * Makes sure all configured directories do actually exist and create them if missing
   * @return true if at least one dir was newly created
   */
  public boolean mkdirs() {
    return reportDir.mkdirs();
  }

}
