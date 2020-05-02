package be.dyadics.teeko

import be.dyadics.teeko.model.{Board, Cell, Move, PlacePiece, Position}
import cats.effect.IO
import colibri.{Observable, Observer}
import colibri.Observable._
import org.scalajs.dom.window
import outwatch._
import outwatch.dsl._
import outwatch.reactive.handler._
import outwatch.util.WebSocket
import io.circe.generic.auto._
import io.circe.syntax._

object Frontend {

  implicit object CellRender extends Render[Cell] {
    import svg._

    def chip(borderColor: String, centerColor: String): VNode = {
      svg(
        attr("viewBox") := "0 0 32 32",
        g(
          attr("fill") := borderColor,
          path(
            attr("d") := "M31.93 17.54C31.13 26.33 23.33 32.78 14.54 31.92C5.75 31.07 -0.74 23.24 0.07 14.46C0.87 5.67 8.67 -0.78 17.46 0.08C26.25 0.93 32.74 8.76 31.93 17.54Z"
          )
        ),
        g(
          attr("fill") := centerColor,
          path(
            attr("d") := "M28.37 17.2C27.76 23.85 21.74 28.72 14.94 28.06C8.14 27.4 3.11 21.47 3.72 14.81C4.33 8.15 10.35 3.29 17.15 3.95C23.95 4.6 28.98 10.54 28.37 17.2Z"
          )
        )
      )
    }

    val redChip = chip("#f60a0a", "#cf0000")
    val blackChip = chip("#323232", "#595959")

    val dot = svg(
      viewBox := "0 0 32 32",
      g(
        attr("fill") := "#000000",
        path(
          attr("d") := "M18.32 15.98C18.34 17.25 17.32 18.29 16.04 18.3C14.76 18.32 13.7 17.3 13.68 16.02C13.66 14.75 14.68 13.71 15.96 13.7C17.24 13.68 18.3 14.7 18.32 15.98Z"
        )
      )
    )

    override def render(cell: Cell): VDomModifier = {
      if (cell == Cell.Red) {
        redChip
      } else if (cell == Cell.Black) {
        blackChip
      } else {
        redChip
      }
    }
  }

  def renderBoard(board: Handler[Board], commandsObserver: Observer[String]): VNode = {
    table(
      for (row <- 0 to 4) yield {
        tr(
          for (col <- 0 to 4) yield {
            td(
              padding := "5px",
//              onClick(board.map(b => b.withCell(Position(row, col), Cell.Red))) --> board,
              onClick(Observable(PlacePiece(Position(row, col)).asInstanceOf[Move].asJson.noSpaces)) --> commandsObserver,
              board.map(b => b.cell(Position(row, col)))
            )
          }
        )
      }
    )
  }

  def main(args: Array[String]): Unit = {

    val webSocket = WebSocket(s"ws://${window.location.host}/rooms/room1")
    webSocket.observable.map { e => println(e.data) }.subscribe(Observer.empty)

    val app = for {
      commandsObserver <- webSocket.observer
      boardHandler <- Handler.create(Board.empty).toIO
      result <- OutWatch.renderInto[IO]("#app", renderBoard(boardHandler, commandsObserver))
    } yield result

    app.unsafeRunSync()
  }
}
