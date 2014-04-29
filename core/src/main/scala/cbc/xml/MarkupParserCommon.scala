/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2013, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

package cbc.xml

/** This is not a public trait - it contains common code shared
 *  between the library level XML parser and the compiler's.
 *  All members should be accessed through those.
 */
trait MarkupParserCommon {
  import Utility._
  import cbc.util.Chars.SU

  protected def unreachable = scala.sys.error("Cannot be reached.")

  type ElementType      // NodeSeq, Tree
  type NamespaceType    // NamespaceBinding, Any
  type AttributesType   // (MetaData, NamespaceBinding), mutable.Map[String, Tree]

  def mkAttributes(name: String, pscope: NamespaceType): AttributesType
  def mkProcInstr(name: String, text: String): ElementType

  /** parse a start or empty tag.
   *  [40] STag         ::= '<' Name { S Attribute } [S]
   *  [44] EmptyElemTag ::= '<' Name { S Attribute } [S]
   */
  protected def xTag(pscope: NamespaceType): (String, AttributesType) = {
    val name = xName
    xSpaceOpt()

    (name, mkAttributes(name, pscope))
  }

  /** '<?' ProcInstr ::= Name [S ({Char} - ({Char}'>?' {Char})]'?>'
   *
   * see [15]
   */
  def xProcInstr: ElementType = {
    val n = xName
    xSpaceOpt()
    xTakeUntil(mkProcInstr(n, _), "?>")
  }

  /** attribute value, terminated by either `'` or `"`. value may not contain `<`.
   @param endCh either `'` or `"`
   */
  def xAttributeValue(endCh: Char): String = {
    val buf = new StringBuilder
    while (ch != endCh) {
      // well-formedness constraint
      if (ch == '<') syntaxError("'<' not allowed in attrib value")
      else if (ch == SU) truncatedError("")
      else buf append ch_returning_nextch
    }
    ch_returning_nextch
    // @todo: normalize attribute value
    buf.toString
  }

  /** [42]  '<' xmlEndTag ::=  '<' '/' Name S? '>'
   */
  def xEndTag(startName: String) {
    xToken('/')
    if (xName != startName)
      errorNoEnd(startName)

    xSpaceOpt()
    xToken('>')
  }

  /** actually, Name ::= (Letter | '_' | ':') (NameChar)*  but starting with ':' cannot happen
   *  Name ::= (Letter | '_') (NameChar)*
   *
   *  see  [5] of XML 1.0 specification
   *
   *  pre-condition:  ch != ':' // assured by definition of XMLSTART token
   *  post-condition: name does neither start, nor end in ':'
   */
  def xName: String = {
    if (ch == SU)
      truncatedError("")
    else if (!isNameStart(ch))
      syntaxError("name expected, but char '%s' cannot start a name" format ch)

    val buf = new StringBuilder

    do buf append ch_returning_nextch
    while (isNameChar(ch))

    if (buf.last == ':') {
      syntaxError( "name cannot end in ':'" )
      buf.toString dropRight 1
    }
    else buf.toString
  }

  /** CharRef ::= "&#" '0'..'9' {'0'..'9'} ";"
   *            | "&#x" '0'..'9'|'A'..'F'|'a'..'f' { hexdigit } ";"
   *
   * see [66]
   */
  def xCharRef(ch: () => Char, nextch: () => Unit): String =
    Utility.parseCharRef(ch, nextch, syntaxError _, truncatedError _)

  def xCharRef(it: Iterator[Char]): String = {
    var c = it.next()
    Utility.parseCharRef(() => c, () => { c = it.next() }, syntaxError _, truncatedError _)
  }

  def xCharRef: String = xCharRef(() => ch, () => nextch())

  /** Create a lookahead reader which does not influence the input */
  def lookahead(): BufferedIterator[Char]

  /** The library and compiler parsers had the interesting distinction of
   *  different behavior for nextch (a function for which there are a total
   *  of two plausible behaviors, so we know the design space was fully
   *  explored.) One of them returned the value of nextch before the increment
   *  and one of them the new value.  So to unify code we have to at least
   *  temporarily abstract over the nextchs.
   */
  def ch: Char
  def nextch(): Unit
  protected def ch_returning_nextch: Char
  def eof: Boolean

  def xHandleError(that: Char, msg: String): Unit
  def syntaxError(str: String): Nothing
  def syntaxError(pos: Int, str: String): Nothing

  def truncatedError(msg: String): Nothing
  def errorNoEnd(tag: String): Nothing

  def xToken(that: Char) {
    if (ch == that) nextch()
    else xHandleError(that, "'%s' expected instead of '%s'".format(that, ch))
  }
  def xToken(that: Seq[Char]) { that foreach xToken }

  /** scan [S] '=' [S]*/
  def xEQ() = { xSpaceOpt(); xToken('='); xSpaceOpt() }

  /** skip optional space S? */
  def xSpaceOpt() = while (isSpace(ch) && !eof) nextch()

  /** scan [3] S ::= (#x20 | #x9 | #xD | #xA)+ */
  def xSpace() =
    if (isSpace(ch)) { nextch(); xSpaceOpt() }
    else xHandleError(ch, "whitespace expected")

  /** Apply a function and return the passed value */
  def returning[T](x: T)(f: T => Unit): T = { f(x); x }

  /** Execute body with a variable saved and restored after execution */
  def saving[A, B](getter: A, setter: A => Unit)(body: => B): B = {
    val saved = getter
    try body
    finally setter(saved)
  }

  /** Take characters from input stream until given String "until"
   *  is seen.  Once seen, the accumulated characters are passed
   *  along with the current Position to the supplied handler function.
   */
  protected def xTakeUntil[T](handler: String => T, until: String): T = {
    val sb = new StringBuilder
    val head = until.head
    val rest = until.tail

    while (true) {
      if (ch == head && peek(rest))
        return handler(sb.toString)
      else if (ch == SU)
        truncatedError("")  // throws TruncatedXMLControl in compiler

      sb append ch
      nextch()
    }
    unreachable
  }

  /** Create a non-destructive lookahead reader and see if the head
   *  of the input would match the given String.  If yes, return true
   *  and drop the entire String from input; if no, return false
   *  and leave input unchanged.
   */
  private def peek(lookingFor: String): Boolean =
    (lookahead() take lookingFor.length sameElements lookingFor.iterator) && {
      // drop the chars from the real reader (all lookahead + orig)
      (0 to lookingFor.length) foreach (_ => nextch())
      true
    }
}