package jp.assasans.protanki.server.commands.handlers

import kotlin.io.path.readText
import mu.KotlinLogging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import jp.assasans.protanki.server.IResourceManager
import jp.assasans.protanki.server.battles.BattlePlayer
import jp.assasans.protanki.server.battles.BattleTeam
import jp.assasans.protanki.server.battles.IBattleProcessor
import jp.assasans.protanki.server.client.Screen
import jp.assasans.protanki.server.client.UserSocket
import jp.assasans.protanki.server.client.send
import jp.assasans.protanki.server.commands.Command
import jp.assasans.protanki.server.commands.CommandHandler
import jp.assasans.protanki.server.commands.CommandName
import jp.assasans.protanki.server.commands.ICommandHandler

/*
Battle exit:
-> switch_battle_select
<- unload_battle
-> i_exit_from_battle
<- remove_player_from_battle [{"battleId":"8405f22972a7a3b1","id":"Assasans"}]
<- releaseSlot [8405f22972a7a3b1, Assasans]
<- init_messages
<- notify_user_leave_battle [Assasans]
* load lobby resources *
<- init_battle_create
<- init_battle_select
<- select [8405f22972a7a3b1]
<- show_battle_info [{{"itemId":"8405f22972a7a3b1", ...}]
*/

class LobbyHandler : ICommandHandler, KoinComponent {
  private val logger = KotlinLogging.logger { }

  private val battleProcessor by inject<IBattleProcessor>()
  private val resourceManager by inject<IResourceManager>()

  @CommandHandler(CommandName.SelectBattle)
  suspend fun selectBattle(socket: UserSocket, id: String) {
    val battle = battleProcessor.getBattle(id) ?: throw Exception("Battle $id not found")

    logger.debug { "Select battle $id -> ${battle.title}" }

    socket.selectedBattle = battle
    battle.showInfoFor(socket)
  }

  @CommandHandler(CommandName.ShowDamageEnabled)
  suspend fun showDamageEnabled(socket: UserSocket, id: String) {
    // TODO(Assasans)
  }

  @CommandHandler(CommandName.Fight)
  suspend fun fight(socket: UserSocket) {
    val player = BattlePlayer(
      socket = socket,
      battle = battleProcessor.battles[0],
      team = BattleTeam.None
    )
    battleProcessor.battles[0].players.add(player)

    socket.screen = Screen.Battle
    socket.initBattleLoad()

    Command(CommandName.InitShotsData, mutableListOf(resourceManager.get("shots-data.json").readText())).send(socket)

    socket.awaitDependency(socket.loadDependency(resourceManager.get("resources/maps/sandbox-summer-1.json").readText()))
    socket.awaitDependency(socket.loadDependency(resourceManager.get("resources/maps/sandbox-summer-2.json").readText()))
    socket.awaitDependency(socket.loadDependency(resourceManager.get("resources/maps/sandbox-summer-3.json").readText()))

    player.init()
    player.createTank()
  }

  @CommandHandler(CommandName.JoinAsSpectator)
  suspend fun joinAsSpectator(socket: UserSocket) {
    val player = BattlePlayer(
      socket = socket,
      battle = battleProcessor.battles[0],
      team = BattleTeam.None,
      isSpectator = true
    )
    battleProcessor.battles[0].players.add(player)

    // BattlePlayer(socket, this, null)

    socket.initBattleLoad()

    socket.awaitDependency(socket.loadDependency(resourceManager.get("resources/maps/sandbox-summer-1.json").readText()))
    socket.awaitDependency(socket.loadDependency(resourceManager.get("resources/maps/sandbox-summer-2.json").readText()))
    socket.awaitDependency(socket.loadDependency(resourceManager.get("resources/maps/sandbox-summer-3.json").readText()))

    player.init()
  }

  @CommandHandler(CommandName.InitSpectatorUser)
  suspend fun initSpectatorUser(socket: UserSocket) {
    Command(CommandName.InitShotsData, listOf(resourceManager.get("shots-data.json").readText())).send(socket) // TODO(Assasans): initBattleLoad?

    val player = socket.battlePlayer ?: throw Exception("No BattlePlayer")
    val battle = player.battle

    player.initLocal()
  }

  @CommandHandler(CommandName.SwitchBattleSelect)
  suspend fun switchBattleSelect(socket: UserSocket) {
    logger.debug { "Switch to battle select" }

    val battle = socket.battle

    if(battle != null && socket.screen == Screen.BattleSelect) {
      // Return to battle

      Command(CommandName.StartLayoutSwitch, listOf("BATTLE")).send(socket)
      Command(CommandName.UnloadBattleSelect).send(socket)
      Command(CommandName.EndLayoutSwitch, listOf("BATTLE", "BATTLE")).send(socket)
    } else {
      Command(CommandName.StartLayoutSwitch, listOf("BATTLE_SELECT")).send(socket)

      if(socket.screen == Screen.Garage) {
        Command(CommandName.UnloadGarage).send(socket)
      }

      socket.screen = Screen.BattleSelect
      socket.loadLobbyResources()

      Command(
        CommandName.EndLayoutSwitch, listOf(
          if(battle != null) "BATTLE" else "BATTLE_SELECT",
          "BATTLE_SELECT"
        )
      ).send(socket)

      socket.initBattleList()

      val selectedBattle = socket.selectedBattle
      if(selectedBattle != null) {
        logger.debug { "Select battle ${selectedBattle.id} -> ${selectedBattle.title}" }

        selectedBattle.selectFor(socket)
        selectedBattle.showInfoFor(socket)
      }
    }
  }

  @CommandHandler(CommandName.SwitchGarage)
  suspend fun switchGarage(socket: UserSocket) {
    logger.debug { "Switch to garage" }

    val battle = socket.battle

    if(battle != null && socket.screen == Screen.Garage) {
      // Return to battle

      Command(CommandName.StartLayoutSwitch, listOf("BATTLE")).send(socket)
      Command(CommandName.UnloadGarage).send(socket)
      Command(CommandName.EndLayoutSwitch, listOf("BATTLE", "BATTLE")).send(socket)
    } else {
      Command(CommandName.StartLayoutSwitch, listOf("GARAGE")).send(socket)

      if(socket.screen == Screen.BattleSelect) {
        Command(CommandName.UnloadBattleSelect).send(socket)
      }

      socket.screen = Screen.Garage
      socket.loadGarageResources()
      socket.initGarage()

      Command(
        CommandName.EndLayoutSwitch, listOf(
          if(battle != null) "BATTLE" else "GARAGE",
          "GARAGE"
        )
      ).send(socket)
    }
  }
}
