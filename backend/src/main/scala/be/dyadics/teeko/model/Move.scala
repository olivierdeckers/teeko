package be.dyadics.teeko.model

sealed trait Move

case class PlacePiece(position: Position) extends Move
case class MovePiece(from: Position, to: Position) extends Move

case class PlayerMove(player: Player, move: Move)
