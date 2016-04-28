package at.nonblocking.maven.nonsnapshot;

import static junit.framework.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.impl.StaticLoggerBinder;

import at.nonblocking.maven.nonsnapshot.impl.DependencyTreeProcessorDefaultImpl;
import at.nonblocking.maven.nonsnapshot.model.MavenArtifact;
import at.nonblocking.maven.nonsnapshot.model.MavenModule;
import at.nonblocking.maven.nonsnapshot.model.MavenModuleDependency;
import at.nonblocking.maven.nonsnapshot.model.MavenProperty;
import static java.util.Arrays.asList;

public class DependencyTreeProcessorDefaultImplTest {

  @BeforeClass
  public static void setupLog() {
    StaticLoggerBinder.getSingleton().setLog(new DebugSystemStreamLog());
  }

  @Test
  public void testBuildDependencyTree() {
    MavenModule wsArtifact1 = new MavenModule(null, "at.nonblocking.at", "parent", "1.0.0");
    MavenModule wsArtifact2 = new MavenModule(null, "at.nonblocking.at", "test1", "1.0.0");
    MavenModule wsArtifact3 = new MavenModule(null, "at.nonblocking.at", "test1-module1", "1.0.0");
    MavenModule wsArtifact4 = new MavenModule(null, "at.nonblocking.at", "test1-module2", "1.0.0");
    MavenModule wsArtifact5 = new MavenModule(null, "at.nonblocking.at", "test2", "1.0.0");
    MavenModule wsArtifact6 = new MavenModule(null, "at.nonblocking.at", "plugin1", "1.0.0");

    wsArtifact1.getDependencies().add(new MavenModuleDependency(0, new MavenArtifact("at.nonblocking.at", "plugin1", "1.0.0")));
    final MavenArtifact externalArtifactJunit = new MavenArtifact("junit", "junit", "4.7");
    wsArtifact1.getDependencies().add(new MavenModuleDependency(0, externalArtifactJunit));
    wsArtifact2.setParent(new MavenArtifact("at.nonblocking.at", "parent", "1.0.0"));
    wsArtifact2.getDependencies().add(new MavenModuleDependency(0, new MavenArtifact("at.nonblocking.at", "test2", "1.0.0")));
    wsArtifact3.setParent(new MavenArtifact("at.nonblocking.at", "test1", "1.0.0"));
    wsArtifact4.setParent(new MavenArtifact("at.nonblocking.at", "test1", "1.0.0"));
    wsArtifact5.setParent(new MavenArtifact("at.nonblocking.at", "parent", "1.0.0"));

    List<MavenModule> artifacts = new ArrayList<>();
    artifacts.add(wsArtifact1);
    artifacts.add(wsArtifact2);
    artifacts.add(wsArtifact3);
    artifacts.add(wsArtifact4);
    artifacts.add(wsArtifact5);
    artifacts.add(wsArtifact6);

    DependencyTreeProcessor dependencyTreeProcessor = new DependencyTreeProcessorDefaultImpl();

    dependencyTreeProcessor.buildDependencyTree(artifacts);

    dependencyTreeProcessor.printMavenModuleTree(wsArtifact1, System.out);

    // wsArtifact1
    assertEquals(wsArtifact6, wsArtifact1.getDependencies().get(0).getArtifact());
    assertEquals(externalArtifactJunit, wsArtifact1.getDependencies().get(1).getArtifact());
    // wsArtifact2
    assertEquals(wsArtifact1, wsArtifact2.getParent());
    assertEquals(wsArtifact5, wsArtifact2.getDependencies().get(0).getArtifact());
    //wsArtifact3
    assertEquals(wsArtifact2, wsArtifact3.getParent());
    //wsArtifact4
    assertEquals(wsArtifact2, wsArtifact4.getParent());
    //wsArtifact5
    assertEquals(wsArtifact1, wsArtifact5.getParent());
  }

  @Test
  public void testBuildDependencyTree_DependencyManagement() {
    MavenModule wsArtifact1 = new MavenModule(null, "at.nonblocking.at", "withDependencyManagment", "1.0.0");
    MavenModule wsArtifact2 = new MavenModule(null, "at.nonblocking.at", "plugin1", "1.0.0");

    wsArtifact1.getDependencyManagements().add(new MavenModuleDependency(0, new MavenArtifact("at.nonblocking.at", "plugin1", "1.0.0")));
    List<MavenModule> artifacts = asList(wsArtifact1, wsArtifact2);

    DependencyTreeProcessor dependencyTreeProcessor = new DependencyTreeProcessorDefaultImpl();

    dependencyTreeProcessor.buildDependencyTree(artifacts);

    assertEquals(wsArtifact2, wsArtifact1.getDependencyManagements().get(0).getArtifact());
  }

  @Test
  public void testBuildDependencyTree_Properties() {
    MavenModule wsArtifact1 = new MavenModule(null, "at.nonblocking.at", "withProperty", "1.0.0");
    MavenModule wsArtifact2 = new MavenModule(null, "at.nonblocking.at", "usingPropertyInDependency", "1.0.0");
    MavenModule wsArtifact3 = new MavenModule(null, "at.nonblocking.at", "usingPropertyInDependencyManagement", "1.0.0");
    MavenModule wsArtifact4 = new MavenModule(null, "at.nonblocking.at", "plugin1", "1.0.0");

    final String PROPERTY_NAME = "propertyName";
    wsArtifact1.getMavenProperties().add(new MavenProperty(PROPERTY_NAME, 0));
    wsArtifact2.getDependencies().add(new MavenModuleDependency(0, new MavenArtifact("at.nonblocking.at", "plugin1", "${" + PROPERTY_NAME + "}")));
    wsArtifact3.getDependencyManagements().add(new MavenModuleDependency(0, new MavenArtifact("at.nonblocking.at", "plugin1", "${" + PROPERTY_NAME + "}")));
    List<MavenModule> artifacts = asList(wsArtifact1, wsArtifact2, wsArtifact3, wsArtifact4);

    DependencyTreeProcessor dependencyTreeProcessor = new DependencyTreeProcessorDefaultImpl();

    dependencyTreeProcessor.buildDependencyTree(artifacts);

    //  wsArtifact1
    assertEquals(2, wsArtifact1.getMavenProperties().get(0).getPropertyReferencingModuleDependency().size());
    assertEquals(wsArtifact4, wsArtifact1.getMavenProperties().get(0).getPropertyReferencingModuleDependency().get(0).getArtifact());
    assertEquals(wsArtifact4, wsArtifact1.getMavenProperties().get(0).getPropertyReferencingModuleDependency().get(1).getArtifact());
    //  wsArtifact2
    assertEquals(wsArtifact4, wsArtifact2.getDependencies().get(0).getArtifact());
    //  wsArtifact3
    assertEquals(wsArtifact4, wsArtifact3.getDependencyManagements().get(0).getArtifact());
  }

  @Test
  public void testMarkAllArtifactsDirtyWithDirtyDependencies() {
    MavenModule wsArtifact1 = new MavenModule(null, "at.nonblocking.at", "test1", "1.0.0");
    MavenModule wsArtifact2 = new MavenModule(null, "at.nonblocking.at", "test2", "1.0.0");
    MavenModule wsArtifact3 = new MavenModule(null, "at.nonblocking.at", "plugin1", "1.0.0");

    wsArtifact2.getDependencies().add(new MavenModuleDependency(0, wsArtifact3));
    wsArtifact3.setDirty(true);

    List<MavenModule> artifacts = new ArrayList<>();
    artifacts.add(wsArtifact1);
    artifacts.add(wsArtifact2);
    artifacts.add(wsArtifact3);

    DependencyTreeProcessor dependencyTreeProcessor = new DependencyTreeProcessorDefaultImpl();

    boolean changes1 = dependencyTreeProcessor.markAllArtifactsDirtyWithDirtyDependencies(artifacts);
    boolean changes2 = dependencyTreeProcessor.markAllArtifactsDirtyWithDirtyDependencies(artifacts);

    assertTrue(changes1);
    assertFalse(changes2);

    assertFalse(wsArtifact1.isDirty());
    assertTrue(wsArtifact2.isDirty());
    assertTrue(wsArtifact3.isDirty());

    dependencyTreeProcessor.printMavenModuleTree(wsArtifact1, System.out);
  }

  @Test
  public void testMarkAllArtifactsDirtyWithDirtyDependenciesRecursive() {
    MavenModule wsArtifact1 = new MavenModule(null, "at.nonblocking.at", "test1", "1.0.0");
    MavenModule wsArtifact2 = new MavenModule(null, "at.nonblocking.at", "test2", "1.0.0");
    MavenModule wsArtifact3 = new MavenModule(null, "at.nonblocking.at", "plugin1", "1.0.0");
    MavenModule wsArtifact4 = new MavenModule(null, "at.nonblocking.at", "test", "1.0.0");

    //4 -> 2 -> 3 (dirty)
    wsArtifact4.getDependencies().add(new MavenModuleDependency(0, wsArtifact2));
    wsArtifact2.getDependencies().add(new MavenModuleDependency(0, wsArtifact3));
    wsArtifact3.setDirty(true);

    List<MavenModule> artifacts = new ArrayList<>();
    artifacts.add(wsArtifact1);
    artifacts.add(wsArtifact2);
    artifacts.add(wsArtifact3);
    artifacts.add(wsArtifact4);

    DependencyTreeProcessor dependencyTreeProcessor = new DependencyTreeProcessorDefaultImpl();

    boolean changes1 = dependencyTreeProcessor.markAllArtifactsDirtyWithDirtyDependencies(artifacts);
    boolean changes2 = dependencyTreeProcessor.markAllArtifactsDirtyWithDirtyDependencies(artifacts);

    assertTrue(changes1);
    assertFalse(changes2);

    assertFalse(wsArtifact1.isDirty());
    assertTrue(wsArtifact2.isDirty());
    assertTrue(wsArtifact3.isDirty());
    assertTrue(wsArtifact4.isDirty());

    dependencyTreeProcessor.printMavenModuleTree(wsArtifact1, System.out);
  }

  @Test
  public void testMarkAllArtifactsDirtyWithDirtyDependencies_DependencyManagement() {
    MavenModule wsArtifact1 = new MavenModule(null, "at.nonblocking.at", "withDependencyManagment", "1.0.0");
    MavenModule wsArtifact2 = new MavenModule(null, "at.nonblocking.at", "plugin1", "1.0.0");

    wsArtifact1.getDependencyManagements().add(new MavenModuleDependency(0, wsArtifact2));

    wsArtifact2.setDirty(true);
    List<MavenModule> artifacts = asList(wsArtifact1, wsArtifact2);

    DependencyTreeProcessor dependencyTreeProcessor = new DependencyTreeProcessorDefaultImpl();

    assertTrue(dependencyTreeProcessor.markAllArtifactsDirtyWithDirtyDependencies(artifacts));
    assertTrue(wsArtifact1.isDirty());
    assertTrue(wsArtifact2.isDirty());
    
    assertFalse(dependencyTreeProcessor.markAllArtifactsDirtyWithDirtyDependencies(artifacts));
  }

  @Test
  public void testMarkAllArtifactsDirtyWithDirtyDependencies_Properties() {
    MavenModule wsArtifact1 = new MavenModule(null, "at.nonblocking.at", "withProperty", "1.0.0");
    MavenModule wsArtifact2 = new MavenModule(null, "at.nonblocking.at", "plugin1", "1.0.0");

    final String PROPERTY_NAME = "propertyName";
    final MavenProperty mavenProperty = new MavenProperty(PROPERTY_NAME, 0);
    mavenProperty.getPropertyReferencingModuleDependency().add(new MavenModuleDependency(0,wsArtifact2));
    wsArtifact1.getMavenProperties().add(mavenProperty);

    wsArtifact2.setDirty(true);
    List<MavenModule> artifacts = asList(wsArtifact1, wsArtifact2);

    DependencyTreeProcessor dependencyTreeProcessor = new DependencyTreeProcessorDefaultImpl();

    assertTrue(dependencyTreeProcessor.markAllArtifactsDirtyWithDirtyDependencies(artifacts));
    assertTrue(wsArtifact1.isDirty());
    assertTrue(wsArtifact2.isDirty());
    
    assertFalse(dependencyTreeProcessor.markAllArtifactsDirtyWithDirtyDependencies(artifacts));
  }
}
