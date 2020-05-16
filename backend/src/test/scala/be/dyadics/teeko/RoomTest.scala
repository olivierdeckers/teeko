package be.dyadics.teeko

import be.dyadics.teeko.model.{GameState, Move, PlacePiece, Position, RoomState}
import be.dyadics.teeko.protocol.Feedback
import be.dyadics.teeko.protocol.Feedback.{UpdatedGameState, UpdatedRoomState}
import cats.effect._
import fs2.concurrent.Queue
import munit.FunSuite

import scala.collection.immutable
import scala.concurrent.ExecutionContext

class RoomTest extends FunSuite {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  class RoomFixture {
    val room = Room[IO]().unsafeRunSync()
    val player1Queue = Queue.unbounded[IO, Move].unsafeRunSync()
    val player2Queue = Queue.unbounded[IO, Move].unsafeRunSync()

    val player1States = room.join("player1", player1Queue.dequeue).unsafeRunSync()
    val player2States = room.join("player2", player2Queue.dequeue).unsafeRunSync()
    val states = player1States.zip(player2States)

    val player1WinsScenario = Seq(
      IO.unit,
      player1Queue.offer1(PlacePiece(Position(0, 0))),
      player2Queue.offer1(PlacePiece(Position(1, 0))),
      player1Queue.offer1(PlacePiece(Position(1, 1))),
      player2Queue.offer1(PlacePiece(Position(2, 0))),
      player1Queue.offer1(PlacePiece(Position(2, 2))),
      player2Queue.offer1(PlacePiece(Position(3, 0))),
      player1Queue.offer1(PlacePiece(Position(3, 3))),
      IO.unit
    )

    def applyMoves(moves: Seq[IO[_]]): immutable.Seq[(Feedback, Feedback)] =
      fs2.Stream
        .emits(moves)
        .evalMap(identity)
        .zipRight(states)
        .compile
        .toList
        .unsafeRunSync()
  }

  test("Playing a game") {
    val fixture = new RoomFixture

    // A list of moves to be played back
    val moves = fixture.player1WinsScenario
    val result = fixture.applyMoves(moves)

    // Check both players have the same view on the game state
    result.foreach { case (p1, p2) => assertEquals(p1, p2) }
    // Check the game finished
    assert(result.last._1.asInstanceOf[UpdatedGameState].gameState.board.isTerminal)
  }

  test("a new game starts after the previous is done") {
    val fixture = new RoomFixture

    val moves = fixture.player1WinsScenario ++ List.fill(2)(IO.unit)
    val result = fixture.applyMoves(moves)

    val Seq(roomUpdate, newGameState) = result.takeRight(2).map(_._1)
    assertEquals(roomUpdate.asInstanceOf[UpdatedRoomState].roomState, RoomState(0, 1))
    assertEquals(newGameState.asInstanceOf[UpdatedGameState].gameState, GameState.initial)
  }

}
