package cp20

import akka.actor.{ActorSystem, Props, Actor}
import akka.routing.RoundRobinRouter
import java.io.File
import java.awt.image.BufferedImage

object exercise2 extends App {

    val dstPath: String = "C:\\Users\\MC\\Desktop\\Billeder\\Funny\\20120821_141246_inverted.jpg"
    val image: File = new File("C:\\Users\\MC\\Desktop\\Billeder\\Funny\\20120821_141246.jpg")
    invertImageColor(10, javax.imageio.ImageIO.read(image), dstPath)

    case object StartInvertImage
    case class InvertImagePart(part: Int, img: BufferedImage)
    case class Result(part: Int, img: BufferedImage)

    class WorkerActor extends Actor {
        def receive = {
            case InvertImagePart(part, img) => {
                for (x <- 0 until img.getWidth)
                    for (y <- 0 until img.getHeight)
                        img.setRGB(x, y, (16777215 - img.getRGB(x, y)) abs)
                sender ! Result(part, img)
            }
        }
    }

    class MasterActor(nrOfWorkers: Int, img: BufferedImage, dstPath: String) extends Actor {

        val imageDst: BufferedImage = new BufferedImage(img.getWidth, img.getHeight, img.getType)
        val sliceHeight: Int = img.getHeight / nrOfWorkers

        var calculated: Int = 0
        val start: Long = System.currentTimeMillis
        val workerRouter = context.actorOf(Props[WorkerActor].withRouter(RoundRobinRouter(nrOfWorkers)), name = "workerRouter")

        def receive = {
            case StartInvertImage =>
                for (i ← 0 until nrOfWorkers)
                    if (i == nrOfWorkers - 1)
                        workerRouter ! InvertImagePart(i, img.getSubimage(0, i*sliceHeight, img.getWidth, img.getHeight-(i*sliceHeight)))
                    else
                        workerRouter ! InvertImagePart(i, img.getSubimage(0, i*sliceHeight, img.getWidth, sliceHeight))
            case Result(part, res) => {
                calculated += 1
                imageDst.createGraphics().drawImage(res, 0, part*sliceHeight, null)
                if (calculated == nrOfWorkers) {
                    println("Using %s actor, the system inverted the color in %s sec.".format(nrOfWorkers, (System.currentTimeMillis - start).toDouble/1000))
                    javax.imageio.ImageIO.write(imageDst, "jpeg", new File(dstPath))
                    context.system.shutdown()
                }
            }
        }
    }

    def invertImageColor(nrOfWorkers: Int, img: BufferedImage, dstPath: String) {

        val system = ActorSystem("cp20Exercise2")

        val cal = system.actorOf(Props(new MasterActor(if (img.getHeight < nrOfWorkers) img.getHeight else nrOfWorkers, img, dstPath)), name = "master")

        cal ! StartInvertImage

    }

}
