package org.apache.openwhisk.core.entity

import org.apache.openwhisk.core.entity.bidLimit.{StorageBidLimit}
import spray.json.{DefaultJsonProtocol, JsValue, RootJsonFormat, deserializationError}

import scala.util.Try

/**
 * Abstract type for limits on triggers and actions. This may
 * expand to include global limits as well (for example limits
 * that require global knowledge).
 */
protected[entity] abstract class Bids {
  protected[entity] def toJson: JsValue
  override def toString = toJson.compactPrint
}

/**
 * Limits on a specific action. Includes the following properties
 * {
 *   timeout: maximum duration in msecs an action is allowed to consume in [100 msecs, 5 minutes],
 *   memory: maximum memory in megabytes an action is allowed to consume within system limit, default [128 MB, 512 MB],
 *   logs: maximum logs line in megabytes an action is allowed to generate [10 MB],
 *   concurrency: maximum number of concurrently processed activations per container [1, 200]
 * }
 *
 */
protected[core] case class ActionBids(bids: List[StorageBidLimit] = List(StorageBidLimit()))
    extends Bids {
  override protected[entity] def toJson = ActionBids.serdes.write(this)
}

/**
 * Limits on a specific trigger. None yet.
 */
protected[core] case class TriggerLimitsBids protected[core] () extends Limits {
  override protected[entity] def toJson: JsValue = TriggerLimitsBids.serdes.write(this)
}

protected[core] object ActionBids extends ArgNormalizer[ActionBids] with DefaultJsonProtocol {

  override protected[core] implicit val serdes = new RootJsonFormat[ActionBids] {
    val helper = jsonFormat1(ActionBids.apply)

    def read(value: JsValue) = {
      val obj = Try {
        value.asJsObject.convertTo[Map[String, JsValue]]
      } getOrElse deserializationError("no valid json object passed")

      val bids = obj.get("bids") map { item => item.convertTo[List[StorageBidLimit]]} getOrElse deserializationError("list storage is missing")

      ActionBids(bids)
    }

    def write(a: ActionBids) = helper.write(a)
  }
}

protected[core] object TriggerLimitsBids extends ArgNormalizer[TriggerLimitsBids] with DefaultJsonProtocol {

  override protected[core] implicit val serdes = jsonFormat0(TriggerLimitsBids.apply _)
}
