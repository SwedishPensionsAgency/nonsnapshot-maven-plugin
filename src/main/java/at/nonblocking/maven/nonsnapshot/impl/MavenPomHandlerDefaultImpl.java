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
package at.nonblocking.maven.nonsnapshot.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import at.nonblocking.maven.nonsnapshot.model.UpdatedUpstreamMavenArtifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.InputLocation;
import org.apache.maven.model.InputLocationTracker;
import org.apache.maven.model.InputSource;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.io.xpp3.MavenXpp3ReaderEx;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.nonblocking.maven.nonsnapshot.MavenPomHandler;
import at.nonblocking.maven.nonsnapshot.exception.NonSnapshotPluginException;
import at.nonblocking.maven.nonsnapshot.model.MavenArtifact;
import at.nonblocking.maven.nonsnapshot.model.MavenModule;
import at.nonblocking.maven.nonsnapshot.model.MavenModuleDependency;
import at.nonblocking.maven.nonsnapshot.model.MavenProperty;

/**
 * Default implementation of {@link MavenPomHandler}
 *
 * @author Juergen Kofler
 */
@Component(role = MavenPomHandler.class, hint = "default")
public class MavenPomHandlerDefaultImpl implements MavenPomHandler {

  private static final Logger LOG = LoggerFactory.getLogger(MavenPomHandlerDefaultImpl.class);

  private static final String LINE_SEPARATOR = System.getProperty("line.separator");

  @Override
  public MavenModule readArtifact(File pomFile) {
    LOG.debug("Loading POM file: {}", pomFile.getAbsolutePath());

    InputSource is = new InputSource();
    MavenXpp3ReaderEx reader = new MavenXpp3ReaderEx();

    try {
      Model model = reader.read(ReaderFactory.newXmlReader(pomFile), false, is);
      model.setPomFile(pomFile);
      return readArtifact(model);

    } catch (IOException | XmlPullParserException e) {
      throw new NonSnapshotPluginException("Failed to load POM: " + pomFile.getAbsolutePath(), e);
    }
  }

  @Override
  public MavenModule readArtifact(Model model) {
    File pomFile = model.getPomFile();

    String groupId = model.getGroupId();
    if (groupId == null) {
      if (model.getParent() != null) {
        groupId = model.getParent().getGroupId();
      } else {
        throw new NonSnapshotPluginException("Invalid POM file: groupId is not set and no parent either: " + pomFile.getAbsolutePath());
      }
    }

    boolean insertVersionTag = false;
    String version = model.getVersion();
    if (version == null) {
      if (model.getParent() != null) {
        version = model.getParent().getVersion();
        insertVersionTag = true;
      } else {
        throw new NonSnapshotPluginException("Invalid POM file: Version is not set and no parent either: " + pomFile.getAbsolutePath());
      }
    }

    MavenModule mavenModule = new MavenModule(pomFile, groupId, model.getArtifactId(), version);
    mavenModule.setInsertVersionTag(insertVersionTag);
    mavenModule.setVersionLocation(getVersionLocation(model));

    // Parent
    if (model.getParent() != null) {
      mavenModule.setParent(new MavenArtifact(model.getParent().getGroupId(),
              model.getParent().getArtifactId(), model.getParent().getVersion()));
      mavenModule.setParentVersionLocation(getVersionLocation(model.getParent()));
    }

    // Properties
    for (String propertyName : model.getProperties().stringPropertyNames()) {
      mavenModule.getMavenProperties().add(new MavenProperty(propertyName, getPropertyLocation(model, propertyName)));
    }

    // Dependencies
    for (Dependency dependency : model.getDependencies()) {
      mavenModule.getDependencies().add(new MavenModuleDependency(
              getVersionLocation(dependency),
              new MavenArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion())));
    }

    // Dependency management
    if (model.getDependencyManagement() != null) {
      for (Dependency dependencyManagement : model.getDependencyManagement().getDependencies()) {
        mavenModule.getDependencyManagements().add(new MavenModuleDependency(
                getVersionLocation(dependencyManagement), 
                new MavenArtifact(dependencyManagement.getGroupId(), dependencyManagement.getArtifactId(), dependencyManagement.getVersion())));
      }
    }

    // Plugins
    if (model.getBuild() != null) {
      for (Plugin plugin : model.getBuild().getPlugins()) {
        mavenModule.getDependencies().add(new MavenModuleDependency(
                getVersionLocation(plugin),
                new MavenArtifact(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion())));

        for (Dependency dependency : plugin.getDependencies()) {
          mavenModule.getDependencies().add(new MavenModuleDependency(
                  getVersionLocation(dependency),
                  new MavenArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion())));
        }
      }
    }

    // Profile Dependencies
    for (Profile profile : model.getProfiles()) {
      for (Dependency dependency : profile.getDependencies()) {
        mavenModule.getDependencies().add(new MavenModuleDependency(
                getVersionLocation(dependency),
                new MavenArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion())));
      }
    }

    // Profile Plugin with Dependecies
    for (Profile profile : model.getProfiles()) {
      if (profile.getBuild() != null) {
        for (Plugin plugin : profile.getBuild().getPlugins()) {
          mavenModule.getDependencies().add(new MavenModuleDependency(
                  getVersionLocation(plugin),
                  new MavenArtifact(plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion())));

          for (Dependency dependency : plugin.getDependencies()) {
            mavenModule.getDependencies().add(new MavenModuleDependency(
                    getVersionLocation(dependency),
                    new MavenArtifact(dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion())));
          }
        }
      }
    }

    return mavenModule;
  }

  private int getVersionLocation(InputLocationTracker tracker) {
    InputLocation location = tracker.getLocation("version");
    if (location == null) {
      location = tracker.getLocation("artifactId");
    }

    return location.getLineNumber();
  }

  private static int getPropertyLocation(InputLocationTracker tracker, String propertyName) {
    InputLocation location = tracker.getLocation("properties");

    if (location != null) {
      location = location.getLocation(propertyName);
    }

    if (location == null) {
      location = tracker.getLocation("artifactId");
    }

    return location.getLineNumber();
  }

  @Override
  public void updateArtifact(MavenModule mavenModule) {
    if (!mavenModule.isDirty()) {
      return;
    }

    List<PomUpdateCommand> commands = new ArrayList<>();

    addUpdateCommand(mavenModule, mavenModule.getVersionLocation(), false, commands);

    if (mavenModule.getParent() != null) {
      if (mavenModule.getParent() instanceof MavenModule) {
        addUpdateCommand((MavenModule) mavenModule.getParent(), mavenModule.getParentVersionLocation(), true, commands);
      } else if (mavenModule.getParent() instanceof UpdatedUpstreamMavenArtifact) {
        addUpdateCommand((UpdatedUpstreamMavenArtifact) mavenModule.getParent(), mavenModule.getParentVersionLocation(), commands);
      }
    }

    for (MavenProperty mavenProperty : mavenModule.getMavenProperties()) {
      addUpdatePropertyCommand(mavenProperty, commands);
    }

    final List<MavenModuleDependency> dependencies = new ArrayList<>(mavenModule.getDependencies());
    dependencies.addAll(mavenModule.getDependencyManagements());
    for (MavenModuleDependency dependency : dependencies) {
      if (dependency.getArtifact() instanceof MavenModule) {
        addUpdateCommand((MavenModule) dependency.getArtifact(), dependency.getVersionLocation(), true, commands);
      } else if (dependency.getArtifact() instanceof UpdatedUpstreamMavenArtifact) {
        addUpdateCommand((UpdatedUpstreamMavenArtifact) dependency.getArtifact(), dependency.getVersionLocation(), commands);
      }
    }

    executeUpdateCommands(commands, mavenModule.getPomFile());
  }

  private void addUpdateCommand(MavenModule mavenModule, Integer lineNumber, boolean dependency, List<PomUpdateCommand> commands) {
    if (!mavenModule.isDirty()) {
      return;
    }
    if (mavenModule.getNewVersion() == null) {
      LOG.warn("No new version set for module {}:{}. Cannot update version!", mavenModule.getGroupId(), mavenModule.getArtifactId());
      return;
    }

    if (!dependency && mavenModule.isInsertVersionTag()) {
      commands.add(new PomUpdateCommand(lineNumber, UPDATE_COMMAND_TYPE.INSERT, "<version>" + mavenModule.getNewVersion() + "</version>", null));
    } else {
      commands.add(new PomUpdateCommand(lineNumber, UPDATE_COMMAND_TYPE.REPLACE, "<version>.*?</version>", "<version>" + mavenModule.getNewVersion() + "</version>"));
    }
  }

  private void addUpdateCommand(UpdatedUpstreamMavenArtifact updatedUpstreamMavenArtifact, Integer lineNumber, List<PomUpdateCommand> commands) {
    commands.add(new PomUpdateCommand(lineNumber, UPDATE_COMMAND_TYPE.REPLACE, "<version>.*?</version>", "<version>" + updatedUpstreamMavenArtifact.getNewVersion() + "</version>"));
  }

  private void addUpdatePropertyCommand(MavenProperty mavenProperty, List<PomUpdateCommand> commands) {
    String newValue = getNewVersion(mavenProperty.getPropertyReferencingModuleDependency());

    if (newValue == null) {
      LOG.debug("No new property value set for property {}. Cannot update property!", mavenProperty.getName());
      return;
    }

    String propertyName = mavenProperty.getName();
    commands.add(new PomUpdateCommand(mavenProperty.getPropertyLocation(), UPDATE_COMMAND_TYPE.REPLACE, "<" + propertyName + ">.*?</" + propertyName + ">", "<" + propertyName + ">" + newValue + "</" + propertyName + ">"));
  }

  private static String getNewVersion(List<MavenModuleDependency> moduleDependencies) {
    final String ERROR_MSG = "Property used to reference modules with different version numbers";
    String newVersion = null;
    for (MavenModuleDependency moduleDependency : moduleDependencies) {
      if (moduleDependency.getArtifact() instanceof MavenModule) {
        MavenModule dependency = (MavenModule) moduleDependency.getArtifact();
        newVersion = getNewValueIfEqualOrNotAlreadyExistingOtherwiseThrowException(newVersion, dependency.getNewVersion(), ERROR_MSG);
      } else if (moduleDependency.getArtifact() instanceof UpdatedUpstreamMavenArtifact) {
        UpdatedUpstreamMavenArtifact dependency = (UpdatedUpstreamMavenArtifact) moduleDependency.getArtifact();
        newVersion = getNewValueIfEqualOrNotAlreadyExistingOtherwiseThrowException(newVersion, dependency.getNewVersion(), ERROR_MSG);
      }
    }
    return newVersion;
  }

  private static String getNewValueIfEqualOrNotAlreadyExistingOtherwiseThrowException(String existingValue, String newValue, String msg) {
    if (existingValue != null && !existingValue.equals(newValue)) {
      throw new NonSnapshotPluginException("Values differ: \"" + existingValue + "\" not equal to \"" + newValue + "\". " + msg);
    }
    return newValue;
  }

  @SuppressWarnings("ConvertToTryWithResources")
  private void executeUpdateCommands(List<PomUpdateCommand> commands, File pomFile) {
    Map<Integer, PomUpdateCommand> commandMap = new HashMap<>();

    for (PomUpdateCommand command : commands) {
      commandMap.put(command.lineNumber, command);
    }

    try {
      LineNumberReader reader = new LineNumberReader(new FileReader(pomFile));
      File tempTarget = File.createTempFile("pom", ".xml");
      PrintWriter writer = new PrintWriter(tempTarget);

      LOG.debug("Writing temporary POM file to: {}", tempTarget.getAbsoluteFile());

      String line;
      while ((line = reader.readLine()) != null) {
        PomUpdateCommand command = commandMap.get(reader.getLineNumber());
        if (command != null) {
          line = executeCommand(line, command);
        }

        writer.write(line + LINE_SEPARATOR);
      }

      reader.close();
      writer.close();

      LOG.debug("Copy temporary POM file to: {}", pomFile.getAbsoluteFile());
      IOUtil.copy(new FileReader(tempTarget), new FileOutputStream(pomFile));
      tempTarget.delete();

    } catch (IOException e) {
      throw new NonSnapshotPluginException("Failed to updated POM file: " + pomFile.getAbsolutePath(), e);
    }
  }

  private String executeCommand(String line, PomUpdateCommand command) {
    switch (command.commandType) {
      case REPLACE:
        LOG.debug("Replacing '{}' with '{}' in line number: {}", new Object[]{command.text1, command.text2, command.lineNumber});
        return line.replaceAll(command.text1, command.text2);
      case INSERT:
        LOG.debug("Inserting '{}' in line number: {}", new Object[]{command.text1, command.lineNumber});
        return line + LINE_SEPARATOR + command.text1;
    }

    return line;
  }

  private enum UPDATE_COMMAND_TYPE {

    INSERT, REPLACE
  }

  private static class PomUpdateCommand {

    int lineNumber;
    UPDATE_COMMAND_TYPE commandType;
    String text1;
    String text2;

    public PomUpdateCommand(int lineNumber, UPDATE_COMMAND_TYPE commandType, String text1, String text2) {
      this.lineNumber = lineNumber;
      this.commandType = commandType;
      this.text1 = text1;
      this.text2 = text2;
    }

  }

}
