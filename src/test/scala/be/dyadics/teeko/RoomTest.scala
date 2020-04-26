package be.dyadics.teeko

import be.dyadics.teeko.model.{Move, PlacePiece, Position}
import cats.effect._
import fs2.concurrent.Queue
import munit.FunSuite

import scala.concurrent.ExecutionContext

class RoomTest extends FunSuite {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  test("Playing a game") {

    val room = Room.apply().unsafeRunSync()
    val player1Queue = Queue.unbounded[IO, Move].unsafeRunSync()
    val player2Queue = Queue.unbounded[IO, Move].unsafeRunSync()

    val player1States = room.join("player1", player1Queue.dequeue).unsafeRunSync()
    val player2States = room.join("player2", player2Queue.dequeue).unsafeRunSync()
    val states = player1States.zip(player2States)

    // A list of moves to be played back
    val moves = Seq(
      player1Queue.offer1(PlacePiece(Position(0, 0))),
      player2Queue.offer1(PlacePiece(Position(1, 0))),
      player1Queue.offer1(PlacePiece(Position(1, 1))),
      player2Queue.offer1(PlacePiece(Position(2, 0))),
      player1Queue.offer1(PlacePiece(Position(2, 2))),
      player2Queue.offer1(PlacePiece(Position(3, 0))),
      player1Queue.offer1(PlacePiece(Position(3, 3))),
      IO.unit
    )

    val result =
      fs2.Stream
        .emits(moves)
        .evalMap(identity)
        .zipRight(states)
        .compile
        .toList
        .unsafeRunSync()

    // Check both players have the same view on the game state
    result.foreach { case (p1, p2) => assertEquals(p1, p2) }
    // Check the game finished
    assert(result.last._1.exists(_.board.isTerminal))
  }

}
