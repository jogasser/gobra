/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package viper.gobra.ast.frontend

import java.nio.file.Paths

import org.bitbucket.inkytonik.kiama.util.Messaging.Messages
import org.bitbucket.inkytonik.kiama.util._
import viper.gobra.ast.frontend.PNode.PPkg
import viper.gobra.frontend.Parser.FromFileSource
import viper.gobra.reporting.VerifierError
import viper.silver.ast.{LineColumnPosition, SourcePosition}

// TODO: comment describing identifier positions (resolution)


sealed trait PNode extends Product {

  def pretty(prettyPrinter: PrettyPrinter = PNode.defaultPrettyPrinter): String = prettyPrinter.format(this)

  lazy val formatted: String = pretty()

  override def toString: String = formatted
}

object PNode {
  type PPkg = String
  val defaultPrettyPrinter = new DefaultPrettyPrinter
}

sealed trait PScope extends PNode
sealed trait PUnorderedScope extends PScope

case class PPackage(
                     packageClause: PPackageClause,
                     programs: Vector[PProgram],
                     positions: PositionManager
                   ) extends PNode with PUnorderedScope {
  // TODO: remove duplicate package imports:
  lazy val imports: Vector[PImport] = programs.flatMap(_.imports)
  lazy val declarations: Vector[PMember] = programs.flatMap(_.declarations)
}

case class PProgram(
                     packageClause: PPackageClause,
                     imports: Vector[PImport],
                     declarations: Vector[PMember]
                   ) extends PNode


class PositionManager extends PositionStore with Messaging {

  def translate[E <: VerifierError](
                                     messages: Messages,
                                     errorFactory: (String, Option[SourcePosition]) => E
                                   ): Vector[E] = {
    messages.sorted map { m =>
      errorFactory(
        formatMessage(m),
        Some(translate(positions.getStart(m.value).get, positions.getFinish(m.value).get))
      )
    }
  }

  def translate(start: Position, end: Position): SourcePosition = {
    val filename = start.source match {
      case FileSource(filename, _) => filename
      case FromFileSource(filename, _) => filename
      case _ => ???
    }
    new SourcePosition(
      Paths.get(filename),
      LineColumnPosition(start.line, start.column),
      Some(LineColumnPosition(end.line, end.column))
    )
  }
}

case class PPackageClause(id: PPkgDef) extends PNode


sealed trait PImport extends PNode {
  def pkg: PPkg
}

case class PQualifiedImport(qualifier: Option[PDefLikeId], pkg: PPkg) extends PImport

case class PUnqualifiedImport(pkg: PPkg) extends PImport


sealed trait PGhostifiable extends PNode

sealed trait PMember extends PNode

sealed trait PActualMember extends PMember

sealed trait PGhostifiableMember extends PActualMember with PGhostifiable

sealed trait PCodeRoot extends PNode

sealed trait PCodeRootWithResult extends PCodeRoot {
  def result: PResult
}

case class PConstDecl(typ: Option[PType], right: Vector[PExpression], left: Vector[PIdnDef]) extends PActualMember with PActualStatement with PGhostifiableStatement with PGhostifiableMember

case class PVarDecl(typ: Option[PType], right: Vector[PExpression], left: Vector[PIdnDef], addressable: Vector[Boolean]) extends PActualMember with PActualStatement with PGhostifiableStatement with PGhostifiableMember

case class PFunctionDecl(
                          id: PIdnDef,
                          args: Vector[PParameter],
                          result: PResult,
                          spec: PFunctionSpec,
                          body: Option[PBlock]
                        ) extends PActualMember with PScope with PCodeRootWithResult with PGhostifiableMember

case class PMethodDecl(
                        id: PIdnDef,
                        receiver: PReceiver,
                        args: Vector[PParameter],
                        result: PResult,
                        spec: PFunctionSpec,
                        body: Option[PBlock]
                      ) extends PActualMember with PScope with PCodeRootWithResult with PGhostifiableMember

sealed trait PTypeDecl extends PActualMember with PActualStatement with PGhostifiableStatement with PGhostifiableMember {

  def left: PIdnDef

  def right: PType
}

case class PTypeDef(right: PType, left: PIdnDef) extends PTypeDecl

case class PTypeAlias(right: PType, left: PIdnDef) extends PTypeDecl


/**
  * Statements
  */

sealed trait PStatement extends PNode

sealed trait PActualStatement extends PStatement

sealed trait PGhostifiableStatement extends PActualStatement with PGhostifiable

case class PLabeledStmt(label: PIdnDef, stmt: PStatement) extends PActualStatement


sealed trait PSimpleStmt extends PActualStatement

case class PEmptyStmt() extends PSimpleStmt with PGhostifiableStatement

case class PExpressionStmt(exp: PExpression) extends PSimpleStmt with PGhostifiableStatement

case class PSendStmt(channel: PExpression, msg: PExpression) extends PSimpleStmt

case class PAssignment(right: Vector[PExpression], left: Vector[PAssignee]) extends PSimpleStmt with PGhostifiableStatement

/* Careful: left is only evaluated once */
case class PAssignmentWithOp(right: PExpression, op: PAssOp, left: PAssignee) extends PSimpleStmt with PGhostifiableStatement

sealed trait PAssOp extends PNode

case class PAddOp() extends PAssOp

case class PSubOp() extends PAssOp

case class PMulOp() extends PAssOp

case class PDivOp() extends PAssOp

case class PModOp() extends PAssOp

case class PShortVarDecl(right: Vector[PExpression], left: Vector[PIdnUnk], addressable: Vector[Boolean]) extends PSimpleStmt with PGhostifiableStatement

case class PIfStmt(ifs: Vector[PIfClause], els: Option[PBlock]) extends PActualStatement with PScope with PGhostifiableStatement

case class PIfClause(pre: Option[PSimpleStmt], condition: PExpression, body: PBlock) extends PNode

case class PExprSwitchStmt(pre: Option[PSimpleStmt], exp: PExpression, cases: Vector[PExprSwitchCase], dflt: Vector[PBlock]) extends PActualStatement with PScope with PGhostifiableStatement

sealed trait PExprSwitchClause extends PNode

case class PExprSwitchDflt(body: PBlock) extends PExprSwitchClause

case class PExprSwitchCase(left: Vector[PExpression], body: PBlock) extends PExprSwitchClause

case class PTypeSwitchStmt(pre: Option[PSimpleStmt], exp: PExpression, binder: Option[PIdnDef], cases: Vector[PTypeSwitchCase], dflt: Vector[PBlock]) extends PActualStatement with PScope with PGhostifiableStatement

sealed trait PTypeSwitchClause extends PNode

case class PTypeSwitchDflt(body: PBlock) extends PTypeSwitchClause

case class PTypeSwitchCase(left: Vector[PType], body: PBlock) extends PTypeSwitchClause

case class PForStmt(pre: Option[PSimpleStmt], cond: PExpression, post: Option[PSimpleStmt], spec: PLoopSpec, body: PBlock) extends PActualStatement with PScope with PGhostifiableStatement

case class PAssForRange(range: PRange, ass: Vector[PAssignee], body: PBlock) extends PActualStatement with PScope with PGhostifiableStatement

case class PShortForRange(range: PRange, shorts: Vector[PIdnUnk], body: PBlock) extends PActualStatement with PScope with PGhostifiableStatement

case class PGoStmt(exp: PExpression) extends PActualStatement

case class PSelectStmt(send: Vector[PSelectSend], rec: Vector[PSelectRecv], aRec: Vector[PSelectAssRecv], sRec: Vector[PSelectShortRecv], dflt: Vector[PSelectDflt]) extends PActualStatement with PScope

sealed trait PSelectClause extends PNode

case class PSelectDflt(body: PBlock) extends PSelectClause

case class PSelectSend(send: PSendStmt, body: PBlock) extends PSelectClause

case class PSelectRecv(recv: PReceive, body: PBlock) extends PSelectClause

case class PSelectAssRecv(recv: PReceive, ass: Vector[PAssignee], body: PBlock) extends PSelectClause

case class PSelectShortRecv(recv: PReceive, shorts: Vector[PIdnUnk], body: PBlock) extends PSelectClause

case class PReturn(exps: Vector[PExpression]) extends PActualStatement

case class PBreak(label: Option[PLabelUse]) extends PActualStatement

case class PContinue(label: Option[PLabelUse]) extends PActualStatement

case class PGoto(label: PLabelDef) extends PActualStatement

case class PDeferStmt(exp: PExpression) extends PActualStatement

// case class PFallThrough() extends PStatement


case class PBlock(stmts: Vector[PStatement]) extends PActualStatement with PScope with PGhostifiableStatement {
  def nonEmptyStmts: Vector[PStatement] = stmts.filterNot {
    case _: PEmptyStmt => true
    case s: PSeq => s.nonEmptyStmts.isEmpty // filter empty sequences
    case b: PBlock => b.nonEmptyStmts.isEmpty // filter empty blocks
    case _ => false
  }
}

case class PSeq(stmts: Vector[PStatement]) extends PActualStatement with PGhostifiableStatement {
  def nonEmptyStmts: Vector[PStatement] = stmts.filterNot {
    case _: PEmptyStmt => true
    case s: PSeq => s.nonEmptyStmts.isEmpty // filter empty sequences
    case b: PBlock => b.nonEmptyStmts.isEmpty // filter empty blocks
    case _ => false
  }
}

/**
  * Expressions
  */


sealed trait PExpressionOrType extends PNode
sealed trait PExpressionAndType extends PNode with PExpression with PType

sealed trait PExpression extends PNode with PExpressionOrType

sealed trait PActualExpression extends PExpression




sealed trait PBuildIn extends PActualExpression

sealed trait PAssignee extends PActualExpression

sealed trait PUnaryExp extends PActualExpression {
  def operand: PExpression
}

case class PNamedOperand(id: PIdnUse) extends PActualExpression with PActualType with PExpressionAndType with PAssignee with PLiteralType with PNamedType {
  override val name : String = id.name
}


sealed trait PLiteral extends PActualExpression

object PLiteral {
  /**
    * Gives a simple sequence literal of type `typ` with unkeyed elements `exprs`.
    */
  def sequence(typ : PType, exprs : Vector[PExpression]) = PCompositeLit(
    PSequenceType(typ),
    PLiteralValue(exprs.map(e => PKeyedElement(None, PExpCompositeVal(e))))
  )

  /**
    * Gives a simple set literal of type `typ` with unkeyed elements `exprs`.
    */
  def set(typ : PType, exprs : Vector[PExpression]) = PCompositeLit(
    PSetType(typ),
    PLiteralValue(exprs.map(e => PKeyedElement(None, PExpCompositeVal(e))))
  )

  /**
    * Gives a simple multiset literal of type `typ` with unkeyed elements `exprs`.
    */
  def multiset(typ : PType, exprs : Vector[PExpression]) = PCompositeLit(
    PMultisetType(typ),
    PLiteralValue(exprs.map(e => PKeyedElement(None, PExpCompositeVal(e))))
  )
}

sealed trait PBasicLiteral extends PLiteral

case class PBoolLit(lit: Boolean) extends PBasicLiteral

case class PIntLit(lit: BigInt) extends PBasicLiteral

case class PNilLit() extends PBasicLiteral

// TODO: add other literals

case class PCompositeLit(typ: PLiteralType, lit: PLiteralValue) extends PLiteral

sealed trait PShortCircuitMisc extends PMisc

case class PLiteralValue(elems: Vector[PKeyedElement]) extends PShortCircuitMisc with PActualMisc

case class PKeyedElement(key: Option[PCompositeKey], exp: PCompositeVal) extends PShortCircuitMisc with PActualMisc

sealed trait PCompositeKey extends PNode

case class PIdentifierKey(id: PIdnUse) extends PCompositeKey

sealed trait PCompositeVal extends PCompositeKey with PShortCircuitMisc with PActualMisc

case class PExpCompositeVal(exp: PExpression) extends PCompositeVal // exp is never a named operand as a key

case class PLitCompositeVal(lit: PLiteralValue) extends PCompositeVal

case class PFunctionLit(args: Vector[PParameter], result: PResult, body: PBlock) extends PLiteral with PCodeRootWithResult with PScope

case class PInvoke(base: PExpressionOrType, args: Vector[PExpression]) extends PActualExpression

// TODO: Check Arguments in language specification, also allows preceding type

case class PDot(base: PExpressionOrType, id: PIdnUse) extends PActualExpression with PActualType with PExpressionAndType with PAssignee with PLiteralType

case class PIndexedExp(base: PExpression, index: PExpression) extends PActualExpression with PAssignee

/**
  * Represents Go's built-in "len(`exp`)" function that returns the
  * length of `exp`, according to its type. The documentation
  * (https://golang.org/pkg/builtin/#len) gives the following
  * possible cases:
  *
  * - Array: the number of elements in `exp`.
  * - Pointer to array: the number of elements in `*exp`.
  * - Slice, or map: the number of elements in `expr`;
  *   if `exp` is nil, "len(`exp`)" is zero.
  * - String: the number of bytes in `exp`.
  * - Channel: the number of elements queued (unread) in the
  *   channel buffer. If `exp` is nil, then "len(`exp`)" is zero.
  *
  * Gobra extends this with:
  *
  * - Sequence: the number of elements in `exp`.
  */
case class PLength(exp : PExpression) extends PActualExpression

/**
  * Represents a slicing expression roughly of the form "`base`[`low`:`high`:`cap`]",
  * where one or more of the indices `low`, `high` and `cap` are optional
  * depending on the type of `base`.
  */
case class PSliceExp(base: PExpression, low: Option[PExpression] = None, high: Option[PExpression] = None, cap: Option[PExpression] = None) extends PActualExpression

case class PTypeAssertion(base: PExpression, typ: PType) extends PActualExpression

case class PReceive(operand: PExpression) extends PUnaryExp

case class PReference(operand: PExpression) extends PUnaryExp

case class PDeref(base: PExpressionOrType) extends PActualExpression with PActualType with PExpressionAndType with PAssignee with PTypeLit

case class PNegation(operand: PExpression) extends PUnaryExp

sealed trait PBinaryExp extends PActualExpression {
  def left: PExpression

  def right: PExpression
}

case class PEquals(left: PExpression, right: PExpression) extends PBinaryExp

case class PUnequals(left: PExpression, right: PExpression) extends PBinaryExp

case class PAnd(left: PExpression, right: PExpression) extends PBinaryExp

case class POr(left: PExpression, right: PExpression) extends PBinaryExp

case class PLess(left: PExpression, right: PExpression) extends PBinaryExp

case class PAtMost(left: PExpression, right: PExpression) extends PBinaryExp

case class PGreater(left: PExpression, right: PExpression) extends PBinaryExp

case class PAtLeast(left: PExpression, right: PExpression) extends PBinaryExp

case class PAdd(left: PExpression, right: PExpression) extends PBinaryExp

case class PSub(left: PExpression, right: PExpression) extends PBinaryExp

case class PMul(left: PExpression, right: PExpression) extends PBinaryExp

case class PMod(left: PExpression, right: PExpression) extends PBinaryExp

case class PDiv(left: PExpression, right: PExpression) extends PBinaryExp


sealed trait PActualExprProofAnnotation extends PActualExpression {
  def op: PExpression
}

case class PUnfolding(pred: PPredicateAccess, op: PExpression) extends PActualExprProofAnnotation

/**
  * Types
  */

sealed trait PType extends PNode with PExpressionOrType

sealed trait PActualType extends PType

sealed trait PLiteralType extends PNode

sealed trait PNamedType extends PActualType {
  def name: String
}

sealed abstract class PPredeclaredType(override val name: String) extends PNamedType

case class PBoolType() extends PPredeclaredType("bool")

case class PIntType() extends PPredeclaredType("int")

// TODO: add more types

// TODO: ellipsis type

sealed trait PTypeLit extends PActualType

case class PArrayType(len: PExpression, elem: PType) extends PTypeLit with PLiteralType

case class PImplicitSizeArrayType(elem: PType) extends PLiteralType

case class PSliceType(elem: PType) extends PTypeLit with PLiteralType

case class PMapType(key: PType, elem: PType) extends PTypeLit with PLiteralType

sealed trait PChannelType extends PTypeLit {
  def elem: PType
}

case class PBiChannelType(elem: PType) extends PChannelType

case class PSendChannelType(elem: PType) extends PChannelType

case class PRecvChannelType(elem: PType) extends PChannelType



case class PStructType(clauses: Vector[PStructClause]) extends PTypeLit with PLiteralType with PUnorderedScope {

  lazy val embedded: Vector[PEmbeddedDecl] = clauses.collect{
    case x: PEmbeddedDecl => x
    case PExplicitGhostStructClause(x: PEmbeddedDecl) => x
  }

  def fields: Vector[PFieldDecl]=  clauses.collect{
    case x: PFieldDecls => x.fields
    case PExplicitGhostStructClause(x: PFieldDecls) => x.fields
  }.flatten
}

sealed trait PStructClause extends PNode

sealed trait PActualStructClause extends PStructClause

// TODO: maybe change to misc
case class PFieldDecls(fields: Vector[PFieldDecl]) extends PActualStructClause

case class PFieldDecl(id: PIdnDef, typ: PType) extends PNode with PActualMisc

case class PEmbeddedDecl(typ: PEmbeddedType, id: PIdnDef) extends PActualStructClause {
  require(id.name == typ.name)
}

sealed trait PMethodRecvType extends PActualType { // TODO: will have to be removed for packages
  def typ: PNamedOperand
}

case class PMethodReceiveName(typ: PNamedOperand) extends PMethodRecvType

case class PMethodReceivePointer(typ: PNamedOperand) extends PMethodRecvType

// TODO: Named type is not allowed to be an interface


case class PFunctionType(args: Vector[PParameter], result: PResult) extends PTypeLit with PScope

case class PInterfaceType(
                           embedded: Vector[PInterfaceName],
                           methSpecs: Vector[PMethodSig],
                           predSpec: Vector[PMPredicateSig]
                         ) extends PTypeLit with PUnorderedScope

sealed trait PInterfaceClause extends PNode

case class PInterfaceName(typ: PNamedOperand) extends PInterfaceClause

// TODO: maybe change to misc
case class PMethodSig(id: PIdnDef, args: Vector[PParameter], result: PResult) extends PInterfaceClause with PScope


/**
  * Identifiers
  */


sealed trait PIdnNode extends PNode {
  def name: String
}

trait PDefLikeId extends PIdnNode
trait PUseLikeId extends PIdnNode

case class PIdnDef(name: String) extends PDefLikeId
case class PIdnUse(name: String) extends PUseLikeId
case class PIdnUnk(name: String) extends PIdnNode


sealed trait PLabelNode extends PNode {
  def name: String
}

trait PDefLikeLabel extends PLabelNode
trait PUseLikeLabel extends PLabelNode

case class PLabelDef(name: String) extends PDefLikeLabel
case class PLabelUse(name: String) extends PUseLikeLabel


sealed trait PPackegeNode extends PNode {
  def name: PPkg
}

trait PDefLikePkg extends PPackegeNode
trait PUseLikePkg extends PPackegeNode

case class PPkgDef(name: PPkg) extends PDefLikePkg
case class PPkgUse(name: PPkg) extends PUseLikePkg


case class PWildcard() extends PDefLikeId with PUseLikeId {
  override def name: String = "_"
}


/**
  * Miscellaneous
  */

sealed trait PMisc extends PNode

sealed trait PActualMisc extends PMisc

case class PRange(exp: PExpression) extends PActualMisc

sealed trait PParameter extends PMisc {
  def typ: PType
}

sealed trait PActualParameter extends PParameter with PActualMisc

case class PNamedParameter(id: PIdnDef, typ: PType, addressable: Boolean) extends PActualParameter

case class PUnnamedParameter(typ: PType) extends PActualParameter

sealed trait PReceiver extends PNode with PActualMisc {
  def typ: PMethodRecvType
}

case class PNamedReceiver(id: PIdnDef, typ: PMethodRecvType, addressable: Boolean) extends PReceiver

case class PUnnamedReceiver(typ: PMethodRecvType) extends PReceiver


case class PResult(outs: Vector[PParameter]) extends PNode with PActualMisc

sealed trait PEmbeddedType extends PNode with PActualMisc {
  def typ: PNamedType
  def name: String = typ.name
}

case class PEmbeddedName(typ: PNamedType) extends PEmbeddedType

case class PEmbeddedPointer(typ: PNamedType) extends PEmbeddedType


/**
  * Ghost
  */

sealed trait PGhostNode extends PNode

sealed trait PGhostifier[T <: PNode] extends PGhostNode {
  def actual: T
}

object PGhostifier {
  def unapply[T <: PNode](arg: PGhostifier[T]): Option[T] = Some(arg.actual)
}


/**
  * Specification
  */

sealed trait PSpecification extends PGhostNode

case class PFunctionSpec(
                      pres: Vector[PExpression],
                      posts: Vector[PExpression],
                      isPure: Boolean = false,
                      ) extends PSpecification


case class PLoopSpec(
                    invariants: Vector[PExpression]
                    ) extends PSpecification


/**
  * Ghost Member
  */

sealed trait PGhostMember extends PMember with PGhostNode

case class PExplicitGhostMember(actual: PGhostifiableMember) extends PGhostMember with PGhostifier[PGhostifiableMember]

case class PFPredicateDecl(
                         id: PIdnDef,
                         args: Vector[PParameter],
                         body: Option[PExpression]
                         ) extends PGhostMember with PScope with PCodeRoot

case class PMPredicateDecl(
                          id: PIdnDef,
                          receiver: PReceiver,
                          args: Vector[PParameter],
                          body: Option[PExpression]
                          ) extends PGhostMember with PScope with PCodeRoot

case class PMPredicateSig(id: PIdnDef, args: Vector[PParameter]) extends PInterfaceClause with PScope

/**
  * Ghost Statement
  */

sealed trait PGhostStatement extends PStatement with PGhostNode

case class PExplicitGhostStatement(actual: PStatement) extends PGhostStatement with PGhostifier[PStatement]

case class PAssert(exp: PExpression) extends PGhostStatement

case class PAssume(exp: PExpression) extends PGhostStatement

case class PExhale(exp: PExpression) extends PGhostStatement

case class PInhale(exp: PExpression) extends PGhostStatement

case class PFold(exp: PPredicateAccess) extends PGhostStatement

case class PUnfold(exp: PPredicateAccess) extends PGhostStatement

/**
  * Ghost Expressions and Assertions
  */

sealed trait PGhostExpression extends PExpression with PGhostNode

/**
  * Conceals all binary ghost expressions, like sequence concatenation,
  * set union, et cetera.
  */
sealed trait PBinaryGhostExp extends PGhostExpression {
  def left : PExpression
  def right : PExpression
}

//sealed trait PPermission extends PGhostExpression
//
//case class PFullPerm() extends PPermission
//
//case class PNoPerm() extends PPermission

case class POld(operand: PExpression) extends PGhostExpression

case class PConditional(cond: PExpression, thn: PExpression, els: PExpression) extends PGhostExpression

case class PImplication(left: PExpression, right: PExpression) extends PGhostExpression

/** Expression has to be deref, field selection, or predicate call */
case class PAccess(exp: PExpression) extends PGhostExpression

/** Specialised version of PAccess that only handles predicate accesses. E.g, used for foldings.  */
case class PPredicateAccess(pred: PInvoke) extends PGhostExpression

case class PForall(vars: Vector[PBoundVariable], triggers: Vector[PTrigger], body: PExpression) extends PGhostExpression with PScope

case class PExists(vars: Vector[PBoundVariable], triggers: Vector[PTrigger], body: PExpression) extends PGhostExpression with PScope


/* ** Ghost collections */

/**
  * Conceals the type of ordered and unordered ghost collections,
  * e.g., sequences, sets, and multisets.
  */
sealed trait PGhostCollectionExp extends PGhostExpression

/**
  * Represents expressions of the form "`left` in `right`",
  * that is, membership of a ghost collection.
  */
case class PIn(left : PExpression, right : PExpression) extends PGhostCollectionExp with PBinaryGhostExp

/**
  * Denotes the cardinality of `exp`, which is expected
  * to be either a set or a multiset.
  */
case class PCardinality(exp : PExpression) extends PGhostCollectionExp

/**
  * Represents a multiplicity expression of the form "`left` # `right`"
  * in Gobra's specification language, where `right` should be a ghost
  * collection of some type 't', and `left` an expression of type 't'.
  * This expression evaluates to the number of times `left` occurs
  * in the collection `right`, i.e., its multiplicity.
  */
case class PMultiplicity(left : PExpression, right : PExpression) extends PGhostCollectionExp with PBinaryGhostExp

/**
  * Represents a ghost collection literal, e.g., a sequence
  * (or set or multiset) literal "seq[`typ`] { `exprs` }".
  */
sealed trait PGhostCollectionLiteral {
  def typ : PType
  def exprs : Vector[PExpression]
}


/* ** Sequence expressions */

/**
  * Conceals all sequence ghost expressions
  * (for example sequence literals, sequence concatenation, etc.).
  */
sealed trait PSequenceExp extends PGhostCollectionExp

/**
  * The appending of two sequences represented by `left` and `right`.
  */
case class PSequenceAppend(left : PExpression, right : PExpression) extends PSequenceExp with PBinaryGhostExp

/**
  * Denotes a sequence update expression "`seq`[e_0 = e'_0, ..., e_n = e'_n]",
  * consisting of a sequence `clauses` of updates roughly of the form `e_i = e'_i`.
  * The `clauses` vector should contain at least one element.
  */
case class PSequenceUpdate(seq : PExpression, clauses : Vector[PSequenceUpdateClause]) extends PSequenceExp {
  /** Constructs a sequence update with only a single clause built from `left` and `right`. */
  def this(seq : PExpression, left : PExpression, right : PExpression) =
    this(seq, Vector(PSequenceUpdateClause(left, right)))
}

/**
  * Represents a single update clause "`left` = `right`"
  * in a sequence update expression "`seq`[`left` = `right`]".
  */
case class PSequenceUpdateClause(left : PExpression, right : PExpression) extends PNode

/**
  * Denotes the range of integers from `low` to `high`
  * (which should both be integers), not including `high` but including `low`,
  * written "seq[`low` .. `high`]" in Gobra's specification language.
  */
case class PRangeSequence(low : PExpression, high : PExpression) extends PSequenceExp


/* ** Unordered ghost collections */

/**
  * Conceals all unsorted ghost collections, like sets and multisets.
  */
sealed trait PUnorderedGhostCollectionExp extends PGhostCollectionExp

/**
  * Represents a union "`left` union `right`" of two unordered ghost collections,
  * `left` and `right`, which should be of comparable types.
  */
case class PUnion(left : PExpression, right : PExpression) extends PUnorderedGhostCollectionExp with PBinaryGhostExp

/**
  * Represents the intersection "`left` intersection `right`" of
  * `left` and `right`, which should be unordered ghost collections
  * of a comparable type.
  */
case class PIntersection(left : PExpression, right : PExpression) extends PUnorderedGhostCollectionExp with PBinaryGhostExp

/**
  * Represents the (multi)set difference "`left` setminus `right`" of
  * `left` and `right`, which should be unordered ghost collections
  * of a comparable type.
  */
case class PSetMinus(left : PExpression, right : PExpression) extends PUnorderedGhostCollectionExp with PBinaryGhostExp

/**
  * Denotes the subset relation "`left` subset `right`",
  * where `left` and `right` should be unordered ghost collections
  * of comparable types.
  */
case class PSubset(left : PExpression, right : PExpression) extends PUnorderedGhostCollectionExp with PBinaryGhostExp


/* ** Set expressions */

/**
  * Conceals all set expressions, e.g., set literals.
  */
sealed trait PSetExp extends PUnorderedGhostCollectionExp

/**
  * Represents the explicit conversion of `exp` to a set
  * (of a matching, appropriate type), written "set(`exp`)" in
  * Gobra's specification language.
  */
case class PSetConversion(exp : PExpression) extends PSetExp


/* ** Multiset expressions */

/**
  * Conceals all multiset expressions, like multiset literals.
  */
sealed trait PMultisetExp extends PUnorderedGhostCollectionExp

/**
  * Represents the explicit conversion of `exp` to a multiset
  * (of a matching, appropriate type), written "mset(`exp`)" in
  * Gobra's specification language.
  */
case class PMultisetConversion(exp : PExpression) extends PMultisetExp


/* ** Types */

/**
  * Conceals the type of ghost types.
  */
sealed trait PGhostType extends PType with PGhostNode

/**
  * Conceals the type of ghost literal types.
  */
sealed trait PGhostLiteralType extends PGhostType with PLiteralType

/**
  * The type of (mathematical) sequences with elements of type `elem`.
  */
case class PSequenceType(elem : PType) extends PGhostLiteralType

/**
  * The type of (mathematical) sets with elements of type `elem`.
  */
case class PSetType(elem : PType) extends PGhostLiteralType

/**
  * The type of (mathematical) multisets with elements of type `elem`.
  */
case class PMultisetType(elem : PType) extends PGhostLiteralType



/**
  * Miscellaneous
  */

sealed trait PGhostMisc extends PMisc with PGhostNode

case class PBoundVariable(id: PIdnDef, typ: PType) extends PGhostMisc

case class PTrigger(exps: Vector[PExpression]) extends PGhostMisc

case class PExplicitGhostParameter(actual: PActualParameter) extends PParameter with PGhostMisc with PGhostifier[PActualParameter] {
  override def typ: PType = actual.typ
}
// TODO: maybe change to misc
case class PExplicitGhostStructClause(actual: PActualStructClause) extends PStructClause with PGhostNode with PGhostifier[PActualStructClause]

/**
  * Required for parsing
  */

case class PPos[T](get: T) extends PNode
