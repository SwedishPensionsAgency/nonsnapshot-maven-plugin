package at.nonblocking.maven.nonsnapshot;

import at.nonblocking.maven.nonsnapshot.impl.ModuleTraverserDefaultImpl;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.apache.maven.project.MavenProject;
import org.junit.Test;

import static junit.framework.Assert.*;
import static org.mockito.Mockito.*;

import java.io.File;
import static java.util.Arrays.asList;
import java.util.List;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;

public class ModuleTraverserDefaultImplTest {

  private final ProjectBuilder mockProjectBuilder = mock(ProjectBuilder.class);
  private final ProjectBuildingResult mockProjectBuildingResult = mock(ProjectBuildingResult.class);
  private final ProjectBuildingRequest mockProjectBuildingRequest = mock(ProjectBuildingRequest.class);

  @Test
  public void readModulesNoProfilesTest() throws Exception {
    MavenProject mavenProject = new MavenProject();
    mavenProject.setFile(new File("src/test/resources/testworkspace/project1/pom.xml"));
    mavenProject.setProjectBuildingRequest(mockProjectBuildingRequest);

    ModuleTraverser moduleTraverser = new ModuleTraverserDefaultImpl();
    moduleTraverser.setMavenProjectBuilder(mockProjectBuilder);

    when(mockProjectBuilder.build(any(File.class), eq(mockProjectBuildingRequest))).thenReturn(mockProjectBuildingResult);
    when(mockProjectBuildingResult.getProject()).thenReturn(mavenProject);

    List<Model> mavenModels = moduleTraverser.findAllModules(mavenProject);

    assertNotNull(mavenModels);
    assertEquals(4, mavenModels.size());

    assertEquals("project1", mavenModels.get(0).getArtifactId());
    assertEquals("module1", mavenModels.get(1).getArtifactId());
    assertEquals("module2", mavenModels.get(2).getArtifactId());
    assertEquals("project2", mavenModels.get(3).getArtifactId());
  }

  @Test
  public void readModulesInProfilesTest() throws Exception {
    MavenProject mavenProject = new MavenProject();
    mavenProject.setFile(new File("src/test/resources/testworkspace/project1/pom.xml"));
    mavenProject.setProjectBuildingRequest(mockProjectBuildingRequest);

    ModuleTraverser moduleTraverser = new ModuleTraverserDefaultImpl();
    moduleTraverser.setMavenProjectBuilder(mockProjectBuilder);

    when(mockProjectBuilder.build(any(File.class), eq(mockProjectBuildingRequest))).thenReturn(mockProjectBuildingResult);
    when(mockProjectBuildingResult.getProject()).thenReturn(mavenProject);
    
    Profile activeProfile = new Profile();
    activeProfile.setId("foo");
    final String moduleInProfile = "module3";
    activeProfile.addModule(moduleInProfile);
    MavenProject mavenProjectWithActiveProfile = new MavenProject();
    mavenProjectWithActiveProfile.setActiveProfiles(asList(activeProfile));
    ProjectBuildingResult mockProjectBuildingResultWithActiveProfile = mock(ProjectBuildingResult.class);
    when(mockProjectBuildingResultWithActiveProfile.getProject()).thenReturn(mavenProjectWithActiveProfile);
    when(mockProjectBuilder.build(eq(mavenProject.getFile()), eq(mockProjectBuildingRequest))).thenReturn(mockProjectBuildingResultWithActiveProfile);

    List<Model> mavenModels = moduleTraverser.findAllModules(mavenProject);

    assertNotNull(mavenModels);
    assertEquals(5, mavenModels.size());

    assertEquals("project1", mavenModels.get(0).getArtifactId());
    assertEquals("module1", mavenModels.get(1).getArtifactId());
    assertEquals("module2", mavenModels.get(2).getArtifactId());
    assertEquals("project2", mavenModels.get(3).getArtifactId());
    assertEquals(moduleInProfile, mavenModels.get(4).getArtifactId());
  }
}
