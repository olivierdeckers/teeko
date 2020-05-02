package be.dyadics.teeko

import be.dyadics.teeko.Room.{Command, Feedback, _}
import be.dyadics.teeko.model._
import cats.effect._
import cats.effect.concurrent.MVar
import cats.implicits._
import cats.{Monad, MonadError}
import fs2._
import fs2.concurrent.{Queue, Topic}

import scala.util.Random

/**
A room exists while at least one person is in it. A room has an ID
A player has an id that is generated on the client side (random + local storage?), to allow re-joining the room in case of connection problems
The first player joining it initialises it with an empty game board
max 2 players can be in a room

players can send moves to the room, and they receive player-specific errors or new game states in response
  */
class Room[F[_]: Concurrent] private (
    commandQueue: Queue[F, Command],
    gameStateTopic: Topic[F, Either[Feedback, GameState]],
    players: MVar[F, Map[PlayerId, Player]]
  )(
    implicit monadError: MonadError[F, Throwable]
  ) {

  val id: String = Random.alphanumeric.take(5).toString()

  def join(playerId: PlayerId, moves: Stream[F, Move]): F[Stream[F, Either[Feedback, GameState]]] =
    for {
      players <- players.take
      player <- players.get(playerId) match {
        case Some(value) => value.pure[F]
        case None =>
          val playerToAdd: F[Player] = players.values.size match {
            case 2 =>
              monadError.raiseError[Player](
                new IllegalStateException("Already two players")
              )
            case 1 => Player.otherPlayer(players.values.head).pure[F]
            case 0 => Player.Black.asInstanceOf[Player].pure[F]
            case n =>
              monadError.raiseError[Player](
                new IllegalStateException(s"Unexpected number of players $n. Should not happen")
              )
          }
          playerToAdd.flatMap(p => {
            Room.this.players
              .put(players.updated(playerId, p))
              .map(_ => p)
          })
      }
      _ <- implicitly[Concurrent[F]]
        .start(moves.evalMap(m => commandQueue.offer1(Command(player, m))).compile.drain)
    } yield gameStateTopic.subscribe(10)

}
object Room {

//  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  type PlayerId = String

  def apply[F[_]: Concurrent](): F[Room[F]] =
    for {
      commands <- Queue.unbounded[F, Command]
      gameState <- Topic[F, Either[Feedback, GameState]](GameState.initial.asRight[Feedback])
      _ <- implicitly[Concurrent[F]]
        .start(updateAndPublishGameState(commands, gameState).compile.drain)
      players <- MVar[F].of(Map.empty[PlayerId, Player])
    } yield new Room(commands, gameState, players)

  def updateAndPublishGameState[F[_]: Monad](
      commandQueue: Queue[F, Command],
      topic: Topic[F, Either[Feedback, GameState]]
    ): Stream[F, Unit] =
    commandQueue.dequeue
      .evalScan(GameState.initial) { (state: GameState, command: Command) =>
        val newState = (state, command) match {
          case (s: PlacingGameState, Command(player, PlacePiece(pos))) =>
            s.move(player, pos).left.map(Room.InvalidMove)
          case (s: MovingGameState, Command(player, MovePiece(from, to))) =>
            s.move(player, from, to).left.map(Room.InvalidMove)
          case _ =>
            Left(UnsupportedOperation)
        }

        topic.publish1(newState) *> newState.getOrElse(state).pure[F]
      }
      .void

  case class Command(player: Player, move: Move)

  sealed trait Feedback
  case object UnsupportedOperation extends Feedback
  case class InvalidMove(invalidMove: GameState.InvalidMove) extends Feedback
}
