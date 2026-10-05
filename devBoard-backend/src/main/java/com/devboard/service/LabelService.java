package com.devboard.service;
import com.devboard.dto.label.*;
import com.devboard.entity.*;
import com.devboard.entity.enums.*;
import com.devboard.event.*;
import com.devboard.exception.*;
import com.devboard.mapper.LabelMapper;
import com.devboard.repository.*;
import com.devboard.security.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class LabelService {
    private final LabelRepository labels;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final BoardLabelMappingRepository mappings;
    private final PermissionService permissions;
    private final ApplicationEventPublisher events;
    @Transactional public void createDefaults(Long projectId) {
        Project p = projects.getReferenceById(projectId);
        Map.of("bug", "#d73a4a", "feature", "#0075ca", "enhancement", "#a2eeef", "documentation", "#0075ca", "urgent", "#b60205", "blocked", "#5319e7")
                .forEach((name,color) -> { Label l = new Label(); l.setProject(p); l.setName(name); l.setColor(color); labels.save(l); });
    }
    @Transactional(readOnly=true) public List<LabelResponse> list(Long projectId, Long userId) {
        permissions.requireRole(projectId, userId, ProjectRole.VIEWER);
        return labels.findByProjectIdOrderByNameAsc(projectId).stream().map(LabelMapper::response).toList();
    }
    @Transactional public LabelResponse create(Long projectId, LabelRequest r, Long userId) {
        permissions.requireRole(projectId, userId, ProjectRole.ADMIN); unique(projectId, r.name(), null);
        Label l = new Label(); l.setProject(projects.getReferenceById(projectId)); l.setName(r.name()); l.setColor(r.color()); l.setDescription(r.description());
        return LabelMapper.response(labels.save(l));
    }
    @Transactional public LabelResponse update(Long id, LabelRequest r, Long userId) {
        Label l = label(id); permissions.requireRole(l.getProject().getId(), userId, ProjectRole.ADMIN);
        unique(l.getProject().getId(), r.name(), id); l.setName(r.name()); l.setColor(r.color()); l.setDescription(r.description());
        tasks.findByLabelsId(id).forEach(t -> queue(t, userId)); return LabelMapper.response(l);
    }
    @Transactional public void delete(Long id, Long userId) {
        Label l = label(id); permissions.requireRole(l.getProject().getId(), userId, ProjectRole.ADMIN);
        for (Task t : tasks.findByLabelsId(id)) { t.getLabels().removeIf(label -> label.getId().equals(id)); queue(t, userId); }
        mappings.deleteByLabelId(id); labels.delete(l);
    }
    @Transactional public List<LabelResponse> apply(Long taskId, List<Long> ids, Long userId) {
        Task t = task(taskId); permissions.requireTaskEditable(t, userId); permissions.requireActiveBoard(t.getColumn().getBoard());
        for (Long id : ids) {
            Label l = label(id);
            if (!l.getProject().getId().equals(t.resolveProjectId())) throw new InvalidRequestException("Label pertence a outro projeto");
            t.getLabels().add(l);
        }
        queue(t, userId); return t.getLabels().stream().map(LabelMapper::response).toList();
    }
    @Transactional public void remove(Long taskId, Long labelId, Long userId) {
        Task t = task(taskId); permissions.requireTaskEditable(t, userId); permissions.requireActiveBoard(t.getColumn().getBoard());
        t.getLabels().removeIf(l -> l.getId().equals(labelId)); queue(t, userId);
    }
    private void queue(Task t, Long userId) {
        events.publishEvent(new TaskActivityEvent(t, userId, TaskActivityType.LABELS_CHANGED, "Labels atualizadas"));
        Board b = t.getColumn().getBoard();
        if (!Boolean.TRUE.equals(b.getArchived()) && b.hasGithubRepo() && t.getGithubIssueId() != null)
            events.publishEvent(new GithubJobEvent("LABELS", b.getId(), b.getGithubRepoId(),
                    b.getGithubUser() == null ? b.getProject().getOwner().getId() : b.getGithubUser().getId(), null, t.getId(), null, b.getGithubGeneration()));
    }
    private void unique(Long projectId, String name, Long id) {
        labels.findByProjectIdAndNameIgnoreCase(projectId, name).filter(l -> !Objects.equals(l.getId(), id)).ifPresent(l -> { throw new ConflictException("Nome de label já existe"); });
    }
    private Label label(Long id) { return labels.findById(id).orElseThrow(() -> new ResourceNotFoundException("Label não encontrada")); }
    private Task task(Long id) { return tasks.findByIdWithDetails(id).orElseThrow(() -> new ResourceNotFoundException("Tarefa não encontrada")); }
}
