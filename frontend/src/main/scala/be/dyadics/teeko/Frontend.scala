package be.dyadics.teeko

import be.dyadics.teeko.model._
import be.dyadics.teeko.protocol.Feedback
import be.dyadics.teeko.protocol.Feedback.UpdatedRoomState
import cats.effect.SyncIO
import cats.implicits._
import colibri.Observable._
import io.circe.generic.auto._
import io.circe.parser._
import org.scalajs.dom.window
import outwatch._
import outwatch.dsl._
import outwatch.reactive.handler._
import outwatch.util.WebSocket

object Frontend {

  def main(args: Array[String]): Unit = {
    val app = for {
      selectedRoomIdHandler <- Handler.create[RoomId]
      menu <- pickRoom(selectedRoomIdHandler)
      board = selectedRoomIdHandler.mapSync(renderBoard)
      content = board.prepend(menu)
      result <- OutWatch
        .renderInto[SyncIO](
          "#app",
          div(content)
        )
    } yield result

    app.unsafeRunSync()
  }

  def parseRoomIdFromHash: Option[RoomId] =
    window.location.hash.drop(1) match {
      case s if s.length == 5 => Some(RoomId(s))
      case _ => None
    }

  def pickRoom(selectedRoomId: Handler[RoomId]): SyncIO[VNode] =
    parseRoomIdFromHash match {
      case Some(id) =>
        selectedRoomId.onNext(id)
        div.pure[SyncIO]
      case None => MenuView.render(selectedRoomId)
    }

  def renderBoard(roomId: RoomId): SyncIO[VNode] = {
    val webSocket = WebSocket(s"ws://${window.location.host}/rooms/${roomId.id}")

    val feedback = webSocket.observable
      .doOnNext { e => println(e.data) }
      .map(_.data)
      .map(x => decode[Feedback](x.asInstanceOf[String]))
      .doOnNext {
        case Left(error) => window.console.error("Could not parse feedback", error)
        case _ => ()
      }
      .collect {
        case Right(feedback) => feedback
      }
      .publish
      .refCount

    val roomState = feedback
      .collect {
        case UpdatedRoomState(roomState) => roomState
      }

    val errors = feedback
      .map {
        case Feedback.InvalidMove(move) => Some(move)
        case _ => None
      }

    for {
      commandsObserver <- SyncIO(webSocket.observer.unsafeRunSync())
      selectedPosition <- Handler.create(Option.empty[Position])
    } yield BoardView.renderBoard(selectedPosition, roomState, errors, commandsObserver)
  }

}
