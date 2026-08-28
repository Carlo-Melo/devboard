package com.devboard.security;

import com.devboard.entity.Project;
import com.devboard.entity.ProjectMember;
import com.devboard.entity.Task;
import com.devboard.entity.enums.ProjectRole;
import com.devboard.exception.ResourceNotFoundException;
import com.devboard.repository.ProjectMemberRepository;
import com.devboard.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Ponto único de verificação de permissão de projeto. Nunca replicar esta lógica dentro de um service.
 *
 * Distinção obrigatória (claude.md): usuário sem vínculo com o projeto → 404 (não revela que o
 * recurso existe); usuário com vínculo, mas papel insuficiente → 403. As duas situações passam
 * por {@link #requireRole}, que resolve ambas a partir do papel efetivo.
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;

    /** Papel efetivo do usuário no projeto, ou {@code null} se não houver vínculo. Dono resolve como ADMIN. */
    public ProjectRole resolveRole(Long projectId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado"));

        if (project.getOwner().getId().equals(userId)) {
            return ProjectRole.ADMIN;
        }

        return memberRepository.findByProjectIdAndUserId(projectId, userId)
                .map(ProjectMember::getRole)
                .orElse(null);
    }

    public boolean isOwner(Long projectId, Long userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Projeto não encontrado"));
        return project.getOwner().getId().equals(userId);
    }

    /**
     * Lança {@link ResourceNotFoundException} se o usuário não tiver nenhum vínculo com o projeto,
     * ou {@link AccessDeniedException} se o papel efetivo for inferior ao exigido.
     */
    public void requireRole(Long projectId, Long userId, ProjectRole minimum) {
        ProjectRole role = resolveRole(projectId, userId);

        if (role == null) {
            throw new ResourceNotFoundException("Projeto não encontrado");
        }

        if (!role.atLeast(minimum)) {
            throw new AccessDeniedException("Você não tem permissão para esta ação");
        }
    }

    public void requireOwner(Long projectId, Long userId) {
        if (!isOwner(projectId, userId)) {
            throw new AccessDeniedException("Apenas o dono do projeto pode realizar esta ação");
        }
    }

    /**
     * Regra "developer só edita a própria tarefa" (spec-tasks.md 6): dono e admin editam qualquer
     * tarefa; developer só a que criou ou à qual está atribuído; viewer nunca. Mesma regra vale
     * para arquivamento (spec-tasks.md 4.5: criador, responsável, dono ou admin).
     */
    public void requireTaskEditable(Task task, Long userId) {
        Long projectId = task.resolveProjectId();
        ProjectRole role = resolveRole(projectId, userId);

        if (role == null) {
            throw new ResourceNotFoundException("Tarefa não encontrada");
        }
        if (role.atLeast(ProjectRole.ADMIN)) {
            return;
        }
        if (role == ProjectRole.DEVELOPER) {
            boolean isCreator = task.getCreator().getId().equals(userId);
            boolean isAssignee = task.getAssignee() != null && task.getAssignee().getId().equals(userId);
            if (isCreator || isAssignee) {
                return;
            }
        }

        throw new AccessDeniedException("Você não tem permissão para esta ação");
    }
}
