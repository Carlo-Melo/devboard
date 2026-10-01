import { BoardResponse } from './board.models';
export interface GithubRepoResponse {
  id: number; fullName: string; description?: string; url: string; defaultBranch: string; linked: boolean;
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
