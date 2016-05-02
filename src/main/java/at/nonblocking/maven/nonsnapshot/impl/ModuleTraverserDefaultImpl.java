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

import at.nonblocking.maven.nonsnapshot.ModuleTraverser;
import at.nonblocking.maven.nonsnapshot.exception.NonSnapshotPluginException;
import org.apache.maven.model.InputSource;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.model.io.xpp3.MavenXpp3ReaderEx;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;

/**
 * Default implementation of a module traverser.
 *
 * @author Juergen Kofler
 */
@Component(role = ModuleTraverser.class, hint = "default")
public class ModuleTraverserDefaultImpl implements ModuleTraverser {

  private static final Logger LOG = LoggerFactory.getLogger(ModuleTraverserDefaultImpl.class);

  private ProjectBuilder mavenProjectBuilder;

  @Override
  public void setMavenProjectBuilder(ProjectBuilder mavenProjectBuilder) {
    this.mavenProjectBuilder = mavenProjectBuilder;
  }
  
  @Override
  public List<Model> findAllModules(MavenProject baseProject) {
    LOG.info("Scanning for Maven modules...");

    List<Model> modelList = new ArrayList<>();
    recursiveFindModules(baseProject.getBasedir(), modelList, baseProject.getProjectBuildingRequest());

    return modelList;
  }

  private void recursiveFindModules(File baseDir, List<Model> modelList, ProjectBuildingRequest pbr) {
    MavenXpp3ReaderEx reader = new MavenXpp3ReaderEx();
    File pom = new File(baseDir, "pom.xml");

    Model model;

    try (XmlStreamReader xmlStreamReader = ReaderFactory.newXmlReader(pom)) {
      InputSource is = new InputSource();
      model = reader.read(xmlStreamReader, false, is);
      model.setPomFile(pom);
      LOG.debug("Found maven module: {}", pom.getParentFile().getAbsolutePath());

    } catch (IOException | XmlPullParserException e) {
      throw new NonSnapshotPluginException("Failed to load POM: " + pom.getAbsolutePath(), e);
    }

    modelList.add(model);

    Set<String> modulePaths = new LinkedHashSet<>();
    modulePaths.addAll(model.getModules());

    buildMavenProjectFromPom(pom, pbr);

    for (Profile activeProfile : buildMavenProjectFromPom(pom, pbr).getActiveProfiles()) {
      modulePaths.addAll(activeProfile.getModules());
    }

    for (String modulePath : modulePaths) {
      File moduleDir = new File(baseDir, modulePath);
      recursiveFindModules(moduleDir, modelList, pbr);
    }
  }

  private MavenProject buildMavenProjectFromPom(File pom, ProjectBuildingRequest pbr) {
    try {
      return mavenProjectBuilder.build(pom, pbr).getProject();
    } catch (ProjectBuildingException ex) {
      throw new NonSnapshotPluginException("Could not build Maven Project from " + pom.getAbsolutePath(), ex);
    }
  }
}
