import { PageResponse } from './page-response.model';

export interface DashboardSummary {
  activeProjects: number; pendingTasks: number; overdueTasks: number; dueSoonTasks: number;
  tasksByStage: Record<string, number>; pendingByPriority: Record<string, number>;
}
export interface DashboardTask {
  id: number; title: string; projectId: number; projectName: string; boardId: number; boardName: string;
  columnName: string; columnRole: string; priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'; dueDate?: string;
}
export interface DashboardActivity {
  id: number; taskId: number; taskTitle: string; projectId: number; projectName: string;
  authorName?: string; type: string; description: string; createdAt: string;
}
export type DashboardTaskPage = PageResponse<DashboardTask>;
export type DashboardActivityPage = PageResponse<DashboardActivity>;
