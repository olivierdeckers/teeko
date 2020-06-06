package be.dyadics.teeko

import be.dyadics.teeko.model.{GameState, Move, PlacePiece, Position, RoomState}
import be.dyadics.teeko.protocol.Feedback
import be.dyadics.teeko.protocol.Feedback.UpdatedRoomState
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

    // Wait a bit so everyone can receive the initial state from the topic before we begin playing the moves
    Thread.sleep(100)

    val states = player1States.zip(player2States)

    val player1WinsScenario = Seq(
      IO.unit, // initial state of the topic
      player1Queue.offer1(PlacePiece(Position(0, 0))),
      player2Queue.offer1(PlacePiece(Position(1, 0))),
      player1Queue.offer1(PlacePiece(Position(0, 1))),
      player2Queue.offer1(PlacePiece(Position(2, 0))),
      player1Queue.offer1(PlacePiece(Position(0, 2))),
      player2Queue.offer1(PlacePiece(Position(3, 0))),
      player1Queue.offer1(PlacePiece(Position(0, 3)))
    )

    def applyMoves(moves: Seq[IO[_]]): immutable.Seq[(Feedback, Feedback)] =
      fs2.Stream
        .emits(moves)
        .evalMap(identity)
        .map(x => {
          println(x)
          x
        })
        .zipRight(states)
        .compile
        .toList
        .unsafeRunSync()
  }

  test("should emit the initial state to both players when joining") {
    val fixture = new RoomFixture
    val Some(initial) = fixture.states.take(1).compile.last.unsafeRunSync()
    assertEquals(initial._1.asInstanceOf[UpdatedRoomState].roomState, RoomState.initial)
    assertEquals(initial._2.asInstanceOf[UpdatedRoomState].roomState, RoomState.initial)
  }

  test("Playing a game") {
    val fixture = new RoomFixture

    // A list of moves to be played back
    val moves = fixture.player1WinsScenario
    val result = fixture.applyMoves(moves)

    // Check both players have the same view on the game state
    result.foreach { case (p1, p2) => assertEquals(p1, p2) }

    // Check the game finished
    assert(result.last._1.asInstanceOf[UpdatedRoomState].roomState.gameState.board.isTerminal)
  }

  test("a new game starts after the previous is done") {
    val fixture = new RoomFixture

    val moves = fixture.player1WinsScenario :+ IO.unit
    val result = fixture.applyMoves(moves)
    result.foreach {
      case (f1, f2) =>
        println(f1)
        println(f2)
        println("")
    }

    val Seq(state) = result.takeRight(1).map(_._1)
    assertEquals(state.asInstanceOf[UpdatedRoomState].roomState, RoomState(0, 1, GameState.initial))
  }

  test("a player that rejoins receives the last state") {
    val fixture = new RoomFixture

    val moves = fixture.player1WinsScenario.take(5)
    val Seq(lastState) = fixture.applyMoves(moves).takeRight(1).map(_._1)

    val queue = Queue.unbounded[IO, Move].unsafeRunSync()
    val rejoinedPlayer = fixture.room.join("player2", queue.dequeue).unsafeRunSync()
    val Some(newInitialState) = rejoinedPlayer.take(1).compile.last.unsafeRunSync()

    assertEquals(newInitialState, lastState)
  }

}
