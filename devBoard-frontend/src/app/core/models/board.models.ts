import { TaskSummaryResponse } from './task.models';

export type ColumnRole = 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE' | 'NONE';

export interface BoardColumnResponse {
  id: number;
  name: string;
  color?: string;
  position: number;
  role: ColumnRole;
  wipLimit?: number;
  taskCount: number;
  tasks: TaskSummaryResponse[];
}

export interface BoardResponse {
  id: number;
  projectId: number;
  name: string;
  description?: string;
  defaultBoard: boolean;
  columns: BoardColumnResponse[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateBoardRequest {
  name: string;
  description?: string;
}

export interface UpdateBoardRequest {
  name: string;
  description?: string;
  defaultBoard?: boolean;
}

export interface CreateColumnRequest {
  name: string;
  color?: string;
  position?: number;
  role?: ColumnRole;
  wipLimit?: number;
}

export interface UpdateColumnRequest {
  name: string;
  color?: string;
  role?: ColumnRole;
  wipLimit?: number;
}

export interface ReorderColumnsRequest {
  columnIds: number[];
}
