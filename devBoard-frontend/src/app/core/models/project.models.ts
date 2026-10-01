import { UserResponse } from './auth.models';

export type ProjectRole = 'ADMIN' | 'DEVELOPER' | 'VIEWER';

export interface CreateProjectRequest {
  name: string;
  description?: string;
}

export interface UpdateProjectRequest {
  name: string;
  description?: string;
}

export interface ProjectSummaryResponse {
  id: number;
  name: string;
  description?: string;
  owner: UserResponse;
  memberCount: number;
  boardCount: number;
  githubBoardCount: number;
  updatedAt: string;
}

export interface ProjectMemberResponse {
  id: number;
  projectId: number;
  user: UserResponse;
  role: ProjectRole;
  invitedBy?: UserResponse;
  joinedAt?: string;
}

export interface BoardSummaryResponse {
  id: number;
  name: string;
  defaultBoard: boolean;
  githubLinked: boolean;
}

export interface ProjectResponse {
  id: number;
  name: string;
  description?: string;
  owner: UserResponse;
  currentUserRole: ProjectRole;
  currentUserOwner: boolean;
  archived: boolean;
  members: ProjectMemberResponse[];
  boards: BoardSummaryResponse[];
  createdAt: string;
  updatedAt: string;
}
