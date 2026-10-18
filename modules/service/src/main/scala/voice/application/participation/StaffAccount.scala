package voice.application.participation

import com.thinkmorestupidless.ankka.core.*
import com.thinkmorestupidless.ankka.sdk.*
import voice.domain.Provenance

/**
 * What Voice holds about a staff person that their identity provider cannot: the name shown on the
 * records they write, the authority a representative acts under, and whether the account is active
 * (R10). Never a role: roles are the token's.
 */
final case class StaffAccountState(
    principal: String,
    displayName: String,
    authority: Option[String],
    active: Boolean,
    provenance: Option[Provenance]
)

final case class WriteStaffAccount(
    displayName: String,
    authority: Option[String],
    active: Boolean,
    provenance: Provenance
)

final class StaffAccountEntity(context: KeyValueEntityContext)
    extends KeyValueEntity[StaffAccountState]:

  def emptyState: StaffAccountState =
    StaffAccountState(context.entityId, "", None, active = false, None)

  def write(command: WriteStaffAccount): Effect[StaffAccountState] =
    val next = StaffAccountState(
      context.entityId,
      command.displayName,
      command.authority,
      command.active,
      Some(command.provenance)
    )
    effects.updateState(next).thenReply(s => s)

  def get: ReadOnlyEffect[StaffAccountState] = effects.reply(currentState)

object StaffAccountEntity
    extends KeyValueEntity.Companion[StaffAccountEntity, StaffAccountState](
      componentId = ComponentId("staff-account"),
      stateSerializer = Codecs.serializer[StaffAccountState]("staff-account")
    ):
  given Serializer[WriteStaffAccount] = Codecs.serializer[WriteStaffAccount]("staff-account-write")

  def create(context: KeyValueEntityContext) = new StaffAccountEntity(context)

  val write = command("write")(_.write)
  val get   = query("get")(_.get)

/** Every staff account, for the operator's listing. */
final class StaffAccountRowsView extends View[StaffAccountState, StaffAccountState]:
  def onChange(state: StaffAccountState): Effect = effects.updateRow(state)

object StaffAccountRows
    extends View.Companion[StaffAccountRowsView, StaffAccountState, StaffAccountState](
      componentId = ComponentId("staff-account-rows"),
      source = ChangeSource.stateOf(StaffAccountEntity),
      rowSerializer = Codecs.serializer[StaffAccountState]("staff-account-row")
    ):
  def create(ctx: ViewComponentContext) = new StaffAccountRowsView
