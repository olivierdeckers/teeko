package be.dyadics.teeko

import cats.effect._
import cats.implicits._
import fs2._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text

import scala.concurrent.duration._

object Server extends IOApp {

  val websocketRoute = HttpRoutes.of[IO] {
    case GET -> Root / "ws" =>
      val toClient = Stream.awakeEvery[IO](1.seconds).map(_ => Text("Ping!"))
      val fromClient: Pipe[IO, WebSocketFrame, Unit] = _.evalMap {
        case Text(t, _) => IO.delay(println(t))
        case f => IO.delay(println("unknown type $f"))
      }
      WebSocketBuilder[IO].build(toClient, fromClient)
  }

  def run(args: List[String]): IO[ExitCode] = {
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(websocketRoute.orNotFound)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
