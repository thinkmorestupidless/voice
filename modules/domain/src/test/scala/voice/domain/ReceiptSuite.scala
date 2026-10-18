package voice.domain

import voice.domain.erasure.*

import java.lang.reflect.{Field, Modifier, ParameterizedType, Type}

/**
 * `erasure.receipt.personal`, at build time: a receipt holds no `Sealed` value and no free text.
 * Its strings are an operator's principal and enum wire names; anything else that can hold text
 * fails here before a receipt can carry it (FR-058).
 */
class ReceiptSuite extends munit.FunSuite:

  /** The only string fields a receipt may reach: a principal's subject, never a person's words. */
  private val AllowedStrings =
    Set("Authority$Operator.principal", "Verification$OperatorVerified.principal")

  private def fields(c: Class[?]): Seq[Field] =
    c.getDeclaredFields.toSeq.filter(f => !Modifier.isStatic(f.getModifiers))

  private def reached(t: Type): Seq[Class[?]] = t match
    case c: Class[?] if c.getName.startsWith("voice.domain.") => Seq(c)
    case p: ParameterizedType => p.getActualTypeArguments.toSeq.flatMap(reached)
    case _                    => Seq.empty

  /** The classes a receipt reaches, with the enum cases of the enums it holds. */
  private def walk(start: Class[?]): Set[Class[?]] =
    def cases(c: Class[?]): Seq[Class[?]] =
      Option(c.getPermittedSubclasses).toSeq.flatten ++ c.getDeclaredClasses.toSeq
    var seen  = Set.empty[Class[?]]
    var queue = List(start)
    while queue.nonEmpty do
      val c = queue.head
      queue = queue.tail
      if !seen(c) then
        seen += c
        queue = queue ++ fields(c).flatMap(f => reached(f.getGenericType)) ++ cases(c).filter(
          _.getName.startsWith("voice.domain.")
        )
    seen

  private def key(c: Class[?], f: Field) =
    c.getName.stripPrefix("voice.domain.erasure.").stripPrefix("voice.domain.") + "." + f.getName

  test("a receipt reaches no sealed value") {
    for c <- walk(classOf[Receipt]); f <- fields(c) do
      assert(
        !f.getType.getName.contains("Sealed") && !f.getGenericType.getTypeName.contains("Sealed"),
        key(c, f)
      )
  }

  test("a receipt holds no free text: its only strings are a principal's subject") {
    val strings = for
      c <- walk(classOf[Receipt]).toSeq
      f <- fields(c)
      if f.getType == classOf[String] || f.getGenericType.getTypeName.contains("java.lang.String")
    yield key(c, f)
    val unexpected = strings.filterNot(s =>
      AllowedStrings.contains(s) || s.endsWith(".wire") || s.endsWith("$name")
    )
    assertEquals(unexpected.toSet, Set.empty[String], Rules.ErasureReceiptPersonal.name)
  }

  test("the receipt counts each class and says whether completion was verified") {
    val at = java.time.Instant.parse("2026-11-02T09:00:00Z")
    val r = ErasureRecord.empty.copy(
      participantId = "prt-1",
      cause = Some(ErasureCause.Dormancy),
      scope = ErasureScope(conversations = Vector("cnv-1", "cnv-2"))
    )
    val receipt = Receipt.of(r, Authority.DormancyRule, at, keyGone = true)
    assertEquals(receipt.cause, ErasureCause.Dormancy)
    assertEquals(receipt.classes.find(_.erased == ErasedClass.Conversations).map(_.count), Some(2))
    assert(receipt.completionVerified)
    assert(!receipt.toString.contains("prt-1"))
  }
