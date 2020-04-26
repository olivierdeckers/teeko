package be.dyadics.teeko.model

import be.dyadics.teeko.model.GameState.{CannotMovePieceFrom, InvalidMove, NotPlayersTurn, PositionAlreadyTaken}

case class Position(row: Int, col: Int)

sealed trait GameState {
  def turn: Player
  def board: Board

  protected def canMoveTo(player: Player, pos: Position): Either[InvalidMove, Unit] =
    for {
      _ <- Either.cond(turn == player, (), NotPlayersTurn)
      _ <- Either.cond(board.cell(pos) == Cell.Empty, (), PositionAlreadyTaken)
    } yield ()
}

case class PlacingGameState(turn: Player, board: Board) extends GameState {
  def move(player: Player, pos: Position): Either[InvalidMove, GameState] =
    canMoveTo(player, pos).map { _ =>
      val nextState = if (board.containsAllStones) MovingGameState else PlacingGameState
      nextState(Player.otherPlayer(player), board.withCell(pos, player.cell))
    }
}

case class MovingGameState(turn: Player, board: Board) extends GameState {

  def canMoveFrom(player: Player, pos: Position): Either[InvalidMove, Unit] =
    Either.cond(board.cell(pos) == player.cell, (), CannotMovePieceFrom)

  def move(player: Player, from: Position, to: Position): Either[InvalidMove, GameState] =
    for {
      _ <- canMoveTo(player, to)
      _ <- canMoveFrom(player, from)
    } yield {
      MovingGameState(
        Player.otherPlayer(player),
        board.withCell(from, Cell.Empty).withCell(to, player.cell)
      )
    }

}

object GameState {
  sealed trait InvalidMove
  case object PositionAlreadyTaken extends InvalidMove
  case object NotPlayersTurn extends InvalidMove
  case object CannotMovePieceFrom extends InvalidMove

  def initial: GameState = PlacingGameState(Player.Black, Board.empty)
}
