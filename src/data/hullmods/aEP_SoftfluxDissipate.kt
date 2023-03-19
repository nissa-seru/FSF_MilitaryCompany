package data.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShieldAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import java.awt.Color

class aEP_SoftfluxDissipate internal constructor() : aEP_BaseHullMod() {
  companion object const {
    const val PER_PUNISH = 5f
    const val PER_BONUS = 15f
    const val ID = "aEP_SoftfluxDissipate"
    const val ID_P = "aEP_SoftfluxDissipate_p"
    const val ID_B = "aEP_SoftfluxDissipate_b"
  }

  init {
    notCompatibleList.add("aEP_RapidDissipate")
    notCompatibleList.add(HullMods.SAFETYOVERRIDES)
    haveToBeWithMod.add("aEP_MarkerDissipation")
  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI , id: String) {
    val numOfDissipation = ship.variant.numFluxVents
    ship.mutableStats.fluxDissipation.modifyFlat(ID_P,-numOfDissipation* PER_PUNISH)
    ship.mutableStats.fluxDissipation.modifyFlat(ID_B,numOfDissipation* PER_BONUS)

    if (!ship.hasListenerOfClass(FluxDissipationDynamic::class.java)) {
      ship.addListener(FluxDissipationDynamic(ship, numOfDissipation * PER_BONUS))
    }
  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {

  }

  override fun getDescriptionParam(index: Int, hullSize: HullSize): String? {
    return null
  }

  override fun shouldAddDescriptionToTooltip(hullSize: HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    ship?:return
    val h = Misc.getHighlightColor()
    //val fluxVent = (ship.variant.numFluxVents + ship.variant.numFluxCapacitors) * BASE_BUFF_PER_VENT
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"), Alignment.MID, 5f)
    val image = tooltip.beginImageWithText(Global.getSettings().getHullModSpec(ID).spriteName, 48f)
    image.addPara("- " + aEP_DataTool.txt("aEP_SoftfluxDissipate02") , 5f, Color.white, Color.green, PER_BONUS.toInt().toString(),"0" )
    image.addPara("- " + aEP_DataTool.txt("aEP_SoftfluxDissipate03") , 5f, Color.white, Color.red, PER_PUNISH.toInt().toString() )
    tooltip.addImageWithText(5f)
  }


  inner class FluxDissipationDynamic(val ship: ShipAPI, val maxBonus: Float):AdvanceableListener{
    val checkTracker = IntervalUtil(0.25f,0.25f)
    var heatingLevel = 0f

    override fun advance(amount: Float) {
      //维持玩家左下角的提示
      val bonus = heatingLevel * maxBonus * MathUtils.clamp(1f-ship.hardFluxLevel,0f,1f)
      if (Global.getCombatEngine().playerShip == ship) {
        Global.getCombatEngine().maintainStatusForPlayerShip(
          this.javaClass.simpleName,  //key
          Global.getSettings().getHullModSpec(ID).spriteName,  //sprite name,full, must be registed in setting first
          Global.getSettings().getHullModSpec(ID).displayName,  //title
          aEP_DataTool.txt("aEP_SoftfluxDissipate01")  + bonus.toInt(),  //data
          false
        )
      }

      checkTracker.advance(amount)
      if(!checkTracker.intervalElapsed()) return
      //根据预热程度，增加舰船幅散
      heatingLevel = aEP_MarkerDissipation.getBufferLevel(ship)
      ship.mutableStats.fluxDissipation.modifyFlat(ID_B, bonus)

    }
  }
}