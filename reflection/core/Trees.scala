package scala.reflect
package core

import org.scalareflect.invariants._
import org.scalareflect.errors._
import org.scalareflect.adt._
import org.scalareflect.annotations._
import scala.{Seq => _}
import scala.collection.immutable.Seq

// TODO: collection-like methods (see http://clang.llvm.org/docs/LibASTMatchersReference.html)
// TODO: rewriting/transformation methods
// TODO: parser
// TODO: unhygienic quasiquotes
// TODO: hygiene + hygienic tree equality
// TODO: what to do with references to particular overloads?
// TODO: consider adding default values for case class fields whenever applicable
// TODO: prettyprinter
// TODO: implement srewrite with palladium
// TODO: implement scaladoc with palladium
// TODO: add moar requires
// TODO: invariants: tree should either have at least one non-trival token or be eq to it's empty value
// TODO: converter: double check conversion of `(f _)(x)` (bug 46)

@root trait Tree {
  // TODO: we also need some sort of host-specific metadata in trees
  def src: SourceContext
  def owner: Scope = parent match { case owner: Scope => owner; case tree => tree.owner }
  // TODO: we still need to figure out how to implement this - either going the Roslyn route or the by-name arguments route.
  def parent: Tree = ???
}

@branch trait Ref extends Tree

@branch trait Name extends Ref {
  def value: String
  def isBackquoted: Boolean
}

@branch trait Term extends Arg with Stmt.Template with Stmt.Block
object Term {
  @branch trait Ref extends Term with core.Ref
  @ast class This(qual: Option[core.Name]) extends Ref
  @ast class Name(value: scala.Predef.String @nonEmpty, isBackquoted: Boolean = false) extends core.Name with Ref with Pat with Member with Has.TermName {
    // TODO: require(!keywords.contains(value) || isBackquoted)
    // TODO: if not backquoted, then not all values should be allowed
    def name: Name = this
    def mods: Seq[Mod] = Nil
  }
  @ast class SuperSelect(qual: Option[core.Name], supertpe: Option[Type.Name], selector: Term.Name) extends Ref
  @ast class Select(qual: Term, selector: Term.Name) extends Ref with Pat

  @ast class Interpolate(prefix: Name, parts: Seq[Lit.String] @nonEmpty, args: Seq[Term]) extends Term {
    // TODO: require(prefix.isInterpolationId)
    require(parts.length == args.length + 1)
  }
  @ast class Apply(fun: Term, args: Seq[Arg]) extends Term
  @ast class ApplyType(fun: Term, args: Seq[Type] @nonEmpty) extends Term
  @ast class ApplyRight(lhs: Term, op: Name, targs: Seq[Type], rhs: Term) extends Term {
    // TODO: require(op.isRightAssocOp)
  }
  @ast class ApplyUnary(op: Name, arg: Term) extends Term {
    // TODO: require(op.isUnaryOp)
  }
  @ast class Assign(lhs: Term.Ref, rhs: Term) extends Term
  @ast class Update(lhs: Apply, rhs: Term) extends Term
  @ast class Return(expr: Term) extends Term
  @ast class Throw(expr: Term) extends Term
  @ast class Ascribe(expr: Term, tpe: Type) extends Term
  @ast class Annotate(expr: Term, annots: Seq[Mod.Annot] @nonEmpty) extends Term with Has.Mods {
    def mods: Seq[Mod] = annots
  }
  @ast class Tuple(elements: Seq[Term] @nonEmpty) extends Term
  @ast class Block(stats: Seq[Stmt.Block]) extends Term with Scope {
    require(stats.collect{ case v: Defn.Var => v }.forall(_.rhs.isDefined))
  }
  @ast class If(cond: Term, thenp: Term, elsep: Option[Term]) extends Term
  @ast class Match(scrut: Term, cases: Cases) extends Term
  @ast class Try(expr: Term, catchp: Option[Term], finallyp: Option[Term]) extends Term
  @ast class Function(params: Seq[Aux.Param], body: Term) extends Term with Scope.Params {
    require(params.forall(_.default.isEmpty))
    require(params.exists(_.mods.exists(_.isInstanceOf[Mod.Implicit])) ==> (params.length == 1))
  }
  @ast class Cases(cases: Seq[Aux.Case]) extends Term {
    def isPartialFunction = !parent.isInstanceOf[Match]
  }
  @ast class While(expr: Term, body: Term) extends Term
  @ast class Do(body: Term, expr: Term) extends Term
  @ast class For(enums: Seq[Enum] @nonEmpty, body: Term) extends Term with Scope {
    require(enums.head.isInstanceOf[Enum.Generator])
  }
  @ast class ForYield(enums: Seq[Enum] @nonEmpty, body: Term) extends Term
  @ast class New(templ: Aux.Template) extends Term
  // TODO: validate that placeholder is put into correct context
  @ast class Placeholder() extends Term
  @ast class Eta(term: Term) extends Term
}

@branch trait Type extends Tree with Scope.Template {
  // TODO: simple type validation
}
object Type {
  @branch trait Ref extends Type with core.Ref
  @ast class Name(value: String @nonEmpty, isBackquoted: Boolean = false) extends core.Name with Ref {
    // TODO: require(keywords.contains(value) ==> isBackquoted)
  }
  @ast class Select(qual: Term.Ref, name: Type.Name) extends Ref {
    // TODO: require(qual.isPath)
  }
  @ast class SuperSelect(qual: Option[core.Name], supertpe: Option[Type.Name], selector: Type.Name) extends Ref
  @ast class Project(qual: Type, name: Type.Name) extends Ref
  @ast class Singleton(ref: Term.Ref) extends Ref {
    // TODO: require(ref.isPath)
  }
  @ast class Apply(tpe: Type, args: Seq[Type] @nonEmpty) extends Type
  @ast class Function(params: Seq[Type], res: Type) extends Type
  @ast class Tuple(elements: Seq[Type] @nonEmpty) extends Type
  @ast class Compound(tpes: Seq[Type], refinement: Seq[Stmt.Refine]) extends Type with Scope.Refine {
    // TODO: require(tpes.length == 1 ==> hasExplicitRefinement)
  }
  @ast class Existential(tpe: Type, quants: Seq[Stmt.Existential] @nonEmpty) extends Type with Scope.Existential
  @ast class Annotate(tpe: Type, annots: Seq[Mod.Annot] @nonEmpty) extends Type with Has.Mods {
    def mods: Seq[Mod] = annots
  }
  // TODO: need to validate that placeholder appears within one of allowed contexts (e.g. `type T = _` is illegal)
  @ast class Placeholder(bounds: Aux.TypeBounds) extends Type
}

@branch trait Pat extends Tree
object Pat {
  @ast class Wildcard() extends Pat
  @ast class SeqWildcard() extends Pat
  @ast class Bind(lhs: Term.Name, rhs: Pat) extends Pat
  @ast class Alternative(lhs: Pat, rhs: Pat) extends Pat
  @ast class Tuple(elements: Seq[Pat] @nonEmpty) extends Pat
  @ast class Extract(ref: Term.Ref, targs: Seq[Type], elements: Seq[Pat]) extends Pat {
    // TODO: require(ref.isStableId)
  }
  @ast class Interpolate(prefix: Term.Name, parts: Seq[Lit.String] @nonEmpty, args: Seq[Pat]) extends Pat {
    // TODO: require(prefix.isInterpolationId)
    require(parts.length == args.length + 1)
  }
  @ast class Typed(lhs: Pat, rhs: Type) extends Pat {
    require(lhs.isInstanceOf[Pat.Wildcard] || lhs.isInstanceOf[Term.Name])
  }
}

@branch trait Member extends Tree with Has.Mods
object Member {
  @branch trait Term extends Member
  @branch trait Type extends Member
  @branch trait Def extends Term with Has.TermName with Stmt.Template with Has.Paramss with Scope.Params {
    def tparams: Seq[Aux.TypeParam]
  }
  @branch trait AbstractOrAliasType extends Type with Has.TypeName {
    def name: core.Type.Name
    def tparams: Seq[Aux.TypeParam]
  }
  @branch trait Template extends Member with Has.Name with Stmt.TopLevel with Stmt.Block with Has.Paramss with Scope.Template {
    def name: core.Name
    def explicits: Seq[Seq[Aux.Param.Named]] = Nil
    def implicits: Seq[Aux.Param.Named] = Nil
    def tparams: Seq[Aux.TypeParam] = Nil
    def templ: Aux.Template
    @mayFail def ctor: Ctor.Primary = fail(ReflectionException("no constructor found"))
  }
}
final case class Overload[+A <: Member](alts: Seq[A]) {
  def resolve(tpes: Seq[core.Type]): A = ??? // TODO: implement this in terms of Tree.attrs and Attribute.Ref
}

@branch trait Decl extends Stmt.Template with Stmt.Refine
object Decl {
  @ast class Val(mods: Seq[Mod],
                 pats: Seq[Term.Name] @nonEmpty,
                 decltpe: core.Type) extends Decl with Stmt.Existential with Has.Mods
  @ast class Var(mods: Seq[Mod],
                 pats: Seq[Term.Name] @nonEmpty,
                 decltpe: core.Type) extends Decl with Has.Mods
  @ast class Def(mods: Seq[Mod],
                 name: Term.Name,
                 tparams: Seq[Aux.TypeParam],
                 explicits: Seq[Seq[Aux.Param.Named]],
                 implicits: Seq[Aux.Param.Named],
                 decltpe: core.Type) extends Decl with Member.Def
  @ast class Procedure(mods: Seq[Mod],
                       name: Term.Name,
                       tparams: Seq[Aux.TypeParam],
                       explicits: Seq[Seq[Aux.Param.Named]],
                       implicits: Seq[Aux.Param.Named]) extends Decl with Member.Def
  @ast class Type(mods: Seq[Mod],
                  name: core.Type.Name,
                  tparams: Seq[Aux.TypeParam],
                  bounds: Aux.TypeBounds) extends Decl with Stmt.Existential with Member.AbstractOrAliasType
}

@branch trait Defn extends Stmt.Block with Stmt.Template
object Defn {
  @ast class Val(mods: Seq[Mod],
                 pats: Seq[Pat] @nonEmpty,
                 decltpe: Option[core.Type],
                 rhs: Term) extends Defn with Has.Mods
  @ast class Var(mods: Seq[Mod],
                 pats: Seq[Pat] @nonEmpty,
                 decltpe: Option[core.Type],
                 rhs: Option[Term]) extends Defn with Has.Mods {
    require(rhs.nonEmpty || pats.forall(_.isInstanceOf[Term.Name]))
    require(decltpe.nonEmpty || rhs.nonEmpty)
  }
  @ast class Def(mods: Seq[Mod],
                 name: Term.Name,
                 tparams: Seq[Aux.TypeParam],
                 explicits: Seq[Seq[Aux.Param.Named]],
                 implicits: Seq[Aux.Param.Named],
                 decltpe: Option[core.Type],
                 body: Term) extends Defn with Member.Def {
    require(mods.exists(_.isInstanceOf[Mod.Macro]) ==> decltpe.nonEmpty)
  }
  @ast class Procedure(mods: Seq[Mod],
                       name: Term.Name,
                       tparams: Seq[Aux.TypeParam],
                       explicits: Seq[Seq[Aux.Param.Named]],
                       implicits: Seq[Aux.Param.Named],
                       body: Term.Block) extends Defn with Member.Def {
  }
  @ast class Type(mods: Seq[Mod],
                  name: core.Type.Name,
                  tparams: Seq[Aux.TypeParam],
                  body: core.Type) extends Defn with Stmt.Refine with Member.AbstractOrAliasType
  @ast class Class(mods: Seq[Mod],
                   name: core.Type.Name,
                   override val tparams: Seq[Aux.TypeParam],
                   declctor: Ctor.Primary,
                   templ: Aux.Template) extends Defn with Member.Template with Member.Type with Has.TypeName {
    @mayFail override def ctor: Ctor.Primary = succeed(declctor)
  }
  @ast class Trait(mods: Seq[Mod],
                   name: core.Type.Name,
                   override val tparams: Seq[Aux.TypeParam],
                   templ: Aux.Template) extends Defn with Member.Template with Member.Type with Has.TypeName {
    require(templ.parents.forall(_.argss.isEmpty))
  }
  @ast class Object(mods: Seq[Mod],
                    name: Term.Name,
                    templ: Aux.Template) extends Defn with Member.Template with Member.Term with Has.TermName {
  }
}

@branch trait Pkg extends Tree {
  def stats: Seq[Stmt.TopLevel]
}
object Pkg {
  @ast class Root private[core] (stats: Seq[Stmt.TopLevel]) extends Pkg with Scope.TopLevel
  @ast class Empty(stats: Seq[Stmt.TopLevel]) extends Pkg with Scope.TopLevel
  @ast class Named(ref: Term.Ref,
                   stats: Seq[Stmt.TopLevel]) extends Pkg with Stmt.TopLevel with Scope.TopLevel with Member.Term with Has.TermName {
    // TODO: require(ref.isQualId)
    def mods: Seq[Mod] = Nil
    def name: Term.Name = ref match {
      case name: Term.Name => name
      case Term.Select(_, name) => name
      case _ => sys.error("this shouldn't have happened")
    }
  }
  @ast class Object(mods: Seq[Mod],
                    name: Term.Name,
                    templ: Aux.Template) extends Tree with Stmt.TopLevel with Member.Template with Member.Term with Has.TermName
}

@branch trait Ctor extends Tree with Has.Mods with Has.Paramss
object Ctor {
  @ast class Primary(mods: Seq[Mod] = Nil,
                     explicits: Seq[Seq[Aux.Param.Named]] = Nil,
                     implicits: Seq[Aux.Param.Named] = Nil) extends Ctor
  @ast class Secondary(mods: Seq[Mod] = Nil,
                       explicits: Seq[Seq[Aux.Param.Named]] @nonEmpty = List(Nil),
                       implicits: Seq[Aux.Param.Named] = Nil,
                       primaryCtorArgss: Seq[Seq[Arg]] = Nil,
                       stats: Seq[Stmt.Block] = Nil) extends Ctor with Stmt.Template with Scope.Params
}

object Stmt {
  @branch trait TopLevel extends Tree
  @branch trait Template extends Block
  @branch trait Block extends Refine
  @branch trait Refine extends Existential
  @branch trait Existential extends Tree
}

@branch trait Scope extends Tree
object Scope {
  @branch trait TopLevel extends Scope with Block
  @branch trait Template extends Block with Params
  @branch trait Block extends Refine
  @branch trait Refine extends Existential
  @branch trait Existential extends Scope
  @branch trait Params extends Scope
}

@branch trait Lit extends Term with Pat
object Lit {
  @branch trait Bool extends Lit
  @ast class True() extends Bool
  @ast class False() extends Bool
  @ast class Int(value: scala.Int) extends Lit with Type
  @ast class Long(value: scala.Long) extends Lit with Type
  @ast class Float(value: scala.Float) extends Lit with Type
  @ast class Double(value: scala.Double) extends Lit with Type
  @ast class Char(value: scala.Char) extends Lit with Type
  @ast class String(value: Predef.String) extends Lit with Type
  // TODO: validate that not all symbols are representable as literals, e.g. scala.Symbol("")
  @ast class Symbol(value: scala.Symbol) extends Lit with Type
  @ast class Null() extends Lit
  @ast class Unit() extends Lit
}

@ast class Import(clauses: Seq[Import.Clause] @nonEmpty) extends Stmt.TopLevel with Stmt.Template with Stmt.Block
object Import {
  // TODO: validate that wildcard import can only be the last one in the list of sels
  @ast class Clause(ref: Term.Ref, sels: Seq[Selector] @nonEmpty) extends Tree {
    // TODO: require(ref.isStableId)
  }

  @branch trait Selector extends Tree
  object Selector {
    @ast class Wildcard() extends Selector
    // TODO: needs some kind of idents here but they can neither be term nor type
    @ast class Name(name: String) extends Selector
    @ast class Rename(from: String, to: String) extends Selector
    @ast class Unimport(name: String) extends Selector
  }
}

@branch trait Arg extends Tree
object Arg {
  @ast class Named(name: Term.Name, arg: Term) extends Arg
  @ast class Seq(arg: Term) extends Arg
}

@branch trait Enum extends Tree
object Enum {
  @ast class Generator(pat: Pat, rhs: Term) extends Enum
  @ast class Val(pat: Pat, rhs: Term) extends Enum
  @ast class Guard(cond: Term) extends Enum
}

@branch trait Mod extends Tree
object Mod {
  @ast class Annot(tpe: Type, argss: Seq[Seq[Arg]]) extends Mod
  @ast class Doc(doc: String) extends Mod // TODO: design representation for scaladoc
  @ast class Private(within: String) extends Mod // TODO: design a name resolution API for these and imports
  @ast class Protected(within: String) extends Mod
  @ast class Implicit() extends Mod
  @ast class Final() extends Mod
  @ast class Sealed() extends Mod
  @ast class Override() extends Mod
  @ast class Case() extends Mod
  @ast class Abstract() extends Mod
  @ast class Covariant() extends Mod
  @ast class Contravariant() extends Mod
  @ast class Lazy() extends Mod
  @ast class AbstractOverride() extends Mod
  @ast class Macro() extends Mod
  @ast class ByNameParam() extends Mod
  @ast class VarargParam() extends Mod
  @ast class ValParam() extends Mod
  @ast class VarParam() extends Mod
}

object Aux {
  @ast class Case(pat: Pat, cond: Option[Term] = None, body: Option[Term] = None) extends Tree with Scope
  @ast class Parent(tpe: Type, argss: Seq[Seq[Arg]] = Nil) extends Ref
  @ast class Template(early: Seq[Defn.Val] = Nil, parents: Seq[Parent] = Nil,
                      self: Self = Self.empty, stats: Seq[Stmt.Template] = Nil) extends Tree with Scope.Template {
    require(parents.isEmpty || !parents.tail.exists(_.argss.nonEmpty))
  }
  @ast class Self(name: Option[Term.Name] = None, decltpe: Option[Type] = None) extends Member.Term {
    def mods: Seq[Mod] = Nil
  }
  @branch trait Param extends Tree with Has.Mods {
    def default: Option[Term]
  }
  object Param {
    @ast class Anonymous(decltpe: Option[Type] = None,
                         mods: Seq[Mod] = Nil,
                         default: Option[Term] = None) extends Param
    @ast class Named(name: Term.Name,
                     decltpe: Type,
                     mods: Seq[Mod] = Nil,
                     default: Option[Term] = None) extends Param with Member.Term with Has.TermName
  }
  @branch trait TypeParam extends Tree with Has.Mods {
    def tparams: Seq[Aux.TypeParam]
    def contextBounds: Seq[core.Type]
    def viewBounds: Seq[core.Type]
    def bounds: Aux.TypeBounds
  }
  object TypeParam {
    @ast class Anonymous(mods: Seq[Mod] = Nil,
                         tparams: Seq[Aux.TypeParam] = Nil,
                         contextBounds: Seq[core.Type] = Nil,
                         viewBounds: Seq[core.Type] = Nil,
                         bounds: Aux.TypeBounds = Aux.TypeBounds.empty) extends TypeParam
    @ast class Named(name: core.Type.Name,
                     mods: Seq[Mod] = Nil,
                     tparams: Seq[Aux.TypeParam] = Nil,
                     contextBounds: Seq[core.Type] = Nil,
                     viewBounds: Seq[core.Type] = Nil,
                     bounds: Aux.TypeBounds = Aux.TypeBounds.empty) extends TypeParam with Member.Type with Has.TypeName
  }
  @ast class TypeBounds(lo: Option[Type] = None, hi: Option[Type] = None) extends Tree
}

object Has {
  @branch trait Mods extends Tree {
    def mods: Seq[Mod]
    // TODO: https://docs.google.com/spreadsheet/ccc?key=0Ahw_zqMtW4nNdC1lRVJvc3VjTUdOX0ppMVpSYzVRSHc&usp=sharing#gid=0
    // * write a script that fetches this google doc and converts it into a, say, CSV spec
    // * write a test that validates the spec by generating source files and parsing them
    // * write a macro that generates implementation of validateAnnots from the spec + extension methods like isImplicit
    private[core] def validateMods(): Unit = ???
  }

  @branch trait Paramss extends Tree {
    def explicits: Seq[Seq[Aux.Param.Named]]
    def implicits: Seq[Aux.Param.Named]
    def paramss: Seq[Seq[Aux.Param.Named]] = explicits :+ implicits
  }

  @branch trait Name extends Member { def name: core.Name }
  @branch trait TermName extends Member.Term with Has.Name { def name: Term.Name }
  @branch trait TypeName extends Member.Type with Has.Name { def name: Type.Name }
}