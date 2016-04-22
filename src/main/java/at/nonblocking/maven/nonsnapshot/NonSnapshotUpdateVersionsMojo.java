/*
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package at.nonblocking.maven.nonsnapshot;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import at.nonblocking.maven.nonsnapshot.exception.NonSnapshotDependencyResolverException;
import at.nonblocking.maven.nonsnapshot.model.MavenArtifact;
import at.nonblocking.maven.nonsnapshot.model.MavenModuleDependency;
import at.nonblocking.maven.nonsnapshot.model.UpdatedUpstreamMavenArtifact;
import org.apache.maven.model.Model;
import org.apache.maven.plugins.annotations.Mojo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.nonblocking.maven.nonsnapshot.exception.NonSnapshotPluginException;
import at.nonblocking.maven.nonsnapshot.model.MavenModule;
import java.io.BufferedReader;
import java.io.FileReader;

/**
 * Main Goal of this Plugin. <br/>
 * <br/>
 * Checks the version number and manipulates it if necessary (if changes were found).
 * <br/>
 * Updates the version of upstream modules.
 * <br/>
 * Commits the POM files if deferPomCommit is false.
 *
 * @author Juergen Kofler
 */
@Mojo(name = "updateVersions", aggregator = true)
public class NonSnapshotUpdateVersionsMojo extends NonSnapshotBaseMojo {

  private static final Logger LOG = LoggerFactory.getLogger(NonSnapshotUpdateVersionsMojo.class);

  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  private String timestamp;

  @Override
  protected void internalExecute() {
    setTimestamp();

    List<Model> mavenModels = getModuleTraverser().findAllModules(getMavenProject(), getMavenProject().getActiveProfiles());

    List<MavenModule> mavenModules = buildModules(mavenModels);

    MavenModule rootModule = mavenModules.get(0);

    getDependencyTreeProcessor().buildDependencyTree(mavenModules);

    markDirtyWhenRevisionChangedOrInvalidQualifier(mavenModules);

    if (getUpstreamDependencies() != null) {
      updateUpstreamArtifacts(mavenModules);
    }

    //Recursively mark artifacts dirty
    boolean changes = getDependencyTreeProcessor().markAllArtifactsDirtyWithDirtyDependencies(mavenModules);
    while (changes) {
      changes = getDependencyTreeProcessor().markAllArtifactsDirtyWithDirtyDependencies(mavenModules);
    }

    setNextRevisionOnDirtyArtifacts(mavenModules);

    dumpArtifactTreeToLog(rootModule);

    writeAndCommitArtifacts(mavenModules);

    if (isStoreChangeTrackerIdInExternalFile()) {
      storeChangeTrackerIdInExternalFile();
    }
  }

  private List<MavenModule> buildModules(List<Model> mavenModels) {
    List<MavenModule> mavenModules = new ArrayList<>();

    for (Model model : mavenModels) {
      MavenModule module = getMavenPomHandler().readArtifact(model);
      mavenModules.add(module);
    }

    return mavenModules;
  }

  protected void writeAndCommitArtifacts(List<MavenModule> mavenModules) {
    List<File> pomsToCommit = new ArrayList<>();

    for (MavenModule mavenModule : mavenModules) {
      if (mavenModule.isDirty() && mavenModule.getNewVersion() != null) {
        getMavenPomHandler().updateArtifact(mavenModule);
        LOG.debug("Add module to dirty registry list: {}", mavenModule.getPomFile().getAbsolutePath());
        pomsToCommit.add(mavenModule.getPomFile());
      }
    }

    if (isGenerateChangedProjectsPropertyFile()) {
      generateChangedProjectsPropertyFile(pomsToCommit);
    }

    if (pomsToCommit.size() > 0) {
      writeDirtyModulesRegistry(pomsToCommit);
      if (isGenerateIncrementalBuildScripts()) {
        generateIncrementalBuildScripts(pomsToCommit);
      }

      if (!isDeferPomCommit()) {
        LOG.info("Committing {} POM files", pomsToCommit.size());
        getScmHandler().commitFiles(pomsToCommit, ScmHandler.NONSNAPSHOT_COMMIT_MESSAGE_PREFIX + " Version of " + pomsToCommit.size() + " artifacts updated");
      } else {
        LOG.info("Deferring the POM commit. Execute nonsnapshot:commit to actually commit the changes.");
      }
    } else {
      LOG.info("Modules are up-to-date. No versions updated.");
    }
  }

  private void markDirtyWhenRevisionChangedOrInvalidQualifier(List<MavenModule> mavenModules) {
    final String storedChangeTrackerId = isStoreChangeTrackerIdInExternalFile() ? readChangeTrackerIdFromExternalFile() : null;
    for (MavenModule mavenModule : mavenModules) {
      String qualifierString = storedChangeTrackerId;
      if (mavenModule.getVersion() == null) {
        LOG.info("No version found for artifact {}:{}. Assigning a new version.", mavenModule.getGroupId(), mavenModule.getArtifactId());
        mavenModule.setDirty(true);

      } else if (mavenModule.getVersion().startsWith("${")) {
        LOG.info("Version property found for artifact {}:{}. Assigning a new version.", mavenModule.getGroupId(), mavenModule.getArtifactId());
        mavenModule.setDirty(true);

      } else if (isStoreChangeTrackerIdInExternalFile() && qualifierString == null) {
        LOG.info("No previous ChangeTrackerId found. Assigning a new version for artifact {}:{}.", mavenModule.getGroupId(), mavenModule.getArtifactId());
        mavenModule.setDirty(true);

      } else {
        String[] versionParts = mavenModule.getVersion().split("-");
        if (qualifierString == null && versionParts.length > 1) {
          qualifierString = versionParts[versionParts.length - 1];
        }

        if (qualifierString == null) {
          LOG.info("Invalid qualifier string found for artifact {}:{}: {}. Assigning a new version.",
                  new Object[]{mavenModule.getGroupId(), mavenModule.getArtifactId(), mavenModule.getVersion()});
          mavenModule.setDirty(true);

        } else if (qualifierString.equals("SNAPSHOT")) {
          LOG.info("Snapshot version found for artifact {}:{}. Assigning a new version.", mavenModule.getGroupId(), mavenModule.getArtifactId());
          mavenModule.setDirty(true);

        } else {
          if (getScmType() == SCM_TYPE.SVN && isUseSvnRevisionQualifier()) {
            boolean changes = getScmHandler().checkChangesSinceRevision(mavenModule.getPomFile().getParentFile(), qualifierString);
            if (changes) {
              LOG.info("Module {}:{}: Revision number is different from the revision number in the version qualifier. Assigning a new version.", mavenModule.getGroupId(), mavenModule.getArtifactId());
              mavenModule.setDirty(true);
            }
          } else {

            //Default: compare timestamps
            boolean changes;

            try {
              Date versionTimestamp;
              if (isStoreChangeTrackerIdInExternalFile()) {
                versionTimestamp = new SimpleDateFormat(getTimestampQualifierPattern()).parse(readChangeTrackerIdFromExternalFile());
              } else {
                versionTimestamp = new SimpleDateFormat(getTimestampQualifierPattern()).parse(qualifierString);
              }
              changes = getScmHandler().checkChangesSinceDate(mavenModule.getPomFile().getParentFile(), versionTimestamp);
            } catch (ParseException e) {
              LOG.debug("Module {}:{}: Invalid timestamp qualifier: {}",
                      new Object[]{mavenModule.getGroupId(), mavenModule.getArtifactId(), qualifierString});
              changes = true;
            }

            if (changes) {
              LOG.info("Module {}:{}: There were commits after the timestamp in the version qualifier. Assigning a new version.", mavenModule.getGroupId(), mavenModule.getArtifactId());
              mavenModule.setDirty(true);
            }
          }
        }
      }
    }
  }

  private void updateUpstreamArtifacts(List<MavenModule> mavenModules) {
    for (MavenModule mavenModule : mavenModules) {
      //Parent
      if (mavenModule.getParent() != null) {
        UpdatedUpstreamMavenArtifact updatedUpstreamMavenArtifactParent = updateUpstreamArtifact(mavenModule.getParent());
        if (updatedUpstreamMavenArtifactParent != null) {
          mavenModule.setParent(updatedUpstreamMavenArtifactParent);
        }
      }

      //Dependencies
      for (MavenModuleDependency moduleDependency : mavenModule.getDependencies()) {
        UpdatedUpstreamMavenArtifact updatedUpstreamMavenArtifactDep = updateUpstreamArtifact(moduleDependency.getArtifact());
        if (updatedUpstreamMavenArtifactDep != null) {
          moduleDependency.setArtifact(updatedUpstreamMavenArtifactDep);
        }
      }
    }
  }

  private UpdatedUpstreamMavenArtifact updateUpstreamArtifact(MavenArtifact upstreamArtifact) {
    if (!(upstreamArtifact instanceof MavenModule)) {
      ProcessedUpstreamDependency upstreamDependency = getUpstreamDependencyHandler().findMatch(upstreamArtifact, getProcessedUpstreamDependencies());
      if (upstreamDependency != null) {
        LOG.debug("Upstream dependency found: {}:{}", upstreamArtifact.getGroupId(), upstreamArtifact.getArtifactId());

        try {
          String latestVersion = getUpstreamDependencyHandler().resolveLatestVersion(upstreamArtifact, upstreamDependency, getRepositorySystem(), getRepositorySystemSession(), getRemoteRepositories());
          if (latestVersion != null) {
            LOG.info("Found newer version for upstream dependency {}:{}: {}", new Object[]{upstreamArtifact.getGroupId(), upstreamArtifact.getArtifactId(), latestVersion});
            return new UpdatedUpstreamMavenArtifact(upstreamArtifact.getGroupId(), upstreamArtifact.getArtifactId(), upstreamArtifact.getVersion(), latestVersion);
          }
        } catch (NonSnapshotDependencyResolverException e) {
          if (isDontFailOnUpstreamVersionResolution()) {
            LOG.warn("Upstream dependency resolution failed (cannot update {}:{}). Error: {}",
                    new Object[]{upstreamArtifact.getGroupId(), upstreamArtifact.getArtifactId(), e.getMessage()});
          } else {
            throw e;
          }
        }
      }
    }

    return null;
  }

  private void setNextRevisionOnDirtyArtifacts(List<MavenModule> mavenModules) {
    for (MavenModule mavenModule : mavenModules) {
      File modulesPath;
      try {
        modulesPath = mavenModule.getPomFile().getParentFile().getCanonicalFile();
      } catch (IOException e) {
        throw new NonSnapshotPluginException("Unexpected IO exception", e);
      }

      if (mavenModule.isDirty()) {
        if (!getScmHandler().isWorkingCopy(modulesPath)) {
          throw new NonSnapshotPluginException("Module path is no working directory: " + modulesPath);
        }
        if (isUseSvnRevisionQualifier()) {
          mavenModule.setNewVersion(getBaseVersion() + "-" + getScmHandler().getNextRevisionId(modulesPath));
        } else if (getModuleIncrementalVersionQualifier() == MODULE_INCREMENTAL_VERSION_QUALIFIER.BUILD_NUMBER) {
          mavenModule.setNewVersion(getBaseVersion()+ getIncrementalVersionSeparator() + getBuildNumber());
        } else {
          mavenModule.setNewVersion(getBaseVersion() + "-" + this.timestamp);
        }
      }
    }
  }

  private void writeDirtyModulesRegistry(List<File> pomFileList) {
    File dirtyModulesRegistryFile = getDirtyModulesRegistryFile();
    LOG.info("Writing dirty modules registry to: {}", dirtyModulesRegistryFile.getAbsolutePath());

    try (PrintWriter writer = new PrintWriter(new FileOutputStream(dirtyModulesRegistryFile, false))) {
      for (File pomFile : pomFileList) {
        String relativeModuleDir = PathUtil.relativePath(getMavenProject().getBasedir(), pomFile.getParentFile());
        if (relativeModuleDir.isEmpty()) {
          relativeModuleDir = ".";
        }
        writer.write(relativeModuleDir + LINE_SEPARATOR);
      }

    } catch (IOException e) {
      throw new NonSnapshotPluginException("Failed to write text file with POMs to commit!", e);
    }
  }

  private void generateIncrementalBuildScripts(List<File> pomFileList) {
    String projectPaths = createProjectPathsString(pomFileList);

    if (isWindows()) {
      File batFile = new File(getMavenProject().getBasedir(), "nonsnapshotBuildIncremental.bat");
      LOG.info("Writing windows batch script for incremental build to: {}", batFile.getAbsolutePath());

      try (PrintWriter writer = new PrintWriter(batFile)) {
        writer.write("@ECHO OFF\n");
        writer.write("REM Incremental build script generated by nonsnapshot-maven-plugin\n");
        writer.write("REM To install all modified modules call:\n");
        writer.write("REM nonsnapshotBuildIncremental.bat install\n\n");
        writer.write("SET MVN_EXEC=mvn.bat\n");
        writer.write("IF DEFINED M2_HOME (set MVN_EXEC=%M2_HOME%\\bin\\mvn.bat)\n");
        writer.write("ECHO Using maven executable: %MVN_EXEC%\n");
        writer.write("%MVN_EXEC% --projects " + projectPaths + " %*");
        writer.close();

      } catch (IOException e) {
        LOG.error("Failed to write windows batch script for incremental build!", e);
      }
    } else {

      File shellFile = new File(getMavenProject().getBasedir(), "nonsnapshotBuildIncremental.sh");
      LOG.info("Writing unix shell script for incremental build to: {}", shellFile.getAbsolutePath());

      try (PrintWriter writer = new PrintWriter(shellFile)) {
        writer.write("#!/bin/sh\n");
        writer.write("# Incremental build script generated by nonsnapshot-maven-plugin\n");
        writer.write("# To install all modified modules call:\n");
        writer.write("# ./nonsnapshotBuildIncremental.sh install\n\n");
        writer.write("MVN_EXEC=mvn\n");
        writer.write("if [ ! -z \"$M2_HOME\" ]; then\n");
        writer.write("  MVN_EXEC=$M2_HOME/bin/mvn\n");
        writer.write("fi\n");
        writer.write("echo \"Using maven executable: $MVN_EXEC\"\n");
        writer.write("$MVN_EXEC --projects " + projectPaths + " $@");
        writer.close();

        Runtime.getRuntime().exec("chmod u+x " + shellFile.getAbsolutePath());

      } catch (IOException e) {
        LOG.error("Failed to write windows batch script for incremental build!", e);
      }
    }
  }

  private void generateChangedProjectsPropertyFile(List<File> pomFileList) {
    String projectPaths = createProjectPathsString(pomFileList);
    if (projectPaths.isEmpty()) {
      projectPaths = "."; //An empty property wont work on Jenkins
    }

    File propertyFile = new File(getMavenProject().getBasedir(), "nonsnapshotChangedProjects.properties");
    LOG.info("Writing changed projects to property file: {}", propertyFile.getAbsolutePath());

    try (PrintWriter writer = new PrintWriter(propertyFile)) {
      writer.write("#Property with changed projects generated by nonsnapshot-maven-plugin\n");
      writer.write("#Can be used together with the Jenkins EnvInject plugin to build changed projects only:\n");
      writer.write("#mvn --projects ${nonsnapshot.changed.projects} install\n");
      writer.write("nonsnapshot.changed.projects=" + projectPaths + "\n");
      writer.close();

    } catch (IOException e) {
      LOG.error("Failed to write changed projects property file!", e);
    }
  }

  private void storeChangeTrackerIdInExternalFile() {
    String changeTrackerIdString = isUseSvnRevisionQualifier() ? getScmHandler().getNextRevisionId(getMavenProject().getBasedir()) : this.timestamp;

    File changeTrackerIdFile = getChangeTrackerIdFile();
    LOG.info("Storing used ChangeTrackerId in file: {}", changeTrackerIdFile.getAbsolutePath());

    try (PrintWriter writer = new PrintWriter(changeTrackerIdFile)) {
      writer.write(changeTrackerIdString);
      writer.close();
    } catch (IOException e) {
      LOG.error("Failed to write used ChangeTrackerId to file!", e);
    }
  }

  private File getChangeTrackerIdFile() {
    return new File(getMavenProject().getBasedir(), "lastUsedChangeTracker.txt");
  }

  private String readChangeTrackerIdFromExternalFile() {
    File changeTrackerIdFile = getChangeTrackerIdFile();
    LOG.info("Reading used ChangeTrackerId in file: {}", changeTrackerIdFile.getAbsolutePath());
    try (BufferedReader reader = new BufferedReader(new FileReader(changeTrackerIdFile))) {
      return reader.readLine();
    } catch (IOException e) {
      LOG.warn("Failed to read used ChangeTrackerId from file {}", changeTrackerIdFile.getAbsolutePath());
      return null;
    }
  }

  private String createProjectPathsString(List<File> pomFileList) {
    StringBuilder projectPaths = new StringBuilder();

    try {
      for (File pomFile : pomFileList) {
        String relativeModuleDir = PathUtil.relativePath(getMavenProject().getBasedir(), pomFile.getParentFile());
        if (relativeModuleDir.isEmpty()) {
          relativeModuleDir = ".";
        }
        if (projectPaths.length() != 0) {
          relativeModuleDir = "," + relativeModuleDir;
        }
        projectPaths.append(relativeModuleDir);
      }

      return projectPaths.toString();

    } catch (IOException e) {
      throw new NonSnapshotPluginException("Failed determine changed project paths!", e);
    }
  }

  private boolean isWindows() {
    return System.getProperty("os.name").toLowerCase().contains("win");
  }

  private void setTimestamp() {
    this.timestamp = new SimpleDateFormat(getTimestampQualifierPattern()).format(new Date());
  }

  private void dumpArtifactTreeToLog(MavenModule rootModule) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    getDependencyTreeProcessor().printMavenModuleTree(rootModule, new PrintStream(baos));
    LOG.info("\n" + baos.toString());
  }
}
