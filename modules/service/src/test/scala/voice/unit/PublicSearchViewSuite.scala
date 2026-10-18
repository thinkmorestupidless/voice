package voice.unit

import voice.application.issues.PublicSearch
import voice.domain.{Classification, FieldClassifications}
import voice.domain.issues.SearchRow

/**
 * `public.search.filter` (contracts/refusals.md): the public search view's query reads only columns
 * the classification table marks public, and every text field a search row holds is public, so a
 * snippet cut from a row can hold nothing else (R14, FR-027).
 */
class PublicSearchViewSuite extends munit.FunSuite:

  private val Read = "payload::jsonb->>'([A-Za-z]+)'".r

  test("the search query names only the declared columns") {
    val named = Read.findAllMatchIn(PublicSearch.SearchSql).map(_.group(1)).toSet
    assert(named.nonEmpty, PublicSearch.SearchSql)
    assertEquals(named -- PublicSearch.Columns.toSet, Set.empty[String])
  }

  test("every declared column is a public field of the row") {
    val fields = classOf[SearchRow].getDeclaredFields.map(_.getName).toSet
    for column <- PublicSearch.Columns do
      assert(fields.contains(column), s"$column is not a field of SearchRow")
      val classified = FieldClassifications.table.get(s"SearchRow.$column")
      assert(
        classified.forall(_ == Classification.Public),
        s"public.search.filter: view public-search, column $column is $classified"
      )
  }

  test("every text field of a search row and its parts is public") {
    for
      (key, c) <- FieldClassifications.table
      if key.startsWith("SearchRow.") || key.startsWith("SearchPart.")
    do assertEquals(c, Classification.Public, s"public.search.filter: $key")
    for f <- classOf[SearchRow].getDeclaredFields if f.getType == classOf[String] do
      assertEquals(
        FieldClassifications.table.get(s"SearchRow.${f.getName}"),
        Some(Classification.Public),
        f.getName
      )
  }
