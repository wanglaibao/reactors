package io.reactors
package debugger
package repl



import io.reactors.common.TransferQueue
import java.io.BufferedReader
import java.io.PrintStream
import java.io.PrintWriter
import java.io.StringReader
import java.io.StringWriter
import java.util.concurrent.locks.ReentrantLock
import scala.collection._
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter._



class ScalaRepl(val system: ReactorSystem) extends Repl {
  private val monitor = system.monitor
  private val lock = new ReentrantLock
  private val startedPromise = Promise[Boolean]()
  private val pendingOutputQueue = new TransferQueue[String](monitor)
  private val commandQueue = new TransferQueue[String](monitor)
  private val outputQueue = new TransferQueue[Repl.Result](monitor)
  class ExtractableWriter {
    val localStringWriter = new StringWriter
    val localPrintWriter = new PrintWriter(localStringWriter)
    val globalOutputStream = new OutputStream {
      def write(b: Int) {
        System.out.write(b)
        localStringWriter.write(b)
      }
    }
    val globalPrintStream = new PrintStream(globalOutputStream)
    def extractPending(): String = {
      val sb = new StringBuilder
      while (!pendingOutputQueue.isEmpty)
        sb.append(pendingOutputQueue.waitUntilDequeue())
      sb.toString
    }
    def extract(): String = {
      var output = localStringWriter.getBuffer.toString
      localStringWriter.getBuffer.setLength(0)
      output = output.split("\n")
        .map(_.replaceFirst("scala> ", ""))
        .filter(_ != "     | ")
        .filter(_.trim() != "")
        .mkString("\n")
      val pendingOutput = extractPending()
      pendingOutput + output
    }
  }
  private val extractableWriter = new ExtractableWriter
  class QueueReader extends BufferedReader(new StringReader("")) {
    var continueMode = false
    override def readLine() = {
      if (continueMode) outputQueue.enqueue(
        Repl.Result(0, extractableWriter.extract(), "     | ", true))
      commandQueue.waitUntilDequeue()
    }
  }
  private val queueReader = new QueueReader
  private val repl = new ILoop(Some(queueReader), extractableWriter.localPrintWriter) {
    override def createInterpreter() {
      super.createInterpreter()
      intp.beQuietDuring {
        intp.bind("system", "io.reactors.ReactorSystem", system)
        intp.bind("println", "Any => Unit",
          (x: Any) => extractableWriter.globalPrintStream.println(x))
      }
    }
    override def processLine(line: String): Boolean = {
      val res = try {
        queueReader.continueMode = true
        super.processLine(line)
      } finally {
        queueReader.continueMode = false
      }
      val output = extractableWriter.extract()
      if (!startedPromise.isCompleted) {
        pendingOutputQueue.enqueue(output)
        startedPromise.trySuccess(true)
      } else {
        outputQueue.enqueue(Repl.Result(0, output, "scala> ", false))
      }
      res
    }
  }
  private val replThread = new Thread(s"reactors-io.${system.name}.repl-thread") {
    override def run() {
      try {
        val settings = new Settings
        settings.Yreplsync.value = true
        settings.usejavacp.value = true
        repl.process(settings)
      } catch {
        case t: Throwable =>
          t.printStackTrace()
          throw t
      }
    }
  }

  {
    // Add import command, which also triggers output of splash message.
    commandQueue.enqueue("import io.reactors._")
    // Start REPL thread.
    replThread.start()
  }

  def tpe = "Scala"

  def started = startedPromise.future

  def eval(cmd: String) = Future {
    lock.lock()
    try {
      monitor.synchronized {
        commandQueue.enqueue(cmd)
        val result = outputQueue.waitUntilDequeue()
        result
      }
    } finally {
      lock.unlock()
    }
  }

  def log(x: Any) = {
    pendingOutputQueue.enqueue(x.toString)
  }

  def flush(): String = {
    monitor.synchronized {
      val lines = mutable.Buffer[String]()
      while (!pendingOutputQueue.isEmpty)  {
        lines += pendingOutputQueue.waitUntilDequeue()
      }
      lines.mkString("\n")
    }
  }

  def shutdown() {
  }
}
