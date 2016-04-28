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

import java.io.PrintStream;
import java.util.List;

import at.nonblocking.maven.nonsnapshot.model.UpdatedUpstreamMavenArtifact;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.nonblocking.maven.nonsnapshot.DependencyTreeProcessor;
import at.nonblocking.maven.nonsnapshot.model.MavenArtifact;
import at.nonblocking.maven.nonsnapshot.model.MavenModule;
import at.nonblocking.maven.nonsnapshot.model.MavenModuleDependency;
import at.nonblocking.maven.nonsnapshot.model.MavenProperty;
import java.util.ArrayList;

/**
 * Default {@link DependencyTreeProcessor} implementation.
 *
 * @author Juergen Kofler
 */
@Component(role = DependencyTreeProcessor.class, hint = "default")
public class DependencyTreeProcessorDefaultImpl implements DependencyTreeProcessor {

  private static final Logger LOG = LoggerFactory.getLogger(DependencyTreeProcessorDefaultImpl.class);

  @Override
  public void buildDependencyTree(List<MavenModule> mavenModules) {
    for (MavenModule mavenModule : mavenModules) {
      if (mavenModule.getParent() != null) {
        MavenModule parentModule = findArtifact(mavenModules, mavenModule.getParent().getGroupId(), mavenModule.getParent().getArtifactId());
        if (parentModule != null) {
          parentModule.getChildren().add(mavenModule);
          mavenModule.setParent(parentModule);
        }
      }

      for (MavenProperty mavenProperty : mavenModule.getMavenProperties()) {
        mavenProperty.getPropertyReferencingModuleDependency().addAll(findPropertyReferencingModuleDependencies(mavenProperty, mavenModules));
      }

      final List<MavenModuleDependency> dependencies = new ArrayList<>(mavenModule.getDependencies());
      dependencies.addAll(mavenModule.getDependencyManagements());

      for (MavenModuleDependency dependency : dependencies) {
        MavenModule dependencyModule = findArtifact(mavenModules, dependency.getArtifact().getGroupId(), dependency.getArtifact()
                .getArtifactId());
        if (dependencyModule != null) {
          dependency.setArtifact(dependencyModule);
        }
      }
    }
  }

  private MavenModule findArtifact(List<MavenModule> artifacts, String groupId, String artifactId) {
    for (MavenModule artifact : artifacts) {
      if (groupId.equals(artifact.getGroupId()) && artifactId.equals(artifact.getArtifactId())) {
        return artifact;
      }
    }

    return null;
  }

  private List<MavenModuleDependency> findPropertyReferencingModuleDependencies(MavenProperty mavenProperty, List<MavenModule> mavenModules) {
    final ArrayList<MavenModuleDependency> result = new ArrayList<>();
    for (MavenModule mavenModule : mavenModules) {
      
      final List<MavenModuleDependency> dependencies = new ArrayList<>(mavenModule.getDependencies());
      dependencies.addAll(mavenModule.getDependencyManagements());
      for(MavenModuleDependency mavenDependency : dependencies){
        final MavenArtifact dependency = mavenDependency.getArtifact();
        if(dependency.getVersion() != null && dependency.getVersion().equals("${" + mavenProperty.getName() + "}")){
          result.add(mavenDependency);
        }
      }
    }

    return result;
  }

  @Override
  public boolean markAllArtifactsDirtyWithDirtyDependencies(List<MavenModule> mavenModules) {
    boolean changes = false;

    for (MavenModule mavenModule : mavenModules) {
      if (mavenModule.isDirty()) {
        continue;
      }

      if (mavenModule.getParent() != null) {
        if (mavenModule.getParent() instanceof MavenModule) {
          MavenModule parentModule = (MavenModule) mavenModule.getParent();
          if (parentModule.isDirty()) {
            LOG.info("Marking module {}:{} dirty because parent is dirty.", mavenModule.getGroupId(), mavenModule.getArtifactId());
            mavenModule.setDirty(true);
            changes = true;
            continue;
          }
        } else if (mavenModule.getParent() instanceof UpdatedUpstreamMavenArtifact) {
          UpdatedUpstreamMavenArtifact updatedUpstreamMavenArtifact = (UpdatedUpstreamMavenArtifact) mavenModule.getParent();
          LOG.info("Marking module {}:{} dirty because upstream parent is dirty: {}:{}.",
                  new Object[]{mavenModule.getGroupId(), mavenModule.getArtifactId(),
                    updatedUpstreamMavenArtifact.getGroupId(), updatedUpstreamMavenArtifact.getArtifactId()});
          mavenModule.setDirty(true);
          changes = true;
          continue;
        }
      }
      
      final List<MavenModuleDependency> dependencies = new ArrayList<>(mavenModule.getDependencies());
      dependencies.addAll(mavenModule.getDependencyManagements());
      for (MavenProperty mavenProperty : mavenModule.getMavenProperties()){
        dependencies.addAll(mavenProperty.getPropertyReferencingModuleDependency());
      }
      for (MavenModuleDependency dependency : dependencies) {
        if (dependency.getArtifact() instanceof MavenModule) {
          MavenModule parentModule = (MavenModule) dependency.getArtifact();
          if (parentModule.isDirty()) {
            LOG.info("Marking module {}:{} dirty because dependency is dirty: {}:{}",
                    new Object[]{mavenModule.getGroupId(), mavenModule.getArtifactId(), parentModule.getGroupId(), parentModule.getArtifactId()});
            mavenModule.setDirty(true);
            changes = true;
            break;
          }
        } else if (dependency.getArtifact() instanceof UpdatedUpstreamMavenArtifact) {
          UpdatedUpstreamMavenArtifact updatedUpstreamMavenArtifact = (UpdatedUpstreamMavenArtifact) dependency.getArtifact();
          LOG.info("Marking module {}:{} dirty because upstream dependency is dirty: {}:{}",
                  new Object[]{mavenModule.getGroupId(), mavenModule.getArtifactId(),
                    updatedUpstreamMavenArtifact.getGroupId(), updatedUpstreamMavenArtifact.getArtifactId()});
          mavenModule.setDirty(true);
          changes = true;
          break;
        }
      }
    }

    return changes;
  }

  @Override
  public void printMavenModuleTree(MavenModule rootModule, PrintStream printStream) {
    printTree(rootModule, printStream, 0);
  }

  private void printTree(MavenArtifact artifact, PrintStream printStream, int level) {
    printStream.print(StringUtils.leftPad(" ", level * 3));

    if (artifact instanceof MavenModule) {
      MavenModule module = (MavenModule) artifact;

      printStream.print(module.getGroupId() + ":" + module.getArtifactId() + ":" + module.getVersion());
      if (module.isDirty()) {
        if (module.getNewVersion() != null) {
          printStream.println(" -> " + module.getNewVersion());
        } else {
          printStream.println(" -> (Dirty, but new version couldn't be determined!)");
        }
      } else {
        printStream.println(" ");
      }

      for (MavenModule child : module.getChildren()) {
        printTree(child, printStream, level + 1);
      }

    } else {
      printStream.println("(" + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion() + ")");
    }
  }
}
