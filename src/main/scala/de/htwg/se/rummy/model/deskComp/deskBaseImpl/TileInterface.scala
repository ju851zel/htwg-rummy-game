package de.htwg.se.rummy.model.deskComp.deskBaseImpl

import de.htwg.se.rummy.model.deskComp.deskBaseImpl.deskImpl.Color


trait TileInterface extends Ordered[TileInterface] {

  val value: Int
  val color: Color.Value
  val ident: Int

  override def compare(that: TileInterface): Int
}
