package be.dyadics.teeko.protocol

import be.dyadics.teeko.model.{GameState, RoomState}

sealed trait Feedback
object Feedback {
  case object UnsupportedOperation extends Feedback
  case class InvalidMove(invalidMove: GameState.InvalidMove) extends Feedback
  case class UpdatedRoomState(roomState: RoomState) extends Feedback
}
