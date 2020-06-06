package be.dyadics.teeko

import be.dyadics.teeko.BoardView.CellRender
import be.dyadics.teeko.model.Cell
import cats.effect._
import cats.implicits._
import colibri.{Cancelable, Observable}
import org.scalajs.dom.window
import outwatch._
import outwatch.dsl._
import outwatch.reactive.handler._

import scala.util.Random

object MenuView {

  sealed trait MenuState extends Product with Serializable
  case object MainMenu extends MenuState
  case object JoinMenu extends MenuState
  case class SelectedRoomId(id: String) extends MenuState

  def appButton: VNode =
    button(
      marginTop := "10px",
      marginBottom := "10px",
      borderRadius := "10px",
      border := "1px solid black",
      backgroundColor := "white",
      width := "100%",
      fontSize.large
    )

  def appInput: VNode =
    input(
      borderRadius := "10px",
      width := "100%",
      marginTop := "10px",
      marginBottom := "10px",
      textAlign.center,
      fontSize.large
    )

  def container: VNode =
    div(
      width := "400px",
      maxWidth := "100%",
      marginTop := "100px",
      marginLeft.auto,
      marginRight.auto,
      display.flex,
      flexDirection.column,
      alignItems.center
    )

  def mainMenu(handler: Handler[MenuState]) = Seq(
    h1("Teeko"),
    appButton(
      "Host a game",
      onClick.use(SelectedRoomId(Random.alphanumeric.take(5).mkString.toLowerCase)) --> handler
    ),
    appButton("Join a game", onClick.use(JoinMenu) --> handler)
  )

  def joinMenu(handler: Handler[MenuState]): SyncIO[Seq[VNode]] =
    for {
      roomIdText <- Handler.create[String]
    } yield Seq(
      h1("Enter a room id"),
      appInput(placeholder := "xxxxx", onChange.value --> roomIdText),
      appButton("Join", onClick.useLatest(roomIdText).map(SelectedRoomId) --> handler),
      appButton("Back", onClick.use(MainMenu) --> handler)
    )

  def render(roomIdHandler: Handler[RoomId]): SyncIO[VNode] =
    for {
      menuStatehandler <- Handler.create[MenuState](MainMenu)
    } yield container(
      div(Cell.Red, width := "50%"),
      managedFunction { () => pipeSelectedRoomTo(menuStatehandler, roomIdHandler) },
      menuStatehandler.map {
        case MainMenu => mainMenu(menuStatehandler).pure[SyncIO]
        case JoinMenu => joinMenu(menuStatehandler)
        case _ => Seq().pure[SyncIO]
      }
    )

  private def pipeSelectedRoomTo(obs: Observable[MenuState], handler: Handler[RoomId]): Cancelable =
    obs
      .collect {
        case SelectedRoomId(id) =>
          window.history.pushState(null, null, s"#$id")
          RoomId(id)
      }
      .subscribe(handler)
}
