// This Source Code Form is subject to the terms of the Mozilla Public
// License, v. 2.0. If a copy of the MPL was not distributed with this
// file, You can obtain one at http://mozilla.org/MPL/2.0/.
//
// Copyright (c) 2011-2020 ETH Zurich.

package viper.gobra.frontend.info.implementation.typing

import org.bitbucket.inkytonik.kiama.==>
import org.bitbucket.inkytonik.kiama.util.Messaging.{Messages, error, noMessages}
import viper.gobra.ast.frontend.PNode
import viper.gobra.frontend.info.base.BuiltInMemberTag._
import viper.gobra.frontend.info.base.Type._
import viper.gobra.frontend.info.implementation.TypeInfoImpl
import viper.gobra.frontend.info.implementation.typing.ghost.separation.GhostType
import viper.gobra.util.TypeBounds.UnboundedInteger
import viper.gobra.util.Violation

trait BuiltInMemberTyping extends BaseTyping { this: TypeInfoImpl =>
  /** abstract type for BuiltInMemberTag tag */
  def typ(tag: BuiltInMemberTag): AbstractType = tag match {
    case t: BuiltInFunctionTag => t match {
      case CloseFunctionTag => AbstractType(
        {
          case (_, Vector(c: ChannelT, IntT(UnboundedInteger), IntT(UnboundedInteger) /* PermissionT */ , PredT(Vector()))) if sendAndBiDirections.contains(c.mod) => noMessages
          case (n, ts) => error(n, s"type error: close expects parameters of bidirectional or sending channel, int, int, and pred() types but got ${ts.mkString(", ")}")
        },
        {
          case ts@Vector(c: ChannelT, IntT(UnboundedInteger), IntT(UnboundedInteger) /* PermissionT */ , PredT(Vector())) if sendAndBiDirections.contains(c.mod) => FunctionT(ts, VoidType)
        })
      case AppendFunctionTag => AbstractType(
        {
          case (_, Vector(c: SliceT, v: VariadicT)) if assignableTo(v.elem, c.elem) => noMessages
          case (_, (h: SliceT) +: tail) if tail.forall(assignableTo(_, h.elem)) => noMessages
          case (n, ts) => error(n, s"type error: append expects first argument of slice type and the second of variadic type but got ${ts.mkString(", ")}")
        },
        {
          case (h: SliceT) +: tail if tail.forall(assignableTo(_, h.elem)) => FunctionT(Vector(h, VariadicT(h.elem)), h)
          case ts@ Vector(c: SliceT, v: VariadicT) if assignableTo(v.elem, c.elem) => FunctionT(ts, c)
        })

      case CopyFunctionTag => AbstractType(
        {
          case (_, Vector(c: SliceT, v: SliceT, PermissionT)) if c.elem == v.elem => noMessages
          case (n, ts) => error(n, s"type error: copy expects two slices of the same type and a permission but got ${ts.mkString(", ")}")
        },
        {
          case ts@ Vector(c: SliceT, v: SliceT, PermissionT) if c.elem == v.elem => FunctionT(ts, INT_TYPE)
        })
    }
    case t: BuiltInFPredicateTag => t match {
      case PredTrueFPredTag => AbstractType(
        {
          case (_, _) => noMessages // it is well-defined for arbitrary arguments
        },
        {
          case args => FunctionT(args, AssertionT)
        })

    }

    case t: BuiltInMethodTag => t match {
      case BufferSizeMethodTag =>
        channelReceiverType(allDirections, _ => FunctionT(Vector(), INT_TYPE))

      case SendGivenPermMethodTag =>
        channelReceiverType(sendAndBiDirections, c => FunctionT(Vector(), PredT(Vector(c.elem))))

      case SendGotPermMethodTag =>
        channelReceiverType(sendAndBiDirections, _ => FunctionT(Vector(), PredT(Vector()))) // we restrict it to pred()

      case RecvGivenPermMethodTag =>
        channelReceiverType(recvAndBiDirections, _ => FunctionT(Vector(), PredT(Vector())))

      case RecvGotPermMethodTag =>
        channelReceiverType(recvAndBiDirections, c => FunctionT(Vector(), PredT(Vector(c.elem))))

      case InitChannelMethodTag => channelReceiverType(allDirections, c => {
        // init's signature is adapted to the heavy simplifications that are in place for the initial support for channels.
        // in particular, the permission for SendGivenPerm and RecvGotPerm as well as SendGotPerm and RecvGivenPerm have
        // to be equal. Thus, they get merged into two parameters: The former pair is called `proPerm` as they describe
        // the invariant that is exhaled at the sender's and inhaled at the receiver's side ("pro" because it "travels" in
        // direction of the send operation). The latter pair is merged to `contraPerm` representing the invariant that
        // "travels" in the opposite direction.
        val proPermArgType = PredT(Vector(c.elem))
        val contraPermArgType = PredT(Vector()) // pred() because we enforce that sendGotPermArgType == recvGivenPermArgType
        FunctionT(Vector(proPermArgType, contraPermArgType), VoidType)
      })

      case CreateDebtChannelMethodTag => channelReceiverType(allDirections, _ => {
        val dividend = IntT(UnboundedInteger)
        val divisor = IntT(UnboundedInteger)
        // val amountArgType = PermissionT
        val predArgType = PredT(Vector())
        FunctionT(Vector(dividend, divisor /* amountArgType */, predArgType), VoidType)
      })

      case RedeemChannelMethodTag => channelReceiverType(allDirections, _ => {
        val predArgType = PredT(Vector())
        FunctionT(Vector(predArgType), VoidType)
      })
    }

    case t: BuiltInMPredicateTag => t match {
      case IsChannelMPredTag =>
        channelReceiverType(allDirections, _ => FunctionT(Vector(), AssertionT))

      case SendChannelMPredTag =>
        channelReceiverType(sendAndBiDirections, _ => FunctionT(Vector(), AssertionT))

      case RecvChannelMPredTag =>
        channelReceiverType(recvAndBiDirections, _ => FunctionT(Vector(), AssertionT))

      case ClosedMPredTag =>
        channelReceiverType(recvAndBiDirections, _ => FunctionT(Vector(), AssertionT))

      case ClosureDebtMPredTag =>
        channelReceiverType(allDirections, _ => {
          val predArgType = PredT(Vector())
          val dividend = IntT(UnboundedInteger)
          val divisor = IntT(UnboundedInteger)
          // val amountArgType = PermissionT
          FunctionT(Vector(predArgType, dividend, divisor /* amountArgType */), AssertionT)
        })

      case TokenMPredTag => channelReceiverType(allDirections, _ => {
        val predArgType = PredT(Vector())
        FunctionT(Vector(predArgType), AssertionT)
      })
    }
  }

  /** ghost typing for arguments of BuiltInMemberTag tag */
  def argGhostTyping(tag: BuiltInMemberTag, args: Vector[Type]): GhostType = tag match {

    case CloseFunctionTag =>
      GhostType.ghostTuple(Vector(false, true, true /* true */, true))

    case AppendFunctionTag =>
      GhostType.ghostTuple(Vector(false, false))

    case CopyFunctionTag =>
      GhostType.ghostTuple(Vector(false, false, true))

    case t: GhostBuiltInMember => t match {
      case _ =>
        if (typ(t).typing.isDefinedAt(args)) {
          ghostArgs(typ(t).typing(args).args.length)
        } else {
          Violation.violation(s"argGhostTyping not defined for ${t.name}")
        }
    }
  }

  /** ghost typing for return values of BuiltInMemberTag tag */
  def returnGhostTyping(tag: BuiltInMemberTag, args: Vector[Type]): GhostType = tag match {

    case CloseFunctionTag => ghostArgs(0)

    case AppendFunctionTag => ghostArgs(0)

    case CopyFunctionTag => GhostType.notGhost

    case t: GhostBuiltInMember => t match {
      case _ =>
        /**
          * Returns 0 ghost results for functions returning `void` and 1 ghost result otherwise.
          * Members having multiple ghost results have to overwrite this function.
          */
        if (typ(t).typing.isDefinedAt(args)) {
          typ(t).typing(args).result match {
            case VoidType => ghostArgs(0)
            case _ => ghostArgs(1) // as a default, we pick a single ghost result.
          }
        } else {
          Violation.violation(s"returnGhostTyping not defined for ${t.name}")
        }
    }

  }

  private lazy val allDirections: Set[ChannelModus] = Set(ChannelModus.Bi, ChannelModus.Send, ChannelModus.Recv)
  private lazy val sendAndBiDirections: Set[ChannelModus] = Set(ChannelModus.Bi, ChannelModus.Send)
  private lazy val recvAndBiDirections: Set[ChannelModus] = Set(ChannelModus.Bi, ChannelModus.Recv)

  /**
    * Simplifies creation of an AbstracType specialized to a channel being the (method or mpredicate) receiver
    */
  private def channelReceiverType(permittedModi: Set[ChannelModus], typing: ChannelT => FunctionT): AbstractType = AbstractType(
    channelReceiverMessages(permittedModi),
    channelReceiverTyping(permittedModi, typing)
  )
  private def channelReceiverMessages(permittedModi: Set[ChannelModus]): (PNode, Vector[Type]) => Messages =
  {
    case (_, Vector(c: ChannelT)) if permittedModi.contains(c.mod) => noMessages
    case (n, ts) => error(n, s"type error: expected a single argument of channel type (permitted channel modi: $permittedModi) but got $ts")
  }
  private def channelReceiverTyping(permittedModi: Set[ChannelModus], typing: ChannelT => FunctionT): Vector[Type] ==> FunctionT =
  {
    case Vector(c: ChannelT) if permittedModi.contains(c.mod) => typing(c)
  }

  private def ghostArgs(arity: Int): GhostType = GhostType.ghostTuple(Vector.fill(arity)(true))
}
