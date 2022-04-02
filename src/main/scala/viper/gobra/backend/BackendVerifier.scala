// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2020 ETH Zurich.

package viper.gobra.backend

import viper.gobra.backend.ViperBackends.{CarbonBackend => Carbon}
import viper.gobra.frontend.{Config, PackageInfo}
import viper.gobra.reporting.BackTranslator.BackTrackInfo
import viper.gobra.reporting.{BackTranslator, BacktranslatingReporter, ChoppedProgressMessage}
import viper.gobra.util.{ChopperUtil, GobraExecutionContext}
import viper.silver
import viper.silver.verifier.VerificationResult
import viper.silver.{ast => vpr}

import scala.collection.concurrent.{Map, TrieMap}
import scala.concurrent.Future

object BackendVerifier {

  private val activeTasks: Map[String, ViperVerifier] = TrieMap()

  case class Task(
                   program: vpr.Program,
                   backtrack: BackTranslator.BackTrackInfo
                 )

  sealed trait Result
  case object Success extends Result
  case class Failure(
                    errors: Vector[silver.verifier.VerificationError],
                    backtrack: BackTranslator.BackTrackInfo
                    ) extends Result

  def verify(task: Task, pkgInfo: PackageInfo)(config: Config)(implicit executor: GobraExecutionContext): Future[Result] = {

    var exePaths: Vector[String] = Vector.empty

    config.z3Exe match {
      case Some(z3Exe) =>
        exePaths ++= Vector("--z3Exe", z3Exe)
      case _ =>
    }

    (config.backend, config.boogieExe) match {
      case (Carbon, Some(boogieExe)) =>
        exePaths ++= Vector("--boogieExe", boogieExe)
      case _ =>
    }

    val verificationResults =  {
      val verifier = config.backend.create(exePaths, config)
      val reporter = BacktranslatingReporter(config.reporter, task.backtrack, config)

      activeTasks.put(config.taskName, verifier)

      if (!config.shouldChop) {
        verifier.verify(config.taskName, reporter, task.program)(executor)
      } else {

        val programs = ChopperUtil.computeChoppedPrograms(task, pkgInfo)(config)
        val num = programs.size

        //// (Felix) Currently, Silicon cannot be invoked concurrently.
        // val verificationResults = Future.traverse(programs.zipWithIndex) { case (program, idx) =>
        //   val programID = s"_programID_${config.inputFiles.head.getFileName}_$idx"
        //   verifier.verify(programID, config.backendConfig, BacktranslatingReporter(config.reporter, task.backtrack, config), program)(executor)
        // }

        programs.zipWithIndex.foldLeft(Future.successful(silver.verifier.Success): Future[VerificationResult]) { case (res, (program, idx)) =>
          for {
            acc <- res
            next <- verifier
              .verify(config.taskName, reporter, program)(executor)
              .andThen(_ => config.reporter report ChoppedProgressMessage(idx+1, num))
          } yield (acc, next) match {
            case (acc, silver.verifier.Success) => acc
            case (silver.verifier.Success, res) => res
            case (l: silver.verifier.Failure, r: silver.verifier.Failure) => silver.verifier.Failure(l.errors ++ r.errors)
          }
        }
      }
    }

    verificationResults
      .map(result => {
        activeTasks.remove(config.taskName)
        convertVerificationResult(result, task.backtrack)
      })
  }

  def stopVerification(taskName: String): Unit = {
    activeTasks.get(taskName) match {
      case Some(verifier) =>
      case None =>
    }
  }

  /**
    * Takes a Viper VerificationResult and converts it to a Gobra Result using the provided backtracking information
    */
  def convertVerificationResult(result: VerificationResult, backTrackInfo: BackTrackInfo): Result = result match {
    case silver.verifier.Success => Success
    case failure: silver.verifier.Failure =>
      val (verificationError, otherError) = failure.errors
        .partition(_.isInstanceOf[silver.verifier.VerificationError])
        .asInstanceOf[(Seq[silver.verifier.VerificationError], Seq[silver.verifier.AbstractError])]

      checkAbstractViperErrors(otherError)

      Failure(verificationError.toVector, backTrackInfo)
  }

  @scala.annotation.elidable(scala.annotation.elidable.ASSERTION)
  private def checkAbstractViperErrors(errors: Seq[silver.verifier.AbstractError]): Unit = {
    if (errors.nonEmpty) {
      var messages: Vector[String] = Vector.empty
      messages ++= Vector("Found non-verification-failures")
      messages ++= errors map (_.readableMessage)

      val completeMessage = messages.mkString("\n")
      throw new java.lang.IllegalStateException(completeMessage)
    }
  }

}
