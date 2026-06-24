package scodec.msgpack

import scodec._
import Attempt._
import scodec.bits.BitVector
import org.scalatest.diagrams.Diagrams
import org.scalatest.flatspec.AnyFlatSpec

abstract class TestSuite extends AnyFlatSpec with Diagrams {
  def roundtrip[A](a: A)(implicit C: Codec[A]) = {
    C.encode(a) match {
      case Failure(error) =>
        fail(error.toString())
      case Successful(encoded) =>
        C.decode(encoded) match {
          case Failure(error) =>
            fail(error.toString())
          case Successful(DecodeResult(decoded, remainder)) =>
            assert(remainder === BitVector.empty)
            assert(decoded === a)
            decoded === a
        }
    }
  }
}
