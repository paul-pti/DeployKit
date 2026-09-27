package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.Project;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.repository.ProjectRepository;
import com.deploykit.security.CurrentUser;
import com.deploykit.support.TestUsers;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProjectAccessTest {

    private final UUID projectId = UUID.randomUUID();
    private final UUID deploymentId = UUID.randomUUID();

    private ProjectRepository projects;
    private DeploymentRecorder recorder;

    @BeforeEach
    void setUp() {
        projects = mock(ProjectRepository.class);
        recorder = mock(DeploymentRecorder.class);
    }

    private ProjectAccess as(CurrentUser user) {
        return new ProjectAccess(projects, recorder, () -> user);
    }

    private void projectOwnedBy(UUID owner) {
        Project project = new Project("demo", "https://github.com/acme/demo", "main", 80, owner);
        ReflectionTestUtils.setField(project, "id", projectId);
        when(projects.findById(projectId)).thenReturn(Optional.of(project));
    }

    private Deployment deployment() {
        Deployment deployment = new Deployment(projectId, 1, "nginx:1", null);
        ReflectionTestUtils.setField(deployment, "id", deploymentId);
        when(recorder.get(deploymentId)).thenReturn(deployment);
        return deployment;
    }

    @Test
    void anOwnerReachesTheirProject() {
        projectOwnedBy(TestUsers.USER_ID);

        assertThat(as(TestUsers.USER).requireProject(projectId).getId()).isEqualTo(projectId);
    }

    @Test
    void anAdministratorReachesAnyProjectIncludingOnesWithoutOwner() {
        projectOwnedBy(TestUsers.USER_ID);
        assertThat(as(TestUsers.ADMIN).requireProject(projectId)).isNotNull();

        projectOwnedBy(null);
        assertThat(as(TestUsers.ADMIN).requireProject(projectId)).isNotNull();
    }

    @Test
    void aUserCannotReachSomeoneElsesProject() {
        projectOwnedBy(TestUsers.OTHER_USER_ID);

        assertThatThrownBy(() -> as(TestUsers.USER).requireProject(projectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aUserCannotReachAProjectThatPredatesAuthentication() {
        projectOwnedBy(null);

        assertThatThrownBy(() -> as(TestUsers.USER).requireProject(projectId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aForbiddenProjectLooksExactlyLikeAMissingOne() {
        UUID missing = UUID.randomUUID();
        when(projects.findById(missing)).thenReturn(Optional.empty());
        projectOwnedBy(TestUsers.OTHER_USER_ID);

        Throwable forbidden = catchThrowable(() -> as(TestUsers.USER).requireProject(projectId));
        Throwable absent = catchThrowable(() -> as(TestUsers.USER).requireProject(missing));

        assertThat(forbidden).isInstanceOf(ResourceNotFoundException.class);
        assertThat(absent).isInstanceOf(ResourceNotFoundException.class);
        assertThat(forbidden.getMessage()).isEqualTo("Project " + projectId + " not found");
        assertThat(absent.getMessage()).isEqualTo("Project " + missing + " not found");
    }

    @Test
    void anOwnerReachesTheDeploymentsOfTheirProject() {
        projectOwnedBy(TestUsers.USER_ID);
        Deployment deployment = deployment();

        assertThat(as(TestUsers.USER).requireDeployment(deploymentId)).isSameAs(deployment);
    }

    @Test
    void anAdministratorReachesAnyDeployment() {
        projectOwnedBy(TestUsers.OTHER_USER_ID);
        Deployment deployment = deployment();

        assertThat(as(TestUsers.ADMIN).requireDeployment(deploymentId)).isSameAs(deployment);
    }

    @Test
    void aUserCannotReachTheDeploymentsOfSomeoneElsesProject() {
        projectOwnedBy(TestUsers.OTHER_USER_ID);
        deployment();

        assertThatThrownBy(() -> as(TestUsers.USER).requireDeployment(deploymentId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Deployment " + deploymentId + " not found");
    }

    @Test
    void anUnknownDeploymentIsNotFound() {
        when(recorder.get(deploymentId)).thenThrow(new ResourceNotFoundException("Deployment " + deploymentId + " not found"));

        assertThatThrownBy(() -> as(TestUsers.USER).requireDeployment(deploymentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aDeploymentWhoseProjectIsGoneIsNotFound() {
        deployment();
        when(projects.findById(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> as(TestUsers.ADMIN).requireDeployment(deploymentId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }
}
