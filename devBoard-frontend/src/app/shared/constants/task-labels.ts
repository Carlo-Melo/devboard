import { TaskPriority, TaskType } from '../../core/models/task.models';

export const PRIORITY_LABELS: Record<TaskPriority, string> = {
  LOW: 'Baixa',
  MEDIUM: 'Média',
  HIGH: 'Alta',
  URGENT: 'Urgente'
};

export const PRIORITY_PILL_CLASSES: Record<TaskPriority, string> = {
  LOW: 'pill-muted',
  MEDIUM: '',
  HIGH: 'pill-warning',
  URGENT: 'pill-danger'
};

export const TYPE_LABELS: Record<TaskType, string> = {
  DEV: 'Dev',
  QA: 'QA',
  DESIGN: 'Design',
  DOCUMENTATION: 'Documentação',
  OPERATIONAL: 'Operacional',
  OTHER: 'Outro'
};
