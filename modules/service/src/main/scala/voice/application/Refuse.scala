package voice.application

import com.thinkmorestupidless.ankka.core.ErrorCode
import voice.domain.{Refusal, Refusals}

/**
 * A refusal as an entity or workflow answers it: `effects.error(message, code)`, read back at the
 * edge.
 */
object Refuse:
  def apply(r: Refusal): (String, ErrorCode) =
    val e = Refusals.toCommandError(r)
    (e.message, e.code)
