/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.openwhisk.core.loadBalancer


import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, Uri}
import akka.http.scaladsl.model.headers.{Authorization, BasicHttpCredentials}
import akka.stream.ActorMaterializer
import org.apache.openwhisk.common._
import org.apache.openwhisk.core.WhiskConfig._
import org.apache.openwhisk.core.connector._
import org.apache.openwhisk.core.containerpool.ContainerPoolConfig
import org.apache.openwhisk.core.entity.ControllerInstanceId
import org.apache.openwhisk.core.entity._
import org.apache.openwhisk.core.invoker.InvokerProvider
import org.apache.openwhisk.core.{ConfigKeys, WhiskConfig}
import org.apache.openwhisk.spi.SpiLoader
import org.apache.openwhisk.utils.ExecutionContextFactory
import pureconfig._
import pureconfig.generic.auto._
import org.apache.openwhisk.core.entity.size._
import java.util.{UUID => Identifier}

import akka.stream.scaladsl.{Sink, Source}
import scala.util.{Failure, Success}
import akka.http.scaladsl.Http

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import com.typesafe.sslconfig.akka.AkkaSSLConfig


/**
 * Lean loadbalancer implemetation.
 *
 * Communicates with Invoker directly without Kafka in the middle. Invoker does not exist as a separate entity, it is built together with Controller
 * Uses LeanMessagingProvider to use in-memory queue instead of Kafka
 */
class LeanBalancer(config: WhiskConfig,
                   feedFactory: FeedFactory,
                   controllerInstance: ControllerInstanceId,
                   implicit val messagingProvider: MessagingProvider = SpiLoader.get[MessagingProvider])(
                    implicit actorSystem: ActorSystem,
                    logging: Logging,
                    materializer: ActorMaterializer)
  extends CommonLoadBalancer(config, feedFactory, controllerInstance) {

  /** Loadbalancer interface methods */
  override def invokerHealth(): Future[IndexedSeq[InvokerHealth]] = Future.successful(IndexedSeq.empty[InvokerHealth])

  override def clusterSize: Int = 1

  //start

  val nextNode = loadConfigOrThrow[String](ConfigKeys.nextNode)

  val badSslConfig = AkkaSSLConfig().mapSettings(s => s.withLoose(s.loose.withDisableSNI(true)))
  val badCtx = Http().createClientHttpsContext(badSslConfig)

  val poolFlow = Http().cachedHostConnectionPoolHttps[String](nextNode, connectionContext = badCtx)


  actorSystem.scheduler.schedule(1.seconds, 1.second) {
    totalActivationsDuringTimeFrame.reset()
  }

  val stackActor = actorSystem.actorOf(Props[StackActor])

  actorSystem.scheduler.scheduleOnce(10.seconds) {
    stackActor ! Start
  }

  actorSystem.scheduler.schedule(12.seconds, 50.milliseconds) {
    stackActor ! FindHighestBid
  }

  actorSystem.scheduler.schedule(12.seconds, 1.minute) {
    lastMinuteAVGExecBid.reset()
    if (lastMinuteActivationCounter.get() > 0) {
      lastMinuteAVGExecBid.accumulate(lastMinuteExecBids.get() / lastMinuteActivationCounter.get())
    }
    lastMinuteExecBids.reset()
    lastMinuteActivationCounter.reset()
  }


  val nodeId = loadConfigOrThrow[String](ConfigKeys.nodeID)

  val activationId = "02bc0353a3894badbc0353a3896bad" + nodeId.substring(nodeId.length - 3)

  val poolConfig: ContainerPoolConfig = loadConfigOrThrow[ContainerPoolConfig](ConfigKeys.containerPool)

  val invokerName = InvokerInstanceId(0, None, None, poolConfig.userMemory)

  /** 1. Publish a message to the loadbalancer */
  override def publish(action: ExecutableWhiskActionMetaData, msg: ActivationMessage)(
    implicit transid: TransactionId): Future[Future[Either[ActivationId, WhiskActivation]]] = {

    // TODO if check and call escalate

    val execBid = action.bids.filter(x => x.nodeId.equals(nodeId)).head.bidExecution.intValue()

    lastMinuteExecBids.accumulate(execBid)
    lastMinuteActivationCounter.accumulate(1)


    if (allowedActionInvokesPerTimeFrame < totalActivationsDuringTimeFrame.get()) {
      logging.info(this, "going to escalate action")
      return escalateToNextNode(action.name.name)
    }

    if (lastMinuteAVGExecBid.get() > 0 && lastMinuteAVGExecBid.intValue() > execBid) {
      logging.info(this, s"escalate to $nextNode")

      val authorization = Authorization(BasicHttpCredentials("23bc46b1-71f6-4ed5-8c54-816aa4f8c502", "123zO3xZCLrMN6v2BKK1dXYFpXlPkccOFqm12CdAsMgRU4VrNZ9lyGVCGuMDGIwP"))

      val outerfuture = Source.single((HttpRequest(
        HttpMethods.POST,
        uri = Uri("/api/v1/namespaces/_/actions/" + action.name.name + "?blocking=true"),
        entity = HttpEntity(
          ContentTypes.`application/json`,
          "{\"number\":100}"
        ),
        headers = List(authorization)

      ), Identifier.randomUUID().toString)).via(poolFlow).map {
        case (Success(response), value) => "success" + response
        case (Failure(ex), value) => "fail" + ex.printStackTrace()
      }.runWith(Sink.foreach[String](println))

      Await.result(outerfuture, 5.second) // TODO differentiate

      logging.info(this, "received response from escalating")

      return Future.successful(Future.successful(
        Left(ActivationId(activationId)))
      )
    }

    /*
    logging.info(this,s"in publish: $allowedActionInvokesPerTimeFrame < ${totalActivationsDuringTimeFrame.get()}")
    if( allowedActionInvokesPerTimeFrame * 0.8 < totalActivationsDuringTimeFrame.get()) {

      implicit val timeout = Timeout(5.seconds)
      val future = stackActor ? StackAction(transid,action, msg)
      val result = Await.result(future, 5.second)
      result match {
        case Accept => println("accepted, continue this method" + action.bids) // TODO
        case Decline => {
          logging.info(this, s"escalate to $nextNode")

          val authorization = Authorization(BasicHttpCredentials("23bc46b1-71f6-4ed5-8c54-816aa4f8c502", "123zO3xZCLrMN6v2BKK1dXYFpXlPkccOFqm12CdAsMgRU4VrNZ9lyGVCGuMDGIwP"))

          val outerfuture = Source.single((HttpRequest(
            HttpMethods.POST,
            uri = Uri("/api/v1/namespaces/_/actions/" + action.name.name +"?blocking=true"),
            entity = HttpEntity(
              ContentTypes.`application/json`,
              "{\"number\":100}"
            ),
            headers = List(authorization)

          ), Identifier.randomUUID().toString)).via(poolFlow).map {
            case (Success(response), value) => "success" + response
            case (Failure(ex), value) => "fail" + ex.printStackTrace()
          }.runWith(Sink.foreach[String](println))

          Await.result(outerfuture, 5.second) // TODO differentiate

          logging.info(this, "received response from escalating")

          return Future.successful(Future.successful(
            Left(ActivationId(activationId)))
          )

        }
      }
    }

     */

    /** 2. Update local state with the activation to be executed scheduled. */
    val activationResult = setupActivation(msg, action, invokerName)
    sendActivationToInvoker(messageProducer, msg, invokerName).map(_ => activationResult)
  }

  private def escalateToNextNode(actionName: String)(implicit transid: TransactionId) = {

    logging.info(this, s"escalate to $nextNode")
    val authorization = Authorization(BasicHttpCredentials("23bc46b1-71f6-4ed5-8c54-816aa4f8c502", "123zO3xZCLrMN6v2BKK1dXYFpXlPkccOFqm12CdAsMgRU4VrNZ9lyGVCGuMDGIwP"))

    val outerfuture = Source.single((HttpRequest(
      HttpMethods.POST,
      uri = Uri("/api/v1/namespaces/_/actions/" + actionName + "?blocking=true"),
      entity = HttpEntity(
        ContentTypes.`application/json`,
        "{\"number\":100}"
      ),
      headers = List(authorization)

    ), Identifier.randomUUID().toString)).via(poolFlow).map {
      case (Success(response), value) => "success" + response
      case (Failure(ex), value) => "fail" + ex.printStackTrace()
    }.runWith(Sink.foreach[String](println))

    Await.result(outerfuture, 60.second) // TODO differentiate

    Future.successful(Future.successful(
      Left(ActivationId(activationId)))
    )

    // evtl. die activID ersetzen durch richtige response
  }

  /** Creates an invoker for executing user actions. There is only one invoker in the lean model. */
  private def makeALocalThreadedInvoker(): Unit = {
    implicit val ec = ExecutionContextFactory.makeCachedThreadPoolExecutionContext()
    val limitConfig: ConcurrencyLimitConfig = loadConfigOrThrow[ConcurrencyLimitConfig](ConfigKeys.concurrencyLimit)
    SpiLoader.get[InvokerProvider].instance(config, invokerName, messageProducer, poolConfig, limitConfig)
  }

  makeALocalThreadedInvoker()

  override protected val invokerPool: ActorRef = actorSystem.actorOf(Props.empty)

  override protected def releaseInvoker(invoker: InvokerInstanceId, entry: ActivationEntry) = {
    // Currently do nothing
  }

  override protected def emitMetrics() = {
    super.emitMetrics()
  }
}

object LeanBalancer extends LoadBalancerProvider {

  override def instance(whiskConfig: WhiskConfig, instance: ControllerInstanceId)(
    implicit actorSystem: ActorSystem,
    logging: Logging,
    materializer: ActorMaterializer): LoadBalancer = {

    new LeanBalancer(whiskConfig, createFeedFactory(whiskConfig, instance), instance)
  }

  def requiredProperties =
    ExecManifest.requiredProperties ++
      wskApiHost
}
