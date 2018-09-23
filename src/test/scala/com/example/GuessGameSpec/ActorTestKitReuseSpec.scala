package com.example.GuessGameSpec

import akka.actor.ActorSystem
import akka.testkit.{DefaultTimeout, ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

/**
  * Created by nirmalya on 9/18/18.
  */
class ActorTestKitReuseSpec  extends TestKit(ActorSystem("GuessingGameTestKit"))
  with DefaultTimeout
  with ImplicitSender
  with WordSpecLike
  with MustMatchers
  with BeforeAndAfterAll {

  /**
    * Runs after the example completes.
    */
  override def afterAll {
    TestKit.shutdownActorSystem(system, verifySystemShutdown = true)
  }
}
