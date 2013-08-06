package main

//#imports
import language.postfixOps
import scala.concurrent.duration._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.RootActorPath
import akka.actor.Terminated
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import akka.io.IO
import spray.can.Http
import spray.can.server.Stats
import spray.util._
import spray.http._
import akka.event._
import HttpMethods._
import MediaTypes._
import scala.concurrent.Await
import scala.concurrent.Future
//#imports

//#messages
case class TransformationJob(text: String)
case class TransformationResult(text: String)
case class JobFailed(reason: String, job: TransformationJob)
case object BackendRegistration
//#messages

object TransformationFrontend {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val config =
      (if (args.nonEmpty) ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${args(0)}")
      else ConfigFactory.empty).withFallback(
        ConfigFactory.parseString("akka.cluster.roles = [frontend]")).
        withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    val frontend = system.actorOf(Props[TransformationFrontend], name = "frontend")
    implicit val actSystem = ActorSystem()
    IO(Http) ! Http.Bind(frontend, interface = "localhost", port = 8080)
  }
}

//#frontend
class TransformationFrontend extends Actor with SprayActorLogging {

  var backends = IndexedSeq.empty[ActorRef]
  var jobCounter = 0
  val duration = 30 seconds
  implicit val timeout = Timeout(duration)

   //Naive round robin routing
  var currentWorker = -1
  def nextWorker = {
    if (currentWorker >= backends.size - 1) {
      currentWorker = 0
    } else {
      currentWorker = currentWorker + 1
    }
    backends(currentWorker)
  }

  def index(s: Int) = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Akka Cluster!</h1>
          <p>{s} workers available</p>
        </body>
      </html>.toString()
    )
  )

  lazy val noWorkers = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Akka Cluster!</h1>
          <p>No workers available</p>
        </body>
      </html>.toString()
    )
  )

  def jobResponse(s: String) = HttpResponse(
    entity = HttpEntity(`text/html`,
      <html>
        <body>
          <h1>Akka Cluster!</h1>
          <p>Response: {s}</p>
        </body>
      </html>.toString()
    )
  )

  case class Message(val msg: String)

  def receive = {

    case _: Http.Connected => sender ! Http.Register(self)

    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      if (backends.isEmpty) {
        sender ! noWorkers
      } else {
        sender ! index(backends.size)
      }

    case HttpRequest(GET, Uri.Path("/work"), _, _, _) =>
      jobCounter += 1
      
      val future = nextWorker ? new TransformationJob(jobCounter + "-job")
      val result = Await.result(future, duration).asInstanceOf[TransformationJob]
      sender ! jobResponse(result.text)

    case BackendRegistration if !backends.contains(sender) =>
      context watch sender
      backends = backends :+ sender

    case Terminated(a) =>
      backends = backends.filterNot(_ == a)
  }
}
//#frontend

object TransformationBackend {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val config =
      (if (args.nonEmpty) ConfigFactory.parseString(s"akka.remote.netty.tcp.port=${args(0)}")
      else ConfigFactory.empty).withFallback(
        ConfigFactory.parseString("akka.cluster.roles = [backend]")).
        withFallback(ConfigFactory.load())

    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props[TransformationBackend], name = "backend")
  }
}

//#backend
class TransformationBackend extends Actor {

  val log = Logging(context.system, this)

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, MemberUp
  // re-subscribe when restart
  override def preStart(): Unit = cluster.subscribe(self, classOf[MemberUp])
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive = {
    case TransformationJob(text) => 
      log.info("received: " + text)
      sender ! new TransformationJob(text.toUpperCase)
    case state: CurrentClusterState =>
      state.members.filter(_.status == MemberStatus.Up) foreach register
    case MemberUp(m) => register(m)
  }

  def register(member: Member): Unit =
    if (member.hasRole("frontend"))
      context.actorSelection(RootActorPath(member.address) / "user" / "frontend") !
        BackendRegistration
}
//#backend