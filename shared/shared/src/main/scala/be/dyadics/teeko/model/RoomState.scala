package be.dyadics.teeko.model

case class RoomState(redScore: Int, blackScore: Int)
object RoomState {
  def initial: RoomState = RoomState(0, 0)
}
