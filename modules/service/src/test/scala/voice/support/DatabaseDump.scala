package voice.support

import java.sql.DriverManager
import scala.collection.mutable
import scala.util.Using

/**
 * Every journal, snapshot, durable state, view and timer table of a kit's database as text, for the
 * assertions that no plaintext personal value remains anywhere (SC-008). Bytes columns are read as
 * UTF-8, which is how the platform stores JSON payloads.
 */
object DatabaseDump:

  def dump(jdbcUrl: String): String =
    Using.resource(DriverManager.getConnection(jdbcUrl, "ankka", "ankka")) { connection =>
      val tables = mutable.ArrayBuffer.empty[String]
      Using.resource(
        connection
          .createStatement()
          .executeQuery(
            "SELECT table_schema, table_name FROM information_schema.tables " +
              "WHERE table_schema NOT IN ('pg_catalog', 'information_schema') AND table_type = 'BASE TABLE'"
          )
      ) { rs =>
        while rs.next() do tables += s""""${rs.getString(1)}"."${rs.getString(2)}""""
      }
      val out = StringBuilder()
      tables.foreach { table =>
        out.append("== ").append(table).append("\n")
        Using.resource(connection.createStatement().executeQuery(s"SELECT * FROM $table")) { rs =>
          val columns = rs.getMetaData.getColumnCount
          while rs.next() do
            (1 to columns).foreach { i =>
              val value = rs.getObject(i) match
                case null               => ""
                case bytes: Array[Byte] => String(bytes, "UTF-8")
                case other              => other.toString
              out.append(value).append('\t')
            }
            out.append('\n')
        }
      }
      out.toString
    }
