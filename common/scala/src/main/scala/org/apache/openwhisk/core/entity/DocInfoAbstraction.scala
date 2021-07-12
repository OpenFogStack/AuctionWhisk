package org.apache.openwhisk.core.entity

import org.apache.openwhisk.core.entity.bidLimit.StorageBidLimit
import spray.json.{JsValue, RootJsonFormat, _}

case class DocInfoAbstraction(_id: String, _rev: String, bids: List[StorageBidLimit]) {
  def toDocInfo(): DocInfo = {
    DocInfo(DocId(_id), DocRevision(_rev.replace("\"", "")))
  }
}

object MyJsonProtocol extends DefaultJsonProtocol {

  def toDocInfo (docinfoabstraction: DocInfoAbstraction): DocInfo = {
    DocInfo(DocId(docinfoabstraction._id), DocRevision(docinfoabstraction._rev))
  }

  implicit object DocInfoAbstractionProtocol extends RootJsonFormat[List[DocInfoAbstraction]] {

    implicit val docInfoAbstractionFormat = jsonFormat3(DocInfoAbstraction)

    override def read(json: JsValue): List[DocInfoAbstraction] = {
      println("in reading")
      json.convertTo[JsArray].elements.map(item => item.convertTo[DocInfoAbstraction]).toList
    }

    override def write(obj: List[DocInfoAbstraction]): JsValue = ???
  }

  }
