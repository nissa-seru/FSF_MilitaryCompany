package data.scripts.hullmods

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignUIAPI
import com.fs.starfarer.api.campaign.econ.MarketAPI
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.impl.campaign.ids.HullMods
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import combat.impl.aEP_BaseCombatEffect
import combat.plugin.aEP_CombatEffectPlugin
import combat.util.aEP_Combat
import combat.util.aEP_Combat.Companion.getTargetCurrentAimed
import combat.util.aEP_DataTool
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_Tool
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.abs

class aEP_EliteShip:aEP_BaseHullMod() {

  companion object{
    const val ID = "aEP_EliteShip"
    const val INSV_ID = "aEP_InSv"
    const val HP_KEY = "aEP_EliteShipHpLastCheck"

    const val SHIELD_DAMAGE_REDUCE_MULT = 0.3f
    const val ARMOR_BONUS_FLAT = 200f
    const val ARMOR_BONUS_PERCENT = 25f

    const val HULL_BONUS_FLAT = 1000f
    const val HULL_BONUS_PERCENT = 30f

    const val PEAK_CR_DURATION_FLAT = 100f
    const val RR_DECREASE_REDUCE_MULT = 0f
    val SPEED_BONUS = LinkedHashMap<ShipAPI.HullSize, Float>()
    init {
      SPEED_BONUS[ShipAPI.HullSize.CAPITAL_SHIP] = 5f
      SPEED_BONUS[ShipAPI.HullSize.CRUISER] = 10f
      SPEED_BONUS[ShipAPI.HullSize.DESTROYER] = 15f
      SPEED_BONUS[ShipAPI.HullSize.FRIGATE] = 25f
    }

    //增加百分之多少部署
    val DP_INCREASE = LinkedHashMap<ShipAPI.HullSize, Float>()
    const val DEFAULT_DP_INCREASE = 25f
    init {
      DP_INCREASE[ShipAPI.HullSize.CAPITAL_SHIP] = 25f
      DP_INCREASE[ShipAPI.HullSize.CRUISER] = 25f
      DP_INCREASE[ShipAPI.HullSize.DESTROYER] = 25f
      DP_INCREASE[ShipAPI.HullSize.FRIGATE] = 25f
    }
    //最少也会增加多少部署
    const val DP_INCREASE_MIN = 2f


    const val DRONE_ID = "aEP_ut_decoy"

    const val SHIELD_ACTIVE_TIME = 6f

    const val HP_LOSS_PERCENT_TO_CHECK = 0.2f

    const val CHECK_COOLDOWN = 6f

    const val SHIELD_CHANCE = 0.5f
    val SHIELD_AMOUNT = LinkedHashMap<ShipAPI.HullSize, Float>()
    const val DEFAULT_SHIELD_AMOUNT = 8000f
    init {
      SHIELD_AMOUNT[ShipAPI.HullSize.CAPITAL_SHIP] = 40000f
      SHIELD_AMOUNT[ShipAPI.HullSize.CRUISER] = 30000f
      SHIELD_AMOUNT[ShipAPI.HullSize.DESTROYER] = 20000f
      SHIELD_AMOUNT[ShipAPI.HullSize.FRIGATE] = 10000f
    }

  }

  init {
    haveToBeWithMod.add(aEP_SpecialHull.ID)
    notCompatibleList.add(HullMods.CONVERTED_HANGAR)
    notCompatibleList.add(HullMods.HEAVYARMOR)
    notCompatibleList.add(HullMods.HARDENED_SHIELDS)

    banShipList.add("aEP_cru_hailiang3")
  }

  override fun applyEffectsBeforeShipCreation(hullSize: ShipAPI.HullSize, stats: MutableShipStatsAPI, id: String?) {

    //dynamic使用getMod而不是getStats
    val baseCost = stats.suppliesToRecover.baseValue
    val increasePercent = (DP_INCREASE[hullSize]?: DEFAULT_DP_INCREASE)/100f
    val increase =  (baseCost * increasePercent).coerceAtLeast(DP_INCREASE_MIN)

    stats.dynamic.getMod(Stats.DEPLOYMENT_POINTS_MOD).modifyFlat(ID, increase)
    stats.shieldDamageTakenMult.modifyMult(ID, 1f - SHIELD_DAMAGE_REDUCE_MULT)

    stats.armorBonus.modifyPercent(ID, ARMOR_BONUS_PERCENT)
    stats.armorBonus.modifyFlat(ID, ARMOR_BONUS_FLAT)

    stats.hullBonus.modifyPercent(ID, HULL_BONUS_PERCENT)
    stats.hullBonus.modifyFlat(ID, HULL_BONUS_FLAT)


    stats.dynamic.getMod(Stats.REPLACEMENT_RATE_DECREASE_MULT).modifyMult(ID,1f - RR_DECREASE_REDUCE_MULT)

    stats.dynamic.getStat(INSV_ID).modifyFlat(ID,0.5f)

    stats.maxSpeed.modifyFlat(ID, SPEED_BONUS[hullSize]?: 5f)

    stats.peakCRDuration.modifyFlat(ID, PEAK_CR_DURATION_FLAT)

  }

  override fun applyEffectsAfterShipCreationImpl(ship: ShipAPI, id: String) {
    // ship.engineController.flameColorShifter.shift(ID, Color.blue, 0.1f, Float.MAX_VALUE, 0.4f)

  }

  override fun advanceInCombat(ship: ShipAPI, amount: Float) {
    val currHitpoint = ship.hitpoints
    val hpLastCheck = (ship.customData[HP_KEY]?:currHitpoint) as Float

    var shouldUse = false

    //改变颜色，类似安超
    ship.engineController.fadeToOtherColor(this, Color.blue, null, 1f, 0.25f)
    ship.engineController.extendFlame(this, 0.1f, 0.1f, 0.1f)

    if(hpLastCheck > currHitpoint){
      if(hpLastCheck - currHitpoint > ship.maxHitpoints * HP_LOSS_PERCENT_TO_CHECK ){
        shouldUse = true
        ship.setCustomData(HP_KEY, currHitpoint)
      }
    }else{
      //如果舰船当前hp涨了，直接把新的hp存进去
      ship.setCustomData(HP_KEY, currHitpoint)
    }

    if(ship.fluxTracker.isOverloaded){
      shouldUse = true
    }

    if(shouldUse && !aEP_Tool.isDead(ship) ){
      //如果需要启用护盾，检查是否处于冷却
      val currentAimed = getTargetCurrentAimed(ID,ship)
      if(currentAimed >= 1f) return

      //如果通过的冷却检查，无论是否通过概率检测，都存入冷却
      val time = Math.max(ship.fluxTracker.overloadTimeRemaining, SHIELD_ACTIVE_TIME)
      aEP_CombatEffectPlugin.addEffect(aEP_Combat.MarkTarget(CHECK_COOLDOWN, ID, 1f, ship))

      val random = MathUtils.getRandomNumberInRange(0f,1f)
      if(random >= 1f - ship.mutableStats.dynamic.getStat(INSV_ID).modifiedValue){
        Global.getCombatEngine().addFloatingText(ship.location, String.format("Protect Test: %.0f",random*100f ), 30f, Color.green, ship, 1f,5f)

        val variant = Global.getSettings().createEmptyVariant(DRONE_ID, Global.getSettings().getHullSpec(DRONE_ID))
        variant.addMod(aEP_ProjectileDenialShield.ID)
        val drone = Global.getCombatEngine().createFXDrone(variant)
        drone.owner = ship.originalOwner
        drone.layer = CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER
        drone.mutableStats.hullDamageTakenMult.modifyMult(ID, 0f) // so it's non-targetable
        drone.isDrone = true
        drone.setShield(ShieldAPI.ShieldType.OMNI, 0f, 1f, 360f)
        drone.collisionRadius = ship.collisionRadius
        drone.shield.radius = ship.collisionRadius + 25f
        drone.shield.innerColor = Color(50,50,75,90)
        drone.shield.ringColor = Color(150,150,150,205)
        drone.mutableStats.fluxCapacity.baseValue = SHIELD_AMOUNT[ship.hullSize]?: DEFAULT_SHIELD_AMOUNT
        drone.mutableStats.fluxDissipation.baseValue = 100f
        drone.mutableStats.shieldUnfoldRateMult.modifyFlat(ID, 1000f)
        drone.location.set(Vector2f(ship.location))
        Global.getCombatEngine().addEntity(drone)
        aEP_CombatEffectPlugin.addEffect(aEP_Combat.RecallFighterJitter(0.33f,drone))
        aEP_CombatEffectPlugin.addEffect(ProtectionDrone(time, drone, ship))

      }else{
        Global.getCombatEngine().addFloatingText(ship.location, String.format("Protect Test: %.0f",random*100f ), 30f, Color.red, ship, 1f,5f)

      }
    }
  }

  /**
    Ship may be null from autofit.
  */
  override fun canBeAddedOrRemovedNow(ship: ShipAPI?, marketOrNull: MarketAPI?, mode: CampaignUIAPI.CoreUITradeMode?): Boolean {
    ship?:return true

    var unusedOpTotal = 0
    unusedOpTotal += ship.variant.getUnusedOP(ship.fleetMember?.fleetCommanderForStats?.stats)
    val hullCost = spec.getCostFor(ship.hullSpec.hullSize)
    if(unusedOpTotal < abs(hullCost)) return false
    return super.canBeAddedOrRemovedNow(ship, marketOrNull, mode)
  }

  override fun getCanNotBeInstalledNowReason(ship: ShipAPI?, marketOrNull: MarketAPI?, mode: CampaignUIAPI.CoreUITradeMode?): String {
    ship?:return ""

    var unusedOpTotal = 0
    unusedOpTotal += ship.variant.getUnusedOP(ship.fleetMember?.fleetCommanderForStats?.stats)
    val hullCost = spec.getCostFor(ship.hullSpec.hullSize)
    if(unusedOpTotal < abs(hullCost)) return txt("aEP_EliteShip09")

    return super.getCanNotBeInstalledNowReason(ship, marketOrNull, mode)
  }

  override fun isApplicableToShip(ship: ShipAPI): Boolean {
    //如果之前已经判断不能装了，直接返回false
    val toReturn = super.isApplicableToShip(ship)
    if(!toReturn) return false

    //检查模块
    //因为在装配页面模块其实还没有算作模块，此处没有用，使用的是模块自带的黑名单
    //可以用作特殊情况的示范代码
    if(ship.isStationModule ){
      return false
    }

    //默认为true
    return true
  }

  override fun getUnapplicableReason(ship: ShipAPI): String {
    //得到默认理由
    val toReturn = super.getUnapplicableReason(ship)

    //检查模块，如果不符合，加上额外的理由
    if(ship.isStationModule ){
      return toReturn + " " + aEP_DataTool.txt("not_module")
    }

    return toReturn
  }

  override fun shouldAddDescriptionToTooltip(hullSize: ShipAPI.HullSize, ship: ShipAPI?, isForModSpec: Boolean): Boolean {
    return true
  }

  override fun addPostDescriptionSection(tooltip: TooltipMakerAPI, hullSize: ShipAPI.HullSize, ship: ShipAPI?, width: Float, isForModSpec: Boolean) {
    val faction = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF)
    val highLight = Misc.getHighlightColor()
    val grayColor = Misc.getGrayColor()
    val txtColor = Misc.getTextColor()
    val barBgColor = faction.darkUIColor
    val factionColor: Color = faction.baseUIColor
    val titleTextColor: Color = faction.color

    //主效果
    tooltip.addSectionHeading(aEP_DataTool.txt("effect"), Alignment.MID, 5f)
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_EliteShip01"), 5f, arrayOf(Color.green,highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("-%.0f", SHIELD_DAMAGE_REDUCE_MULT*100f) +"%")
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_EliteShip02"), 5f, arrayOf(Color.green,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("+%.0f", ARMOR_BONUS_FLAT) +" " + String.format("+%.0f", ARMOR_BONUS_PERCENT)+"%")
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_EliteShip06"), 5f, arrayOf(Color.green,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("+%.0f", HULL_BONUS_FLAT) +" "+ String.format("+%.0f", HULL_BONUS_PERCENT)+"%")
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_EliteShip05"), 5f, arrayOf(Color.green,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("+%.0f", SPEED_BONUS[hullSize]?: 5f))
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_EliteShip10"), 5f, arrayOf(Color.green,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("+%.0f",  PEAK_CR_DURATION_FLAT ))
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_EliteShip07"), 5f, arrayOf(Color.green,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("%.0f",  HP_LOSS_PERCENT_TO_CHECK * 100f)+"%",
      aEP_DataTool.txt("aEP_EliteShip08"),
      String.format("%.0f",  SHIELD_CHANCE * 100f),
      String.format("%.0f",  SHIELD_AMOUNT[ship?.hullSize?:ShipAPI.HullSize.FRIGATE]?: DEFAULT_SHIELD_AMOUNT))

    //负面
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("aEP_EliteShip03"), 5f, arrayOf(Color.red,highLight, highLight),
      aEP_ID.HULLMOD_POINT,
      String.format("+%.0f", DP_INCREASE[hullSize]?: DEFAULT_DP_INCREASE) +"%",
      String.format("+%.0f", DP_INCREASE_MIN))
    //不兼容
    tooltip.addPara("{%s}"+ aEP_DataTool.txt("not_compatible") +"{%s}", 5f, arrayOf(Color.red, highLight), aEP_ID.HULLMOD_POINT,  showModName(notCompatibleList))

    //预热完全后额外效果
    //tooltip.addSectionHeading(aEP_DataTool.txt("when_soft_up"),txtColor,barBgColor, Alignment.MID, 5f)

    //灰字额外说明
    //tooltip.addPara(aEP_DataTool.txt("MD_des04"), grayColor, 5f)

  }

  class ProtectionDrone(lifetime:Float, entity: CombatEntityAPI, val protected:ShipAPI): aEP_BaseCombatEffect(lifetime, entity){
    override fun advanceImpl(amount: Float) {

      val fighter = entity as ShipAPI
      val stayLoc = protected.location
      if(protected.isHulk || !protected.isAlive || !Global.getCombatEngine().isEntityInPlay(protected)){
        stayLoc.set(fighter.location)
      }
      if(fighter.fluxTracker.isOverloaded || fighter.fluxLevel > 0.98f){
        shouldEnd = true
      }

      //根据当前幅能抖动护盾
      var level = fighter.fluxLevel
      val shift = 68
      val shieldColor = Color(62+(shift * level).toInt(), 72, 165,90)
      fighter.shield.innerColor = shieldColor
      val maxRangeBonus = fighter.collisionRadius * 1f
      val jitterRangeBonus: Float = 5f + level * maxRangeBonus
      fighter.isJitterShields = false
      fighter.setJitter(
        aEP_Combat.RecallFighterJitter.ID,
        shieldColor,
        level * 0.25f + 0.75f, 24, 0f, jitterRangeBonus)

      //在最后几秒缩小护盾半径
      val dur = 2f
      val leftTime = lifeTime - time
      if(leftTime < dur){
        fighter.shield?.arc = 90f + 270f * (leftTime / dur)
      }

      //设定飞机位置和朝向
      fighter.location.set(protected.location.x, protected.location.y)
      fighter.facing = protected.facing
      fighter.shield.toggleOn()
      val shieldTarget = protected.mouseTarget?: aEP_Tool.getExtendedLocationFromPoint(protected.location, protected.facing,500f)
      fighter.setShieldTargetOverride(shieldTarget.x, shieldTarget.x)

      //保护母舰防爆类
      aEP_ProjectileDenialShield.keepExplosionProtectListenerToParent(fighter, protected)
    }

    override fun readyToEnd() {
      val fighter = entity as ShipAPI
      Global.getCombatEngine().spawnExplosion(
        fighter.location,
        fighter.velocity,
        Color(201,160,60),
        fighter.collisionRadius*5f,
        2.4f)
      fighter.shield.toggleOff()
      Global.getCombatEngine().removeEntity(fighter)
    }
  }
}