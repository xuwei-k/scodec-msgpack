package scodec.msgpack

import scodec._

private[msgpack] trait ScodecPlatform {
  def pairCodecs[A, B](l: Codec[A], r: Codec[B]) =
    l :: r
}
