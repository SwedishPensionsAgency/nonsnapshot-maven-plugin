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

import at.nonblocking.maven.nonsnapshot.DirtyModulesDependencyOrderResolver;
import at.nonblocking.maven.nonsnapshot.model.MavenArtifact;
import at.nonblocking.maven.nonsnapshot.model.MavenModule;
import at.nonblocking.maven.nonsnapshot.model.MavenModuleDependency;
import at.nonblocking.maven.nonsnapshot.model.MavenProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.codehaus.plexus.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of DirtyModulesDependencyOrderResolver.
 *
 * @author Per Åkerlund
 */
@Component(role = DirtyModulesDependencyOrderResolver.class, hint = "default")
public class DirtyModulesDependencyOrderResolverDefaultImpl implements DirtyModulesDependencyOrderResolver {

  private static final Logger LOG = LoggerFactory.getLogger(DirtyModulesDependencyOrderResolverDefaultImpl.class);

  @Override
  public List<MavenModule> sortDirtyModulesInDependencyOrder(List<MavenModule> mavenModules) {

    LOG.info("Resolving dirty modules in dependency order");

    // Create an unresolved maven module artifact key list
    List<String> unResolvedMavenModuleKeys = new ArrayList<>();

    // Create a keymap for modules
    Map<String, MavenModule> mavenModuleMap = new HashMap<>();

    // Initiate resources for resolving dependencies for maven modules
    for (MavenModule mavenModule : mavenModules) {
      if (mavenModule.isDirty() && mavenModule.getNewVersion() != null && !mavenModuleMap.containsKey(getArtifactKey(mavenModule))) {
        mavenModuleMap.put(getArtifactKey(mavenModule), mavenModule);
        unResolvedMavenModuleKeys.add(getArtifactKey(mavenModule));
      }
    }

    // Create a resolved dependency order list
    List<MavenModule> dependencyResolvedMavenModules = new LinkedList<>();

    int mapIndexToCheck = 0;

    // While we can resolve dependencies
    while (mapIndexToCheck < unResolvedMavenModuleKeys.size()) {

      MavenModule unresolvedMavenModuleToCheck = mavenModuleMap.get(unResolvedMavenModuleKeys.get(mapIndexToCheck));

      boolean internalDependencyFound = false;

      // Check if we got parent in unresolved modules
      if (unresolvedMavenModuleToCheck.getParent() != null && mavenModuleMap.containsKey(getArtifactKey(unresolvedMavenModuleToCheck.getParent()))) {
        internalDependencyFound = true;
      } else {
        // Check if we have dependencies to any other unresolved modules 
        final List<MavenModuleDependency> dependencies = new ArrayList<>(unresolvedMavenModuleToCheck.getDependencies());
        dependencies.addAll(unresolvedMavenModuleToCheck.getDependencyManagements());
        for (MavenProperty mavenProperty : unresolvedMavenModuleToCheck.getMavenProperties()) {
          dependencies.addAll(mavenProperty.getPropertyReferencingModuleDependency());
        }
        for (MavenModuleDependency mavenModuleDependency : dependencies) {
          if (mavenModuleMap.containsKey(getArtifactKey(mavenModuleDependency.getArtifact()))) {
            internalDependencyFound = true;
            break;
          }
        }
      }

      if (internalDependencyFound) {
        mapIndexToCheck++;
      } else {
        LOG.debug("Maven module {} is dependencyresolved", getArtifactKey(unresolvedMavenModuleToCheck));
        dependencyResolvedMavenModules.add(mavenModuleMap.remove(getArtifactKey(unresolvedMavenModuleToCheck)));
        unResolvedMavenModuleKeys.remove(mapIndexToCheck);
        mapIndexToCheck = 0;
      }
    }

    // Check if we could resolve all modules
    if (unResolvedMavenModuleKeys.isEmpty()) {
      return dependencyResolvedMavenModules;
    }

    for (MavenModule mavenModule : mavenModuleMap.values()) {
      LOG.warn("We couldn't resolve dependency order for module: {}", getArtifactKey(mavenModule));
    }

    // We couldn't resolve all dependencies, return modules in order as is
    return mavenModules;
  }

  private static String getArtifactKey(MavenArtifact mavenArtifact) {
    return mavenArtifact.getGroupId() + "." + mavenArtifact.getArtifactId();
  }
}
