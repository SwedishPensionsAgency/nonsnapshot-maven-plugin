package at.nonblocking.maven.nonsnapshot;

import at.nonblocking.maven.nonsnapshot.impl.DirtyModulesDependencyOrderResolverDefaultImpl;
import at.nonblocking.maven.nonsnapshot.model.MavenModule;
import at.nonblocking.maven.nonsnapshot.model.MavenModuleDependency;
import static java.util.Arrays.asList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DirtyModulesDependencyOrderResolverDefaultImplTest {

  private final DirtyModulesDependencyOrderResolver resolver = new DirtyModulesDependencyOrderResolverDefaultImpl();

  @Test
  public void testModuleDependencyResolutionWithParentChildAndDeclaredDependency() {
    MavenModule artifact1 = createMockArtifact("artifact1");
    MavenModule artifact2 = createMockArtifact("artifact2");
    when(artifact2.getParent()).thenReturn(artifact1);
    MavenModule artifact3 = createMockArtifact("artifact3");
    when(artifact3.getParent()).thenReturn(artifact2);
    MavenModule artifact4 = createMockArtifact("artifact4");
    when(artifact4.getParent()).thenReturn(artifact1);
    final List<MavenModuleDependency> artifact4DependencyList = asList(createMockDependencyOn(artifact3));
    when(artifact4.getDependencies()).thenReturn(artifact4DependencyList);

    List<MavenModule> unsortedModules = asList(artifact3, artifact2, artifact4, artifact1);
    List<MavenModule> expectedResultSortedDirtyModules = asList(artifact1, artifact2, artifact3, artifact4);

    final List<MavenModule> result = resolver.sortDirtyModulesInDependencyOrder(unsortedModules);

    assertEquals(expectedResultSortedDirtyModules, result);
  }

  private MavenModule createMockArtifact(final String artifactId) {
    MavenModule artifact = mock(MavenModule.class);
    when(artifact.getGroupId()).thenReturn("Artifact.Group");
    when(artifact.getArtifactId()).thenReturn(artifactId);
    when(artifact.toString()).thenReturn(artifactId);
    when(artifact.isDirty()).thenReturn(true);
    when(artifact.getNewVersion()).thenReturn("New version of" + artifactId);
    return artifact;
  }

  private MavenModuleDependency createMockDependencyOn(MavenModule artifact) {
    MavenModuleDependency depOn3 = mock(MavenModuleDependency.class);
    when(depOn3.getArtifact()).thenReturn(artifact);
    return depOn3;
  }
}
