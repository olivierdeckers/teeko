package be.dyadics.teeko

import outwatch.dom._
import outwatch.dom.dsl._
import monix.execution.Scheduler.Implicits.global

object Frontend {
  def main(args: Array[String]): Unit = {

    OutWatch.renderInto("#app", h1("Hello World" + Shared.sharedString)).unsafeRunSync()
  }
}
