package org.apache.openwhisk.core.entity

object NodeStorageLimit {

   def toBytes(limit: String): BigInt = {
    val arr = limit.split(" ")

    require(arr.size == 2)
    require(arr.head.toLong > 0)

    arr.tail.head match {
      case "B" => arr.head.toInt
      case "KB" => arr.head.toInt * 1000
      case "m" => arr.head.toInt * 1000 * 1000
      case "GB" => arr.head.toInt * 1000 * 1000 * 1000
      case _ => 1 // TODO throw Configuration Exception here
    }
  }
}

case class NodeStorageLimit (value: Double)
