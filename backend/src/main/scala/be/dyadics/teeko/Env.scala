package be.dyadics.teeko

object Env {
  final case class Prod(subenv: String) extends Env
  case object Dev extends Env
  case object Test extends Env
}

sealed trait Env extends Product with Serializable
