package cp20

import akka.actor._
import akka.routing.RoundRobinRouter
import util.Random

object exercise1 extends App {

    calAvg(2, 500000, 100)

    case object StartCal
    case class AvgCal(lst: List[Int])
    case class Result(res: Double)

    class WorkerActor extends Actor {
        def receive = {
            case AvgCal(lst) => sender ! Result(lst.sum.toDouble / lst.length)
        }
    }

    class MasterActor(nrOfWorkers: Int, nrOfEachPart: Int, sizeOfRandElements: Int) extends Actor {

        var avg: Double = 0.0
        var calculated: Int = 0
        val lst: List[Int] = List.fill(nrOfEachPart * nrOfWorkers)(Random.nextInt(sizeOfRandElements + 1))

        val start: Long = System.currentTimeMillis
        val workerRouter = context.actorOf(Props[WorkerActor].withRouter(RoundRobinRouter(nrOfWorkers)), name = "workerRouter")

        def receive = {
            case StartCal =>
                for (i ← 0 until nrOfWorkers) workerRouter ! AvgCal(lst.slice(i*nrOfEachPart, (i+1)*nrOfEachPart))
            case Result(res) => {
                avg += res
                calculated += 1
                if (calculated == nrOfWorkers) {
                    println("Time %s sec.".format((System.currentTimeMillis - start).toDouble/1000))
                    println(
                        "Using %s actor, the system calculated the avg. of %s integers between 0 and %s to be %s"
                            .format(nrOfWorkers, nrOfEachPart * nrOfWorkers,sizeOfRandElements,(avg / nrOfWorkers))
                    )
                    context.system.shutdown()
                }
            }
        }
    }

    def calAvg(nrOfWorkers: Int, nrOfEachPart: Int, sizeOfRandElements: Int) {

        val system = ActorSystem("cp20Exercise1")

        val cal = system.actorOf(Props(new MasterActor(nrOfWorkers, nrOfEachPart, sizeOfRandElements)), name = "master")

        cal ! StartCal

    }
}
