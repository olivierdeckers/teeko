package be.dyadics.teeko.model

sealed trait Player {
  def cell: Cell
}

object Player {
  case object Red extends Player {
    override def cell: Cell = Cell.Red
  }
  case object Black extends Player {
    override def cell: Cell = Cell.Black
  }

  def otherPlayer(player: Player): Player = (Set[Player](Black, Red) - player).head
}
