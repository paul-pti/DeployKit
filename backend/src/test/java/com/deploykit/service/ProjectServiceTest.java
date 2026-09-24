package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deploykit.domain.DeploymentStatus;
import com.deploykit.domain.Environment;
import com.deploykit.domain.Project;
import com.deploykit.dto.CreateProjectRequest;
import com.deploykit.dto.ProjectResponse;
import com.deploykit.exception.ConflictException;
import com.deploykit.exception.DuplicateResourceException;
import com.deploykit.exception.KubernetesOperationException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.mapper.ProjectMapper;
import com.deploykit.security.CurrentUser;
import com.deploykit.support.TestUsers;
import com.deploykit.repository.DeploymentRepository;
import com.deploykit.repository.EnvironmentRepository;
import com.deploykit.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository repository;

    @Mock
    private DeploymentRepository deploymentRepository;

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private KubernetesService kubernetesService;

    @Mock
    private DeploymentRecorder recorder;

    private final ProjectMapper mapper = new ProjectMapper();

    /** Who is calling; administrators by default, tests about ownership switch to a plain user. */
    private CurrentUser caller = TestUsers.ADMIN;

    private ProjectService service() {
        return new ProjectService(repository, mapper, deploymentRepository, environmentRepository, kubernetesService,
                new ProjectAccess(repository, recorder, () -> caller));
    }

    private static CreateProjectRequest request(String name, String branch) {
        return new CreateProjectRequest(name, "https://github.com/acme/app", branch, 8080);
    }

    @Test
    void createDefaultsBranchToMainAndTrimsName() {
        when(repository.existsByOwnerIdAndName(TestUsers.ADMIN_ID, "demo")).thenReturn(false);
        when(repository.saveAndFlush(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = service().create(request("  demo  ", null));

        ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("demo");
        assertThat(saved.getValue().getBranch()).isEqualTo("main");
        assertThat(response.port()).isEqualTo(8080);
    }

    @Test
    void createKeepsExplicitBranch() {
        when(repository.existsByOwnerIdAndName(TestUsers.ADMIN_ID, "demo")).thenReturn(false);
        when(repository.saveAndFlush(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        ProjectResponse response = service().create(request("demo", "release/1.0"));

        assertThat(response.branch()).isEqualTo("release/1.0");
    }

    @Test
    void createRejectsDuplicateName() {
        when(repository.existsByOwnerIdAndName(TestUsers.ADMIN_ID, "demo")).thenReturn(true);

        assertThatThrownBy(() -> service().create(request("demo", "main")))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("demo");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void listMapsProjects() {
        when(repository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(new Project("a", "https://github.com/acme/a", "main", 80)));

        assertThat(service().list()).extracting(ProjectResponse::name).containsExactly("a");
    }

    @Test
    void getThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().get(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesExistingProject() {
        UUID id = UUID.randomUUID();
        Project project = new Project("a", "https://github.com/acme/a", "main", 80);
        when(repository.findById(id)).thenReturn(Optional.of(project));

        service().delete(id);

        verify(repository).delete(project);
    }

    @Test
    void deleteRefusesWhileADeploymentIsInProgress() {
        UUID id = UUID.randomUUID();
        Project project = new Project("a", "https://github.com/acme/a", "main", 80);
        when(repository.findById(id)).thenReturn(Optional.of(project));
        when(deploymentRepository.existsByProjectIdAndStatusIn(id, DeploymentStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service().delete(id)).isInstanceOf(ConflictException.class);
        verify(repository, never()).delete(any());
        verifyNoInteractions(kubernetesService);
    }

    @Test
    void deleteRemovesTheProjectsKubernetesNamespaces() {
        UUID id = UUID.randomUUID();
        Project project = new Project("a", "https://github.com/acme/a", "main", 80);
        when(repository.findById(id)).thenReturn(Optional.of(project));
        when(environmentRepository.findAllByProjectId(id))
                .thenReturn(List.of(new Environment(id, "default", "dk-a-123456")));

        service().delete(id);

        verify(kubernetesService).deleteNamespace("dk-a-123456");
        verify(repository).delete(project);
    }

    @Test
    void deleteStillSucceedsWhenTheClusterIsUnreachable() {
        UUID id = UUID.randomUUID();
        Project project = new Project("a", "https://github.com/acme/a", "main", 80);
        when(repository.findById(id)).thenReturn(Optional.of(project));
        when(environmentRepository.findAllByProjectId(id))
                .thenReturn(List.of(new Environment(id, "default", "dk-a-123456")));
        doThrow(new KubernetesOperationException("cluster down", new RuntimeException()))
                .when(kubernetesService).deleteNamespace("dk-a-123456");

        service().delete(id);

        verify(repository).delete(project);
    }

    private Project ownedBy(UUID owner) {
        return new Project("a", "https://github.com/acme/a", "main", 80, owner);
    }

    @Test
    void createRecordsTheCallerAsOwnerAndChecksNamesAmongTheirOwnProjects() {
        caller = TestUsers.USER;
        when(repository.existsByOwnerIdAndName(TestUsers.USER_ID, "demo")).thenReturn(false);
        when(repository.saveAndFlush(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        service().create(request("demo", "main"));

        ArgumentCaptor<Project> saved = ArgumentCaptor.forClass(Project.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getOwnerId()).isEqualTo(TestUsers.USER_ID);
    }

    @Test
    void aNameTakenByAnotherUserDoesNotBlockMe() {
        caller = TestUsers.USER;
        // Someone else has a project called "demo", but the check is scoped to my own projects.
        when(repository.existsByOwnerIdAndName(TestUsers.USER_ID, "demo")).thenReturn(false);
        when(repository.saveAndFlush(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service().create(request("demo", "main")).name()).isEqualTo("demo");
    }

    @Test
    void aUserListsOnlyTheirOwnProjects() {
        caller = TestUsers.USER;
        when(repository.findAllByOwnerIdOrderByCreatedAtDesc(TestUsers.USER_ID)).thenReturn(List.of(ownedBy(TestUsers.USER_ID)));

        assertThat(service().list()).hasSize(1);
        verify(repository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void anAdministratorListsEveryProject() {
        when(repository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(ownedBy(TestUsers.USER_ID), ownedBy(TestUsers.OTHER_USER_ID), ownedBy(null)));

        assertThat(service().list()).hasSize(3);
        verify(repository, never()).findAllByOwnerIdOrderByCreatedAtDesc(any());
    }

    @Test
    void aUserCanReadAndDeleteTheirOwnProject() {
        caller = TestUsers.USER;
        UUID id = UUID.randomUUID();
        Project project = ownedBy(TestUsers.USER_ID);
        when(repository.findById(id)).thenReturn(Optional.of(project));

        assertThat(service().get(id).name()).isEqualTo("a");
        service().delete(id);

        verify(repository).delete(project);
    }

    @Test
    void aUserCannotReadSomeoneElsesProject() {
        caller = TestUsers.USER;
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(ownedBy(TestUsers.OTHER_USER_ID)));

        assertThatThrownBy(() -> service().get(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aUserCannotDeleteSomeoneElsesProjectAndNothingIsTouched() {
        caller = TestUsers.USER;
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(ownedBy(TestUsers.OTHER_USER_ID)));

        assertThatThrownBy(() -> service().delete(id)).isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).delete(any());
        verifyNoInteractions(kubernetesService, deploymentRepository, environmentRepository);
    }

    @Test
    void deleteThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(repository, never()).delete(any());
    }
}
