package at.nonblocking.maven.nonsnapshot;

import at.nonblocking.maven.nonsnapshot.exception.NonSnapshotPluginException;
import static junit.framework.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.IOUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.impl.StaticLoggerBinder;

import at.nonblocking.maven.nonsnapshot.impl.MavenPomHandlerDefaultImpl;
import at.nonblocking.maven.nonsnapshot.model.MavenModule;
import at.nonblocking.maven.nonsnapshot.model.MavenModuleDependency;
import at.nonblocking.maven.nonsnapshot.model.MavenProperty;
import at.nonblocking.maven.nonsnapshot.model.UpdatedUpstreamMavenArtifact;

public class MavenPomHandlerDefaultImplTest {

  @BeforeClass
  public static void setupLog() {
    StaticLoggerBinder.getSingleton().setLog(new DebugSystemStreamLog());
  }

  @Test
  public void testReadArtifact() throws Exception {
    File pomFile = new File("target/test-pom.xml");
    IOUtil.copy(new FileReader("src/test/resources/test-pom.xml"), new FileOutputStream(pomFile));

    MavenPomHandler pomHandler = new MavenPomHandlerDefaultImpl();

    MavenModule wsArtifact = pomHandler.readArtifact(pomFile);

    assertEquals("at.nonblocking", wsArtifact.getGroupId());
    assertEquals("test1", wsArtifact.getArtifactId());
    assertEquals("1.0.0-SNAPSHOT", wsArtifact.getVersion());
    assertEquals(8, wsArtifact.getVersionLocation());
    assertFalse(wsArtifact.isInsertVersionTag());
    assertNull(wsArtifact.getParent());

    assertEquals(6, wsArtifact.getDependencies().size());

    assertEquals(14, wsArtifact.getDependencies().get(0).getVersionLocation());
    assertEquals(20, wsArtifact.getDependencies().get(1).getVersionLocation());
    assertEquals(26, wsArtifact.getDependencies().get(2).getVersionLocation());
    assertEquals(35, wsArtifact.getDependencies().get(3).getVersionLocation());
    assertEquals(40, wsArtifact.getDependencies().get(4).getVersionLocation());
    assertEquals(46, wsArtifact.getDependencies().get(5).getVersionLocation());
  }

  @Test
  public void testReadArtifactWithParent() throws Exception {
    File pomFile = new File("target/test-pom-parent.xml");
    IOUtil.copy(new FileReader("src/test/resources/test-pom-parent.xml"), new FileOutputStream(pomFile));

    MavenPomHandler pomHandler = new MavenPomHandlerDefaultImpl();

    MavenModule wsArtifact = pomHandler.readArtifact(pomFile);

    assertEquals("at.nonblocking", wsArtifact.getGroupId());
    assertEquals("test1", wsArtifact.getArtifactId());
    assertEquals("1.0.0-SNAPSHOT", wsArtifact.getVersion());
    assertEquals(12, wsArtifact.getVersionLocation());

    assertNotNull(wsArtifact.getParent());

    assertEquals(9, wsArtifact.getParentVersionLocation());
  }

  @Test
  public void testReadArtifactWithPropertyDependencyVersion() throws Exception {
    File pomFile = new File("target/test-pom-property.xml");
    IOUtil.copy(new FileReader("src/test/resources/test-pom-property.xml"), new FileOutputStream(pomFile));

    MavenPomHandler pomHandler = new MavenPomHandlerDefaultImpl();

    MavenModule wsArtifact = pomHandler.readArtifact(pomFile);

    assertEquals(1, wsArtifact.getMavenProperties().size());
    final MavenProperty property1 = wsArtifact.getMavenProperties().get(0);
    assertEquals(11, property1.getPropertyLocation());
    assertEquals(0, property1.getPropertyReferencingModuleDependency().size()); // Initialized in buildDependencyTree step

    assertEquals(2, wsArtifact.getDependencies().size());
    assertEquals(18, wsArtifact.getDependencies().get(0).getVersionLocation());
    assertEquals(23, wsArtifact.getDependencies().get(1).getVersionLocation());
  }

  @Test
  public void testReadArtifactWithDependencyManagement() throws Exception {
    File pomFile = new File("target/test-pom-dependency-management.xml");
    IOUtil.copy(new FileReader("src/test/resources/test-pom-dependency-management.xml"), new FileOutputStream(pomFile));

    MavenPomHandler pomHandler = new MavenPomHandlerDefaultImpl();

    MavenModule wsArtifact = pomHandler.readArtifact(pomFile);

    assertEquals(0, wsArtifact.getDependencies().size());

    assertEquals(2, wsArtifact.getDependencyManagements().size());
    assertEquals(15, wsArtifact.getDependencyManagements().get(0).getVersionLocation());
    assertEquals(20, wsArtifact.getDependencyManagements().get(1).getVersionLocation());

    assertEquals(0, wsArtifact.getMavenProperties().size());
  }

  @Test
  public void testReadAndUpdateArtifact() throws Exception {
    File pomFile = new File("target/test-pom.xml");
    IOUtil.copy(new FileReader("src/test/resources/test-pom.xml"), new FileOutputStream(pomFile));

    MavenPomHandler pomHandler = new MavenPomHandlerDefaultImpl();

    MavenModule wsArtifact = pomHandler.readArtifact(pomFile);

    wsArtifact.setDirty(true);
    wsArtifact.setNewVersion("1.1.1-12345");

    MavenModule dependentArtifact = new MavenModule(null, "at.nonblocking.at", "test2", "2.0.5-123");
    dependentArtifact.setDirty(true);
    dependentArtifact.setNewVersion("5.0.1-555");

    wsArtifact.getDependencies().get(1).setArtifact(dependentArtifact);

    pomHandler.updateArtifact(wsArtifact);

    Model pom = new MavenXpp3Reader().read(new FileInputStream(pomFile));

    assertEquals("1.1.1-12345", pom.getVersion());
    assertEquals("5.0.1-555", pom.getDependencies().get(1).getVersion());
  }

  @Test
  public void testReadAndUpdateArtifactWithParent() throws Exception {
    File pomFile = new File("target/test-pom-parent.xml");
    IOUtil.copy(new FileReader("src/test/resources/test-pom-parent.xml"), new FileOutputStream(pomFile));

    MavenPomHandler pomHandler = new MavenPomHandlerDefaultImpl();

    MavenModule wsArtifact = pomHandler.readArtifact(pomFile);

    wsArtifact.setDirty(true);
    wsArtifact.setNewVersion("1.1.1-12345");

    MavenModule parentArtifact = new MavenModule(null, "at.nonblocking.at", "parent-test", "1.4.5-123");
    parentArtifact.setDirty(true);
    parentArtifact.setNewVersion("3.3.3-456");

    wsArtifact.setParent(parentArtifact);

    pomHandler.updateArtifact(wsArtifact);

    Model pom = new MavenXpp3Reader().read(new FileInputStream(pomFile));

    assertEquals("1.1.1-12345", pom.getVersion());
    assertEquals("3.3.3-456", pom.getParent().getVersion());
  }

  @Test
  public void testReadAndUpdateArtifactWithNoVersion() throws Exception {
    File pomFile = new File("target/test-pom-noversion.xml");
    IOUtil.copy(new FileReader("src/test/resources/test-pom-noversion.xml"), new FileOutputStream(pomFile));

    MavenPomHandler pomHandler = new MavenPomHandlerDefaultImpl();

    MavenModule wsArtifact = pomHandler.readArtifact(pomFile);

    assertNotNull(wsArtifact.getVersion());
    assertTrue(wsArtifact.isInsertVersionTag());

    wsArtifact.setDirty(true);
    wsArtifact.setNewVersion("1.1.1-12345");

    pomHandler.updateArtifact(wsArtifact);

    Model pom = new MavenXpp3Reader().read(new FileInputStream(pomFile));

    assertEquals("1.1.1-12345", pom.getVersion());
  }

  @Test
  public void testReadAndUpdateArtifactInsertVersionTag() throws Exception {
    File pomFile = new File("target/test-pom.xml");
    IOUtil.copy(new FileReader("src/test/resources/test-pom.xml"), new FileOutputStream(pomFile));

    MavenPomHandler pomHandler = new MavenPomHandlerDefaultImpl();

    MavenModule wsArtifact = pomHandler.readArtifact(pomFile);

    wsArtifact.setDirty(true);
    wsArtifact.setNewVersion("1.1.1-12345");

    MavenModule dependentArtifact = new MavenModule(null, "at.nonblocking.at", "test2", "2.0.5-123");
    dependentArtifact.setDirty(true);
    dependentArtifact.setNewVersion("5.0.1-555");
    dependentArtifact.setInsertVersionTag(true);

    wsArtifact.getDependencies().get(1).setArtifact(dependentArtifact);

    pomHandler.updateArtifact(wsArtifact);

    Model pom = new MavenXpp3Reader().read(new FileInputStream(pomFile));

    assertEquals("1.1.1-12345", pom.getVersion());
    assertEquals("5.0.1-555", pom.getDependencies().get(1).getVersion());
  }

  @Test
  public void testReadAndUpdateArtifactWithPropertyDependencyVersion_MavenModule() throws Exception {
    File pomFile = new File("target/test-pom-property.xml");
    IOUtil.copy(new FileReader("src/test/resources/test-pom-property.xml"), new FileOutputStream(pomFile));

    MavenPomHandler pomHandler = new MavenPomHandlerDefaultImpl();

    MavenModule wsArtifact = pomHandler.readArtifact(pomFile);

    wsArtifact.setDirty(true);
    final String newPropertyVersion = "1.1.1-12345";
    MavenModule dependentArtifact = new MavenModule(null, "at.nonblocking.at", "test2", "2.0.5-123");
    dependentArtifact.setDirty(true);
    dependentArtifact.setNewVersion(newPropertyVersion);
    final MavenProperty propertyToUpdate = wsArtifact.getMavenProperties().get(0);
    propertyToUpdate.getPropertyReferencingModuleDependency().add(new MavenModuleDependency(18, dependentArtifact));
    propertyToUpdate.getPropertyReferencingModuleDependency().add(new MavenModuleDependency(23, dependentArtifact));

    pomHandler.updateArtifact(wsArtifact);

    Model pom = new MavenXpp3Reader().read(new FileInputStream(pomFile));

    assertEquals("1.1.1-12345", pom.getProperties().getProperty(propertyToUpdate.getName()));
    assertEquals("${" + propertyToUpdate.getName() + "}", pom.getDependencies().get(0).getVersion());
    assertEquals("${" + propertyToUpdate.getName() + "}", pom.getDependencies().get(1).getVersion());
  }

  @Test
  public void testReadAndUpdateArtifactWithPropertyDependencyVersion_UpdatedUpstreamMavenArtifact() throws Exception {
    File pomFile = new File("target/test-pom-property.xml");
    IOUtil.copy(new FileReader("src/test/resources/test-pom-property.xml"), new FileOutputStream(pomFile));

    MavenPomHandler pomHandler = new MavenPomHandlerDefaultImpl();

    MavenModule wsArtifact = pomHandler.readArtifact(pomFile);

    wsArtifact.setDirty(true);
    final String newPropertyVersion = "1.1.1-12345";
    UpdatedUpstreamMavenArtifact dependentArtifact = new UpdatedUpstreamMavenArtifact("at.nonblocking.at", "test1", "oldversion", newPropertyVersion);
    final MavenProperty propertyToUpdate = wsArtifact.getMavenProperties().get(0);
    propertyToUpdate.getPropertyReferencingModuleDependency().add(new MavenModuleDependency(18, dependentArtifact));
    propertyToUpdate.getPropertyReferencingModuleDependency().add(new MavenModuleDependency(23, dependentArtifact));

    pomHandler.updateArtifact(wsArtifact);

    Model pom = new MavenXpp3Reader().read(new FileInputStream(pomFile));

    assertEquals("1.1.1-12345", pom.getProperties().getProperty(propertyToUpdate.getName()));
    assertEquals("${" + propertyToUpdate.getName() + "}", pom.getDependencies().get(0).getVersion());
    assertEquals("${" + propertyToUpdate.getName() + "}", pom.getDependencies().get(1).getVersion());
  }

  @Test(expected = NonSnapshotPluginException.class)
  public void testReadAndUpdateArtifactWithPropertyDependencyVersion_DifferentVersionForReferencedDependencies() throws Exception {
    File pomFile = new File("target/test-pom-property.xml");
    IOUtil.copy(new FileReader("src/test/resources/test-pom-property.xml"), new FileOutputStream(pomFile));

    MavenPomHandler pomHandler = new MavenPomHandlerDefaultImpl();

    MavenModule wsArtifact = pomHandler.readArtifact(pomFile);

    wsArtifact.setDirty(true);
    final MavenProperty propertyToUpdate = wsArtifact.getMavenProperties().get(0);
    UpdatedUpstreamMavenArtifact dependentArtifact1 = new UpdatedUpstreamMavenArtifact("at.nonblocking.at", "test1", "oldversion", "1.1.1-12345");
    propertyToUpdate.getPropertyReferencingModuleDependency().add(new MavenModuleDependency(18, dependentArtifact1));
    UpdatedUpstreamMavenArtifact dependentArtifact2 = new UpdatedUpstreamMavenArtifact("at.nonblocking.at", "test2", "oldversion", "2.2.2-12345");
    propertyToUpdate.getPropertyReferencingModuleDependency().add(new MavenModuleDependency(23, dependentArtifact2));

    pomHandler.updateArtifact(wsArtifact);
  }
  
  @Test
  public void testReadAndUpdateArtifactWithDependencyManagement() throws Exception {
    File pomFile = new File("target/test-pom-dependency-management.xml");
    IOUtil.copy(new FileReader("src/test/resources/test-pom-dependency-management.xml"), new FileOutputStream(pomFile));

    MavenPomHandler pomHandler = new MavenPomHandlerDefaultImpl();

    MavenModule wsArtifact = pomHandler.readArtifact(pomFile);

    wsArtifact.setDirty(true);
    final String newDependencyVersion = "1.1.1-12345";
    MavenModule dependentArtifact = new MavenModule(null, "at.nonblocking.at", "test2", "2.0.5-123");
    dependentArtifact.setDirty(true);
    dependentArtifact.setNewVersion(newDependencyVersion);
    final MavenModuleDependency dependencyToUpdate = wsArtifact.getDependencyManagements().get(1);
    dependencyToUpdate.setArtifact(dependentArtifact);

    pomHandler.updateArtifact(wsArtifact);

    Model pom = new MavenXpp3Reader().read(new FileInputStream(pomFile));

    assertEquals("${at.nonblocking.test1.version}", pom.getDependencyManagement().getDependencies().get(0).getVersion());
    assertEquals(newDependencyVersion, pom.getDependencyManagement().getDependencies().get(1).getVersion());
  }
}