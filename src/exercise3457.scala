package cp20

import akka.actor._
import java.io.{IOException, File}
import akka.routing.RoundRobinRouter
import util.Random
import akka.actor.SupervisorStrategy.Restart
import util.matching.Regex
import util.matching.Regex.Match

object exercise3457 extends App {

    traverseDirectory(new File("C:\\Users\\MC\\Documents\\Courses\\Cay\\API\\scala-docs-2.9.2\\scala\\collection"), """\w+""".r, 100    )

    case object StartMaster
    case class SearchFileAction(file: File)
    case class SearchDirAction(dir: File)
    case class SearchFileResult(file: File, it: Iterator[Match])

    class SearchFileActor(reg: Regex, reportTo: ActorRef) extends Actor {

        def receive = {
            case SearchFileAction(file: File) => {
                if (file.toString == "---error file---")
                    throw new IOException("auto-generated IOException")
                else {
                    val source = scala.io.Source.fromFile(file)
                    reportTo ! SearchFileResult(file, reg.findAllMatchIn(source mkString))
                    source close()
                }
            }
        }
    }

    class TraverseDirectoryActor(dir: File, reg: Regex, nrOfFileWorkers: Int) extends Actor {

        var result: Map[String, Array[String]] = Map()

        var finishTraversing = false
        var dirLevel = 0
        var nrOfFiles = 0
        var nrOfFilesRead = 0
        var nrOfExceptions = 0
        var nrOfIOExceptions = 0
        val start: Long = System.currentTimeMillis

        val searchFileWorkerRouter = context.actorOf(Props(new SearchFileActor(reg, context.self)).withRouter(RoundRobinRouter(nrOfFileWorkers)), name = "searchFileWorkerRouter")

        def isFinished = {
            if (nrOfFilesRead % 100 == 0) println(nrOfFilesRead)
            if (finishTraversing && nrOfFiles == nrOfFilesRead + nrOfExceptions) {
                val time = (System.currentTimeMillis - start).toDouble / 1000
//                for ((m, fileNames) <- result) {
//                    println("\n'%s' is found in the following files:".format(m))
//                    for (fileName <- fileNames)
//                        println("\t%s".format(fileName))
//                }
                println("\nIt took %s sec. and %s actors to traverse the directory '%s'".format(time, nrOfFileWorkers + 1, dir.toString))
                println("There where %s words in %s files that matched the regular expression '%s'".format(result.size, nrOfFilesRead, reg.toString))
                println("There where %s Exception thrown, %s of them where IOExceptions".format(nrOfExceptions, nrOfIOExceptions))

                context.system.shutdown
            }
        }

        override val supervisorStrategy =
            OneForOneStrategy(maxNrOfRetries = -1, withinTimeRange = scala.concurrent.duration.Duration.Inf) {
                case e: IOException => {
                    nrOfExceptions += 1
                    nrOfIOExceptions += 1
                    isFinished
                    Restart
                }
                case _ => {
                    nrOfExceptions += 1
                    isFinished
                    Restart
                }
            }

        def receive = {
             case SearchDirAction(dir: File) => {
                 dirLevel += 1
                 dir.listFiles().foreach(file =>
                    if (file.isDirectory)
                        receive(SearchDirAction(file))
                    else if (file.isFile) {
                        nrOfFiles += 1
                        searchFileWorkerRouter ! SearchFileAction(if (Random.nextInt(100) == 0) new File("---error file---") else file)
                    }
                 )
                 dirLevel -= 1
                 if (dirLevel == 0) finishTraversing = true
             }
             case StartMaster => receive(SearchDirAction(dir))
             case SearchFileResult(file, it) => {
                 for (m <- it) {
                     val files: Array[String] = result getOrElse(m toString, Array[String]())
                     if (!files.contains(file.toString)) result = result updated(m.toString, files :+ file.toString)
                 }
                 nrOfFilesRead += 1
                 isFinished
            }
        }
    }

    def traverseDirectory(dir: File, reg: Regex, nrOfWorkers: Int = 10) = {
        if (dir isDirectory) {

            val system = ActorSystem("cp20Exercise3457")

            val traverse = system.actorOf(Props(new TraverseDirectoryActor(dir, reg, nrOfWorkers)), name = "master")

            traverse ! StartMaster
        } else {
            println("%s is not a directory.".format(dir))
        }
    }

}
