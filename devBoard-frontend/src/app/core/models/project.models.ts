import { UserResponse } from './auth.models';

export type ProjectRole = 'ADMIN' | 'DEVELOPER' | 'VIEWER';

export interface CreateProjectRequest {
  name: string;
  description?: string;
}

export interface UpdateProjectRequest {
  name: string;
  description?: string;
  watchedBranches?: string[];
  defaultBaseBranch?: string;
}

export interface ProjectSummaryResponse {
  id: number;
  name: string;
  description?: string;
  owner: UserResponse;
  memberCount: number;
  githubLinked: boolean;
  lastSyncAt?: string;
  updatedAt: string;
}

export interface ProjectMemberResponse {
  user: UserResponse;
  role: ProjectRole;
}

export interface BoardSummaryResponse {
  id: number;
  name: string;
  defaultBoard: boolean;
}

export interface ProjectResponse {
  id: number;
  name: string;
  description?: string;
  owner: UserResponse;
  currentUserRole: ProjectRole;
  currentUserOwner: boolean;
  githubRepoId?: number;
  githubRepoOwner?: string;
  githubRepoName?: string;
  githubRepoUrl?: string;
  watchedBranches: string[];
  defaultBaseBranch: string;
  archived: boolean;
  lastSyncAt?: string;
  members: ProjectMemberResponse[];
  boards: BoardSummaryResponse[];
  createdAt: string;
  updatedAt: string;
}
