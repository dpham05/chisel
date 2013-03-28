package Chisel
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashSet
import scala.math._
import java.io.File;
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import scala.sys.process._
import Node._
import Reg._
import ChiselError._
import Component._
import Literal._

class FloBackend extends Backend {
  var isSubNodes = true
  var isRnd = true
  override def emitDec(node: Node): String = 
    nodeName(node) + " = "

  override def emitTmp(node: Node): String = 
    emitRef(node)

  override def emitRef(node: Node): String = {
    if (node.litOf == null) {
      node match {
        case x: Lit =>
          "" + x.value

        case x: Binding =>
          emitRef(x.inputs(0))

        case x: Bits => 
          if (!node.isInObject && node.inputs.length == 1) emitRef(node.inputs(0)) else super.emitRef(node) 
          // super.emitRef(node) 

        case _ =>
          super.emitRef(node)
      }
    } else {
      "" + node.litOf.value
    }
  }

  def trueAll(dstName: String, refs: Seq[Node]): String = {
    def trueRef (k: Int): String = {
      val n = refs.length - k
      if (n == 0) "1" else if (n == 1) emitRef(refs(k)) else (dstName + "__" + k)
    }
    def doTrueAll (k: Int): String = {
      if ((refs.length - k) <= 1)
        ""
      else 
        doTrueAll(k + 1) + trueRef(k) + " = or " + emitRef(refs(k)) + " " + trueRef(k+1) + "\n"
    }
    doTrueAll(0) + dstName + " = or 1 " + trueRef(0) + "\n"
  }

  def emit(node: Node): String = {
    // println("NODE " + node)
    val nn = node.name
    /*
    if (node.name == "T181") {
      println("NODE " + node + " NAME " + nn + " HC " + node.hashCode + " LAST " + lastT181 + " = " + (node == lastT181))
      lastT181 = node
    }
    */
    node match {
      case x: Mux =>
        emitDec(x) + "mux " + emitRef(x.inputs(0)) + " " + emitRef(x.inputs(1)) + " " + emitRef(x.inputs(2)) + "\n"
      
      case o: Op => 
        emitDec(o) +
        (if (o.inputs.length == 1) {
          o.op match {
            case "~" => "not/" + node.inputs(0).width + " " + emitRef(node.inputs(0))
            case "!" => "not/" + node.inputs(0).width + " " + emitRef(node.inputs(0))
            case "-" => {
              "neg/" + node.inputs(0).width + " " + emitRef(node.inputs(0))
            }
          }
         } else {
           o.op match {
             case "<"  => "lt/"   + node.inputs(0).width + " " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case ">=" => "gte/"  + node.inputs(0).width + " " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case "<=" => "gte/"  + node.inputs(0).width + " " + emitRef(node.inputs(1)) + " " + emitRef(node.inputs(0))
             case ">"  => "lt/"   + node.inputs(0).width + " " + emitRef(node.inputs(1)) + " " + emitRef(node.inputs(0))
             case "+"  => "add/" + node.width + " " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case "-"  => "sub/" + node.width + " " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case "*"  => "mul/" + node.width + " " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case "!"  => "not/" + node.width + " " + emitRef(node.inputs(0))
             case "<<" => "lsh/" + node.width + " " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case ">>" => "rsh/" + node.width + " " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case "##" => "cat/" + node.inputs(1).width + " " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case "|"  => "or "  + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case "||" => "or "  + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case "&"  => "and " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case "&&" => "and " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case "^"  => "xor " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case "==" => "eq "  + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
             case "!=" => "neq " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(1))
           }
         }) + "\n"

      case x: Extract =>
        if (node.width < 0) println("RSH -1 NODE " + node)
        // println("EXTRACT " + node + " W " + node.width)
        emitDec(node) + "rsh/" + node.width + " " + emitRef(node.inputs(0)) + " " + emitRef(node.inputs(2)) + "\n"

      case x: Fill =>
        emitDec(x) + "fill/" + node.width + " " + emitRef(node.inputs(0)) + "\n"
        
      case x: Binding =>
        println("FOUND BINDING")
        ""

      case x: Bits =>
        if (x.inputs.length == 1) {
          // println("NAME " + x.name + " DIR " + x.dir + " COMP " + x.componentOf + " TOP-COMP " + topComponent)
          if (x.dir == OUTPUT && x.componentOf == topComponent && x.consumers.length == 0)
            emitDec(x) + (if (isRnd) "eat" else ("out/" + x.width))  + " " + emitRef(x.inputs(0)) + "\n"
          else if (node.isInObject && node.litOf == null) {
            if (x.inputs(0).isInstanceOf[Literal])
              println("LIT IN OBJECT NAME " + x.name + " " + x.consumers.length + " CONSUMERS " + x.consumers(0))
            emitDec(x) + "mov " + emitRef(x.inputs(0)) + "\n"
          } else
            ""
          // println("--> NO CONSUMERS " + x + " = " + x.consumers.length);
          // ""
        } else
          emitDec(x) + (if (x.name == "reset") "rst" else ((if (isRnd) "rnd/" else "in/")) + x.width) + "\n"

      case m: Mem[_] =>
        // for (r <- m.reads)
        //   println(">> READ " + r)
        emitDec(m) + "mem " + m.n + "\n" + trueAll(emitRef(m) + "__is_all_read", m.reads)

      case r: MemRead[_] =>
        val w = r.mem.writes(0);
        // var rw: MemWrite[_] = null
        // println("NUM WRITES " + r.mem.writes.length)
        // for (i <- 0 until r.mem.writes.length)
        //   if (r.mem.writes(i).isReal)
        //     rw = r.mem.writes(i)
        // println("  REAL-WRITE " + rw)
        // println("  READ " + r + " HASH " + r.hashCode + " NAME " + r.name + " subnodeNode " + r.subnodeNode + " HASH " + r.subnodeNode.hashCode)
        emitRef(r) + "__is_read = and " + emitRef(w) + "__write " + emitRef(r.cond) + "\n" +
        emitDec(r) + "rd " + emitRef(r) + "__is_read" + " " + emitRef(r.mem) + " " + emitRef(r.addr) + "\n" // emitRef(m.mem) 

      case w: MemWrite[_] =>
        if (w.inputs.length == 2)
          return ""
        emitRef(w) + "__is_wr" + " = and " + emitRef(w.mem) + "__is_all_read" + " " + emitRef(w.cond) + "\n" + 
        emitDec(w) + "wr " + emitRef(w) + "__is_wr" + " " + emitRef(w.mem) + " " + emitRef(w.addr) + " " + emitRef(w.data) + "\n" +
        emitRef(w) + "__write0 = reg 1 " + emitRef(w) + "\n" +
        emitRef(w) + "__write = or 1 " + emitRef(w) + "__write0" + "\n"       

      case x: Reg => // TODO: need resetVal treatment
        (if (x.isReset) 
          (emitRef(x) + "__update = mux " + emitRef(x.inputs.last) + " " + emitRef(x.resetVal) + " " + emitRef(x.updateVal)) 
         else "") +
        emitDec(x) + "reg " + (if (x.isEnable) emitRef(x.enableSignal) else "1") + " " + 
          (if (x.isReset) (emitRef(x) + "__update") else emitRef(x.updateVal)) + "\n"

      case x: Log2 => // TODO: log2 instruction?
        emitDec(x) + "log2/" + x.width + " " + emitRef(x.inputs(0)) + "\n"

      case c: Cat =>
        emitDec(c) + "cat/" + c.inputs(1).width + " " + emitRef(c.inputs(0)) + " " + emitRef(c.inputs(1)) + "\n"
        
      case l: Literal =>
        ""
      case _ =>
        println("NO EMITTER FOR " + node)
        ""
    }
  }

  def genSubNodes(c: Component, nodes: Seq[Node]) = {
    val walked = new HashSet[Node]
    for (n <- nodes) {
      n.getSubNodes
      for (sn <- n.subnodes)
        walked += sn
    }
    walked
  }

  def nameNode(c: Component, m: Node) = {
    if (m.name != "" && !(m == c.reset) && !(m.component == null)) {
      // only modify name if it is not the reset signal or not in top component
      if(!m.isSetComponentName && (m.name != "reset" || !(m.component == c))) {
        // print("NODE(" + m.hashCode + ") " + m.name + " NAMING USING " + m.component.getPathName);
	m.name = m.component.getPathName + "__" + m.name;
        m.isSetComponentName = true
        // println(" -> " + m.name)
      }
    }
  }
  def renameSubnode(c: Component, node: Node) = {
  }

  /*
  def renameNodes(c: Component, nodes: Seq[Node]) = {
    for (m <- nodes) {
      m match {
        case l: Literal => ;
        case any        => 
          if (isSubNodes) {
            if (m.isInstanceOf[MemRead[_]])
              println("BEG RENAME(" + m.hashCode + ") " + m + " NAME " + m.name + " SUBNODES " + m.subnodes.length)
            for (i <- 0 until m.subnodes.length) {
              val snn = m.subnodes(i).subnodeNode;
              if (snn != null)
                nameNode(c, snn)
              if (m.isInstanceOf[MemRead[_]])
                println("  SNN " + snn + " NAME " + snn.name + " SN-NAME " + m.subnodes(i).name)
              if (snn != null && m.subnodes(i).name == "") {
                m.subnodes(i).setName(nodeName(snn) + (if (m.subnodes.length > 1) ("__s" + i) else ""))
                if (snn.isInstanceOf[MemRead[_]])
                  println("  RENAME NODE(" + m.hashCode + ") " + m + " SNN(" + snn.hashCode + ") " + snn + " NAME " + snn.name + " SUBNODES " + snn.subnodes.length)
              } // else
                // println("M " + m + " I " + i + " SN " + m.subnodes(i) + " SNN " + snn);
              // print("  SNN(" + snn.hashCode + ") " + snn.name);
              // println(" SUBNODE(" + m.subnodes(i).hashCode + ") NAME "+ m.subnodes(i).name)
            }
          } else {
            nameNode(c, m)
          }
        if (m.isInstanceOf[MemRead[_]])
          println("END RENAME(" + m.hashCode + ") " + m + " NAME " + m.name + " SUBNODES " + m.subnodes.length)
      }
    }
  }
  */

  def renameNodes(c: Component, nodes: Seq[Node]) = {
    for (m <- nodes) {
      m match {
        case l: Literal => ;
        case any        => 
          val snn = m.subnodeNode;
          if (snn != null)
            nameNode(c, snn)
          // else
          //   println("SNN NOT FOUND " + m)
          if (snn != null && m.name == "") {
            m.setName(nodeName(snn) + (if (snn.subnodes.length > 1) ("__s" + m.subnodeIndex) else ""))
          }
      }
    }
  }

  override def elaborate(c: Component): Unit = {
    components.foreach(_.elaborate(0));
    for (c <- components)
      c.markComponent();
    c.genAllMuxes;
    components.foreach(_.postMarkNet(0));
    val base_name = ensure_dir(targetDir)
    val out = new java.io.FileWriter(base_name + c.name + ".flo");
    topComponent = c;
    assignResets()
    c.inferAll();
    if(saveWidthWarnings)
      widthWriter = new java.io.FileWriter(base_name + c.name + ".width.warnings")
    c.forceMatchingWidths;
    c.removeTypeNodes()
    if(!ChiselErrors.isEmpty){
      for(err <- ChiselErrors)	err.printError;
      throw new IllegalStateException("CODE HAS " + ChiselErrors.length + " ERRORS");
      return
    }
    collectNodesIntoComp(c)
    transform(c, transforms)
    c.traceNodes();
    if(!ChiselErrors.isEmpty){
      for(err <- ChiselErrors)	err.printError;
      throw new IllegalStateException("CODE HAS " + ChiselErrors.length + " ERRORS");
      return
    }
    if(!dontFindCombLoop) c.findCombLoop();
    for (cc <- components) {
      if (!(cc == c)) {
        c.mods       ++= cc.mods;
        c.asserts    ++= cc.asserts;
        c.blackboxes ++= cc.blackboxes;
        c.debugs     ++= cc.debugs;
      }
    }
    c.findConsumers();
    c.verifyAllMuxes;
    if(!ChiselErrors.isEmpty){
      for(err <- ChiselErrors)	err.printError;
      throw new IllegalStateException("CODE HAS " + ChiselErrors.length + " ERRORS");
      return
    }
    c.collectNodes(c);
    def updateMems(): ArrayBuffer[Node] = {
      println("SEARCHING FOR MEM")
      val res = new ArrayBuffer[Node]();
      for (mod <- c.omods) {
        mod match {
          case m: Mem[_] => m.writes.clear(); m.reads.clear(); 
          case _ =>;
        }
      }
      for (mod <- c.omods) {
        mod match {
          case w: MemWrite[_] => w.addWrite; res += w.subnodeNode; // println("ADDING WRITE " + w);
          case r: MemRead[_]  => r.addRead;  res += r.subnodeNode; // println("ADDING READ " + r);
          case _ =>;
        }
      }
      res
    }
    if (isSubNodes) {
      val walked = genSubNodes(c, c.mods);
      // renameNodes(c, c.mods);
      println("FINDING SUBNODES")
      c.findSubNodeOrdering(); // search from roots  -- create omods
      for (m <- c.omods)
        m.addConsumers
      println("RENAMING SUBNODES")
      // renameNodes(c, c.omods);
      val mem_mods = updateMems()
      // renameNodes(c, mem_mods)
    } else {
      c.findOrdering(); // search from roots  -- create omods
      updateMems()
      renameNodes(c, c.omods);
    }
    if (isReportDims) {
      val (numNodes, maxWidth, maxDepth) = c.findGraphDims();
      println("NUM " + numNodes + " MAX-WIDTH " + maxWidth + " MAX-DEPTH " + maxDepth);
    }

    for (m <- c.omods) 
      out.write(emit(m))
    out.close();
    if(saveComponentTrace)
      printStack
  }

  override def wordBits = 32
}
