package life.catalogue.common.io;

import com.google.common.io.Resources;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

/**
 *
 */
public class PathUtils {
  
  public static String getFileExtension(Path p) {
    String name = p.toString();
    return name.substring(name.lastIndexOf(".") + 1).toLowerCase();
  }
  
  public static String getBasename(Path p) {
    return FilenameUtils.getBaseName(p.toString());
  }
  
  public static String getFilename(Path p) {
    return FilenameUtils.getName(p.toString());
  }

  public static Iterable<Path> listFiles(Path folder, final Set<String> allowedSuffices) throws IOException {
    if (folder == null || !Files.isDirectory(folder)) return Collections.emptyList();
    return Files.newDirectoryStream(folder, new DirectoryStream.Filter<Path>() {
      @Override
      public boolean accept(Path p) throws IOException {
        return Files.isRegularFile(p) && (allowedSuffices == null || allowedSuffices.contains(getFileExtension(p)));
      }
    });
  }

  public static void removeFileAndParentsIfEmpty(Path p) throws IOException {
    if (p == null) return;
    
    if (Files.isRegularFile(p)) {
      Files.deleteIfExists(p);
    } else if (Files.isDirectory(p)) {
      try {
        Files.delete(p);
      } catch (DirectoryNotEmptyException e) {
        return;
      }
    }
    removeFileAndParentsIfEmpty(p.getParent());
  }
  
  /**
   * Quietly deletes a file, returning true if successful
   */
  public static boolean deleteQuietly(Path p) {
    try {
      Files.delete(p);
      return true;
    } catch (IOException e) {
      return false;
    }
  }
  
  /**
   * Recursively delete a directory including the given root dir.
   * Does not following symbolic links.
   */
  public static void deleteRecursively(Path dir) throws IOException {
    if (Files.exists(dir)) {
      Files.walk(dir)
          .sorted(Comparator.reverseOrder())
          .forEach(PathUtils::deleteQuietly);
    }
  }
  
  /**
   * Recursively delete all content of a directory but keeps the given root dir.
   * Does not following symbolic links.
   */
  public static void cleanDirectory(Path dir) throws IOException {
    if (Files.exists(dir)) {
      Files.walk(dir)
          .sorted(Comparator.reverseOrder())
          .filter(p -> !p.equals(dir))
          .forEach(PathUtils::deleteQuietly);
    }
  }
  
  /**
   * Read a classpath resource at test time as a Path.
   * Not that this requires actual files and does NOT work with classpath resources from jar files!
   */
  public static Path classPathTestRes(String resource) {
    URL url = Resources.getResource(resource);
    try {
      return Paths.get(url.toURI());
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
  
  public static File classPathTestFile(String resource) {
    return classPathTestRes(resource).toFile();
  }
}
