package be.dyadics.teeko.model

case class Board(private val cells: Array[Cell]) {

  def cell(pos: Position): Cell = cells(posToIndex(pos))

  def withCell(pos: Position, cell: Cell): Board =
    copy(cells = cells.updated(posToIndex(pos), cell))

  def moveCell(from: Position, to: Position): Board =
    withCell(to, cell(from)).withCell(from, Cell.Empty)

  def containsAllStones: Boolean = cells.count(_ != Cell.Empty) match {
    case 8 => true
    case _ => false
  }

  private def posToIndex(pos: Position) = pos.row * 5 + pos.col

  def isTerminal: Boolean =
    (_rowsOfFour ++ _colsOfFour ++ _diagonalsOfFour ++ _squaresOfFour).exists(r =>
      Seq(Seq(Cell.Red), Seq(Cell.Black)).contains(r.distinct)
    )

  override def toString: String =
    s"Board(isTerminal=${isTerminal}, stones=${cells.count(_ != Cell.Empty)}, cells=${cells.toList})"

  def _rowsOfFour: Seq[Seq[Cell]] =
    for {
      row <- 0 to 4
      startCol <- 0 to 1
      startIndex = (row * 5) + startCol
      endIndex = (row * 5) + startCol + 4
      indices = startIndex until endIndex
    } yield indices.map(cells)

  def _colsOfFour: Seq[Seq[Cell]] =
    for {
      col <- 0 to 4
      startRow <- 0 to 1
      startIndex = startRow * 5 + col
      endIndex = (startRow + 4) * 5 + col
      indices = startIndex until endIndex by 5
    } yield indices.map(cells)

  def _diagonalsOfFour: Seq[Seq[Cell]] = {
    val topLeftToBottomRight = for {
      startCol <- 0 to 1
      startRow <- 0 to 1
      indices = (0 to 3)
        .map(offset => (startCol + offset, startRow + offset))
        .map { case (col, row) => row * 5 + col }
    } yield indices.map(cells)

    val bottomLeftToTopRight = for {
      startCol <- 0 to 1
      startRow <- 3 to 4
      indices = (0 to 3)
        .map(offset => (startCol + offset, startRow - offset))
        .map { case (col, row) => row * 5 + col }
    } yield indices.map(cells)

    topLeftToBottomRight ++ bottomLeftToTopRight
  }

  def _squaresOfFour: Seq[Seq[Cell]] =
    for {
      startCol <- 0 to 3
      startRow <- 0 to 3
      indices = Seq((0, 0), (0, 1), (1, 0), (1, 1))
        .map { case (colOffset, rowOffset) => (startCol + colOffset, startRow + rowOffset) }
        .map { case (col, row) => row * 5 + col }
    } yield indices.map(cells)
}

object Board {
  def empty: Board = Board(Array.fill(25)(Cell.Empty))
}
