import { UserResponse } from './auth.models';
import { ProjectRole } from './project.models';

export type InviteStatus = 'PENDING' | 'ACCEPTED' | 'EXPIRED' | 'REVOKED';

export interface CreateEmailInviteRequest {
  email: string;
  role: ProjectRole;
}

export interface InviteResponse {
  id: number;
  projectId: number;
  type: 'EMAIL' | 'LINK';
  email?: string;
  role: ProjectRole;
  status: InviteStatus;
  acceptanceUrl: string;
  invitedBy: UserResponse;
  uses: number;
  maxUses?: number;
  expiresAt: string;
  createdAt: string;
}

export interface InvitePublicResponse {
  projectName: string;
  inviterName: string;
  role: ProjectRole;
  expiresAt: string;
}

export interface GithubImportResponse {
  added: number;
  invited: number;
  ignored: number;
}
