package com.example.protocol

import akka.{ Done, NotUsed }
import akka.stream.scaladsl.{ Flow, Keep, Source }
import java.util.Random

import akka.actor.ActorSystem
import akka.stream.{ ActorMaterializer, ActorMaterializerSettings }
import akka.stream.scaladsl.Sink
import com.example.protocol

import scala.concurrent.Future
import scala.util.{ Failure, Success }
import scala.concurrent.ExecutionContext.Implicits.global

trait SessionCarrier { val sessionID: String }

sealed trait ScoreCarrier { val score: Int }

trait RoundCarrier { val roundID: Int }

trait SessionAndRoundPairCarrier extends SessionCarrier with RoundCarrier

trait PointsCarrier { val pointsEarnedByGuessing: Int }

sealed trait GuessingGameMessageToAndFro extends SessionCarrier

sealed trait GuessCarrier

case class ServerPlayerGuessTriplet(guessedByServer: Int, guessedByPlayer: Option[Int] = None, pointEarned: Option[Int] = None)

sealed trait GamePlayError
case class IncorrectSessionIDProvided(sessionID: String) extends GamePlayError
case class RoundIDMismatchBetweenPlayerAndServer(sessionID: String, roundID: Int) extends GamePlayError
case class RoundIDMandatoryButNotPassed(sessionID: String) extends GamePlayError

sealed trait RoundCheckingResult
case class CarrierOfValidRound(message: GuessingGameMessageToAndFro) extends RoundCheckingResult
case class CarrierOfInvalidRound(sessionID: String, roundID: Int) extends RoundCheckingResult
case object CarrierOfNonExistentRound extends RoundCheckingResult

case class StartARound(sessionID: String) extends GuessingGameMessageToAndFro
case class RoundStarted(sessionID: String, roundID: Int) extends GuessingGameMessageToAndFro with SessionAndRoundPairCarrier
case class GuessSubmittedByPlayer(sessionID: String, roundID: Int, guessedByPlayer: Int)
  extends GuessingGameMessageToAndFro with RoundCarrier with GuessCarrier

case class AccumulatedScoreSoFar(sessionID: String, roundID: Int, score: Int) extends ScoreCarrier with SessionAndRoundPairCarrier
case class FinalScore(sessionID: String, score: Int) extends ScoreCarrier with SessionCarrier
case class EndOfThisRoundScore(sessionID: String, roundID: Int, score: Int) extends ScoreCarrier with SessionAndRoundPairCarrier

case class PointsEarnedThisRound(guessedByPlayer: GuessCarrier, pointsEarnedByGuessing: Int) extends PointsCarrier

case class CorrectNumberGuessed(sessionID: String, roundID: Int, playerGuessed: Int) extends SessionAndRoundPairCarrier with GuessCarrier
case class IncorrectNumberGuessed(sessionID: String, roundID: Int, playerGuessed: Int, serverChose: Int) extends SessionAndRoundPairCarrier with GuessCarrier
case class FailedToCheckNumberGuessed(sessionID: String, roundID: Int, clientGuessed: Int) extends SessionAndRoundPairCarrier with GuessCarrier

case class FinalScoreToPlayer(sessionID: String, finalScore: Int) extends GuessingGameMessageToAndFro
case class ScoreAndNextGuessPromptToPlayer(sessionID: String, roundID: Int, currentScore: Int) extends GuessingGameMessageToAndFro with RoundCarrier

object SessionService {
  def exists(m: SessionCarrier) = Scorer.exists(m.sessionID)
}

object GameService {
  def isCorrectlyGuessed(m: GuessSubmittedByPlayer) = {

    val roundsOfThisPlayer = Scorer.accessRoundsForTheSession(m.sessionID)

    if (roundsOfThisPlayer(m.roundID).guessedByServer == m.guessedByPlayer)
      (roundsOfThisPlayer(m.roundID), true)
    else
      (roundsOfThisPlayer(m.roundID), false)
  }
}

object RoundService {

  def roundExists(sessionID: String, roundID: Int) = Scorer.roundExists(sessionID, roundID)
}

object Scorer {

  type SessionIdentifier = String
  type RoundIdentifier = Int

  private var scoreBoard: Map[String, Vector[ServerPlayerGuessTriplet]] = Map.empty
  private def calculateScore(roundTrail: Vector[ServerPlayerGuessTriplet]): Int = {
    roundTrail.map(t => t.pointEarned.getOrElse(0)).sum
  }

  var choiceGenerator: Option[() => Int] = None

  // Required for ease of testing
  val defaultChoiceGenerator: () => Int = { () => new Random().nextInt(5) }

  // Required for ease of testing
  def attachChoiceGenerator(chooser: () => Int): Unit = { this.choiceGenerator = Some(chooser) }

  // Required for ease of testing
  def detachChoiceGenerator(): Unit = { this.choiceGenerator = None }

  // Required for ease of testing
  def populateWithSessions(sessions: List[SessionIdentifier]) = {

    scoreBoard = sessions.foldLeft(scoreBoard)((board,session) => {
      board + (session -> Vector.empty)
    })
  }

  // Required for ease of testing
  def cleanseAllExistingSessions = scoreBoard = Map.empty

  def exists(sessionID: SessionIdentifier) = scoreBoard contains(sessionID)





  def allRoundsOver(sessionIDCarrier: SessionIdentifier): Boolean =
    scoreBoard.contains(sessionIDCarrier) && (scoreBoard(sessionIDCarrier).size == 3)

  def roundExists(sessionID: SessionIdentifier, roundID: RoundIdentifier) = {
    val roundsSoFar = scoreBoard.getOrElse(sessionID, Vector.empty)
    roundsSoFar.isDefinedAt(roundID) && (roundID == (roundsSoFar.length - 1))
  }

  def accessRoundsForTheSession(sessionID: SessionIdentifier) = {

   // Position of every entry - from the head of the Vector - represents a Round.
   // Therefore, if a Round hasn't been played as yet, the Vector has no entry for that.
   //  At the beginning of a session, all we have is an empty Vector.
    scoreBoard.get(sessionID).getOrElse(Vector.empty)

  }

  def attachServerGuessWithRound(sessionID: SessionIdentifier, roundID: RoundIdentifier, guessed: Int) = {


    val allRoundsSoFar = accessRoundsForTheSession(sessionID)

    (allRoundsSoFar :+ ServerPlayerGuessTriplet(guessedByServer = guessed))

  }

  def holdServerGuessForUpcomingRound(roundData: (SessionIdentifier, RoundIdentifier, Int)): (RoundIdentifier, Int) = {

    val sessionIDAsKey = roundData._1
    val guessedNumber = roundData._2

    scoreBoard = scoreBoard
      .updated(
        sessionIDAsKey,
        attachServerGuessWithRound(roundData._1, roundData._2, roundData._3))
    (roundData._2, roundData._3)

  }

  def updateRoundWithGuessAndPoints(sessionID: SessionIdentifier, roundID: RoundIdentifier, guessedByPlayer: Int, pointsEarnedThisRound: Int) = {

    val allRoundsSoFar = accessRoundsForTheSession(sessionID)

    val updatedRoundTriple = allRoundsSoFar(roundID).copy(guessedByPlayer = Some(guessedByPlayer), pointEarned = Some(pointsEarnedThisRound))

    allRoundsSoFar.updated(roundID, updatedRoundTriple)
  }

  def addPointsObtainedThisRound(roundData: (SessionIdentifier, RoundIdentifier, Int, Int)): Int = {

    val sessionIDAsKey = roundData._1
    val currentRound = roundData._2
    val guessedByPlayer = roundData._3
    val pointEarnedThisRound = roundData._4

    scoreBoard = scoreBoard.updated(
      sessionIDAsKey,
      updateRoundWithGuessAndPoints(
        sessionIDAsKey,
        currentRound,
        guessedByPlayer,
        pointEarnedThisRound))

    calculateScore(scoreBoard(sessionIDAsKey))
  }

  def removeFromScoreBoard(sessionIDAsKey: String): Int = {
    if (!scoreBoard.contains(sessionIDAsKey))
      throw new RuntimeException(s"Attempt to remove nonexistent session $sessionIDAsKey")
    else {

      val currentScore = calculateScore(scoreBoard(sessionIDAsKey))
      scoreBoard = scoreBoard - sessionIDAsKey
      currentScore
    }
  }

}

/**
 * Created by nirmalya on 9/14/18.
 */
object GuessingGameAppFunctionality {

  val letServerChooseANumber: PartialFunction[GuessingGameMessageToAndFro, (GuessingGameMessageToAndFro,Int)] = {
    case s: StartARound =>
      val nextChoiceGenerator = Scorer.choiceGenerator.getOrElse(Scorer.defaultChoiceGenerator)
      (s, nextChoiceGenerator())
  }

  val askServerToRememberChosenNumber: PartialFunction[(GuessingGameMessageToAndFro,Int),GuessingGameMessageToAndFro] = {
    case s: (StartARound,Int) =>
      // Round starting, so RoundID is '0' below
      Scorer.holdServerGuessForUpcomingRound((s._1.sessionID, 0, s._2))
      (s._1)
  }



  val numberToOfferToPlayer: PartialFunction[GuessingGameMessageToAndFro, GuessingGameMessageToAndFro] = {

    case s: StartARound =>
      val nextChoiceByServer = Scorer.choiceGenerator.getOrElse(Scorer.defaultChoiceGenerator)
      // First round, hence RoundID == 0
      Scorer.holdServerGuessForUpcomingRound((s.sessionID, 0, nextChoiceByServer()))
      RoundStarted(s.sessionID, roundID = 0)
  }

  val assignPoints: PartialFunction[GuessCarrier, PointsCarrier] = {
    case m: CorrectNumberGuessed => PointsEarnedThisRound(m, 1)
    case n: IncorrectNumberGuessed => PointsEarnedThisRound(n, 0)
  }

  val ascertainCorrectnessOfGuessedNumber: PartialFunction[GuessingGameMessageToAndFro, GuessCarrier] = {
    case m: GuessSubmittedByPlayer =>

      val guessCheckStatus = GameService.isCorrectlyGuessed(m)

      if (GameService.isCorrectlyGuessed(m)._2 == true)
        CorrectNumberGuessed(m.sessionID, m.roundID, m.guessedByPlayer)
      else
        IncorrectNumberGuessed(m.sessionID, m.roundID, m.guessedByPlayer, guessCheckStatus._1.guessedByServer)

  }

  val isValidRound: PartialFunction[GuessingGameMessageToAndFro, RoundCheckingResult] = {

    case m: GuessingGameMessageToAndFro with RoundCarrier =>
      if (RoundService.roundExists(m.sessionID, m.roundID))
        CarrierOfValidRound(m)
      else
        CarrierOfInvalidRound(m.sessionID, m.roundID)
    case _ => CarrierOfNonExistentRound
  }

  val computeScoreUsingPointsEarnedThisRound: PartialFunction[PointsCarrier, AccumulatedScoreSoFar] = {

    case (PointsEarnedThisRound(CorrectNumberGuessed(sessionID, roundID, playerGuessed), points)) =>
      val totalScoreNow = Scorer.addPointsObtainedThisRound(sessionID, roundID, playerGuessed, points)
      AccumulatedScoreSoFar(sessionID, roundID, totalScoreNow)

    case (PointsEarnedThisRound(IncorrectNumberGuessed(sessionID, roundID, playerGuessed, serverChose), points)) =>
      val totalScoreNow = Scorer.addPointsObtainedThisRound(sessionID, roundID, playerGuessed, points)
      AccumulatedScoreSoFar(sessionID, roundID, totalScoreNow)
  }

  def createApplicableScoreCardForPlayer(accumulatedScoreSoFar: AccumulatedScoreSoFar): ScoreCarrier = {

    if (Scorer.allRoundsOver(accumulatedScoreSoFar.sessionID))
      FinalScore(accumulatedScoreSoFar.sessionID, accumulatedScoreSoFar.score)
    else
      EndOfThisRoundScore(accumulatedScoreSoFar.sessionID, accumulatedScoreSoFar.roundID, accumulatedScoreSoFar.score)
  }

  val removeSessionOnEndOfGame: PartialFunction[ScoreCarrier, ScoreCarrier] = {
    case finalScore: FinalScore =>
      Scorer.removeFromScoreBoard(finalScore.sessionID)
      finalScore
  }



  val offerANewRoundToGuessAgain: PartialFunction[ScoreCarrier, GuessingGameMessageToAndFro] = {

    case FinalScore(sessionID, score) => FinalScoreToPlayer(sessionID, score)
    case EndOfThisRoundScore(sessionID, roundID, score) =>
      val nextChoiceByServer = Scorer.choiceGenerator.getOrElse(Scorer.defaultChoiceGenerator)
      val nextRoundID = roundID + 1
      Scorer.holdServerGuessForUpcomingRound((sessionID, nextRoundID, nextChoiceByServer()))
      ScoreAndNextGuessPromptToPlayer(sessionID, nextRoundID, score)
  }

}

import GuessingGameAppFunctionality._
object FlowNodes {

  val sessionExistenceChecker: Flow[GuessingGameMessageToAndFro, Either[GamePlayError, GuessingGameMessageToAndFro], NotUsed] = {
    Flow.fromFunction(m => if (SessionService.exists(m)) Right(m) else Left(IncorrectSessionIDProvided(m.sessionID)))
  }

  val guessedNumberVerifier: Flow[Either[GamePlayError, GuessingGameMessageToAndFro], Either[GamePlayError, GuessCarrier], NotUsed] = Flow
    .fromFunction(m =>
      m match {
        case Left(x) => Left(x)
        case Right(y) => Right(ascertainCorrectnessOfGuessedNumber(y))

      })

  val pointsAssigner: Flow[Either[GamePlayError,GuessCarrier], Either[GamePlayError,PointsCarrier], _] =
    Flow.fromFunction(m => {

      m match {

        case Left(x)     => Left(x)
        case Right(y)    =>  Right(assignPoints(y))
      }

    })

  val guessNumberPreparator: Flow[Either[GamePlayError, GuessingGameMessageToAndFro], Either[GamePlayError, GuessingGameMessageToAndFro], _] = Flow
    .fromFunction(m =>
      m match {
        case Left(x) => Left(x)
        case Right(y) => Right(numberToOfferToPlayer(y))

      })

  val roundCorrectnessChecker: Flow[Either[GamePlayError, GuessingGameMessageToAndFro], Either[GamePlayError, GuessingGameMessageToAndFro], NotUsed] = {
    Flow.fromFunction(m =>
      m match {
        case Left(x) => Left(x)
        case Right(y) => isValidRound(y) match {

          case CarrierOfValidRound(m) => Right(m)
          case CarrierOfInvalidRound(s, r) => Left(RoundIDMismatchBetweenPlayerAndServer(s, r))
          case CarrierOfNonExistentRound => Left(RoundIDMandatoryButNotPassed(y.sessionID))
        }

      })
  }

  val scoreBoardUpdater: Flow[Either[GamePlayError, PointsCarrier], Either[GamePlayError, AccumulatedScoreSoFar], _] =
    Flow.fromFunction(m =>
      m match {

        case Left(x) => Left(x)
        case Right(y) => Right(computeScoreUsingPointsEarnedThisRound(y))
      })

  val currentScorePreparator: Flow[Either[GamePlayError, AccumulatedScoreSoFar], Either[GamePlayError, ScoreCarrier], _] =
    Flow.fromFunction(m => {
      m match {

        case Left(x) => Left(x)
        case Right(y) => Right(createApplicableScoreCardForPlayer(y))
      }

    })

  val gameTerminationDecider: Flow[Either[GamePlayError, ScoreCarrier], Either[GamePlayError, ScoreCarrier], _] =
    Flow.fromFunction(m => {

      m match {

        case Left(x) => Left(x)
        case Right(y) => Right(removeSessionOnEndOfGame.applyOrElse(y, identity[ScoreCarrier]))
      }

    })

  val nextGuessGenerator: Flow[Either[GamePlayError, ScoreCarrier], Either[GamePlayError, GuessingGameMessageToAndFro], _] =
    Flow.fromFunction(m => {

      m match {

        case Left(x) => Left(x)
        case Right(y) => Right(offerANewRoundToGuessAgain(y))

      }

    })

  val nextChoiceGenerator: Flow[Either[GamePlayError, GuessingGameMessageToAndFro], Either[GamePlayError, (GuessingGameMessageToAndFro,Int)], _] =
    Flow.fromFunction(m =>
      m match {
        case Left(x) => Left(x)
        case Right(y) => Right(letServerChooseANumber(y))

      })


  val nextChoiceJournalKeeper: Flow[Either[GamePlayError, (GuessingGameMessageToAndFro,Int)], Either[GamePlayError, GuessingGameMessageToAndFro], _] =
    Flow.fromFunction(m =>
      m match {
        case Left(x) => Left(x)
        case Right(y) => Right(askServerToRememberChosenNumber(y))

      })


}
