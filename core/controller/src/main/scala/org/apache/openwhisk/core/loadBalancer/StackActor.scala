package org.apache.openwhisk.core.loadBalancer

import akka.actor.{Actor, ActorRef}
import org.apache.openwhisk.common.TransactionId
import org.apache.openwhisk.core.ConfigKeys
import org.apache.openwhisk.core.connector.ActivationMessage
import org.apache.openwhisk.core.entity.ExecutableWhiskActionMetaData
import pureconfig.loadConfigOrThrow

class StackActor extends Actor {

  private val nodeId = loadConfigOrThrow[String](ConfigKeys.nodeID)

  override def receive: Receive = {
    case _ => {
      context.become(receive(Map(), Map()))
    }
  }

  def receive(mapExec: Map[TransactionId, (ExecutableWhiskActionMetaData, ActivationMessage)],
              mapSender :Map[TransactionId,ActorRef]): Receive = {
    case StackAction(a,b,c) =>
      val mewMap = mapExec + (a -> (b,c) )
      val newSenders = mapSender + (a -> sender())
      context.become(receive(mewMap, newSenders))
    case FindHighestBid => {
      if(!mapExec.isEmpty) {
        val collection =  mapExec.map( x => x._2._1.bids).flatten.filter(x => x.nodeId.equals(nodeId))
        if(collection.isEmpty){
          println("collection is empty, become emtpy context")
          context.become(receive(Map(), Map()))
        }
        println("max for bidExecution")
        val storageBiid = collection.maxBy(_.bidExecution)
        val highestEntry = mapExec.filter( x => {
          x._2._1.bids.contains(storageBiid)
        }).maxBy(_._1.id)

        mapSender.foreach(x => {
          println(s"send to $x")
          if(x._1 == highestEntry._1) x._2 ! Accept
          else x._2 ! Decline
        })
        context.become(receive(Map(), Map()))
      }}
  }

}

object FindHighestBid
object Start
object Accept
object Decline
object RegisterAndWait

case class StackAction(transactionId: TransactionId, executableWhiskActionMetaData: ExecutableWhiskActionMetaData, activationMessage: ActivationMessage)

