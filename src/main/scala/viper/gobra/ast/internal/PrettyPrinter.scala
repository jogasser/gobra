package viper.gobra.ast.internal

import org.bitbucket.inkytonik.kiama
import viper.gobra.ast.printing.PrettyPrinterCombinators

trait PrettyPrinter {
  def format(node : Node): String
  def format(typ : Type): String
}

class DefaultPrettyPrinter extends PrettyPrinter with kiama.output.PrettyPrinter with PrettyPrinterCombinators {

  override val defaultIndent = 2
  override val defaultWidth  = 80

  override def format(node : Node) : String = pretty(show(node)).layout
  override def format(typ : Type) : String = pretty(showType(typ)).layout

  def show(n: Node): Doc = n match {
    case n: Program => showProgram(n)
    case n: Member => showMember(n)
    case n: Field => showField(n)
    case n: Stmt => showStmt(n)
    case n: Assignee => showAssignee(n)
    case n: CompositeObject => showCompositeObject(n)
    case n: Assertion => showAss(n)
    case n: Accessible => showAcc(n)
    case n: Expr => showExpr(n)
    case n: Addressable => showAddressable(n)
    case n: Proxy => showProxy(n)
  }

  // program

  def showProgram(p: Program): Doc = p match {
    case Program(types, members, _) =>
      ssep(types map showTopType, line <> line) <>
      ssep(members map showMember, line <> line)
  }

  // member

  def showTopType(t: TopType): Doc = t match {
    case d: DefinedT => showTypeDecl(d)
    case _ => emptyDoc
  }

  def showMember(m: Member): Doc = m match {
    case n: Method => showMethod(n)
    case n: PureMethod => showPureMethod(n)
    case n: Function => showFunction(n)
    case n: PureFunction => showPureFunction(n)
    case n: FPredicate => showFPredicate(n)
    case n: MPredicate => showMPredicate(n)
  }

  def showFunction(f: Function): Doc = f match {
    case Function(name, args, results, pres, posts, body) =>
      "func" <+> name.name <> parens(showFormalArgList(args)) <+> parens(showVarDeclList(results)) <>
        spec(showPreconditions(pres) <> showPostconditions(posts)) <> opt(body)(b => block(showStmt(b)))
  }

  def showPureFunction(f: PureFunction): Doc = f match {
    case PureFunction(name, args, results, pres, body) =>
      "pure func" <+> name.name <> parens(showFormalArgList(args)) <+> parens(showVarDeclList(results)) <>
        spec(showPreconditions(pres)) <> opt(body)(b => block("return" <+> showExpr(b)))
  }

  def showMethod(m: Method): Doc = m match {
    case Method(receiver, name, args, results, pres, posts, body) =>
      "func" <+> parens(showVarDecl(receiver)) <+> name.name <> parens(showFormalArgList(args)) <+> parens(showVarDeclList(results)) <>
        spec(showPreconditions(pres) <> showPostconditions(posts)) <> opt(body)(b => block(showStmt(b)))
  }

  def showPureMethod(m: PureMethod): Doc = m match {
    case PureMethod(receiver, name, args, results, pres, body) =>
      "pure func" <+> parens(showVarDecl(receiver)) <+> name.name <> parens(showFormalArgList(args)) <+> parens(showVarDeclList(results)) <>
        spec(showPreconditions(pres)) <> opt(body)(b => block("return" <+> showExpr(b)))
  }

  def showFPredicate(predicate: FPredicate): Doc = predicate match {
    case FPredicate(name, args, body) =>
    "pred" <+> name.name <> parens(showFormalArgList(args)) <> opt(body)(b => block(showAss(b)))
  }

  def showMPredicate(predicate: MPredicate): Doc = predicate match {
    case MPredicate(recv, name, args, body) =>
      "pred" <+> parens(showVarDecl(recv)) <+> name.name <> parens(showFormalArgList(args)) <> opt(body)(b => block(showAss(b)))
  }

  def showField(field: Field): Doc = field match {
    case Field(name, typ) => "field" <> name <> ":" <+> showType(typ)
  }

  def showTypeDecl(t: DefinedT): Doc =
    "type" <+> t.name <+> "..."

  def showPreconditions[T <: Assertion](list: Vector[T]): Doc =
    hcat(list  map ("requires " <> showAss(_) <> line))

  def showPostconditions[T <: Assertion](list: Vector[T]): Doc =
    hcat(list  map ("ensures " <> showAss(_) <> line))

  def showFormalArgList[T <: Parameter](list: Vector[T]): Doc =
    showVarDeclList(list)

  // statements

  def showStmt(s: Stmt): Doc = s match {
    case Block(decls, stmts) => "decl" <+> showBottomDeclList(decls) <> line <> showStmtList(stmts)
    case Seqn(stmts) => ssep(stmts map showStmt, line)
    case If(cond, thn, els) => "if" <> parens(showExpr(cond)) <+> block(showStmt(thn)) <+> "else" <+> block(showStmt(els))
    case While(cond, invs, body) => "while" <> parens(showExpr(cond)) <> line <>
      hcat(invs  map ("invariant " <> showAss(_) <> line)) <> block(showStmt(body))

    case Make(target, typ) => showVar(target) <+> "=" <+> "new" <> brackets(showCompositeObject(typ))
    case SingleAss(left, right) => showAssignee(left) <+> "=" <+> showExpr(right)

    case FunctionCall(targets, func, args) =>
      (if (targets.nonEmpty) showVarList(targets) <+> "=" <> space else emptyDoc) <>
        func.name <> parens(showExprList(args))

    case MethodCall(targets, recv, meth, args) =>
      (if (targets.nonEmpty) showVarList(targets) <+> "=" <> space else emptyDoc) <>
        showExpr(recv) <> meth.name <> parens(showExprList(args))

    case Return() => "return"
    case Assert(ass) => "assert" <+> showAss(ass)
    case Assume(ass) => "assume" <+> showAss(ass)
    case Inhale(ass) => "inhale" <+> showAss(ass)
    case Exhale(ass) => "exhale" <+> showAss(ass)
    case Fold(acc)   => "fold" <+> showAss(acc)
    case Unfold(acc) => "unfold" <+> showAss(acc)
  }

  def showComposite(c: CompositeObject): Doc = showLit(c.op)

  def showProxy(x: Proxy): Doc = x match {
    case FunctionProxy(name) => name
    case MethodProxy(name, _) => name
    case FPredicateProxy(name) => name
    case MPredicateProxy(name, _) => name
  }

  def showBottomDecl(x: BottomDeclaration): Doc = x match {
    case localVar: LocalVar => showVar(localVar)
    case outParam: Parameter.Out => showVar(outParam)
  }

  protected def showStmtList[T <: Stmt](list: Vector[T]): Doc =
    ssep(list map showStmt, line)

  protected def showVarList[T <: Var](list: Vector[T]): Doc =
    showList(list)(showVar)

  protected def showVarDeclList[T <: Var](list: Vector[T]): Doc =
    showList(list)(showVarDecl)

  protected def showBottomDeclList[T <: BottomDeclaration](list: Vector[T]): Doc =
    showList(list)(showBottomDecl)

  protected def showAssigneeList[T <: Assignee](list: Vector[T]): Doc =
    showList(list)(showAssignee)

  protected def showExprList[T <: Expr](list: Vector[T]): Doc =
    showList(list)(showExpr)

  def showAssignee(ass: Assignee): Doc = ass match {
    case Assignee.Var(v) => showVar(v)
    case Assignee.Pointer(e) => showExpr(e)
    case Assignee.Field(f) => showExpr(f)
  }

  def showCompositeObject(co: CompositeObject): Doc = showLit(co.op)

  // assertions

  def showAss(a: Assertion): Doc = a match {
    case SepAnd(left, right) => showAss(left) <+> "&&" <+> showAss(right)
    case ExprAssertion(exp) => showExpr(exp)
    case Implication(left, right) => showExpr(left) <+> "==>" <+> showAss(right)
    case Access(e) => "acc" <> parens(showAcc(e))
  }

  def showAcc(acc: Accessible): Doc = acc match {
    case Accessible.Pointer(der) => showExpr(der)
    case Accessible.Field(op) => showExpr(op)
    case Accessible.Predicate(op) => showPredicateAcc(op)
  }

  def showPredicateAcc(access: PredicateAccess): Doc = access match {
    case FPredicateAccess(pred, args) => pred.name <> parens(showExprList(args))
    case MPredicateAccess(recv, pred, args) => showExpr(recv) <> pred.name <> parens(showExprList(args))
    case MemoryPredicateAccess(arg) => "memory" <> parens(showExpr(arg))
  }

  // expressions

  def showExpr(e: Expr): Doc = e match {
    case Unfolding(acc, exp) => "unfolding" <+> showAss(acc) <+> "in" <+> showExpr(exp)

    case Old(op) => "old(" <> showExpr(op) <> ")"

    case Conditional(cond, thn, els, _) => showExpr(cond) <> "?" <> showExpr(thn) <> ":" <> showExpr(els)

    case PureFunctionCall(func, args, _) =>
      func.name <> parens(showExprList(args))

    case PureMethodCall(recv, meth, args, _) =>
      showExpr(recv) <> meth.name <> parens(showExprList(args))

    case SequenceLength(op) => "|" <> showExpr(op) <> "|"
    case SequenceLiteral(exprs) => "seq" <+> braces(space <> showExprList(exprs) <>
      (if (exprs.nonEmpty) space else emptyDoc))
    case RangeSequence(low, high) =>
      "seq" <> brackets(showExpr(low) <+> ".." <+> showExpr(high))
    case EmptySequence(typ) => "seq" <> brackets(showType(typ)) <+> braces(space)
    case SequenceAppend(left, right) => showExpr(left) <+> "++" <+> showExpr(right)
    case SequenceUpdate(seq, left, right) =>
      showExpr(seq) <> brackets(showExpr(left) <+> "=" <+> showExpr(right))
    case SequenceContains(left, right) => showExpr(left) <+> "in" <+> showExpr(right)
    case SequenceIndex(left, right) => showExpr(left) <> brackets(showExpr(right))
    case SequenceDrop(left, right) => showExpr(left) <> brackets(showExpr(right) <> colon)
    case SequenceTake(left, right) => showExpr(left) <> brackets(colon <> showExpr(right))

    case EmptySet(typ) => "set" <> brackets(showType(typ)) <+> braces(space)
    case SetLiteral(exprs) => "set" <+> braces(space <> showExprList(exprs) <>
      (if (exprs.nonEmpty) space else emptyDoc))
    case SetUnion(left, right) => showExpr(left) <+> "union" <+> showExpr(right)
    case SetIntersection(left, right) => showExpr(left) <+> "intersection" <+> showExpr(right)
    case SetMinus(left, right) => showExpr(left) <+> "setminus" <+> showExpr(right)
    case Subset(left, right) => showExpr(left) <+> "subset" <+> showExpr(right)

    case DfltVal(typ) => "dflt" <> brackets(showType(typ))
    case Tuple(args) => parens(showExprList(args))
    case Deref(exp, typ) => "*" <> showExpr(exp)
    case Ref(ref, typ) => "&" <> showAddressable(ref)
    case FieldRef(recv, field) => showExpr(recv) <> "."  <> field.name
    case Negation(op) => "!" <> showExpr(op)
    case BinaryExpr(left, op, right, _) => showExpr(left) <+> op <+> showExpr(right)
    case lit: Lit => showLit(lit)
    case v: Var => showVar(v)
  }

  def showAddressable(a: Addressable): Doc = showExpr(a.op)

  // literals

  def showLit(l: Lit): Doc = l match {
    case IntLit(v) => v.toString
    case BoolLit(b) => if (b) "true" else "false"
    case NilLit() => "nil"
    case StructLit(t, args) => showType(t) <> braces(showExprList(args))
  }

  // variables

  def showVar(v: Var): Doc = v match {
    case Parameter.In(id, _)    => id
    case Parameter.Out(id, _)    => id
    case LocalVar.Ref(id, _) => id
    case LocalVar.Val(id, _) => id
    case LocalVar.Inter(id, _) => id
  }

  def showVarDecl(v: Var): Doc = v match {
    case Parameter.In(id, t)    => id <> ":" <+> showType(t)
    case Parameter.Out(id, t)    => id <> ":" <+> showType(t)
    case LocalVar.Ref(id, t) => id <> ":" <+> "!" <> showType(t)
    case LocalVar.Val(id, t) => id <> ":" <+> showType(t)
    case LocalVar.Inter(id, t) => id <> ":" <+> "?" <> showType(t)
  }

  // types

  def showType(typ : Type) : Doc = typ match {
    case BoolT => "bool"
    case IntT => "int"
    case VoidT => "void"
    case NilT => "nil"
    case PermissionT => "perm"
    case DefinedT(name) => name
    case PointerT(t) => "*" <> showType(t)
    case TupleT(ts) => parens(showTypeList(ts))
    case struct: StructT => emptyDoc <> block(hcat(struct.fields map showField))
    case SequenceT(elem) => "seq" <> brackets(showType(elem))
    case SetT(elem) => "set" <> brackets(showType(elem))
  }

  private def showTypeList[T <: Type](list: Vector[T]): Doc =
    showList(list)(showType)

  def showList[T](list: Vector[T])(f: T => Doc): Doc = ssep(list map f, comma <> space)

}

class ShortPrettyPrinter extends DefaultPrettyPrinter {

  override val defaultIndent = 2
  override val defaultWidth  = 80

  override def format(node: Node): String =
    pretty(show(node)).layout


  override def showFunction(f: Function): Doc = f match {
    case Function(name, args, results, pres, posts, body) =>
      "func" <+> name.name <> parens(showFormalArgList(args)) <+> parens(showVarDeclList(results)) <>
        spec(showPreconditions(pres) <> showPostconditions(posts))
  }

  override def showPureFunction(f: PureFunction): Doc = f match {
    case PureFunction(name, args, results, pres, body) =>
      "pure func" <+> name.name <> parens(showFormalArgList(args)) <+> parens(showVarDeclList(results)) <>
        spec(showPreconditions(pres))
  }

  override def showMethod(m: Method): Doc = m match {
    case Method(receiver, name, args, results, pres, posts, body) =>
      "func" <+> parens(showVarDecl(receiver)) <+> name.name <> parens(showFormalArgList(args)) <+> parens(showVarDeclList(results)) <>
        spec(showPreconditions(pres) <> showPostconditions(posts))
  }

  override def showPureMethod(m: PureMethod): Doc = m match {
    case PureMethod(receiver, name, args, results, pres, body) =>
      "pure func" <+> parens(showVarDecl(receiver)) <+> name.name <> parens(showFormalArgList(args)) <+> parens(showVarDeclList(results)) <>
        spec(showPreconditions(pres))
  }

  override def showFPredicate(predicate: FPredicate): Doc = predicate match {
    case FPredicate(name, args, body) =>
      "pred" <+> name.name <> parens(showFormalArgList(args))
  }

  override def showMPredicate(predicate: MPredicate): Doc = predicate match {
    case MPredicate(recv, name, args, body) =>
      "pred" <+> parens(showVarDecl(recv)) <+> name.name <> parens(showFormalArgList(args))
  }

  // statements

  override def showStmt(s: Stmt): Doc = s match {
    case Block(decls, stmts) => "decl" <+> showBottomDeclList(decls)
    case Seqn(stmts) => emptyDoc
    case If(cond, thn, els) => "if" <> parens(showExpr(cond)) <+> "{...}" <+> "else" <+> "{...}"
    case While(cond, invs, body) => "while" <> parens(showExpr(cond)) <> line <>
      hcat(invs  map ("invariant " <> showAss(_) <> line))

    case Make(target, typ) => showVar(target) <+> "=" <+> "new" <> brackets(showCompositeObject(typ))
    case SingleAss(left, right) => showAssignee(left) <+> "=" <+> showExpr(right)

    case FunctionCall(targets, func, args) =>
      (if (targets.nonEmpty) showVarList(targets) <+> "=" <> space else emptyDoc) <>
        func.name <> parens(showExprList(args))

    case MethodCall(targets, recv, meth, args) =>
      (if (targets.nonEmpty) showVarList(targets) <+> "=" <> space else emptyDoc) <>
        showExpr(recv) <> meth.name <> parens(showExprList(args))

    case Return() => "return"
    case Assert(ass) => "assert" <+> showAss(ass)
    case Assume(ass) => "assume" <+> showAss(ass)
    case Inhale(ass) => "inhale" <+> showAss(ass)
    case Exhale(ass) => "exhale" <+> showAss(ass)
    case Fold(acc)   => "fold" <+> showAss(acc)
    case Unfold(acc) => "unfold" <+> showAss(acc)
  }

}
