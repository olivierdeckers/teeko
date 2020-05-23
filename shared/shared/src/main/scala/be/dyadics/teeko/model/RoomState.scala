package be.dyadics.teeko.model

case class RoomState(redScore: Int, blackScore: Int, gameState: GameState)
object RoomState {
  def initial: RoomState = RoomState(0, 0, GameState.initial)
}
