package data.scripts.campaign.intel

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.*
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode
import com.fs.starfarer.api.campaign.rules.MemoryAPI
import com.fs.starfarer.api.combat.EngagementResultAPI
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.ids.MemFlags
import com.fs.starfarer.api.ui.*
import com.fs.starfarer.api.util.Misc
import combat.util.aEP_DataTool.txt
import combat.util.aEP_ID
import combat.util.aEP_ID.Companion.CANCEL
import combat.util.aEP_ID.Companion.CONFIRM
import combat.util.aEP_ID.Companion.RETURN
import combat.util.aEP_ID.Companion.SELECT
import combat.util.aEP_Tool
import data.hullmods.aEP_CruiseMissileCarrier
import data.scripts.campaign.entity.aEP_CruiseMissileEntityPlugin
import jdk.jfr.Description
import org.lazywizard.lazylib.MathUtils
import org.lwjgl.opengl.GL11
import java.awt.Color
import java.util.*

class aEP_CruiseMissileLoadIntel:aEP_BaseMission(0f) {
  companion object{
    const val SELECT_MEMBER_BUTTON = "select_member"
    const val RELOAD_MEMBER_BUTTON = "reload_member"
    const val FIRE_MEMBER_BUTTON = "fire_member"
    //务必保证variantId和ItemId的一致性
    const val S1_ITEM_ID = "aEP_cruise_missile"
    const val S2_ITEM_ID = "aEP_cruise_missile2"
    const val S1_VAR_ID = "aEP_CruiseMissile"
    const val S2_VAR_ID = "aEP_CruiseMissile2"
    const val MEMORY_ID = "$" + "aEP_CruiseMissileLoadIntel"
    const val CR_THRESHOLD = 0.5f
    const val FIRE = "FIRE!"

    //<memberId, <missileItemId, 装填了多久> >
    //注意移出everyframe和intel的时候清理
    //防止内存泄露太多
    var LOADING_MAP = HashMap<String,ArrayList<String>>()

    val MISSILE_LOAD_SPEED_MAG = HashMap<String,Float>()
    init {
      MISSILE_LOAD_SPEED_MAG[S1_ITEM_ID] = 5f
      MISSILE_LOAD_SPEED_MAG[S2_ITEM_ID] = 2f
    }

    public val ITEM_TO_SHIP_ID = HashMap<String,String>()
    init {
      ITEM_TO_SHIP_ID[S1_ITEM_ID] = S1_VAR_ID
      ITEM_TO_SHIP_ID[S2_ITEM_ID] = S2_VAR_ID
    }

    fun createLoading(memberId:String, missileItemId:String){
      if(!ITEM_TO_SHIP_ID.keys.contains(missileItemId)) return
      val data = ArrayList<String>()
      data.add(missileItemId)
      data.add(0f.toString())
      LOADING_MAP[memberId] = data
    }

    fun checkMemberIdValid(memberId: String):Boolean{
      for(member in Global.getSector()?.playerFleet?.membersWithFightersCopy?:return false){
        if(member.id.equals(memberId)) return true
      }
      return false
    }

    /**
     * @return 如果没有get到，默认返回0f
     * */
    fun getLoadedAmount(memberId: String): Float{
      val data = LOADING_MAP[memberId]
      data?: return 0f
      val itemId= data[0]
      val curr = data[1].toFloat()
      val max = MISSILE_LOAD_SPEED_MAG.get(itemId)?:1f
      return MathUtils.clamp(curr/max,0f,1f)
    }

    /**
     * @return 如果没有get到，默认返回""
     * */
    fun getLoadedItemId(memberId: String): String{
      val data = LOADING_MAP[memberId]
      data?: return ""
      val itemId= data[0]
      return itemId
    }

    public fun createMissileEntityFromPlayer(missileItemId: String){
      /**
       * Adds a custom entity.
       * Use SectorEntityToken.setFixedLocation() or .setCircularOrbit (or setOrbit) to set its location and/or orbit.
       * @param id unique id. autogenerated if null.
       * @param name default name for entity used if this is null
       * @param type id in custom_entities.json
       * @param factionId defaults to "neutral" if not specified
       * @return
       */
      val missileShipId = ITEM_TO_SHIP_ID[missileItemId]
      val missile = Global.getSector().playerFleet.containingLocation.addCustomEntity(
        null,
        null,
        missileShipId,
        Global.getSector().playerFleet.faction.id
      )
      missile.memoryWithoutUpdate[MemFlags.FLEET_IGNORED_BY_OTHER_FLEETS] = true
      missile.facing = Global.getSector().playerFleet.facing
      missile.containingLocation = Global.getSector().playerFleet.containingLocation
      missile.setLocation(Global.getSector().playerFleet.location.x, Global.getSector().playerFleet.location.y)
      val plugin = missile.customPlugin as aEP_CruiseMissileEntityPlugin
      plugin.setVariantId(missileShipId)
    }
  }

  val pad = 3f
  val opad = 10f
  val h = Misc.getHighlightColor()
  val n = Misc.getTextColor()
  val g = Misc.getGrayColor()
  val c = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF).baseUIColor
  val d = Global.getSector().getFaction(aEP_ID.FACTION_ID_FSF).darkUIColor
  val sizeMult = 1f

  var selectedMember : FleetMemberAPI? = null
  var loadingPercent = 0f

  val missileFleetMembers = LinkedList<FleetMemberAPI>()
  var done = false

  //稳定的给导弹增加装填进度,放进memory，因为静态变量在游戏重启以后读档可不能保存
  override fun advanceImpl(amount: Float) {
    if(Global.getSector().memoryWithoutUpdate.contains(MEMORY_ID)){
      LOADING_MAP = Global.getSector().memoryWithoutUpdate.get(MEMORY_ID) as HashMap<String, ArrayList<String>>
    }

    //如果对应的member已经不在舰队中，返还装填中的导弹并移除
    val toRemove = ArrayList<String>()
    for( key in LOADING_MAP.keys){
      val data = LOADING_MAP[key]?: continue
      val missileItemName = data[0]
      var loadedAmount = data[1].toFloat()
      loadedAmount += Misc.getDays(amount)
      val loadSpeed = MISSILE_LOAD_SPEED_MAG[missileItemName]?:999999999f
      loadedAmount = MathUtils.clamp(loadedAmount,0f,loadSpeed)
      data[1] = loadedAmount.toString()
      //如果对应的member已经不在舰队中，返还装填中的导弹并移除
      if (!checkMemberIdValid(key)) {
        val loadedNow = getLoadedItemId(key)
        if(!loadedNow.equals(""))
          Global.getSector().playerFleet.cargo.addSpecial(SpecialItemData(loadedNow,null),1f)
        toRemove.add(key)
      }
    }
    LOADING_MAP.keys.removeAll(toRemove)

    //检出舰队中的所有包含巡洋导弹运载舰的成员
    missileFleetMembers.clear()
    for(m in Global.getSector().playerFleet?.membersWithFightersCopy?:return){
      if(m.variant.hasHullMod(aEP_CruiseMissileCarrier.ID)) missileFleetMembers.add(m)
    }

    Global.getSector().memoryWithoutUpdate.set(MEMORY_ID, LOADING_MAP)

    //如果玩家舰队中不存在任何导弹运载舰，自我移除
    if(missileFleetMembers.size == 0){
      //从Intel中移除，源于aEP_BaseIntel
      //自动联动了everyFrame的isDone方法，会自动从everyFrame中移除
      readyToEnd = true
    }
  }

  override fun hasLargeDescription(): Boolean {
    return true
  }

  override fun createLargeDescription(panel: CustomPanelAPI, width: Float, height: Float) {

    //------------------------------------------

    //创造一个覆盖整个页面的info背景
    //info顶上放一个heading
    val info = panel.createUIElement(width, height, true)
    info.addSectionHeading(txt("aEP_CruiseMissileLoadIntel01"), Color.white, d, Alignment.MID, opad)
    //info设置完了数据要存入底层panel才会显示
    panel.addUIElement(info).inTL(0f, 0f)

    //------------------------------------------

    //创造左列的舰船选择列表shipSelectPanel方格
    val shipSelectPanelW = width * 0.25f
    val shipSelectPanelH = height - 20f*sizeMult - opad
    val shipSelectPanel = panel.createUIElement(shipSelectPanelW, shipSelectPanelH, true)
    //检出舰队中的所有包含巡洋导弹运载舰的成员
    missileFleetMembers.clear()
    for(m in Global.getSector().playerFleet?.membersWithFightersCopy?:return){
      if(m.variant.hasHullMod(aEP_CruiseMissileCarrier.ID)) missileFleetMembers.add(m)
    }
    //为每个成员绘制数据
    var i = 0
    for(missileCarrier in missileFleetMembers){
      shipSelectPanel.beginImageWithText(missileCarrier.hullSpec.spriteName,75f*sizeMult)
      shipSelectPanel.addPara(missileCarrier.shipName,pad)
      shipSelectPanel.addImageWithText(pad)
      //如果第一次打开没有选择过任何member，每一个按钮都一样，否则给被选中的那个单独换色
      if(selectedMember == null){
        shipSelectPanel.addButton("Select",SELECT_MEMBER_BUTTON+i.toString(), c, d,75f*sizeMult,25*sizeMult,pad)
      }else{
        if(selectedMember == missileCarrier)
          shipSelectPanel.addButton("Select",SELECT_MEMBER_BUTTON+i.toString(), h, d,75f*sizeMult,25*sizeMult,pad)
        else
          shipSelectPanel.addButton("Select",SELECT_MEMBER_BUTTON+i.toString(), c, d,75f*sizeMult,25*sizeMult,pad)
      }

      i +=1
    }
    //左侧创造一列，低于heading一定距离，此为设定方框左上角的左边
    //此方法要在修改完子面板后再调用，否则会出界
    panel.addUIElement(shipSelectPanel).inTL(0f, 20f*sizeMult+opad)


    //------------------------------------------

    //创造右边上半详细的装填信息
    val reloadInfoPanelW = width * 0.75f
    val reloadInfoPanelH = height - 20f*sizeMult - opad
    val reloadInfoPanel = panel.createUIElement(reloadInfoPanelW, reloadInfoPanelH, true)
    createLoadingInfo(reloadInfoPanel)
    panel.addUIElement(reloadInfoPanel).inTL(width*0.25f, 20f*sizeMult+opad)


    /*
    //横向grid
    shipSelectPanel.beginGrid(50f, 10, Color.red)
    shipSelectPanel.addToGrid(0,1,"sdds","sds")
    shipSelectPanel.addGrid(pad)
    //检出舰队中的所有包含巡洋导弹运载舰的成员
    val missileFleetMembers = LinkedList<FleetMemberAPI>()
    for(m in Global.getSector().playerFleet?.membersWithFightersCopy?:return){
      if(m.variant.hasHullMod(ID)){
        missileFleetMembers.add(m)
      }
    }
    //为每个成员绘制数据
    for(missileCarrier in missileFleetMembers){
      shipSelectPanel.beginImageWithText(missileCarrier.hullSpec.spriteName,50f*sizeMult)
      shipSelectPanel.addPara(missileCarrier.shipName,pad)
      shipSelectPanel.addImageWithText(pad)
      val b = shipSelectPanel.addAreaCheckbox("xxx","sd", Color.WHITE, Color.WHITE, Color.WHITE,shipSelectPanelW,shipSelectPanelW/3f,pad)
      b.isChecked = false
    }

     */


  }

  override fun createIntelInfo(info: TooltipMakerAPI, mode: ListInfoMode?) {
    val pad = 3f
    val opad = 10f
    val h = Misc.getHighlightColor()
    val g = Misc.getGrayColor()
    val c = faction.baseUIColor
    info.setParaFontDefault()
    info.addPara(txt("aEP_CruiseMissileLoadIntel01"), c, pad)
    info.setBulletedListMode(BULLET)
    info.addPara(txt("aEP_CruiseMissileLoadIntel02"), opad, g, h)
  }

  override fun buttonPressConfirmed(buttonId: Any?, ui: IntelUIAPI?) {
    buttonId?: return
    ui?: return
    //SELECT_MEMBER_BUTTON必须用contains，因为之前在后缀加了1、2、3来区别选的是那艘
    if(buttonId.toString().contains(SELECT_MEMBER_BUTTON)){
      val numInList = buttonId.toString().replace(SELECT_MEMBER_BUTTON,"").toInt()
      selectedMember = missileFleetMembers[numInList]
    }else if(buttonId.toString().equals(RELOAD_MEMBER_BUTTON)){
      val x = ShowCargoPickDialog(selectedMember?.id?:"",ui, this)
      ui.showDialog(Global.getSector().playerFleet,x)
      //dialog在下一帧才被造出来，不能本帧就dismiss
      //在cargo选择的时候dismiss
      //Global.getSector().campaignUI.currentInteractionDialog.dismiss()
    }else if(buttonId.toString().equals(FIRE_MEMBER_BUTTON)){
      val selectedMemberId = selectedMember?.id?:""
      if(getLoadedAmount(selectedMemberId) >= 1f){
        //在移除loading以前发射哦，如果先移除，就无法get到itemId了，之后检索不到id也无法扣除玩家cargo里的导弹
        createMissileEntityFromPlayer(getLoadedItemId(selectedMemberId))
        LOADING_MAP.keys.remove(selectedMemberId)
      }
    }

    ui.updateUIForItem(this)
  }

  private fun createLoadingInfo(reloadInfoPanel: TooltipMakerAPI){
    selectedMember?: return
    val selectedMember = selectedMember as FleetMemberAPI
    val data = (LOADING_MAP[selectedMember.id])
    val missileItemName = data?.get(0)?:S1_ITEM_ID
    loadingPercent = data?.get(1)?.toFloat()?:0f
    var loadRate =  loadingPercent.div(MISSILE_LOAD_SPEED_MAG[missileItemName]?:1f).times(100).toInt()
    loadRate = MathUtils.clamp(loadRate,0,100)
    //画选择的舰队成员的数据
    var spriteSize = 100f
    var spriteName = Global.getSettings().getHullSpec(selectedMember.hullSpec.hullId).spriteName
    var showName = Global.getSettings().getHullSpec(selectedMember.hullSpec.hullId).hullName
    val image = reloadInfoPanel.beginImageWithText(spriteName,spriteSize*sizeMult)
    image.setBulletedListMode(BULLET)
    image.addPara(selectedMember.shipName,h,pad)
    //显示本成员的cr状态
    if(selectedMember.isMothballed || selectedMember.repairTracker.cr < CR_THRESHOLD){

    }else{

    }
    image.addPara(showName,n,pad)
    //显示本成员装填完成度，如果get不到就0
    image.addPara(txt("aEP_CruiseMissileLoadIntel05"),pad,n,h,loadRate.toString())
    image.setBulletedListMode("")
    reloadInfoPanel.addImageWithText(pad)

    //画导弹的数据
    spriteSize = 80f
    //之前得到是specialItem的Id，转换为舰船的Id方便得到贴图
    val missileShipName = ITEM_TO_SHIP_ID[missileItemName]?: ITEM_TO_SHIP_ID.values.elementAt(0)
    spriteName = Global.getSettings().getHullSpec(missileShipName).spriteName
    showName = Global.getSettings().getHullSpec(missileShipName).hullName
    if(data == null){
      //如果无装填，画无装填图标
      val image2 = reloadInfoPanel.beginImageWithText(Global.getSettings().getSpriteName("aEP_items","none"),spriteSize*sizeMult)
      image2.setBulletedListMode(BULLET)
      image2.addPara(txt("aEP_CruiseMissileLoadIntel03"),h,pad)
      image2.addPara(txt("aEP_CruiseMissileLoadIntel04"),n,pad)
      image2.setBulletedListMode("")
      reloadInfoPanel.addImageWithText(pad)
    } else{
      //如果正在装填，画导弹ship的图标
      val image2 = reloadInfoPanel.beginImageWithText(spriteName,spriteSize*sizeMult)
      image2.setBulletedListMode(BULLET)
      image2.addPara(Global.getSettings().getHullSpec(missileShipName).hullName,h,pad)
      image2.addPara(Global.getSettings().getDescription(missileShipName,com.fs.starfarer.api.loading.Description.Type.SHIP).text1FirstPara,n,pad)
      image2.setBulletedListMode("")
      reloadInfoPanel.addImageWithText(pad)

    }

    //加一个按钮，用于重新选择要装填的导弹
    reloadInfoPanel.addButton(SELECT,RELOAD_MEMBER_BUTTON, c, d,Alignment.LMID,CutStyle.ALL,75f*sizeMult,25*sizeMult,opad)

    //如果当前导弹已经装填完毕，加一个按钮，可以发射导弹
    if(loadRate >= 100){
      reloadInfoPanel.addButton(FIRE,FIRE_MEMBER_BUTTON, Color.white, Color.red,Alignment.LMID,CutStyle.ALL,75f*sizeMult,25*sizeMult,opad)
    }
  }

  override fun readyToEnd() {
    //返还导弹
    for( loaded in LOADING_MAP.values){
      //如果即将顶掉了一个存在装填效果，先还一个导弹回cargo
      val loadedNow = loaded[0]
      Global.getSector().playerFleet.cargo.addSpecial(SpecialItemData(loadedNow,null),1f)
    }
    //清空LOADING_MAP
    LOADING_MAP.clear()
    Global.getSector().memoryWithoutUpdate.keys.remove(MEMORY_ID)
  }


  class ShowCargoPickDialog : InteractionDialogPlugin{
    lateinit var dialog:InteractionDialogAPI
    lateinit var cargoListener : CargoPickListener
    lateinit var loading: CargoAPI
    lateinit var selectedMemberId:String
    lateinit var ui: IntelUIAPI
    lateinit var intelInfoPlugin:IntelInfoPlugin

    constructor(selectedMemberId: String,ui: IntelUIAPI, intelInfoPlugin: IntelInfoPlugin){
      this.selectedMemberId = selectedMemberId
      this.ui = ui
      this.intelInfoPlugin = intelInfoPlugin
    }

    override fun init(dialog: InteractionDialogAPI) {
      this.dialog = dialog
      this.selectedMemberId = selectedMemberId
      //Available Cargo 在上方的那个Cargo，从这里选东西拖下去
      loading = Global.getFactory().createCargo(false)
      //搜寻玩家Cargo，看看有哪些种类导弹可以装，加一发进loading
      val playerCargoCopy = aEP_Tool.getPlayerCargo()
      //整理一下，反正是copy
      playerCargoCopy.sort()
      //如果存在多于1个的某种导弹，加一个进loading仓作为可选弹
      for( itemId in ITEM_TO_SHIP_ID.keys ){
        val specialData = SpecialItemData(itemId,null)
        if (playerCargoCopy.getQuantity(CargoAPI.CargoItemType.SPECIAL,specialData) >= 1f){
          loading.addSpecial(specialData,1f)
        }
      }
      //创造一个cargoListener，并给它设置点击确定后对应的fleetMember
      //传入ui和intelInfoPlugin是为了在点击confirm以后，立刻刷新intelUi
      cargoListener = CargoPickListener(dialog,loading,selectedMemberId,ui,intelInfoPlugin)
      dialog.showCargoPickerDialog(
        "Confirm",
        CONFIRM,
        CANCEL,
        true,
        200f,
        loading,
        cargoListener)
      dialog.optionPanel.addOption(RETURN,RETURN)
    }

    //这2个输入都可能为空
    override fun optionSelected(optionText: String?, optionData: Any?) {
    }
    //这2个输入都可能为空
    override fun optionMousedOver(optionText: String?, optionData: Any?) {

    }

    /**
     * 在dialog显示时持续调用，显示cargo页面时也会
     */
    override fun advance(amount: Float) {
    }
    override fun backFromEngagement(battleResult: EngagementResultAPI) {}
    override fun getContext(): Any? {
      return null
    }

    override fun getMemoryMap(): Map<String, MemoryAPI>? {
      return null
    }
  }

  class CargoPickListener: CargoPickerListener {
    var dialog: InteractionDialogAPI
    var picked: CargoAPI? = null
    var available: CargoAPI
    var selectedMemberId: String
    var ui: IntelUIAPI
    var intelInfoPlugin:IntelInfoPlugin

    //传入ui和intelInfoPlugin是为了在点击confirm以后，立刻刷新intelUi
    constructor(dialog: InteractionDialogAPI, available: CargoAPI, selectedMemberId:String,ui: IntelUIAPI, intelInfoPlugin:IntelInfoPlugin){
      this.dialog = dialog
      this.available = available
      this.selectedMemberId = selectedMemberId
      this.ui = ui
      this.intelInfoPlugin = intelInfoPlugin
    }

    //Selected Cargo，下方那个初始空着的Cargo，选择Confirm时调用
    //因为下面的recreateTextPanel做了限制，这里只可能有0或1个stack
    override fun pickedCargo(cargo: CargoAPI?) {
      cargo?:return
      cargo.sort()
      if(cargo.stacksCopy.size > 0){
        val missileItemId = cargo.stacksCopy[0].specialItemSpecIfSpecial.id

        //如果即将顶掉了一个存在装填效果，先还一个导弹回cargo
        val loadedNow = getLoadedItemId(selectedMemberId)
        if(!loadedNow.equals(""))
          Global.getSector().playerFleet.cargo.addSpecial(SpecialItemData(loadedNow,null),1f)

        //装入新装填效果
        createLoading( selectedMemberId,missileItemId)
        //装哪个导弹，移除玩家cargo中的一个对应item
        Global.getSector().playerFleet.cargo.removeItems(CargoAPI.CargoItemType.SPECIAL,SpecialItemData(missileItemId,null),1f)
        aEP_Tool.addDebugLog("$selectedMemberId cruise missile $missileItemId loaded")

      }else{ // 如果不选择导弹，移除已存的装填效果，返还导弹
        //如果即将顶掉了一个存在装填效果，先还一个导弹回cargo
        val loadedNow = getLoadedItemId(selectedMemberId)
        if(!loadedNow.equals(""))
          Global.getSector().playerFleet.cargo.addSpecial(SpecialItemData(loadedNow,null),1f)

        //移除装填效果
        LOADING_MAP.keys.remove(selectedMemberId)
      }
      dialog.dismiss()
      ui.updateUIForItem(intelInfoPlugin)
    }

    //选择Cancel时调用
    override fun cancelledCargoSelection() {
      dialog.dismiss()
      ui.updateUIForItem(intelInfoPlugin)
    }

    /**
     * 本方法会每帧调用，甚至可以做动画，神奇吧
     * @param cargo pickedCargo，下面那个货舱
     * @param combined 点起来的那个stack加上下面pickedCargo合起来算成一个cargo
     * */
    override fun recreateTextPanel(panel: TooltipMakerAPI?, cargo: CargoAPI?, pickedUp: CargoStackAPI?, pickedUpFromSource: Boolean, combined: CargoAPI?) {
      picked = cargo ?: return
      //如果拖下来超过1个导弹，重置pickedCargo，把东西倒回availableCargo
      //不可以选2种导弹装填
      if(combined?.stacksCopy?.size?:0 > 1) {
        for(stack in cargo.stacksCopy){
          available.addFromStack(stack)
          cargo.removeStack(stack)
        }
        return
      }
    }
  }

}
