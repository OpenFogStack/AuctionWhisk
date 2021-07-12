package org.apache.openwhisk.core.entity.bidLimit


import org.apache.openwhisk.core.ConfigKeys
import org.apache.openwhisk.core.entity.{ArgNormalizer, bidLimit}
import pureconfig._
import pureconfig.generic.auto._
import spray.json._

import scala.util.Try


//import scala.util.{Failure, Success, Try}

import pureconfig.generic.auto._

case class StorageBidLimitConfig(bidStorage: Double, bidExecution: Double, nodeId: String)

case class StorageBidLimit private (val bidStorage: Double, val bidExecution: Double , val nodeId: String) {
  def equals(secondParam: StorageBidLimit) =
    if(bidStorage == secondParam.bidStorage && bidExecution == secondParam.bidExecution && nodeId.equals(secondParam.nodeId)) true
    else false

}

protected[core] object StorageBidLimit extends ArgNormalizer[StorageBidLimit] {
  val config = loadConfigOrThrow[StorageBidLimitConfig](ConfigKeys.storageLimit)

  /** These values are set once at the beginning. Dynamic configuration updates are not supported at the moment. */
  protected[core] val BID_STORAGE: Double = config.bidStorage
  protected[core] val BID_EXECUTION: Double = config.bidExecution
  protected[core] val NODE_ID: String = config.nodeId

  /** A singleton CloudStorageBidLimit with default value */
  protected[core] val standardCloudStorageBidLimit = StorageBidLimit(BID_STORAGE, BID_EXECUTION, NODE_ID)

  /** Gets CloudStorageBidLimit with default value */
  protected[core] def apply(): StorageBidLimit = standardCloudStorageBidLimit

  /**
   * Creates CloudStorageBidLimit for limit, iff limit is within permissible range.
   *
   * @return CloudStorageBidLimit with limit set
   * @throws IllegalArgumentException if limit does not conform to requirements
   */
  @throws[IllegalArgumentException]
  protected[core] def apply(bidStorage: Double, bidExecution: Double, nodeId: String): StorageBidLimit = {
    require(bidStorage >= BID_STORAGE, s"bidStorage $bidStorage below allowed threshold of $BID_STORAGE")
    require(bidExecution >= BID_EXECUTION, s"bidExecution $bidExecution below allowed threshold of $BID_EXECUTION")
    new StorageBidLimit(bidStorage, bidExecution, nodeId)
  }

  override protected[core] implicit val serdes = new RootJsonFormat[StorageBidLimit] with DefaultJsonProtocol {

    def read(value: JsValue) = {

      val obj = Try {
        value.asJsObject.convertTo[Map[String, JsValue]]
      } getOrElse deserializationError("no valid json object passed")

      val nodeId = obj.get("nodeId") map { item => item.convertTo[String]} getOrElse deserializationError("node id is missing")
      val bidStorage = obj.get("bidStorage") map { item => item.convertTo[Double]} getOrElse deserializationError("bidStorage is missing")
      val bidExecution = obj.get("bidExecution") map { item => item.convertTo[Double]} getOrElse deserializationError("bidExecution is missing")

      bidLimit.StorageBidLimit(bidStorage, bidExecution, nodeId)
    }

    def write(m: StorageBidLimit) = {

      JsObject("bidStorage" -> JsNumber(m.bidStorage), "bidExecution" -> JsNumber(m.bidExecution), "nodeId" -> JsString(m.nodeId))

    }

  }
}
