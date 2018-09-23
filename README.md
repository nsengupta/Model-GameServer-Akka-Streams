This is the codebase that accompanies my blog [here](http://blogoloquy.blogspot.com/2018/09/model-correctly-and-write-less-code.html) .
 
 The code is only meant to substantiate a concept. So, the implementations are mostly very trivial. For example, no calls are made to external services and 
 therefore, no asynchronous boundaries are introduced, even though that is a commonly used mechanism.
  
  There are only two files:
 
 * src/main/scala/com/example/protocol/GuessingGameProtocol.scala
 * src/test/scala/com/example/GuessGameSpec/GuessingGameStreamTest.scala
 
 All messages that are exchanged between the server and any client(player) must be a type of GuessingGameMessageToAndFro:
 
 ```sealed trait GuessingGameMessageToAndFro extends SessionCarrier```
 
 However, messages which are moving between transformations, are of different types. For example, any transformer that deals with scores, expects a ScoreCarrier:
 ```sealed trait ScoreCarrier { val score: Int }```
 
 Similarly, following types are also defined for constraining what transformers can accept and emit:
 
 ```trait SessionCarrier { val sessionID: String }```
 ```sealed trait GuessCarrier```
 ```trait PointsCarrier { val pointsEarnedByGuessing: Int }```
 
 Because any transformer can emit an error instead of another acceptable type, all transformers are designed to expect an Either[A,B]: 
 
 Example:
 
 ```
 val sessionExistenceChecker: Flow[GuessingGameMessageToAndFro, Either[GamePlayError, GuessingGameMessageToAndFro], NotUsed] = {
     Flow.fromFunction(m => if (SessionService.exists(m)) 
                                   Right(m) 
                            else 
                                   Left(IncorrectSessionIDProvided(m.sessionID)
                      ))
 }
 ```
 
Another example:

```
val guessNumberPreparator: Flow[Either[GamePlayError, GuessingGameMessageToAndFro], Either[GamePlayError, GuessingGameMessageToAndFro], _] = Flow
    .fromFunction(m =>
      m match {
        case Left(x) => Left(x)
        case Right(y) => Right(numberToOfferToPlayer(y))

})
```
      
      
All utility functions are assembled inside GuessingGameAppFunctionality object.
All flow-defining functions are assembled inside FlowNodes object.

An object called Scorer, behaves as a rudimentary database and holds state of ongoing rounds, for each session.

Testcases should help to exemplify how the flows are supposed to be constructed and used. 
