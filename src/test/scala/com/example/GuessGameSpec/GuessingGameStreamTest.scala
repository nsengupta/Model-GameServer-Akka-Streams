package com.example.GuessGameSpec


import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestProbe
import org.scalatest.MustMatchers
import com.example.GuessGameSpec.ActorTestKitReuseSpec
import com.example.protocol._

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import com.example.protocol.GuessingGameAppFunctionality._
import com.example.protocol.FlowNodes._


/**
  * Created by nirmalya on 9/18/18.
  */
class GuessingGameStreamTest extends ActorTestKitReuseSpec with MustMatchers {

  implicit val materializer = ActorMaterializer()(system)
  implicit val ec: ExecutionContext = system.dispatcher

  val startRoundForMeFlow = (sessionExistenceChecker).via(guessNumberPreparator)

  "PlayerStartingAGame" should {

    "Create a flow that produces a message with same SessionID and new as passed and 0 as Round Identifier" in {

      Scorer.populateWithSessions(List("231","123","901","902","803","804"))

      val s1 = StartARound("123")

      val future = Source.single(s1).via(startRoundForMeFlow).toMat(Sink.head)(Keep.right).run()

      val result = Await.result(future, 3.seconds)


      assert(
        (result.isLeft == false) &&
          (result.right.get match {
                case RoundStarted(sessionID,roundID) => sessionID == "123" && roundID == 0
                case _                               => false
              }
          )
      )

      Scorer.cleanseAllExistingSessions
    }

    "Create a flow that produces a message with an error when a non-existent sessionID is passed" in {

      Scorer.populateWithSessions(List("231","123","901","902","803","804"))

      val s1 = StartARound("321")

      val future = Source.single(s1).via(startRoundForMeFlow).toMat(Sink.head)(Keep.right).run()

      val result = Await.result(future, 3.seconds)

      assert(
        (result.isRight == false) &&
          (result.left.get match {
            case IncorrectSessionIDProvided("321")       => true
            case _                               => false
          })
      )
    }

    "Flow produces a message with an error when a non-existent sessionID is passed" in {

      Scorer.populateWithSessions(List("231","123","901","902","803","804"))

      val s1 = StartARound("321")

      val future = Source.single(s1).via(startRoundForMeFlow).toMat(Sink.head)(Keep.right).run()

      val result = Await.result(future, 3.seconds)

      assert(
        (result.isRight == false) &&
          (result.left.get match {
            case IncorrectSessionIDProvided("321")       => true
            case _                               => false
          })
      )

      Scorer.cleanseAllExistingSessions
    }

  }

  "PlayerPlayingRoundsOfGame" should {

    "Run a flow that confirms that player has guessed right" in {

      Scorer.populateWithSessions(List("231","123","901","902","803","804"))

      val generateNumber3Only: () => (Int) = () => 3

      Scorer.attachChoiceGenerator(generateNumber3Only)

      val s1 = StartARound("803")

      val roundStartedFuture = Source.single(s1).via(startRoundForMeFlow).toMat(Sink.head)(Keep.right).run()

      val result = Await.result(roundStartedFuture, 3.seconds)

      assert(
        !result.isLeft &&
                  (result.right.get match {
                    case RoundStarted(sessionID,roundID) => sessionID == "803" && roundID == 0
                    case _                               => false
                  }
                )
      )

      val messageRoundStarted = result.right.get.asInstanceOf[RoundStarted]

      val guessedNumberVerifierFuture =
        Source.single(
                       GuessSubmittedByPlayer(
                           messageRoundStarted.sessionID,
                           messageRoundStarted.roundID,
                           guessedByPlayer = 3
                       )
        )
        .via((sessionExistenceChecker))
        .via(roundCorrectnessChecker)
        .via(guessedNumberVerifier)
        .toMat(Sink.head)(Keep.right)
        .run()


      val result2 = Await.result(guessedNumberVerifierFuture, 3.seconds)
      assert(!result2.isLeft &&
        (
          result2.right.get match {
            case m: GuessCarrier =>
              m.asInstanceOf[CorrectNumberGuessed].sessionID      == "803" &&
              m.asInstanceOf[CorrectNumberGuessed].roundID        == 0 &&
              m.asInstanceOf[CorrectNumberGuessed].playerGuessed  == 3

            case _      => false

          }
        )
      )

      Scorer.detachChoiceGenerator()
      Scorer.cleanseAllExistingSessions
    }

    "Create a flow that confirms that when a player has guessed correctly, he is given points" in {

      Scorer.populateWithSessions(List("231","123","901","902","803","804"))

      val generateNumber3Only: () => (Int) = () => 3

      Scorer.attachChoiceGenerator(generateNumber3Only)

      val s1 = StartARound("803")

      val roundStartedFuture = Source.single(s1).via(startRoundForMeFlow).toMat(Sink.head)(Keep.right).run()

      val result = Await.result(roundStartedFuture, 3.seconds)

      assert(
        !result.isLeft &&
          (result.right.get match {
            case RoundStarted(sessionID,roundID) => sessionID == "803" && roundID == 0
            case _                               => false
          }
            )
      )

      val messageRoundStarted = result.right.get.asInstanceOf[RoundStarted]

      val pointsAssignerFuture =
        Source.single(
                GuessSubmittedByPlayer(
                  messageRoundStarted.sessionID,
                  messageRoundStarted.roundID,
                  guessedByPlayer = 3
                )
        )
          .via((sessionExistenceChecker))
          .via(roundCorrectnessChecker)
          .via(guessedNumberVerifier)
          .via(pointsAssigner)
          .toMat(Sink.head)(Keep.right)
          .run()


      val result2 = Await.result(pointsAssignerFuture, 3.seconds)
      assert(!result2.isLeft &&
        (
          result2.right.get match {
            case m: PointsCarrier =>
              m.asInstanceOf[PointsEarnedThisRound].guessedByPlayer.isInstanceOf[CorrectNumberGuessed] &&
                m.asInstanceOf[PointsEarnedThisRound].pointsEarnedByGuessing == 1

            case _      => false

          }
        )
      )

      Scorer.detachChoiceGenerator()
      Scorer.cleanseAllExistingSessions
    }

    "Create a flow that calculates a player's current score correctly, when a player has guessed correctly once" in {

      Scorer.populateWithSessions(List("231","123","901","902","803","804"))

      val generateNumber3Only: () => (Int) = () => 3

      Scorer.attachChoiceGenerator(generateNumber3Only)

      val s1 = StartARound("803")

      val roundStartedFuture = Source.single(s1).via(startRoundForMeFlow).toMat(Sink.head)(Keep.right).run()

      val result = Await.result(roundStartedFuture, 3.seconds)

      assert(
        !result.isLeft &&
          (result.right.get match {
            case RoundStarted(sessionID,roundID) => sessionID == "803" && roundID == 0
            case _                               => false
          }
            )
      )

      val messageRoundStarted = result.right.get.asInstanceOf[RoundStarted]

      val currentScorePreparatorFuture =
        Source.single(
          GuessSubmittedByPlayer(
            messageRoundStarted.sessionID,
            messageRoundStarted.roundID,
            guessedByPlayer = 3
          )
        )
          .via((sessionExistenceChecker))
          .via(roundCorrectnessChecker)
          .via(guessedNumberVerifier)
          .via(pointsAssigner)
          .via(scoreBoardUpdater)
          .via(currentScorePreparator)
          .via(gameTerminationDecider)
          .via(nextGuessGenerator)
          .toMat(Sink.head)(Keep.right)
          .run()


      val result2 = Await.result(currentScorePreparatorFuture, 3.seconds)
      assert(!result2.isLeft &&
        (
          result2.right.get match {
            case m: GuessingGameMessageToAndFro =>
              m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].sessionID      == "803" &&
              m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].roundID        == 1     &&
              m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].currentScore   == 1

            case _      => false

          }
         )
      )

      Scorer.detachChoiceGenerator()
      Scorer.cleanseAllExistingSessions
    }

    "Create a flow that calculates a player's current score correctly, when a player has guessed correctly twice in a row" in {

      Scorer.populateWithSessions(List("231","123","901","902","803","804"))

      val generateNumber3Only: () => (Int) = () => 3

      Scorer.attachChoiceGenerator(generateNumber3Only)

      val s1 = StartARound("803")

      val roundStartedFuture = Source.single(s1).via(startRoundForMeFlow).toMat(Sink.head)(Keep.right).run()

      val result = Await.result(roundStartedFuture, 3.seconds)

      assert(
        !result.isLeft &&
          (result.right.get match {
            case RoundStarted(sessionID,roundID) => sessionID == "803" && roundID == 0
            case _                               => false
          }
            )
      )

      val messageRoundStarted = result.right.get.asInstanceOf[RoundStarted]

      val currentScorePreparatorFuture_FirstRound =
        Source.single(
          GuessSubmittedByPlayer(
            messageRoundStarted.sessionID,
            messageRoundStarted.roundID,
            guessedByPlayer = 3
          )
        )
          .via((sessionExistenceChecker))
          .via(roundCorrectnessChecker)
          .via(guessedNumberVerifier)
          .via(pointsAssigner)
          .via(scoreBoardUpdater)
          .via(currentScorePreparator)
          .via(gameTerminationDecider)
          .via(nextGuessGenerator)
          .toMat(Sink.head)(Keep.right)
          .run()


      val result2 = Await.result(currentScorePreparatorFuture_FirstRound, 3.seconds)

      val returnedValues =

          result2.right.get match {
            case m: GuessingGameMessageToAndFro =>
              (m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].sessionID,
                m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].roundID,
                m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].currentScore)

            case _      => ("INCORRECT_SESSION_ID",-1,-1)
          }

      val currentScorePreparatorFuture_SecondRound =
        Source.single(
          GuessSubmittedByPlayer(
            returnedValues._1,
            returnedValues._2,
            guessedByPlayer = 3
          )
        )
          .via((sessionExistenceChecker))
          .via(roundCorrectnessChecker)
          .via(guessedNumberVerifier)
          .via(pointsAssigner)
          .via(scoreBoardUpdater)
          .via(currentScorePreparator)
          .via(gameTerminationDecider)
          .via(nextGuessGenerator)
          .toMat(Sink.head)(Keep.right)
          .run()

      val result3 = Await.result(currentScorePreparatorFuture_SecondRound, 3.seconds)

      val (sessionIDReturned:String,roundIDReturned:Int,currentScore:Int) =

        result3.right.get match {
          case m: GuessingGameMessageToAndFro =>
            (m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].sessionID,
              m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].roundID,
              m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].currentScore)

          case _      => ("INCORRECT_SESSION_ID",-1,-1)
        }

      assert(sessionIDReturned == "803" && roundIDReturned == 2 && currentScore == 2)

      val roundsAndPoints = Scorer.accessRoundsForTheSession(sessionIDReturned)

      assert(roundsAndPoints.length == 3)
      assert(
        roundsAndPoints(0).guessedByServer ==  3                &&
        roundsAndPoints(0).guessedByPlayer.isEmpty == false     &&
        roundsAndPoints(0).guessedByPlayer.get == 3             &&
        roundsAndPoints(0).pointEarned.isEmpty == false         &&
        roundsAndPoints(0).pointEarned.get == 1
      )

      assert(
          roundsAndPoints(1).guessedByServer ==  3                &&
          roundsAndPoints(1).guessedByPlayer.isEmpty == false     &&
          roundsAndPoints(1).guessedByPlayer.get == 3             &&
          roundsAndPoints(1).pointEarned.isEmpty == false         &&
          roundsAndPoints(0).pointEarned.get == 1
      )

      Scorer.detachChoiceGenerator()
      Scorer.cleanseAllExistingSessions
    }

    "Create a flow that calculates a player's final score correctly, when a player has played 3 rounds" in {

      Scorer.populateWithSessions(List("231","123","901","902","803","804"))

      val generateNumber3Only: () => (Int) = () => 3

      Scorer.attachChoiceGenerator(generateNumber3Only)

      val s1 = StartARound("803")

      val roundStartedFuture = Source.single(s1).via(startRoundForMeFlow).toMat(Sink.head)(Keep.right).run()

      val result = Await.result(roundStartedFuture, 3.seconds)

      assert(
        !result.isLeft &&
          (result.right.get match {
            case RoundStarted(sessionID,roundID) => sessionID == "803" && roundID == 0
            case _                               => false
          }
            )
      )

      val messageRoundStarted = result.right.get.asInstanceOf[RoundStarted]

      val currentScorePreparatorFuture_FirstRound =
        Source.single(
          GuessSubmittedByPlayer(
            messageRoundStarted.sessionID,
            messageRoundStarted.roundID,
            guessedByPlayer = 3
          )
        )
          .via((sessionExistenceChecker))
          .via(roundCorrectnessChecker)
          .via(guessedNumberVerifier)
          .via(pointsAssigner)
          .via(scoreBoardUpdater)
          .via(currentScorePreparator)
          .via(gameTerminationDecider)
          .via(nextGuessGenerator)
          .toMat(Sink.head)(Keep.right)
          .run()


      val result2 = Await.result(currentScorePreparatorFuture_FirstRound, 3.seconds)

      val returnedValues =

        result2.right.get match {
          case m: GuessingGameMessageToAndFro =>
            (m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].sessionID,
              m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].roundID,
              m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].currentScore)

          case _      => ("INCORRECT_SESSION_ID",-1,-1)
        }

      val currentScorePreparatorFuture_SecondRound =
        Source.single(
          GuessSubmittedByPlayer(
            returnedValues._1,
            returnedValues._2,
            guessedByPlayer = 3
          )
        )
          .via((sessionExistenceChecker))
          .via(roundCorrectnessChecker)
          .via(guessedNumberVerifier)
          .via(pointsAssigner)
          .via(scoreBoardUpdater)
          .via(currentScorePreparator)
          .via(gameTerminationDecider)
          .via(nextGuessGenerator)
          .toMat(Sink.head)(Keep.right)
          .run()

      val result3 = Await.result(currentScorePreparatorFuture_SecondRound, 3.seconds)

      val (sessionIDReturned:String,roundIDReturned:Int,currentScore:Int) =

        result3.right.get match {
          case m: GuessingGameMessageToAndFro =>
            (m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].sessionID,
              m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].roundID,
              m.asInstanceOf[ScoreAndNextGuessPromptToPlayer].currentScore)

          case _      => ("INCORRECT_SESSION_ID",-1,-1)
        }

      val currentScorePreparatorFuture_ThirdRound =
        Source.single(
          GuessSubmittedByPlayer(
            sessionIDReturned,
            roundIDReturned,
            guessedByPlayer = 3
          )
        )
          .via((sessionExistenceChecker))
          .via(roundCorrectnessChecker)
          .via(guessedNumberVerifier)
          .via(pointsAssigner)
          .via(scoreBoardUpdater)
          .via(currentScorePreparator)
          .via(gameTerminationDecider)
          .via(nextGuessGenerator)
          .toMat(Sink.head)(Keep.right)
          .run()

      val result4 = Await.result(currentScorePreparatorFuture_ThirdRound, 3.seconds)

      assert (result4.isLeft == false && result4.right.get.isInstanceOf[FinalScoreToPlayer])

      val (finalSessionIDReturned:String,finalScore:Int) =

        result4.right.get match {
          case m: GuessingGameMessageToAndFro =>
            (m.asInstanceOf[FinalScoreToPlayer].sessionID,
              m.asInstanceOf[FinalScoreToPlayer].finalScore)

          case _      => ("INCORRECT_SESSION_ID",-1)
        }

      assert(finalScore == 3)

      // Because, at the end of final (3rd) round, the session's entry is removed from global scoreboard
      assert(Scorer.exists(finalSessionIDReturned) == false)


      Scorer.detachChoiceGenerator()
      Scorer.cleanseAllExistingSessions
    }

  }

}
