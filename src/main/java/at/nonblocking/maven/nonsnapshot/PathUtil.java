package at.nonblocking.maven.nonsnapshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility to compare paths
 *
 * @author Juergen Kofler
 */
public final class PathUtil {

  private PathUtil() {}

  public static String relativePath(File baseDirectory, File file) throws IOException {
    Path basePath = Paths.get(baseDirectory.getCanonicalPath());
    Path filePath = Paths.get(file.getCanonicalPath());
    String relativeModuleDir = basePath.relativize(filePath).toString();
    return relativeModuleDir.replaceAll("\\\\", "/");
  }

  public static String getFullSubPath(File moduleDirectory, String subPath) throws IOException {
    return moduleDirectory.getAbsolutePath() + File.separator + subPath;
  }

  public static boolean doFilePathExists(String filePath) throws IOException {
    return new File(filePath).exists();
  }
}
