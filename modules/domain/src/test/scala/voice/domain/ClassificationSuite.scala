package voice.domain

import java.io.File
import java.lang.reflect.{Field, Modifier, ParameterizedType, Type}
import java.net.URI
import scala.jdk.CollectionConverters.*

/**
 * Lists every event and state type in `voice.domain` and holds each to three rules:
 *
 *   - a text field that is not sealed has a classification (FR-059);
 *   - every event carries its `Provenance` (FR-062);
 *   - no field is shaped like an ideological, partisan or demographic profile (FR-035).
 *
 * It grows with each story by itself: a new record is found on the classpath.
 */
class ClassificationSuite extends munit.FunSuite:

  private val Profile =
    "(?i).*(ideolog|partisan|party|politic|religio|ethnic|race|gender|sexual|demograph|ageband|voting|income).*".r

  /** Every class compiled into `voice.domain`, from the directories on the classpath. */
  private lazy val classes: Seq[Class[?]] =
    val loader = getClass.getClassLoader
    def walk(dir: File, pkg: String): Seq[String] =
      dir.listFiles().toSeq.flatMap { f =>
        if f.isDirectory then walk(f, s"$pkg.${f.getName}")
        else if f.getName.endsWith(".class") then Seq(s"$pkg.${f.getName.stripSuffix(".class")}")
        else Seq.empty
      }
    loader
      .getResources("voice/domain")
      .asScala
      .filter(_.getProtocol == "file")
      .flatMap(url => walk(File(URI(url.toString)), "voice.domain"))
      .toSeq
      .distinct
      .flatMap(name => scala.util.Try(Class.forName(name, false, loader)).toOption)
      .filter(c => !c.isInterface && !c.getName.contains("$anon") && !c.getName.endsWith("$"))
      .toSeq

  private def simpleOf(c: Class[?]): String = c.getName.stripPrefix(c.getPackageName + ".")

  private def isRecordRoot(c: Class[?]): Boolean =
    val simple = simpleOf(c)
    val root   = simple.takeWhile(_ != '$')
    root.endsWith("Event") || root.endsWith("Record") || root.endsWith("State") && !root.endsWith(
      "ChronologyState"
    )

  private def isEventCase(c: Class[?]): Boolean =
    val simple = simpleOf(c)
    simple.takeWhile(_ != '$').endsWith("Event") && simple.contains('$') &&
    classOf[Product].isAssignableFrom(c)

  /** The label and wire name an enum's constructor gives each of its cases: a category. */
  private def enumLabel(c: Class[?]): Boolean =
    classOf[scala.reflect.Enum].isAssignableFrom(c) && !c.getName.contains('$')

  private def key(c: Class[?], field: String): String =
    simpleOf(c).replace('$', '.') + "." + field

  private def fields(c: Class[?]): Seq[Field] =
    c.getDeclaredFields.toSeq.filter(f => !Modifier.isStatic(f.getModifiers))

  private def mentionsText(t: Type): Boolean = t match
    case c: Class[?] => c == classOf[String]
    case p: ParameterizedType =>
      val raw = p.getRawType.asInstanceOf[Class[?]]
      raw != classOf[Sealed[?]] && p.getActualTypeArguments.exists(mentionsText)
    case _ => false

  /** The `voice.domain` case classes a field's type reaches, to classify their fields too. */
  private def reached(t: Type): Seq[Class[?]] = t match
    case c: Class[?]
        if c.getName.startsWith("voice.domain.") && classOf[Product].isAssignableFrom(c) =>
      Seq(c)
    case p: ParameterizedType if p.getRawType != classOf[Sealed[?]] =>
      p.getActualTypeArguments.toSeq.flatMap(reached)
    case _ => Seq.empty

  private lazy val records: Seq[Class[?]] =
    val roots = classes.filter(isRecordRoot).filter(c => classOf[Product].isAssignableFrom(c))
    var seen  = roots.toSet
    var todo  = roots
    while todo.nonEmpty do
      val next =
        todo.flatMap(c => fields(c).flatMap(f => reached(f.getGenericType))).filterNot(seen)
      seen ++= next
      todo = next.distinct
    seen.toSeq.sortBy(_.getName)

  test("the suite finds the records it checks, in every package") {
    assert(classes.nonEmpty, "no classes found under voice/domain")
    val names = records.map(simpleOf).toSet
    assert(names.contains("ParticipantState"), names.toString)
    assert(names.contains("ContributionRecord"), names.toString)
    assert(classes.exists(c => simpleOf(c) == "ConversationEvent$MessageReceived"))
  }

  test("every text field of an event or a state is sealed or classified (FR-059)") {
    val unclassified = for
      c <- records
      f <- fields(c)
      if mentionsText(f.getGenericType) && !enumLabel(c) && FieldClassifications
        .of(key(c, f.getName))
        .isEmpty
    yield key(c, f.getName)
    assertEquals(unclassified, Seq.empty, "add each to FieldClassifications")
  }

  test("every event carries its provenance (FR-062)") {
    val bare =
      classes.filter(isEventCase).filterNot(c => fields(c).exists(_.getType == classOf[Provenance]))
    assertEquals(bare.map(_.getName), Seq.empty)
  }

  test("no field is shaped like an ideological, partisan or demographic profile (FR-035)") {
    val profiled = for
      c <- records
      f <- fields(c)
      if Profile.matches(f.getName)
    yield key(c, f.getName)
    assertEquals(profiled, Seq.empty)
  }
