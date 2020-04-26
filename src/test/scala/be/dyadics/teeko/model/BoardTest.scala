package be.dyadics.teeko.model

class BoardTest extends munit.FunSuite {

  test("Empty board is not terminal") {
    assert(!Board.empty.isTerminal)
  }

  test("xOfFour is always four long") {
    assert(Board.empty._rowsOfFour.forall(_.size == 4))
    assert(Board.empty._colsOfFour.forall(_.size == 4))
    assert(Board.empty._diagonalsOfFour.forall(_.size == 4))
    assert(Board.empty._squaresOfFour.forall(_.size == 4))
  }

  test("the amount of xOfFour is correct") {
    assert(Board.empty._rowsOfFour.size == 10)
    assert(Board.empty._colsOfFour.size == 10)
    assert(Board.empty._diagonalsOfFour.size == 8)
    assert(Board.empty._squaresOfFour.size == 16)
  }
}
