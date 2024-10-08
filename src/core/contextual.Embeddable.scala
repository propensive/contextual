package contextual

trait Embeddable:
  type Self
  type Format
  type Operand
  def embed(value: Self): Operand
