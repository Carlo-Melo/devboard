import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BoardService } from '../../../core/services/board.service';
import { BoardResponse } from '../../../core/models/board.models';
import { ApiError } from '../../../core/models/api-error.model';

@Component({
  selector: 'app-board-view',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './board-view.component.html',
  styleUrl: './board-view.component.scss'
})
export class BoardViewComponent implements OnInit {

  board: BoardResponse | null = null;
  isLoading = true;
  errorMessage: string | null = null;

  constructor(
    private boardService: BoardService,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.isLoading = true;

    this.boardService.getById(id).subscribe({
      next: (board) => {
        this.board = board;
        this.isLoading = false;
      },
      error: (err: ApiError) => {
        this.errorMessage = err.status === 404
          ? 'Quadro não encontrado.'
          : 'Não foi possível carregar o quadro.';
        this.isLoading = false;
      }
    });
  }

  isOverWip(columnCount: number, wipLimit: number | undefined): boolean {
    return !!wipLimit && columnCount > wipLimit;
  }
}
