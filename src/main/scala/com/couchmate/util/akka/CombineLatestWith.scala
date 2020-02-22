package com.couchmate.util.akka

import akka.stream.{Attributes, FanInShape2, Inlet, Outlet}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}

object CombineLatestWith extends CombineLatestWithApply

trait CombineLatestWithApply {

  def apply[A1, A2, O](combineFn: (A1, A2) => O): CombineLatestWith[A1, A2, O] =
    new CombineLatestWith(combineFn)

}

class CombineLatestWith[A1, A2, O](val combineFn: (A1, A2) => O)
  extends GraphStage[FanInShape2[A1, A2, O]] {
  override def initialAttributes: Attributes = Attributes.name("CombineLatestWith")
  override val shape: FanInShape2[A1, A2, O] = new FanInShape2[A1, A2, O]("CombineLatestWith")
  def out: Outlet[O] = shape.out
  val in0: Inlet[A1] = shape.in0
  val in1: Inlet[A2] = shape.in1

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) { outer =>
    val inlet0 = new CombineLatestInlet(in0)
    val inlet1 = new CombineLatestInlet(in1)

    private var waitingForTuple = false
    private var staleTupleValues = true

    private var runningUpstreams = 2
    private def upstreamsClosed = runningUpstreams == 0

    override def preStart(): Unit = {
      pull(in0)
      pull(in1)
    }

    setHandler(
      out,
      new OutHandler {
        override def onPull(): Unit = {
          if (hasAllValues) {
            if (staleTupleValues) {
              waitingForTuple = true
            } else {
              pushOutput()
            }
          } else {
            waitingForTuple = true
          }
          tryPullAllIfNeeded()
        }
      }
    )

    setHandler(in0, inlet0)
    setHandler(in1, inlet1)

    private def hasAllValues = inlet0.hasValue&&inlet1.hasValue

    private def pushOutput(): Unit = {
      push(out, combineFn(inlet0.value, inlet1.value))
      if (upstreamsClosed) completeStage()
      staleTupleValues = true
    }

    private def tryPullAllIfNeeded(): Unit = {
      if (!hasBeenPulled(in0)) {
        tryPull(in0)
      }
      if (!hasBeenPulled(in1)) {
        tryPull(in1)
      }
    }

    private class CombineLatestInlet[T](in: Inlet[T]) extends InHandler {
      var value: T = _
      var hasValue = false

      override def onPush(): Unit = {
        value = outer.grab(in)
        hasValue = true
        outer.staleTupleValues = false
        if (outer.waitingForTuple && outer.hasAllValues) {
          outer.pushOutput()
          outer.waitingForTuple = false
          outer.tryPullAllIfNeeded()
        }
      }

      override def onUpstreamFinish(): Unit = {
        outer.runningUpstreams -= 1
        if (outer.upstreamsClosed) completeStage()
      }
    }
  }

  override def toString = "CombineLatestWith"
}
