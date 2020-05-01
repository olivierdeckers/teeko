package be.dyadics.teeko.model

object Cell {
  case object Empty extends Cell
  case object Red extends Cell
  case object Black extends Cell
}

sealed trait Cell
