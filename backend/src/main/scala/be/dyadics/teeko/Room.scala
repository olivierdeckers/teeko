package be.dyadics.teeko

import be.dyadics.teeko.Room.{Command, _}
import be.dyadics.teeko.model._
import be.dyadics.teeko.protocol.Feedback
import be.dyadics.teeko.protocol.Feedback.{UpdatedGameState, UpdatedRoomState}
import cats.{Eq, Monad}
import cats.effect._
import cats.effect.concurrent.MVar
import cats.implicits._
import fs2._
import fs2.concurrent.{Queue, Topic}

import scala.util.Random

/**
A player has an id that is generated on the client side (random + local storage?), to allow re-joining the room in case of connection problems
  */
class Room[F[_]: Concurrent] private (
    commandQueue: Queue[F, Command],
    //TODO split in a room state and game state topic, so we can provide a sensible initial value that can be read by new joiners
    roomFeedbackTopic: Topic[F, Feedback],
    playersM: MVar[F, Map[PlayerId, Player]],
    roomStateM: MVar[F, RoomState]
  ) {

  val id: String = Random.alphanumeric.take(5).toString()

  def join(playerId: PlayerId, moves: Stream[F, Move]): F[Stream[F, Feedback]] =
    for {
      player <- addPlayer(playerId)
      _ <- Concurrent[F].start(
        moves.evalMap(m => commandQueue.offer1(Command(player, m))).compile.drain
      )
      _ <- roomStateM.read //TODO remove or actually use
      //TODO if players need to rejoin, they should get the latest state instead of an initial state
      feedback = Stream.emits(
        Seq(
          UpdatedRoomState(RoomState.initial),
          UpdatedGameState(GameState.initial)
        )
      ) ++ roomFeedbackTopic.subscribe(10)
    } yield feedback

  private def addPlayer(playerId: PlayerId): F[Player] =
    for {
      players <- playersM.take
      player <- players.get(playerId) match {
        case Some(value) => value.pure[F]
        case None =>
          determinePlayerColor(players)
            .liftTo[F]
            .flatMap(p =>
              Room.this.playersM
                .put(players.updated(playerId, p))
                .map(_ => p)
            )
      }
    } yield player

}
object Room {

  type PlayerId = String

  def apply[F[_]: Concurrent](): F[Room[F]] =
    for {
      commands <- Queue.unbounded[F, Command]
      gameState <- Topic[F, Feedback](UpdatedRoomState(RoomState.initial))
      _ <- Concurrent[F].start(
        gameState.subscribe(10).map(x => println("new gamestate published" + x)).compile.drain
      )
      _ <- Concurrent[F].start(updateRoomState(commands, gameState).compile.drain)
      players <- MVar[F].of(Map.empty[PlayerId, Player])
      roomState <- MVar[F].of(RoomState.initial)
    } yield new Room(commands, gameState, players, roomState)

  implicit val gameStateEq: Eq[GameState] = (x: GameState, y: GameState) => x == y

  def updateAndPublishGameState[F[_]: Monad](
      commandQueue: Queue[F, Command],
      topic: Topic[F, Feedback]
    ): Stream[F, GameState] =
    commandQueue.dequeue
      .evalScan(GameState.initial) { (state: GameState, command: Command) =>
        val newState: Either[Feedback, GameState] = (state, command) match {
          case (s: PlacingGameState, Command(player, PlacePiece(pos))) =>
            s.move(player, pos).left.map(Feedback.InvalidMove)
          case (s: MovingGameState, Command(player, MovePiece(from, to))) =>
            s.move(player, from, to).left.map(Feedback.InvalidMove)
          case _ =>
            Left(Feedback.UnsupportedOperation)
        }

        newState.left.toOption.traverse(topic.publish1) *> newState
          .getOrElse(state)
          .pure[F]
      }
      .map(x => {
        println(s"seen $x")
        x
      })
      .changes
      .evalTap(state => topic.publish1(UpdatedGameState(state)))
      .takeThrough(!_.board.isTerminal)

  def updateRoomState[F[_]: Monad](
      commandQueue: Queue[F, Command],
      topic: Topic[F, Feedback]
    ): Stream[F, RoomState] =
    Stream
      .emit(())
      .repeat
      .flatMap(_ => {
        Stream.eval_(topic.publish1(UpdatedGameState(GameState.initial))) ++
          updateAndPublishGameState(commandQueue, topic).takeRight(1)
      })
      .scan(RoomState.initial) { (state: RoomState, terminatedGame: GameState) =>
        val winner: Option[Player] = terminatedGame.board.winner
        winner match {
          case Some(Player.Red) => state.copy(redScore = state.redScore + 1)
          case Some(Player.Black) => state.copy(blackScore = state.blackScore + 1)
          case None => throw new IllegalStateException("This should not happen")
        }
      }
      .evalTap(updatedState => topic.publish1(UpdatedRoomState(updatedState)))

  def determinePlayerColor(currentPlayers: Map[PlayerId, Player]): Either[Throwable, Player] = {
    currentPlayers.values.size match {
      case 2 => Left(new IllegalStateException("Already two players"))
      case 1 => Player.otherPlayer(currentPlayers.values.head).asRight
      case 0 => Player.Black.asInstanceOf[Player].asRight
      case n =>
        Left(new IllegalStateException(s"Unexpected number of players $n. Should not happen"))
    }
  }

  case class Command(player: Player, move: Move)
}
