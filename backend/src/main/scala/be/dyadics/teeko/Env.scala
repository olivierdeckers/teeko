package be.dyadics.teeko

/**
  * Copyright (C) 02/05/2020 - REstore NV
  */
object Env {
  final case class Prod(subenv: String) extends Env
  case object Dev extends Env
  case object Test extends Env
}

sealed trait Env extends Product with Serializable
