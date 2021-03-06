package de.htwg.se.rummy.controller.component

import com.google.inject.{Guice, Injector}
import de.htwg.se.rummy.RummyModule
import de.htwg.se.rummy.controller.ControllerInterface
import de.htwg.se.rummy.controller.component.ControllerState._
import de.htwg.se.rummy.controller.component.command._
import de.htwg.se.rummy.model.DeskInterface
import de.htwg.se.rummy.model.deskComp.deskBaseImpl
import de.htwg.se.rummy.model.deskComp.deskBaseImpl.deskImpl.{Color, Tile}
import de.htwg.se.rummy.model.deskComp.deskBaseImpl.{PlayerInterface, TileInterface}
import de.htwg.se.rummy.model.fileIO.FileIOInterface
import de.htwg.se.rummy.util.UndoManager
import net.codingwell.scalaguice.InjectorExtensions._
import play.api.libs.json.JsObject

import scala.collection.immutable.SortedSet
import scala.util.Random

class Controller(var desk: DeskInterface) extends ControllerInterface {

  val injector: Injector = Guice.createInjector(new RummyModule)
  private val undoManager = new UndoManager
  private val fileIO = injector.instance[FileIOInterface]

  override def userFinishedPlay(): Unit = if (userPutTileDown == 0) {
    if (desk.bagOfTiles.isEmpty) switchState(AnswerState.BAG_IS_EMPTY, ControllerState.P_TURN)
    else undoManager.doStep(new TakeTileCommand(this, desk.getTileFromBag))
  } else if (desk.checkTable()) {
    if (desk.currentPlayerWon()) switchState(AnswerState.P_WON, ControllerState.KILL)
    else undoManager.doStep(new FinishedCommand(userPutTileDown, this))
  } else switchState(AnswerState.TABLE_NOT_CORRECT, ControllerState.P_TURN)

  override def moveTile(thisTile: TileInterface, toThisTile: TileInterface): Unit =
    undoManager.doStep(new MoveTileCommand(thisTile, toThisTile, this))

  override def layDownTile(tile: TileInterface): Unit = if (getCurrentPlayer has tile)
    undoManager.doStep(new LayDownCommand(tile, this)) else
    switchState(AnswerState.P_DOES_NOT_OWN_TILE, ControllerState.P_TURN)

  override def getCurrentPlayer: PlayerInterface =
    desk.getCurrentPlayer

  override def getPlayerByName(name: String): Option[PlayerInterface] =
    desk.getPlayerByName(name)

  override def getPreviousPlayer: PlayerInterface =
    desk.getPreviousPlayer

  override def getNextPlayer: PlayerInterface =
    desk.getNextPlayer

  override def addPlayerAndInit(newName: String, max: Int): Unit = if (!hasLessThan4Players)
    switchState(AnswerState.ENOUGH_PLAYER, ControllerState.INSERTING_NAMES)
  else undoManager.doStep(new NameCommand(newName, max, this))

  override def hasLessThan4Players: Boolean =
    desk.lessThan4P

  override def switchToNextPlayer(): Unit =
    undoManager.doStep(new SwitchPlayerCommand(this))

  override def nameInputFinished(): Unit = if (desk.correctAmountOfPlayers) {
    undoManager.emptyStack()
    switchState(AnswerState.INSERTING_NAMES_FINISHED, P_TURN)
  } else switchState(AnswerState.NOT_ENOUGH_PLAYERS, ControllerState.INSERTING_NAMES)

  override def getAmountOfPlayers: Int = desk.amountOfPlayers

  override def setsOnDeskAreCorrect: Boolean = desk.checkTable()

  override def removeTileFromSet(tile: TileInterface): Unit = desk = desk.removeFromTable(tile)

  override def undo(): Unit = {
    undoManager.undoStep()
    notifyObservers()
  }

  override def redo(): Unit = {
    undoManager.redoStep()
    notifyObservers()
  }

  override def storeFile(): Unit = {
    fileIO.save(desk)
    switchState(AnswerState.STORED_FILE, controllerState)
  }

  override def switchState(answer: AnswerState.Value, c: ControllerState.Value): Unit = {
    answerState = answer
    controllerState = c
    notifyObservers()
  }

  override def loadFile(): Unit = fileIO.load match {
    case Some(value) =>
      desk = value
      switchState(AnswerState.LOADED_FILE, P_TURN)
    case None =>
      switchState(AnswerState.COULD_NOT_LOAD_FILE, MENU)
      createDesk(12)
  }

  override def createDesk(amount: Int): Unit = {
    var bagOfTiles: Set[TileInterface] = Set[TileInterface]()
    (1 to amount).foreach(value => Set(Color.RED, Color.YELLOW, Color.GREEN, Color.BLUE)
      .foreach(color => (0 to 1).foreach(ident => bagOfTiles += Tile(value, color, ident))))
    desk = deskBaseImpl.Desk(List[PlayerInterface](), Random.shuffle(bagOfTiles), Set[SortedSet[TileInterface]]())
    switchState(AnswerState.CREATED_DESK, ControllerState.INSERTING_NAMES)
  }

  override def viewOfTable: Set[SortedSet[TileInterface]] =
    desk.tableView

  override def viewOfBoard: SortedSet[TileInterface] =
    desk.boardView

  override def currentControllerState: ControllerState.Value =
    controllerState

  override def currentAnswerState: AnswerState.Value =
    answerState

  override def toJson: JsObject =
    fileIO.toJson(desk)


}

