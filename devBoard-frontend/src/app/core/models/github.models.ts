import { BoardResponse } from './board.models';
import { PageResponse } from './page-response.model';
export interface GithubRepoResponse {
  id: number; fullName: string; description?: string; url: string; defaultBranch: string; linked: boolean; privateRepository: boolean;
}
export interface GithubSettingsResponse {
  board: BoardResponse;
  moveOnCommit: boolean; moveOnPrOpen: boolean; moveOnPrMerge: boolean;
  importIssues: boolean; closeIssueOnDone: boolean; branchPattern: string;
}
export interface GithubSettingsRequest {
  moveOnCommit: boolean; moveOnPrOpen: boolean; moveOnPrMerge: boolean;
  importIssues: boolean; closeIssueOnDone: boolean; branchPattern: string;
  defaultBaseBranch: string; watchedBranches: string[];
}
export interface GithubDestination { projectId: number; projectName: string; boardId: number; boardName: string; }
export type GithubRepoPage = PageResponse<GithubRepoResponse>;
