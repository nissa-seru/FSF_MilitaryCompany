package data.scripts.world.aEP_systems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.DerelictShipEntityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.*;
import com.fs.starfarer.api.impl.campaign.procgen.NebulaEditor;
import com.fs.starfarer.api.impl.campaign.procgen.StarSystemGenerator;
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.special.ShipRecoverySpecial;
import com.fs.starfarer.api.impl.campaign.terrain.DebrisFieldTerrainPlugin;
import com.fs.starfarer.api.impl.campaign.terrain.HyperspaceTerrainPlugin;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.Random;

public class aEP_IND_Lamdor implements SectorGeneratorPlugin {

  public static String SHENDU_SPEC_ID = "aEP_des_shendu";

  @Override
  public void generate(SectorAPI sector) {
    StarSystemAPI system = sector.createStarSystem("Lamdor");
    system.getLocation().set(3650f, -22480f);
    system.setLightColor(new Color(255, 160, 120, 100));// light color in entire system, affects all entities
    LocationAPI hyper = Global.getSector().getHyperspace();
    system.setBackgroundTextureFilename("graphics/backgrounds/background4.jpg");

    //system.getMemoryWithoutUpdate().set(MusicPlayerPluginImpl.MUSIC_SET_MEM_KEY, "music_title");


    //add center star
    PlanetAPI IND_HomeStar = system.initStar("aEP_IND_HomeStar",
      "aEP_IND_Homestar",
      250f,
      350f);


    //add planet 1
    PlanetAPI IND_HomePlanet = system.addPlanet("IND_HomePlanet", //Unique id for this planet (or null to have it be autogenerated)
      IND_HomeStar, // What the planet orbits (orbit is always circular)
      "Lamdor X", //Name
      "aEP_IND_Homeplanet", //Planet type id in planets.json
      60, //Starting angle in orbit, i.e. 0 = to the right of the star
      120, // Planet radius, pixels at default zoom
      7800, //Orbit radius, pixels at default zoom
      600);//Days it takes to complete an orbit. 1 day = 10 seconds.

    //add market for planet 1
    MarketAPI M02 = Global.getFactory().createMarket("IND_HomePlanet", IND_HomePlanet.getName(), 0);//id, name, size

    M02.setSize(4);
    M02.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
    M02.setPrimaryEntity(IND_HomePlanet);

    M02.setFactionId("independent");
    M02.setPrimaryEntity(IND_HomePlanet);
    M02.addCondition(Conditions.POPULATION_4);
    M02.addIndustry(Industries.POPULATION);
    M02.addIndustry("spaceport");
    M02.addIndustry("grounddefenses");
    M02.addIndustry("militarybase");
    M02.addIndustry("orbitalstation");
    M02.addIndustry("waystation");

    M02.addSubmarket(Submarkets.SUBMARKET_OPEN);
    M02.addSubmarket(Submarkets.SUBMARKET_BLACK);
    M02.addSubmarket(Submarkets.SUBMARKET_STORAGE);

    M02.addCondition("cold");
    M02.addCondition("poor_light");


    M02.getTariff().modifyFlat("default_tariff", IND_HomePlanet.getFaction().getTariffFraction());

    //这把市场粘到星球上，这时星球的Condition就是Market的Condition了
    //给Market加Condition就是给星球加Condition
    IND_HomePlanet.setMarket(M02);
    //这把市场粘到星球上
    IND_HomePlanet.setFaction(M02.getFactionId());


    Global.getSector().getEconomy().addMarket(M02, true);// marketAPI, isJunkAround


    //planet 2
    PlanetAPI IND_PiratePlanet = system.addPlanet("aEP_IND_PiratePlanet", //Unique id for this planet (or null to have it be autogenerated)
      IND_HomeStar, // What the planet orbits (orbit is always circular)
      "Lamdor Y", //Name
      "aEP_IND_Pirateplanet", //Planet type id in planets.json
      -35, //Starting angle in orbit, i.e. 0 = to the right of the star
      120, // Planet radius, pixels at default zoom
      2800, //Orbit radius, pixels at default zoom
      240);//Days it takes to complete an orbit. 1 day = 10 seconds.


    //add station 2 for planet 2
    SectorEntityToken IND_PirateStation = system.addCustomEntity("aEP_IND_Piratestation",// id
      null,// name
      "aEP_IND_Piratestation",// type id in planets.json
      "pirates");//faction id
    IND_PirateStation.setCircularOrbit(IND_PiratePlanet, 0, 300, 30);//which to orbit, starting angle, radius, orbit days

    //add market for station2
    MarketAPI M01 = Global.getFactory().createMarket("IND_PirateStation", IND_PirateStation.getName(), 3);//id, name, size
    M01.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
    M01.setFactionId("pirates");
    M01.setPrimaryEntity(IND_PirateStation);
    M01.addCondition("population_3");
    M01.addIndustry("population");
    M01.addIndustry("spaceport");
    M01.addIndustry("militarybase");
    M01.addIndustry("orbitalstation");
    M01.addSubmarket("open_market");
    M01.addSubmarket("storage");
    M01.getTariff().modifyFlat("default_tariff", IND_PirateStation.getFaction().getTariffFraction());
    IND_PirateStation.setMarket(M01);
    Global.getSector().getEconomy().addMarket(M01, true);// marketAPI, isJunkAround


    SectorEntityToken IND_buoy = system.addCustomEntity("aEP_IND_buoy",// id
      null,// name
      "nav_buoy",// type id in planets.json
      "independent");//faction id
    IND_buoy.setCircularOrbit(IND_HomeStar, 0, 4100, 400);//which to orbit, starting angle, radius, orbit days

    SectorEntityToken IND_relay = system.addCustomEntity("aEP_IND_relay",// id
      null,// name
      "comm_relay",// type id in planets.json
      "independent");//faction id
    IND_relay.setCircularOrbit(IND_HomePlanet, 300, 1200, 100);//which to orbit, starting angle, radius, orbit days


	     /*SectorEntityToken focus, java.lang.String category, java.lang.String key, float bandWidthInTexture, int bandIndex, java.awt.Color color,
						    float bandWidthInEngine, float middleRadius, float orbitDays, java.lang.String terrainId, java.lang.String optionalName
		 */
    system.addRingBand(IND_HomeStar, "misc", "rings_ice0", 256f, 0, Color.white, 256f, 6000, 80f);
    system.addRingBand(IND_HomeStar, "misc", "rings_ice0", 256f, 0, Color.white, 256f, 5500, 140f);
    system.addRingBand(IND_HomeStar, "misc", "rings_ice0", 256f, 1, Color.white, 256f, 5000, 160f, Terrain.RING, "Cloud Ring");

    system.addRingBand(IND_HomeStar, "misc", "rings_ice0", 256f, 0, Color.white, 256f, 8500, 100f);
    system.addRingBand(IND_HomeStar, "misc", "rings_ice0", 256f, 0, Color.white, 256f, 8000, 160f);
    system.addRingBand(IND_HomeStar, "misc", "rings_ice0", 256f, 1, Color.white, 256f, 8500, 180f, Terrain.RING, "Cloud Ring");


    // generates hyperspace destinations for in-system jump points
    system.autogenerateHyperspaceJumpPoints(true, true);

    //在边缘生成2个随机的先进航母
    //创造一个的残骸实体，并绑上打捞参数
    DerelictShipEntityPlugin.DerelictShipData params = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData(SHENDU_SPEC_ID+"_Standard", ShipRecoverySpecial.ShipCondition.WRECKED, 0f), false);
    params.ship.nameAlwaysKnown = true;
    params.durationDays = 999999999f;
    params.ship.addDmods = true;
    SectorEntityToken ship = BaseThemeGenerator.addSalvageEntity(IND_HomeStar.getContainingLocation(),
            Entities.WRECK, Factions.NEUTRAL, params);
    //设置实体位置
    ship.setCircularOrbit(IND_HomeStar,
            102,
            5400f,
            600f);
    ship.setDiscoverable(true);

    //创造一份特殊舰船打捞数据
    ShipRecoverySpecial.ShipRecoverySpecialData specialData = new ShipRecoverySpecial.ShipRecoverySpecialData("abandoned");
    ShipRecoverySpecial.PerShipData perData = new ShipRecoverySpecial.PerShipData(SHENDU_SPEC_ID+"_Standard", ShipRecoverySpecial.ShipCondition.WRECKED);
    perData.addDmods = true;
    perData.condition = ShipRecoverySpecial.ShipCondition.AVERAGE;
    perData.nameAlwaysKnown = true;
    specialData.addShip(perData);
    specialData.storyPointRecovery = true;
    //把特殊打捞数据绑给实体
    Misc.setSalvageSpecial(ship, specialData);


    //创造第二个残骸实体
    DerelictShipEntityPlugin.DerelictShipData params2 = new DerelictShipEntityPlugin.DerelictShipData(new ShipRecoverySpecial.PerShipData(SHENDU_SPEC_ID+"_Standard", ShipRecoverySpecial.ShipCondition.WRECKED, 0f), false);
    params2.ship.nameAlwaysKnown = true;
    params2.durationDays = 999999999f;
    params2.ship.addDmods = true;
    SectorEntityToken ship2 = BaseThemeGenerator.addSalvageEntity(IND_HomeStar.getContainingLocation(),
            Entities.WRECK, Factions.NEUTRAL, params2);
    //设置实体位置
    ship2.setCircularOrbit(IND_HomeStar,
            103,
            5460f,
            600f);
    ship2.setDiscoverable(true);

    //创造第二份特殊舰船打捞数据
    ShipRecoverySpecial.ShipRecoverySpecialData specialData2 = new ShipRecoverySpecial.ShipRecoverySpecialData("abandoned");
    ShipRecoverySpecial.PerShipData perData2 = new ShipRecoverySpecial.PerShipData(SHENDU_SPEC_ID+"_Standard", ShipRecoverySpecial.ShipCondition.AVERAGE);
    perData2.addDmods = true;
    perData2.condition = ShipRecoverySpecial.ShipCondition.AVERAGE;
    perData2.nameAlwaysKnown = true;
    specialData2.addShip(perData2);
    specialData2.storyPointRecovery = true;
    //把特殊打捞数据绑给实体
    Misc.setSalvageSpecial(ship2, specialData2);

    //加一片废墟
    //add debris field
    DebrisFieldTerrainPlugin.DebrisFieldParams debrisFieldParams = new DebrisFieldTerrainPlugin.DebrisFieldParams(
            200f, // field radius - should not go above 1000 for performance reasons
            1.6f, // density, visual - affects number of debris pieces
            999999999f, // duration in days
            30f); // days the field will keep generating glowing pieces

    debrisFieldParams.source = DebrisFieldTerrainPlugin.DebrisFieldSource.GEN;
    SectorEntityToken debris = Misc.addDebrisField(IND_HomeStar.getContainingLocation(), debrisFieldParams, StarSystemGenerator.random);
    // makes the debris field always visible on map/sensors and not give any xp or notification on being discovered
    debris.setSensorProfile(null);
    debris.setDiscoverable(null);

    debris.setCircularOrbit(IND_HomeStar,
            102.5f,
            5400f,
            600f);

    cleanup(system);
  }


  //from Tart scripts
  //Clean nearby Nebula(nearby system)
  private void cleanup(StarSystemAPI system) {
    HyperspaceTerrainPlugin plugin = (HyperspaceTerrainPlugin) Misc.getHyperspaceTerrain().getPlugin();
    NebulaEditor editor = new NebulaEditor(plugin);
    float minRadius = plugin.getTileSize() * 2f;

    float radius = system.getMaxRadiusInHyperspace();
    editor.clearArc(system.getLocation().x, system.getLocation().y, 0, radius + minRadius * 0.5f, 0, 360f);
    editor.clearArc(system.getLocation().x, system.getLocation().y, 0, radius + minRadius, 0, 360f, 0.25f);
  }

}
