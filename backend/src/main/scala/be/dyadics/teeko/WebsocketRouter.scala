package be.dyadics.teeko

import java.util.UUID

import be.dyadics.teeko.model.Move
import fs2.Stream
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import zio._
import zio.clock.Clock
import zio.stm._

class WebsocketRouter[R <: Clock] {
  import zio.interop.catz._
  type Task[A] = RIO[R, A]

  private val dsl: Http4sDsl[Task] = Http4sDsl[Task]
  import dsl._

  val routes: Task[HttpRoutes[Task]] =
    TMap.empty[String, Room[Task]].commit.map { rooms =>
      HttpRoutes.of[Task] {
        case req @ GET -> Root / "rooms" / roomId =>
          val playerId =
            req.cookies.find(_.name == "playerId").fold(UUID.randomUUID().toString)(_.content)
          for {
            roomOpt <- rooms.get(roomId).commit
            room <- roomOpt.fold(Room[Task]())(Task.succeed(_))
            _: Unit <- STM.whenM(rooms.contains(roomId).map(!_))(rooms.put(roomId, room)).commit
            queue <- fs2.concurrent.Queue.unbounded[Task, WebSocketFrame]
            moves = queue.dequeue.flatMap {
              case Text(t, _) =>
                println(s"Received a value $t")
                decode[Move](t).fold(e => Stream.eval_(Task.effect(println(e))), Stream(_))
              case f => Stream.eval_(Task.effect(println(s"unknown type $f")))
            }
            feedbackStream <- room.join(playerId, moves)
            feedbackFrames = feedbackStream
              .map(f => Text(f.asJson.noSpaces))
            webSocket <- WebSocketBuilder[Task].build(feedbackFrames, queue.enqueue)
          } yield webSocket
      }
    }

}
