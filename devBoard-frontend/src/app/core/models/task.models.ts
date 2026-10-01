import { UserResponse } from './auth.models';

export type TaskType = 'DEV' | 'QA' | 'DESIGN' | 'DOCUMENTATION' | 'OPERATIONAL' | 'OTHER';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export type TaskActivityType =
  | 'GITHUB_ISSUE_LINKED'
  | 'BRANCH_CREATED'
  | 'COMMIT_RECEIVED'
  | 'PR_OPENED'
  | 'PR_MERGED'
  | 'PR_CLOSED'
  | 'LABELS_CHANGED'
  | 'WIP_EXCEEDED'
  | 'CREATED'
  | 'TITLE_CHANGED'
  | 'DESCRIPTION_CHANGED'
  | 'TYPE_CHANGED'
  | 'PRIORITY_CHANGED'
  | 'ASSIGNEE_CHANGED'
  | 'COLLABORATORS_CHANGED'
  | 'DUE_DATE_CHANGED'
  | 'ESTIMATE_CHANGED'
  | 'MOVED'
  | 'COMMENTED'
  | 'ARCHIVED';

export interface CreateTaskRequest {
  columnId: number;
  title: string;
  description?: string;
  type?: TaskType;
  priority?: TaskPriority;
  assigneeId?: number;
  collaboratorIds?: number[];
  dueDate?: string;
  estimate?: number;
  githubIssueId?: number;
  labelIds?: number[];
}

export interface LabelResponse {
  id: number;
  projectId: number;
  name: string;
  color: string;
  description?: string;
}

export interface GithubTaskFields {
  githubIssueId?: number;
  githubIssueUrl?: string;
  githubPrId?: number;
  githubPrUrl?: string;
  githubPrState?: 'OPEN' | 'MERGED' | 'CLOSED';
  branch?: string;
  labels: LabelResponse[];
}

export interface UpdateTaskRequest {
  title: string;
  description?: string;
  type?: TaskType;
  priority?: TaskPriority;
  assigneeId?: number;
  collaboratorIds?: number[];
  dueDate?: string;
  estimate?: number;
}

export interface MoveTaskRequest {
  columnId: number;
  position: number;
}

export interface TaskSummaryResponse extends GithubTaskFields {
  id: number;
  title: string;
  type: TaskType;
  priority: TaskPriority;
  assignee?: UserResponse;
  dueDate?: string;
  estimate?: number;
  position: number;
  commentCount: number;
}

export interface CreateCommentRequest {
  content: string;
}

export interface UpdateCommentRequest {
  content: string;
}

export interface CommentResponse {
  id: number;
  taskId: number;
  author: UserResponse;
  content: string;
  edited: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface ActivityResponse {
  id: number;
  taskId: number;
  author?: UserResponse;
  type: TaskActivityType;
  description: string;
  metadata?: string;
  createdAt: string;
}

export interface TaskResponse extends GithubTaskFields {
  id: number;
  projectId: number;
  boardId: number;
  columnId: number;
  columnName: string;
  title: string;
  description?: string;
  position: number;
  type: TaskType;
  priority: TaskPriority;
  assignee?: UserResponse;
  collaborators: UserResponse[];
  creator: UserResponse;
  dueDate?: string;
  estimate?: number;
  archived: boolean;
  archivedAt?: string;
  completedAt?: string;
  createdAt: string;
  updatedAt: string;
  comments: CommentResponse[];
  activities: ActivityResponse[];
}
