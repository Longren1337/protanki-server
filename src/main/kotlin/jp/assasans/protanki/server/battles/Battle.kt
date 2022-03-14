package jp.assasans.protanki.server.battles

import mu.KotlinLogging
import jp.assasans.protanki.server.client.*
import jp.assasans.protanki.server.client.railgun.FireTarget
import jp.assasans.protanki.server.client.railgun.ShotTarget
import jp.assasans.protanki.server.commands.Command
import jp.assasans.protanki.server.commands.CommandName
import jp.assasans.protanki.server.garage.ServerGarageUserItemWeapon

interface ITickHandler {
  suspend fun tick() {}
}

enum class TankState {
  Dead,
  Respawn,
  SemiActive,
  Active
}

abstract class WeaponHandler(
  val player: BattlePlayer,
  val item: ServerGarageUserItemWeapon
) {
}

class RailgunWeaponHandler(
  player: BattlePlayer,
  weapon: ServerGarageUserItemWeapon
) : WeaponHandler(player, weapon) {
  suspend fun fireStart() {
    val tank = player.tank ?: throw Exception("No Tank")

    Command(CommandName.StartFire, listOf(tank.id)).sendTo(tank.player.battle)
  }

  suspend fun fireTarget(target: FireTarget) {
    val tank = player.tank ?: throw Exception("No Tank")

    Command(
      CommandName.ShotTarget,
      listOf(
        tank.id,
        ShotTarget(target).toJson()
      )
    ).sendTo(tank.player.battle)
  }
}

enum class BattleTeam(val id: Int, val key: String) {
  Red(0, "RED"),
  Blue(1, "BLUE"),

  None(2, "NONE");

  companion object {
    private val map = values().associateBy(BattleTeam::key)

    fun get(key: String) = map[key]
  }
}

class BattleMap(
  val id: Int,
  val name: String,
  val preview: Int
)

enum class SendTarget {
  Players,
  Spectators
}

suspend fun Command.sendTo(
  battle: Battle,
  vararg targets: SendTarget = arrayOf(SendTarget.Players, SendTarget.Spectators)
) = battle.sendTo(this, *targets)

fun List<BattlePlayer>.users() = filter { player -> !player.isSpectator }
fun List<BattlePlayer>.spectators() = filter { player -> player.isSpectator }

class Battle(
  val id: String,
  val title: String,
  var map: BattleMap,
  var fund: Int = 1337228
) : ITickHandler {
  companion object {
    private var lastId: Int = 1

    fun generateId(): String {
      return "test-${lastId++}"
    }
  }

  private val logger = KotlinLogging.logger { }

  val players: MutableList<BattlePlayer> = mutableListOf()

  suspend fun selectFor(socket: UserSocket) {
    Command(
      CommandName.SelectBattle,
      listOf(
        id
      )
    ).send(socket)
  }

  suspend fun showInfoFor(socket: UserSocket) {
    Command(CommandName.ClientSelectBattle, listOf(id)).send(socket)

    Command(
      CommandName.ShowBattleInfo,
      listOf(
        ShowDmBattleInfoData(
          itemId = id,
          battleMode = "DM",
          scoreLimit = 300,
          timeLimitInSec = 600,
          timeLeftInSec = 212,
          preview = map.preview,
          maxPeopleCount = 8,
          name = title,
          minRank = 0,
          maxRank = 16,
          spectator = true,
          withoutBonuses = false,
          withoutCrystals = false,
          withoutSupplies = false,
          users = listOf(
            BattleUser(user = "Luminate", kills = 666, score = 1337)
          ),
          score = 123
        ).toJson()
      )
    ).send(socket)
  }

  suspend fun sendTo(
    command: Command,
    vararg targets: SendTarget = arrayOf(SendTarget.Players, SendTarget.Spectators)
  ) {
    if(targets.contains(SendTarget.Players)) {
      players
        .users()
        .forEach { player -> command.send(player) }
    }
    if(targets.contains(SendTarget.Spectators)) {
      players
        .spectators()
        .forEach { player -> command.send(player) }
    }
  }

  override suspend fun tick() {
    players.forEach { player ->
      logger.trace { "Running tick handler for player ${player.user.username}" }
      player.tick()
    }
  }
}

interface IBattleProcessor : ITickHandler {
  val battles: MutableList<Battle>

  fun getBattle(id: String): Battle?
}

class BattleProcessor : IBattleProcessor {
  private val logger = KotlinLogging.logger { }

  override val battles: MutableList<Battle> = mutableListOf()

  override fun getBattle(id: String): Battle? = battles.singleOrNull { battle -> battle.id == id }

  override suspend fun tick() {
    battles.forEach { battle ->
      logger.trace { "Running tick handler for battle ${battle.id}" }
      battle.tick()
    }
  }
}
