package net.sf.freecol.common.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import net.sf.freecol.FreeCol;
import net.sf.freecol.common.model.Map.Position;

import org.w3c.dom.Element;

/**
 * Represents a colony. A colony contains {@link Building}s and
 * {@link ColonyTile}s. The latter represents the tiles around the
 * <code>Colony</code> where working is possible.
 */
public final class Colony extends Settlement implements Location, Nameable {
    private static final Logger logger = Logger.getLogger(Colony.class.getName());

    public static final String COPYRIGHT = "Copyright (C) 2003-2007 The FreeCol Team";

    public static final String LICENSE = "http://www.gnu.org/licenses/gpl.html";

    public static final String REVISION = "$Revision$";

    public static final int BUILDING_UNIT_ADDITION = 1000;

    private static final int BELLS_PER_REBEL = 100;

    /** The name of the colony. */
    private String name;

    /**
     * Places a unit may work. Either a <code>Building</code> or a
     * <code>ColonyTile</code>.
     */
    private ArrayList<WorkLocation> workLocations = new ArrayList<WorkLocation>();

    private HashMap<Integer, Building> buildingMap = new HashMap<Integer, Building>();

    private int hammers;

    private int bells;

    /** The SoL membership this turn. */
    private int sonsOfLiberty;

    /** The SoL membership last turn. */
    private int oldSonsOfLiberty;

    /** The number of tories this turn. */
    private int tories;

    /** The number of tories last turn. */
    private int oldTories;

    /** The current production bonus. */
    private int productionBonus;

    /**
     * Identifies what this colony is currently building. This is the type of
     * the "Building" that is being built, or if
     * <code>currentlyBuilding >= BUILDING_UNIT_ADDITION</code> the type of
     * the <code>Unit</code> (+BUILDING_UNIT_ADDITION) that is currently
     * beeing build
     */
    private int currentlyBuilding;

    // Whether this colony is landlocked
    private boolean landLocked = true;

    // Will only be used on enemy colonies:
    private int unitCount = -1;

    // Temporary variable:
    private int lastVisited = -1;

    /**
     * High levels for warehouse warnings.
     */
    private int[] highLevel = new int[Goods.NUMBER_OF_TYPES];

    /**
     * Low levels for warehouse warnings.
     */
    private int[] lowLevel = new int[Goods.NUMBER_OF_TYPES];

    /**
     * Export levels for custom house.
     */
    private int[] exportLevel = new int[Goods.NUMBER_OF_TYPES];

    /**
     * Export settings for custom house.
     */
    private boolean[] exports = new boolean[Goods.NUMBER_OF_TYPES];


    /**
     * Creates a new <code>Colony</code>.
     * 
     * @param game The <code>Game</code> in which this object belongs.
     * @param owner The <code>Player</code> owning this <code>Colony</code>.
     * @param name The name of the new <code>Colony</code>.
     * @param tile The location of the <code>Colony</code>.
     */
    public Colony(Game game, Player owner, String name, Tile tile) {
        super(game, owner, tile);
        Iterator<Position> exploreIt = getGame().getMap().getCircleIterator(getTile().getPosition(), true,
                getLineOfSight());
        while (exploreIt.hasNext()) {
            Tile t = getGame().getMap().getTile(exploreIt.next());
            t.setExploredBy(owner, true);
        }
        owner.invalidateCanSeeTiles();
        goodsContainer = new GoodsContainer(game, this);
        initializeWarehouseSettings();
        this.name = name;
        hammers = 0;
        bells = 0;
        sonsOfLiberty = 0;
        oldSonsOfLiberty = 0;
        Map map = game.getMap();
        int ownerNation = owner.getNation();
        tile.setNationOwner(ownerNation);
        for (int direction = 0; direction < Map.NUMBER_OF_DIRECTIONS; direction++) {
            Tile t = map.getNeighbourOrNull(direction, tile);
            if (t.getNationOwner() == Player.NO_NATION) {
                t.setNationOwner(ownerNation);
                // t.setOwner(this);
            }
            addWorkLocation(new ColonyTile(game, this, t));
            if (t.getType() == Tile.OCEAN) {
                landLocked = false;
            }
        }
        addWorkLocation(new ColonyTile(game, this, tile));
        int numberOfTypes = FreeCol.getSpecification().numberOfBuildingTypes();
        for (int type = 0; type < numberOfTypes; type++) {
            BuildingType buildingType = FreeCol.getSpecification().buildingType(type);
            if (buildingType.level(0).hammersRequired > 0) {
                addWorkLocation(new Building(game, this, type, Building.NOT_BUILT));
            } else {
                addWorkLocation(new Building(game, this, type, Building.HOUSE));
            }
        }
        if (landLocked)
            currentlyBuilding = Building.WAREHOUSE;
        else
            currentlyBuilding = Building.DOCK;
    }

    /**
     * Initiates a new <code>Colony</code> from an XML representation.
     * 
     * @param game The <code>Game</code> this object belongs to.
     * @param in The input stream containing the XML.
     * @throws XMLStreamException if an error occured during parsing.
     */
    public Colony(Game game, XMLStreamReader in) throws XMLStreamException {
        super(game, in);
        readFromXML(in);
    }

    /**
     * Initiates a new <code>Colony</code> from an XML representation.
     * 
     * @param game The <code>Game</code> this object belongs to.
     * @param e An XML-element that will be used to initialize this object.
     */
    public Colony(Game game, Element e) {
        super(game, e);
        readFromXMLElement(e);
    }

    /**
     * Initiates a new <code>Colony</code> with the given ID. The object
     * should later be initialized by calling either
     * {@link #readFromXML(XMLStreamReader)} or
     * {@link #readFromXMLElement(Element)}.
     * 
     * @param game The <code>Game</code> in which this object belong.
     * @param id The unique identifier for this object.
     */
    public Colony(Game game, String id) {
        super(game, id);
    }

    private void addWorkLocation(WorkLocation workLocation) {
        workLocations.add(workLocation);
        if (workLocation instanceof Building) {
            Building building = (Building) workLocation;
            buildingMap.put(building.getType(), building);
        }
    }

    /**
     * Initializes warehouse/export settings.
     */
    private void initializeWarehouseSettings() {
        for (int goodsType = 0; goodsType < Goods.NUMBER_OF_TYPES; goodsType++) {
            exports[goodsType] = false;
            lowLevel[goodsType] = 10;
            highLevel[goodsType] = 90;
            exportLevel[goodsType] = 50;
        }
    }

    /**
     * Updates SoL and builds stockade if possible.
     */
    public void updatePopulation() {
        if (getUnitCount() >= 3 && getOwner().hasFather(FoundingFather.LA_SALLE)) {
            if (!getBuilding(Building.STOCKADE).isBuilt()) {
                getBuilding(Building.STOCKADE).setLevel(Building.HOUSE);
            }
        }
        getTile().updatePlayerExploredTiles();
        if (getUnitCount() > 0) {
            updateSoL();
        }
    }

    /**
     * Get the <code>HighLevel</code> value.
     * 
     * @return an <code>int[]</code> value
     */
    public int[] getHighLevel() {
        return highLevel;
    }

    /**
     * Set the <code>HighLevel</code> value.
     * 
     * @param newHighLevel The new HighLevel value.
     */
    public void setHighLevel(final int[] newHighLevel) {
        this.highLevel = newHighLevel;
    }

    /**
     * Get the <code>LowLevel</code> value.
     * 
     * @return an <code>int[]</code> value
     */
    public int[] getLowLevel() {
        return lowLevel;
    }

    /**
     * Set the <code>LowLevel</code> value.
     * 
     * @param newLowLevel The new LowLevel value.
     */
    public void setLowLevel(final int[] newLowLevel) {
        this.lowLevel = newLowLevel;
    }

    /**
     * Get the <code>ExportLevel</code> value.
     * 
     * @return an <code>int[]</code> value
     */
    public int[] getExportLevel() {
        return exportLevel;
    }

    /**
     * Set the <code>ExportLevel</code> value.
     * 
     * @param newExportLevel The new ExportLevel value.
     */
    public void setExportLevel(final int[] newExportLevel) {
        this.exportLevel = newExportLevel;
    }

    /**
     * Returns whether this colony is landlocked, or has access to the ocean.
     * 
     * @return <code>true</code> if there are no adjacent tiles to this
     *         <code>Colony</code>'s tile being ocean tiles.
     */
    public boolean isLandLocked() {
        return landLocked;
    }

    /**
     * Returns whether this colony has undead units.
     * 
     * @return whether this colony has undead units.
     */
    public boolean isUndead() {
        final Iterator<Unit> unitIterator = getUnitIterator();
        return unitIterator.hasNext() && unitIterator.next().isUndead();
    }

    /**
     * Sets the owner of this <code>Colony</code>, including all units
     * within, and change main tile nation ownership.
     * 
     * @param owner The <code>Player</code> that shall own this
     *            <code>Settlement</code>.
     * @see Settlement#getOwner
     */
    @Override
    public void setOwner(Player owner) {
        // TODO: Erik - this only works if called on the server!
        super.setOwner(owner);
        tile.setNationOwner(owner.getNation());
        for (Unit unit : getUnitList()) {
            unit.setOwner(owner);
            if (unit.getLocation() instanceof ColonyTile) {
                ((ColonyTile) unit.getLocation()).getWorkTile().setNationOwner(owner.getNation());
            }
        }
        for (Unit target : tile.getUnitList()) {
            target.setOwner(getOwner());
        }
        // Changing the owner might alter bonuses applied by founding fathers:
        updatePopulation();
    }

    /**
     * Sets the number of units inside the colony, used in enemy colonies
     * 
     * @param unitCount The units inside the colony
     * @see #getUnitCount
     */
    public void setUnitCount(int unitCount) {
        this.unitCount = unitCount;
    }

    /**
     * Damages all ship located on this <code>Colony</code>'s
     * <code>Tile</code>. That is: they are sent to the closest location for
     * repair.
     * 
     * @see Unit#shipDamaged
     */
    public void damageAllShips() {
        Iterator<Unit> iter = getTile().getUnitIterator();
        while (iter.hasNext()) {
            Unit u = iter.next();
            if (u.isNaval()) {
                u.shipDamaged();
            }
        }
    }

    /**
     * Returns the building for producing the given type of goods.
     * 
     * @param goodsType The type of goods.
     * @return The <code>Building</code> which produces the given type of
     *         goods, or <code>null</code> if such a building cannot be found.
     */
    public Building getBuildingForProducing(int goodsType) {
        Building b;
        switch (goodsType) {
        case Goods.MUSKETS:
            b = getBuilding(Building.ARMORY);
            break;
        case Goods.RUM:
            b = getBuilding(Building.DISTILLER);
            break;
        case Goods.CIGARS:
            b = getBuilding(Building.TOBACCONIST);
            break;
        case Goods.CLOTH:
            b = getBuilding(Building.WEAVER);
            break;
        case Goods.COATS:
            b = getBuilding(Building.FUR_TRADER);
            break;
        case Goods.TOOLS:
            b = getBuilding(Building.BLACKSMITH);
            break;
        case Goods.CROSSES:
            b = getBuilding(Building.CHURCH);
            break;
        case Goods.HAMMERS:
            b = getBuilding(Building.CARPENTER);
            break;
        case Goods.BELLS:
            b = getBuilding(Building.TOWN_HALL);
            break;
        default:
            b = null;
        }
        return (b != null && b.isBuilt()) ? b : null;
    }

    /**
     * Returns the colony's existing building for the given goods type.
     * 
     * @param goodsType The goods type.
     * @return The Building for the <code>goodsType</code>, or
     *         <code>null</code> if not exists or not fully built.
     * @see Goods
     */
    public Building getBuildingForConsuming(int goodsType) {
        Building b;
        switch (goodsType) {
        case Goods.TOOLS:
            b = getBuilding(Building.ARMORY);
            break;
        case Goods.LUMBER:
            b = getBuilding(Building.CARPENTER);
            break;
        case Goods.SUGAR:
            b = getBuilding(Building.DISTILLER);
            break;
        case Goods.TOBACCO:
            b = getBuilding(Building.TOBACCONIST);
            break;
        case Goods.COTTON:
            b = getBuilding(Building.WEAVER);
            break;
        case Goods.FURS:
            b = getBuilding(Building.FUR_TRADER);
            break;
        case Goods.ORE:
            b = getBuilding(Building.BLACKSMITH);
            break;
        default:
            b = null;
        }
        if (b != null && b.isBuilt()) {
            return b;
        }
        return null;
    }

    /**
     * Gets an <code>Iterator</code> of every location in this
     * <code>Colony</code> where a {@link Unit} can work.
     * 
     * @return The <code>Iterator</code>.
     * @see WorkLocation
     */
    public Iterator<WorkLocation> getWorkLocationIterator() {
        return workLocations.iterator();
    }

    /**
     * Gets an <code>Iterator</code> of every {@link Building} in this
     * <code>Colony</code>.
     * 
     * @return The <code>Iterator</code>.
     * @see Building
     */
    public Iterator<Building> getBuildingIterator() {
        ArrayList<Building> b = new ArrayList<Building>();
        for (WorkLocation location : workLocations) {
            if (location instanceof Building) {
                b.add((Building) location);
            }
        }
        return b.iterator();
    }

    /**
     * Gets an <code>Iterator</code> of every {@link ColonyTile} in this
     * <code>Colony</code>.
     * 
     * @return The <code>Iterator</code>.
     * @see ColonyTile
     */
    public Iterator<ColonyTile> getColonyTileIterator() {
        ArrayList<ColonyTile> b = new ArrayList<ColonyTile>();
        for (WorkLocation location : workLocations) {
            if (location instanceof ColonyTile) {
                b.add((ColonyTile) location);
            }
        }
        return b.iterator();
    }

    /**
     * Gets a <code>Building</code> of the specified type.
     * 
     * @param type The type of building to get.
     * @return The <code>Building</code>.
     */
    public Building getBuilding(int type) {
        return buildingMap.get(new Integer(type));
        /*
         * Iterator<Building> buildingIterator = getBuildingIterator(); while
         * (buildingIterator.hasNext()) { Building building =
         * buildingIterator.next(); if (building.getType() == type) { return
         * building; } } return null;
         */
    }

    /**
     * Gets the specified <code>ColonyTile</code>.
     * 
     * @param x The x-coordinate of the <code>Tile</code>.
     * @param y The y-coordinate of the <code>Tile</code>.
     * @return The <code>ColonyTile</code> for the <code>Tile</code>
     *         returned by {@link #getTile(int, int)}.
     */
    public ColonyTile getColonyTile(int x, int y) {
        Tile t = getTile(x, y);
        Iterator<ColonyTile> i = getColonyTileIterator();
        while (i.hasNext()) {
            ColonyTile c = i.next();
            if (c.getWorkTile() == t) {
                return c;
            }
        }
        return null;
    }

    /**
     * Returns the <code>ColonyTile</code> matching the given
     * <code>Tile</code>.
     * 
     * @param t The <code>Tile</code> to get the <code>ColonyTile</code>
     *            for.
     * @return The <code>ColonyTile</code>
     */
    public ColonyTile getColonyTile(Tile t) {
        Iterator<ColonyTile> i = getColonyTileIterator();
        while (i.hasNext()) {
            ColonyTile c = i.next();
            if (c.getWorkTile() == t) {
                return c;
            }
        }
        return null;
    }

    /**
     * Gets a vacant <code>WorkLocation</code> for the given <code>Unit</code>.
     * 
     * @param locatable The <code>Unit</code>
     * @return A vacant <code>WorkLocation</code> for the given
     *         <code>Unit</code> or <code>null</code> if there is no such
     *         location.
     */
    public WorkLocation getVacantWorkLocationFor(Unit locatable) {
        WorkLocation w = getVacantColonyTileFor(locatable, Goods.FOOD);
        if (w != null && w.canAdd(locatable) && getVacantColonyTileProductionFor(locatable, Goods.FOOD) > 0) {
            return w;
        }
        Iterator<Building> i = getBuildingIterator();
        while (i.hasNext()) {
            w = i.next();
            if (w.canAdd(locatable)) {
                return w;
            }
        }
        Iterator<WorkLocation> it = getWorkLocationIterator();
        while (it.hasNext()) {
            w = it.next();
            if (w.canAdd(locatable)) {
                return w;
            }
        }
        return null;
    }

    /**
     * Adds a <code>Locatable</code> to this Location.
     * 
     * @param locatable The <code>Locatable</code> to add to this Location.
     */
    @Override
    public void add(Locatable locatable) {
        if (locatable instanceof Unit) {
            if (((Unit) locatable).isColonist()) {
                WorkLocation w = getVacantWorkLocationFor((Unit) locatable);
                if (w != null) {
                    locatable.setLocation(w);
                } else {
                    logger.warning("Could not find a 'WorkLocation' for " + locatable + " in " + this);
                }
            } else {
                locatable.setLocation(getTile());
            }
            updatePopulation();
        } else if (locatable instanceof Goods) {
            goodsContainer.addGoods((Goods) locatable);
        } else {
            logger.warning("Tried to add an unrecognized 'Locatable' to a 'Colony'.");
        }
    }

    /**
     * Removes a <code>Locatable</code> from this Location.
     * 
     * @param locatable The <code>Locatable</code> to remove from this
     *            Location.
     */
    @Override
    public void remove(Locatable locatable) {
        if (locatable instanceof Unit) {
            Iterator<WorkLocation> i = getWorkLocationIterator();
            while (i.hasNext()) {
                WorkLocation w = i.next();
                if (w.contains(locatable)) {
                    w.remove(locatable);
                    updatePopulation();
                    return;
                }
            }
        } else if (locatable instanceof Goods) {
            goodsContainer.removeGoods((Goods) locatable);
        } else {
            logger.warning("Tried to remove an unrecognized 'Locatable' from a 'Colony'.");
        }
    }

    /**
     * Gets the amount of Units at this Location. These units are located in a
     * {@link WorkLocation} in this <code>Colony</code>.
     * 
     * @return The amount of Units at this Location.
     */
    @Override
    public int getUnitCount() {
        int count = 0;
        if (unitCount != -1) {
            return unitCount;
        }
        Iterator<WorkLocation> i = getWorkLocationIterator();
        while (i.hasNext()) {
            WorkLocation w = i.next();
            count += w.getUnitCount();
        }
        return count;
    }

    /**
     * Gives the food needed to keep all current colonists alive in this colony.
     * 
     * @return The amount of food eaten in this colony each this turn.
     */
    public int getFoodConsumption() {
        return 2 * getUnitCount();
    }

    /**
     * Gets the amount of one type of Goods at this Colony.
     * 
     * @param type The type of goods to look for.
     * @return The amount of this type of Goods at this Location.
     */
    public int getGoodsCount(int type) {
        return goodsContainer.getGoodsCount(type);
    }

    /**
     * Removes a specified amount of a type of Goods from this containter.
     * 
     * @param type The type of Goods to remove from this container.
     * @param amount The amount of Goods to remove from this container.
     */
    public void removeGoods(int type, int amount) {
        goodsContainer.removeGoods(type, amount);
    }

    public void removeGoods(Goods goods) {
        goodsContainer.removeGoods(goods.getType(), goods.getAmount());
    }

    public void addGoods(int type, int amount) {
        goodsContainer.addGoods(type, amount);
    }

    public List<Unit> getUnitList() {
        ArrayList<Unit> units = new ArrayList<Unit>();
        for (WorkLocation wl : workLocations) {
            for (Unit unit : wl.getUnitList()) {
                units.add(unit);
            }
        }
        return units;
    }
    
    /**
     * Returns a list of all units in this colony of the given type.
     * 
     * @param type The type of the units to include in the list. For instance
     *            Unit.EXPERT_FARMER.
     * @return A list of all the units of the given type in this colony.
     */
    public List<Unit> getUnitList(int type) {
        ArrayList<Unit> units = new ArrayList<Unit>();
        for (WorkLocation wl : workLocations) {
            for (Unit unit : wl.getUnitList()) {
                if (unit.getType() == type) {
                    units.add(unit);
                }
            }
        }
        return units;
    }

    public Iterator<Unit> getUnitIterator() {
        return getUnitList().iterator();
    }

    /**
     * Returns true if the custom house should export this type of goods.
     * 
     * @param type The type of goods.
     * @return True if the custom house should export this type of goods.
     */
    public boolean getExports(int type) {
        return exports[type];
    }

    /**
     * Returns true if the custom house should export these goods.
     * 
     * @param goods The goods.
     * @return True if the custom house should export these goods.
     */
    public boolean getExports(Goods goods) {
        return exports[goods.getType()];
    }

    /**
     * Sets whether the custom house should export these goods.
     * 
     * @param type the type of goods.
     * @param value a <code>boolean</code> value
     */
    public void setExports(int type, boolean value) {
        exports[type] = value;
    }

    /**
     * Sets whether the custom house should export these goods.
     * 
     * @param goods the goods.
     * @param value a <code>boolean</code> value
     */
    public void setExports(Goods goods, boolean value) {
        setExports(goods.getType(), value);
    }

    @Override
    public boolean contains(Locatable locatable) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean canAdd(Locatable locatable) {
        // throw new UnsupportedOperationException();
        if (locatable instanceof Unit && ((Unit) locatable).getOwner() == getOwner()) {
            return true;
        }
        return false;
    }

    /**
     * Returns true if this colony has a schoolhouse and the unit is a
     * skilled unit with a skill level not exceeding the level of the
     * schoolhouse. The number of units already in the schoolhouse and
     * the availability of pupils are not taken into account. @see
     * Building#canAddAsTeacher
     * 
     * @param unit The unit to add as a teacher.
     * @return <code>true</code> if this unit could be added.
    */

    public boolean canTrain(Unit unit) {
        return getBuilding(Building.SCHOOLHOUSE).canAddAsTeacher(unit);
    }

    public boolean canTrain(int unitType) {
        return getBuilding(Building.SCHOOLHOUSE).canAddAsTeacher(unitType);
    }

    /**
     * Gets the <code>Unit</code> that is currently defending this
     * <code>Colony</code>.
     * <p>
     * Note! Several callers fail to handle null as a return value. Return an
     * arbitrary unarmed land unit unless Paul Revere is present as founding
     * father, in which case the unit can be armed as well. Also note that the
     * colony would typically be defended by a unit outside it.
     * 
     * @param attacker The target that would be attacking this colony.
     * @return The <code>Unit</code> that has been choosen to defend this
     *         colony.
     * @see Tile#getDefendingUnit(Unit)
     */
    @Override
    public Unit getDefendingUnit(Unit attacker) {
        return getDefendingUnit();
    }

    /**
     * Gets the <code>Unit</code> that is currently defending this
     * <code>Colony</code>. Note that the colony will normally be defended by
     * units outside of the colony (@see Tile#getDefendingUnit).
     * <p>
     * Note! Several callers fail to handle null as a return value. Return an
     * arbitrary unarmed land unit.
     * 
     * @return The <code>Unit</code> that has been choosen to defend this
     *         colony.
     */
    private Unit getDefendingUnit() {
        // Sanity check - are there units available?
        List<Unit> unitList = getUnitList();
        if (unitList.isEmpty()) {
            throw new IllegalStateException("Colony " + name + " contains no units!");
        }
        // Get the first unit
        // TODO should return an experienced soldier rather if available.
        return unitList.get(0);
    }

    /**
     * Adds to the hammer count of the colony.
     * 
     * @param amount The number of hammers to add.
     */
    public void addHammers(int amount) {
        if (currentlyBuilding == -1) {
            addModelMessage(this, "model.colony.cannotBuild", new String[][] { { "%colony%", getName() } },
                    ModelMessage.WARNING, this);
            return;
        }
        // Building only:
        if (currentlyBuilding < BUILDING_UNIT_ADDITION) {
            if (getBuilding(currentlyBuilding).getNextPop() > getUnitCount()) {
                addModelMessage(this, "model.colony.buildNeedPop", new String[][] { { "%colony%", getName() },
                        { "%building%", getBuilding(currentlyBuilding).getNextName() } }, ModelMessage.WARNING);
                return;
            }
            if (getBuilding(currentlyBuilding).getNextHammers() == -1) {
                addModelMessage(this, "model.colony.alreadyBuilt", new String[][] { { "%colony%", getName() },
                        { "%building%", getBuilding(currentlyBuilding).getName() } }, ModelMessage.WARNING);
            }
        }
        hammers += amount;
    }

    /**
     * Returns an <code>Iterator</code> of every unit type this colony may
     * build.
     * 
     * @return An <code>Iterator</code> on <code>Integer</code> -objects
     *         where the values are the unit type values.
     */
    public Iterator<Integer> getBuildableUnitIterator() {
        ArrayList<Integer> buildableUnits = new ArrayList<Integer>();
        buildableUnits.add(Unit.WAGON_TRAIN);
        if (getBuilding(Building.ARMORY).isBuilt()) {
            buildableUnits.add(Unit.ARTILLERY);
        }
        if (getBuilding(Building.DOCK).getLevel() >= Building.FACTORY) {
            buildableUnits.add(Unit.CARAVEL);
            buildableUnits.add(Unit.MERCHANTMAN);
            buildableUnits.add(Unit.GALLEON);
            buildableUnits.add(Unit.PRIVATEER);
            buildableUnits.add(Unit.FRIGATE);
            if (owner.getRebellionState() >= Player.REBELLION_POST_WAR) {
                buildableUnits.add(Unit.MAN_O_WAR);
            }
        }
        return buildableUnits.iterator();
    }

    /**
     * Checks if this colony may build the given unit type.
     * 
     * @param unitType The unit type to test against.
     * @return The result.
     */
    public boolean canBuildUnit(int unitType) {
        Iterator<Integer> buildableUnitIterator = getBuildableUnitIterator();
        while (buildableUnitIterator.hasNext()) {
            if (unitType == buildableUnitIterator.next()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the hammer count of the colony.
     * 
     * @return The current hammer count of the colony.
     */
    public int getHammers() {
        return hammers;
    }

    /**
     * Returns the type of building currently being built.
     * 
     * @return The type of building currently being built.
     */
    public int getCurrentlyBuilding() {
        return currentlyBuilding;
    }

    /**
     * Sets the type of building to be built.
     * 
     * @param type The type of building to be built.
     */
    public void setCurrentlyBuilding(int type) {
        currentlyBuilding = type;
    }

    /**
     * Sets the type of building to None, so no building is done.
     */
    public void stopBuilding() {
        setCurrentlyBuilding(Building.NONE);
    }

    /**
     * Adds to the bell count of the colony.
     * 
     * @param amount The number of bells to add.
     */
    public void addBells(int amount) {
        if (getMembers() <= getUnitCount() + 1) {
            bells += amount;
        }
        if (bells <= 0) {
            bells = 0;
        }
    }

    /**
     * Adds to the bell count of the colony.
     * 
     * @param amount The percentage of SoL to add.
     */
    public void addSoL(int amount) {
        /*
         * The number of bells to be generated in order to get the appropriate
         * SoL is determined by the formula: int membership = ... in
         * "updateSoL()":
         */
        bells += (bells * amount) / 100;
    }

    /**
     * Returns the bell count of the colony.
     * 
     * @return The current bell count of the colony.
     */
    public int getBells() {
        return bells;
    }

    /**
     * Returns the current SoL membership of the colony.
     * 
     * @return The current SoL membership of the colony.
     */
    public int getSoL() {
        return sonsOfLiberty;
    }

    /**
     * Calculates the current SoL membership of the colony based on the number
     * of bells and colonists.
     */
    private void updateSoL() {
        int units = getUnitCount();
        if (units <= 0) {
            return;
        }
        // Update "addSol(int)" and "getMembers()" if this formula gets changed:
        int membership = (bells * 100) / (BELLS_PER_REBEL * getUnitCount());
        if (membership < 0)
            membership = 0;
        if (membership > 100)
            membership = 100;
        sonsOfLiberty = membership;
        tories = (units - getMembers());
    }

    /**
     * Return the number of sons of liberty
     */
    public int getMembers() {
        return Math.min(bells / BELLS_PER_REBEL, getUnitCount());
    }

    /**
     * Returns the Tory membership of the colony.
     * 
     * @return The current Tory membership of the colony.
     */
    public int getTory() {
        return 100 - getSoL();
    }

    /**
     * Returns the production bonus, if any, of the colony.
     * 
     * @return The current production bonus of the colony.
     */
    public int getProductionBonus() {
        return productionBonus;
    }

    /**
     * Gets a string representation of the Colony. Currently this method just
     * returns the name of the <code>Colony</code>, but that may change
     * later.
     * 
     * @return The name of the colony.
     * @see #getName
     */
    @Override
    public String toString() {
        return name;
    }

    /**
     * Gets the name of this <code>Colony</code>.
     * 
     * @return The name as a <code>String</code>.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name of this <code>Colony</code>.
     * 
     * @param newName The new name of this Colony.
     */
    public void setName(String newName) {
        this.name = newName;
    }

    /**
     * Returns the name of this location.
     * 
     * @return The name of this location.
     */
    public String getLocationName() {
        return name;
    }

    /**
     * Gets the production of food.
     * 
     * @return The same as <code>getProductionOf(Goods.FOOD)</code>.
     */
    public int getFoodProduction() {
        return getProductionOf(Goods.FOOD);
    }

    /**
     * Returns the production of the given type of goods.
     * 
     * @param goodsType The type of goods to get the production for.
     * @return The production of the given type of goods the current turn by all
     *         of the <code>Colony</code>'s {@link Building buildings} and
     *         {@link ColonyTile tiles}.
     */
    public int getProductionOf(int goodsType) {
        int amount = 0;
        if (goodsType == Goods.HORSES) {
            return getHorseProduction();
        }
        Iterator<WorkLocation> workLocationIterator = getWorkLocationIterator();
        while (workLocationIterator.hasNext()) {
            amount += workLocationIterator.next().getProductionOf(goodsType);
        }
        return amount;
    }

    /**
     * Returns a vacant <code>ColonyTile</code> where the given
     * <code>unit</code> produces the maximum output of the given
     * <code>goodsType</code>.
     * 
     * @param unit The <code>Unit</code> to find a vacant
     *            <code>ColonyTile</code> for.
     * @param goodsType The type of goods that should be produced.
     * @return The <code>ColonyTile</code> giving the highest production of
     *         the given goods for the given unit or <code>null</code> if
     *         there is no available <code>ColonyTile</code> for producing
     *         that goods.
     */
    public ColonyTile getVacantColonyTileFor(Unit unit, int goodsType) {
        ColonyTile bestPick = null;
        int highestProduction = -2;
        Iterator<ColonyTile> colonyTileIterator = getColonyTileIterator();
        while (colonyTileIterator.hasNext()) {
            ColonyTile colonyTile = colonyTileIterator.next();
            if (colonyTile.canAdd(unit)) {
                Tile workTile = colonyTile.getWorkTile();
                /*
                 * canAdd ensures workTile it's empty or unit it's working in it
                 * so unit can work in it if it's owned by none, by europeans or
                 * unit's owner has the founding father Peter Minuit
                 */
                boolean ourLand = workTile.getNationOwner() == Player.NO_NATION
                        || Player.isEuropean(workTile.getNationOwner())
                        || unit.getOwner().hasFather(FoundingFather.PETER_MINUIT);
                if (ourLand && unit.getFarmedPotential(goodsType, colonyTile.getWorkTile()) > highestProduction) {
                    highestProduction = unit.getFarmedPotential(goodsType, colonyTile.getWorkTile());
                    bestPick = colonyTile;
                }
            }
        }
        return bestPick;
    }

    /**
     * Returns the production of a vacant <code>ColonyTile</code> where the
     * given <code>unit</code> produces the maximum output of the given
     * <code>goodsType</code>.
     * 
     * @param unit The <code>Unit</code> to find the highest possible
     *            <code>ColonyTile</code>-production for.
     * @param goodsType The type of goods that should be produced.
     * @return The highest possible production on a vacant
     *         <code>ColonyTile</code> for the given goods and the given unit.
     */
    public int getVacantColonyTileProductionFor(Unit unit, int goodsType) {
        ColonyTile bestPick = getVacantColonyTileFor(unit, goodsType);
        return bestPick != null ? unit.getFarmedPotential(goodsType, bestPick.getWorkTile()) : 0;
    }

    /**
     * Returns the horse production (given that enough food is being produced
     * and a sufficient storage capacity).
     * 
     * @return The number of producable horses.
     */
    public int getPotentialHorseProduction() {
        if (getGoodsCount(Goods.HORSES) < 2) {
            return 0;
        }
        int maxAmount = getGoodsCount(Goods.HORSES) / 10;
        if (!getBuilding(Building.STABLES).isBuilt()) {
            maxAmount /= 2;
        }
        return Math.max(1, maxAmount);
    }

    /**
     * Gets the production of horses in this <code>Colony</code>.
     * 
     * @return The total production of horses in this <code>Colony</code>.
     */
    public int getHorseProduction() {
        int surplus = getFoodProduction() - getFoodConsumption();
        int maxSpace = getWarehouseCapacity() - getGoodsCount(Goods.HORSES);
        int potential = Math.min(getPotentialHorseProduction(), maxSpace);
        return Math.max(Math.min(surplus / 2, potential), 0); // store the half of surplus
    }

    /**
     * Returns how much of a Good will be produced by this colony this turn
     * 
     * @param goodsType The goods' type.
     * @return The amount of the given goods will be produced for next turn.
     */
    public int getProductionNextTurn(int goodsType) {
        int count = 0;
        Building building = getBuildingForProducing(goodsType);
        if (building == null) {
            count = getProductionOf(goodsType);
        } else {
            count = building.getProductionNextTurn();
        }
        return count;
    }

    /**
     * Returns how much of a Good will be produced by this colony this turn,
     * taking into account how much is consumed - by workers, horses, etc.
     * 
     * @param goodsType The goods' type.
     * @return The amount of the given goods currently unallocated for next
     *         turn.
     */
    public int getProductionNetOf(int goodsType) {
        int count = getProductionNextTurn(goodsType);
        int used = 0;
        switch (goodsType) {
        case Goods.FOOD:
            used = getFoodConsumption();
            used += getHorseProduction();
            break;
        default:
            Building bldg = getBuildingForConsuming(goodsType);
            if (bldg != null) {
                used = bldg.getGoodsInputNextTurn();
            }
        // TODO FIXME This should also take into account tools needed for a
        // current building project
        }
        count -= used;
        return count;
    }

    private void checkBuildingComplete() {
        // In order to avoid duplicate messages:
        if (lastVisited == getGame().getTurn().getNumber()) {
            return;
        }
        lastVisited = getGame().getTurn().getNumber();
        if (getCurrentlyBuilding() >= Colony.BUILDING_UNIT_ADDITION) {
            int unitType = getCurrentlyBuilding() - BUILDING_UNIT_ADDITION;
            if (canBuildUnit(unitType) && Unit.getNextHammers(unitType) <= getHammers()
                    && Unit.getNextHammers(unitType) != -1) {
                if (Unit.getNextTools(unitType) <= getGoodsCount(Goods.TOOLS)) {
                    Unit unit = getGame().getModelController().createUnit(getID() + "buildUnit", getTile(), getOwner(),
                            unitType);
                    hammers = 0;
                    removeGoods(Goods.TOOLS, Unit.getNextTools(unit.getType()));
                    addModelMessage(this, "model.colony.unitReady", new String[][] { { "%colony%", getName() },
                            { "%unit%", unit.getName() } }, ModelMessage.UNIT_ADDED, unit);
                } else {
                    addModelMessage(this, "model.colony.itemNeedTools", new String[][] { { "%colony%", getName() },
                            { "%item%", Unit.getName(unitType) } }, ModelMessage.MISSING_GOODS, new Goods(Goods.TOOLS));
                }
            }
        } else if (currentlyBuilding != -1) {
            int hammersRequired = getBuilding(currentlyBuilding).getNextHammers();
            int toolsRequired = getBuilding(currentlyBuilding).getNextTools();
            if ((hammers >= hammersRequired) && (hammersRequired != -1)) {
                hammers = hammersRequired;
                if (getGoodsCount(Goods.TOOLS) >= toolsRequired) {
                    if (!getBuilding(currentlyBuilding).canBuildNext()) {
                        throw new IllegalStateException("Cannot build the selected building.");
                    }
                    if (toolsRequired > 0) {
                        removeGoods(Goods.TOOLS, toolsRequired);
                    }
                    hammers = 0;
                    getBuilding(currentlyBuilding).setLevel(getBuilding(currentlyBuilding).getLevel() + 1);
                    addModelMessage(this, "model.colony.buildingReady", new String[][] { { "%colony%", getName() },
                            { "%building%", getBuilding(currentlyBuilding).getName() } },
                            ModelMessage.BUILDING_COMPLETED, this);
                    if (!getBuilding(currentlyBuilding).canBuildNext()) {
                        stopBuilding();
                    }
                    getTile().updatePlayerExploredTiles();
                } else {
                    addModelMessage(this, "model.colony.itemNeedTools", new String[][] { { "%colony%", getName() },
                            { "%item%", getBuilding(currentlyBuilding).getNextName() } }, ModelMessage.MISSING_GOODS,
                            new Goods(Goods.TOOLS));
                }
            }
        }
    }

    /**
     * Returns the price for the remaining hammers and tools for the
     * {@link Building} that is currently being built.
     * 
     * @return The price.
     * @see #payForBuilding
     */
    public int getPriceForBuilding() {
        // Any changes in this method should also be reflected in
        // "payForBuilding()"
        int hammersRemaining = 0;
        int toolsRemaining = 0;
        if (getCurrentlyBuilding() >= Colony.BUILDING_UNIT_ADDITION) {
            int unitType = getCurrentlyBuilding() - BUILDING_UNIT_ADDITION;
            hammersRemaining = Math.max(Unit.getNextHammers(unitType) - hammers, 0);
            toolsRemaining = Math.max(Unit.getNextTools(unitType) - getGoodsCount(Goods.TOOLS), 0);
        } else if (getCurrentlyBuilding() != -1) {
            hammersRemaining = Math.max(getBuilding(currentlyBuilding).getNextHammers() - hammers, 0);
            toolsRemaining = Math.max(getBuilding(currentlyBuilding).getNextTools() - getGoodsCount(Goods.TOOLS), 0);
        }
        int price = hammersRemaining * getGameOptions().getInteger(GameOptions.HAMMER_PRICE)
                + (getOwner().getMarket().getBidPrice(Goods.TOOLS, toolsRemaining) * 110) / 100;
        return price;
    }

    /**
     * Buys the remaining hammers and tools for the {@link Building} that is
     * currently being built.
     * 
     * @exception IllegalStateException If the owner of this <code>Colony</code>
     *                has an insufficient amount of gold.
     * @see #getPriceForBuilding
     */
    public void payForBuilding() {
        // Any changes in this method should also be reflected in
        // "getPriceForBuilding()"
        if (getPriceForBuilding() > getOwner().getGold()) {
            throw new IllegalStateException("Not enough gold.");
        }
        int hammersRemaining = 0;
        int toolsRemaining = 0;
        if (getCurrentlyBuilding() >= Colony.BUILDING_UNIT_ADDITION) {
            int unitType = getCurrentlyBuilding() - BUILDING_UNIT_ADDITION;
            hammersRemaining = Math.max(Unit.getNextHammers(unitType) - hammers, 0);
            toolsRemaining = Math.max(Unit.getNextTools(unitType) - getGoodsCount(Goods.TOOLS), 0);
            hammers = Math.max(Unit.getNextHammers(unitType), hammers);
        } else if (getCurrentlyBuilding() != -1) {
            hammersRemaining = Math.max(getBuilding(currentlyBuilding).getNextHammers() - hammers, 0);
            toolsRemaining = Math.max(getBuilding(currentlyBuilding).getNextTools() - getGoodsCount(Goods.TOOLS), 0);
            hammers = Math.max(getBuilding(currentlyBuilding).getNextHammers(), hammers);
        }
        if (hammersRemaining > 0) {
            getOwner().modifyGold(-hammersRemaining * getGameOptions().getInteger(GameOptions.HAMMER_PRICE));
        }
        if (toolsRemaining > 0) {
            getOwner().getMarket().buy(Goods.TOOLS, toolsRemaining, getOwner());
            getGoodsContainer().addGoods(Goods.TOOLS, toolsRemaining);
        }
    }

    /**
     * Bombard a unit with the given outcome.
     * 
     * @param defender The <code>Unit</code> defending against bombardment.
     * @param result The result of the bombardment.
     */
    public void bombard(Unit defender, int result) {
        if (defender == null) {
            throw new NullPointerException();
        }
        Building building = getBuilding(Building.STOCKADE);
        switch (result) {
        case Unit.ATTACK_EVADES:
            // send message to both parties
            addModelMessage(this, "model.unit.shipEvadedBombardment",
                            new String[][] {
                                { "%colony%", getName() },
                                { "%building%", building.getName() },
                                { "%unit%", defender.getName() },
                                { "%nation%", defender.getOwner().getNationAsString() } }, 
                            ModelMessage.DEFAULT, this);
            addModelMessage(defender, "model.unit.shipEvadedBombardment",
                            new String[][] { { "%colony%", getName() },
                                             { "%building%", building.getName() },
                                             { "%unit%", defender.getName() },
                                             { "%nation%", defender.getOwner().getNationAsString() } }, 
                            ModelMessage.DEFAULT, this);
            break;
        case Unit.ATTACK_WIN:
            defender.shipDamaged(this, building);
            addModelMessage(this, "model.unit.enemyShipDamagedByBombardment",
                            new String[][] {
                                { "%colony%", getName() },
                                { "%building%", building.getName() },
                                { "%unit%", defender.getName() },
                                { "%nation%", defender.getOwner().getNationAsString() } }, ModelMessage.UNIT_DEMOTED);
            break;
        case Unit.ATTACK_GREAT_WIN:
            defender.shipSunk(this, building);
            addModelMessage(this, "model.unit.shipSunkByBombardment",
                            new String[][] {
                                { "%colony%", getName() },
                                { "%building%", building.getName() },
                                { "%unit%", defender.getName() },
                                { "%nation%", defender.getOwner().getNationAsString() } },
                            ModelMessage.UNIT_DEMOTED);
            break;
        default:
            logger.warning("Illegal result of bombardment!");
            throw new IllegalArgumentException("Illegal result of bombardment!");
        }
    }

    /**
     * Returns a random unit from this colony. At this moment, this method
     * always returns the first unit in the colony.
     * 
     * @return A random unit from this <code>Colony</code>. This
     *         <code>Unit</code> will either be working in a {@link Building}
     *         or a {@link ColonyTile}.
     */
    public Unit getRandomUnit() {
        return getFirstUnit();
        // return getUnitIterator().hasNext() ? getUnitIterator().next() : null;
    }

    private Unit getFirstUnit() {
        Iterator<WorkLocation> wli = getWorkLocationIterator();
        while (wli.hasNext()) {
            WorkLocation wl = wli.next();
            Iterator<Unit> unitIterator = wl.getUnitIterator();
            while (unitIterator.hasNext()) {
                Unit o = unitIterator.next();
                if (o != null) {
                    return o;
                }
            }
        }
        return null;
    }


    // Repair any damaged ships:
    /**
     * TODO: move this to the drydock/shipyard, and give Europe a
     * shipyard, too.
     */
    private void repairShips() {
        for (Unit unit : getTile().getUnitList()) {
            if (unit.isNaval() && unit.isUnderRepair()) {
                unit.setHitpoints(unit.getHitpoints() + 1);
                if (unit.getHitpoints() == Unit.getInitialHitpoints(unit.getType())) {
                    addModelMessage(this, "model.unit.shipRepaired",
                            new String[][] {
                                { "%unit%", unit.getName() },
                                { "%repairLocation%", getLocationName() } },
                                ModelMessage.DEFAULT, this);
                }
            }
        }
    }

    // Save state of warehouse
    private void saveWarehouseState() {
        logger.finest("Saving state of warehouse in " + getName());
        getGoodsContainer().saveState();
    }


    // Update all colony tiles
    private void addColonyTileProduction() {
        Iterator<ColonyTile> tileIterator = getColonyTileIterator();
        while (tileIterator.hasNext()) {
            ColonyTile tile = tileIterator.next();
            logger.finest("Calling newTurn for colony tile " + tile.toString());
            tile.newTurn();
        }
    }


    // Eat food:
    private void updateFood() {
        int eat = getFoodConsumption();
        int food = getGoodsCount(Goods.FOOD);

        if (eat > food) {
            // Kill a colonist:
            getRandomUnit().dispose();
            removeGoods(Goods.FOOD, food);
            addModelMessage(this, "model.colony.colonistStarved", new String[][] { { "%colony%", getName() } },
                    ModelMessage.UNIT_LOST);
        } else {
            removeGoods(Goods.FOOD, eat);

            if (eat > getFoodProduction() && (food - eat) / (eat - getFoodProduction()) <= 3) {
                addModelMessage(this, "model.colony.famineFeared", new String[][] { { "%colony%", getName() },
                        { "%number%", Integer.toString((food - eat) / (eat - getFoodProduction())) } },
                        ModelMessage.WARNING);
            }
        }
    }


    // Breed horses:
    private void updateHorses() {
        int horseProduction = getHorseProduction();
        if (horseProduction != 0) {
            removeGoods(Goods.FOOD, horseProduction);
            addGoods(Goods.HORSES, horseProduction);
        }
    }

    // Create a new colonist if there is enough food:
    private void checkForNewColonist() {
        if (getGoodsCount(Goods.FOOD) >= 200) {
            Unit u = getGame().getModelController().createUnit(getID() + "newTurn200food", getTile(), getOwner(),
                                                               Unit.FREE_COLONIST);
            removeGoods(Goods.FOOD, 200);
            addModelMessage(this, "model.colony.newColonist", new String[][] { { "%colony%", getName() } },
                            ModelMessage.UNIT_ADDED, u);
            logger.info("New colonist created in " + getName() + " with ID=" + u.getID());
        }
    }


    // Update carpenter and blacksmith
    private void addHammersAndTools() {
        Building building = getBuilding(Building.CARPENTER);
        logger.finest("Calling newTurn for building " + building.getName());
        building.newTurn();
        building = getBuilding(Building.BLACKSMITH);
        logger.finest("Calling newTurn for building " + building.getName());
        building.newTurn();
    }



    // Update all buildings except carpenter and blacksmith
    private void addBuildingProduction() {
        Iterator<Building> buildingIterator = getBuildingIterator();
        while (buildingIterator.hasNext()) {
            Building building = buildingIterator.next();
            switch (building.getType()) {
            case Building.CARPENTER:
            case Building.BLACKSMITH:
                continue;
            default:
                logger.finest("Calling newTurn for building " + building.getName());
                building.newTurn();
            }
        }
    }


    // Export goods if custom house is built
    private void exportGoods() {
        if (getBuilding(Building.CUSTOM_HOUSE).isBuilt()) {
            List<Goods> exportGoods = getCompactGoods();
            for (Goods goods : exportGoods) {
                if (getExports(goods) && (owner.canTrade(goods, Market.CUSTOM_HOUSE))) {
                    int type = goods.getType();
                    int amount = goods.getAmount() - getExportLevel()[type];
                    if (amount > 0) {
                        removeGoods(type, amount);
                        getOwner().getMarket().sell(type, amount, owner, Market.CUSTOM_HOUSE);
                    }
                }
            }
        }
    }


    // Warn about levels that will be exceeded next turn
    private void createWarehouseCapacityWarning() {
        for (int goodsType = 1; goodsType < Goods.NUMBER_OF_TYPES; goodsType++) {
            if (getExports(goodsType)  && (owner.canTrade(goodsType, Market.CUSTOM_HOUSE))) {
                // capacity will never be exceeded
                continue;
            } else if (goodsContainer.getGoodsCount(goodsType) < getWarehouseCapacity()) {
                int waste = (goodsContainer.getGoodsCount(goodsType) + getProductionNetOf(goodsType) -
                             getWarehouseCapacity());
                if (waste > 0) {
                    addModelMessage(this, "model.building.warehouseSoonFull",
                                    new String [][] {{"%goods%", Goods.getName(goodsType)},
                                                     {"%colony%", getName()},
                                                     {"%amount%", String.valueOf(waste)}},
                                    ModelMessage.WAREHOUSE_CAPACITY,
                                    new Goods(goodsType));
                }
            }
        }
    }


    private void createSoLMessages() {
        final int difficulty = getOwner().getDifficulty();
        final int veryBadGovernment = 10 - difficulty;
        final int badGovernment = 6 - difficulty;
        if (sonsOfLiberty / 10 != oldSonsOfLiberty / 10) {
            if (sonsOfLiberty > oldSonsOfLiberty) {
                addModelMessage(this, "model.colony.SoLIncrease", new String[][] {
                        { "%oldSoL%", String.valueOf(oldSonsOfLiberty) },
                        { "%newSoL%", String.valueOf(sonsOfLiberty) }, { "%colony%", getName() } },
                        ModelMessage.SONS_OF_LIBERTY, new Goods(Goods.BELLS));
            } else {
                addModelMessage(this, "model.colony.SoLDecrease", new String[][] {
                        { "%oldSoL%", String.valueOf(oldSonsOfLiberty) },
                        { "%newSoL%", String.valueOf(sonsOfLiberty) }, { "%colony%", getName() } },
                        ModelMessage.SONS_OF_LIBERTY, new Goods(Goods.BELLS));
            }
        }

        int bonus = 0;
        if (sonsOfLiberty == 100) {
            // there are no tories left
            bonus = 2;
            if (oldSonsOfLiberty < 100) {
                addModelMessage(this, "model.colony.SoL100", new String[][] { { "%colony%", getName() } },
                        ModelMessage.SONS_OF_LIBERTY, new Goods(Goods.BELLS));
            }
        } else {
            if (sonsOfLiberty >= 50) {
                bonus += 1;
                if (oldSonsOfLiberty < 50) {
                    addModelMessage(this, "model.colony.SoL50", new String[][] { { "%colony%", getName() } },
                            ModelMessage.SONS_OF_LIBERTY, new Goods(Goods.BELLS));
                }
            }
            if (tories > veryBadGovernment) {
                bonus -= 2;
                if (oldTories <= veryBadGovernment) {
                    // government has become very bad
                    addModelMessage(this, "model.colony.veryBadGovernment",
                            new String[][] { { "%colony%", getName() } }, ModelMessage.GOVERNMENT_EFFICIENCY,
                            new Goods(Goods.BELLS));
                }
            } else if (tories > badGovernment) {
                bonus -= 1;
                if (oldTories <= badGovernment) {
                    // government has become bad
                    addModelMessage(this, "model.colony.badGovernment", new String[][] { { "%colony%", getName() } },
                            ModelMessage.GOVERNMENT_EFFICIENCY, new Goods(Goods.BELLS));
                } else if (oldTories > veryBadGovernment) {
                    // government has improved, but is still bad
                    addModelMessage(this, "model.colony.governmentImproved1",
                            new String[][] { { "%colony%", getName() } }, ModelMessage.GOVERNMENT_EFFICIENCY,
                            new Goods(Goods.BELLS));
                }
            } else if (oldTories > badGovernment) {
                // government was bad, but has improved
                addModelMessage(this, "model.colony.governmentImproved2", new String[][] { { "%colony%", getName() } },
                        ModelMessage.GOVERNMENT_EFFICIENCY, new Goods(Goods.BELLS));
            }
        }

        // TODO-LATER: REMOVE THIS WHEN THE AI CAN HANDLE PRODUCTION PENALTIES:
        if (getOwner().isAI()) {
            productionBonus = Math.max(0, bonus);
        } else {
            productionBonus = bonus;
        }

    }


    /**
     * Prepares this <code>Colony</code> for a new turn.
     */
    @Override
    public void newTurn() {
        // Skip doing work in enemy colonies.
        if (unitCount != -1) {
            return;
        }

        if (getTile() == null) {
            // Fix NullPointerException below
            logger.warning("Colony " + getName() + " lacks a tile!");
            return;
        }

        // TODO: make warehouse a building
        saveWarehouseState();

        addColonyTileProduction();
        updateFood();
        if (getUnitCount() == 0) {
            dispose();
            return;
        }
        updateHorses();
        checkForNewColonist();

        // TODO: this needs to be made future-proof by considering all
        // materials that might be used for construction work. This
        // will require a Buildable interface, and suitable methods
        // for the *Type classes.
        addHammersAndTools();

        // The following tasks consume hammers/tools,
        // or may do so in the future
        repairShips();
        checkBuildingComplete();

        addBuildingProduction();
        exportGoods();
        // Throw away goods there is no room for.
        goodsContainer.cleanAndReport(getWarehouseCapacity(), getLowLevel(), getHighLevel());
        // TODO: make warehouse a building
        createWarehouseCapacityWarning();

        // Remove bells:
        bells -= Math.max(0, getUnitCount() - 2);
        if (bells < 0) {
            bells = 0;
        }

        // Update SoL:
        updateSoL();
        createSoLMessages();
        // Remember current SoL and tories for check changes at the next turn
        oldSonsOfLiberty = sonsOfLiberty;
        oldTories = tories;

    }


    /**
     * Returns the capacity of this colony's warehouse. All goods above this
     * limit, except {@link Goods#FOOD}, will be removed when calling
     * {@link #newTurn}.
     * 
     * @return The capacity of this <code>Colony</code>'s warehouse.
     */
    public int getWarehouseCapacity() {
        return 100 + getBuilding(Building.WAREHOUSE).getLevel() * 100;
    }

    /**
     * Disposes this <code>Colony</code>. All <code>WorkLocation</code>s
     * owned by this <code>Colony</code> will also be destroyed.
     */
    @Override
    public void dispose() {
        Iterator<WorkLocation> i = getWorkLocationIterator();
        while (i.hasNext()) {
            ((FreeColGameObject) i.next()).dispose();
        }
        super.dispose();
    }

    /**
     * This method writes an XML-representation of this object to the given
     * stream. <br>
     * <br>
     * Only attributes visible to the given <code>Player</code> will be added
     * to that representation if <code>showAll</code> is set to
     * <code>false</code>.
     * 
     * @param out The target stream.
     * @param player The <code>Player</code> this XML-representation should be
     *            made for, or <code>null</code> if
     *            <code>showAll == true</code>.
     * @param showAll Only attributes visible to <code>player</code> will be
     *            added to the representation if <code>showAll</code> is set
     *            to <i>false</i>.
     * @param toSavedGame If <code>true</code> then information that is only
     *            needed when saving a game is added.
     * @throws XMLStreamException if there are any problems writing to the
     *             stream.
     */
    @Override
    protected void toXMLImpl(XMLStreamWriter out, Player player, boolean showAll, boolean toSavedGame)
            throws XMLStreamException {
        // Start element:
        out.writeStartElement(getXMLElementTagName());
        // Add attributes:
        out.writeAttribute("ID", getID());
        out.writeAttribute("name", name);
        out.writeAttribute("owner", owner.getID());
        out.writeAttribute("tile", tile.getID());
        if (getGame().isClientTrusted() || showAll || player == getOwner()) {
            out.writeAttribute("hammers", Integer.toString(hammers));
            out.writeAttribute("bells", Integer.toString(bells));
            out.writeAttribute("sonsOfLiberty", Integer.toString(sonsOfLiberty));
            out.writeAttribute("oldSonsOfLiberty", Integer.toString(oldSonsOfLiberty));
            out.writeAttribute("tories", Integer.toString(tories));
            out.writeAttribute("oldTories", Integer.toString(oldTories));
            out.writeAttribute("productionBonus", Integer.toString(productionBonus));
            out.writeAttribute("currentlyBuilding", Integer.toString(currentlyBuilding));
            out.writeAttribute("landLocked", Boolean.toString(landLocked));
            char[] exportsCharArray = new char[exports.length];
            for (int i = 0; i < exports.length; i++) {
                exportsCharArray[i] = (exports[i] ? '1' : '0');
            }
            out.writeAttribute("exports", new String(exportsCharArray));
            toArrayElement("lowLevel", lowLevel, out);
            toArrayElement("highLevel", highLevel, out);
            toArrayElement("exportLevel", exportLevel, out);
            Iterator<WorkLocation> workLocationIterator = workLocations.iterator();
            while (workLocationIterator.hasNext()) {
                ((FreeColGameObject) workLocationIterator.next()).toXML(out, player, showAll, toSavedGame);
            }
        } else {
            out.writeAttribute("unitCount", Integer.toString(getUnitCount()));
            getBuilding(Building.STOCKADE).toXML(out, player, showAll, toSavedGame);
        }
        goodsContainer.toXML(out, player, showAll, toSavedGame);
        // End element:
        out.writeEndElement();
    }

    /**
     * Initialize this object from an XML-representation of this object.
     * 
     * @param in The input stream with the XML.
     */
    @Override
    protected void readFromXMLImpl(XMLStreamReader in) throws XMLStreamException {
        initializeWarehouseSettings();
        setID(in.getAttributeValue(null, "ID"));
        name = in.getAttributeValue(null, "name");
        owner = (Player) getGame().getFreeColGameObject(in.getAttributeValue(null, "owner"));
        if (owner == null) {
            owner = new Player(getGame(), in.getAttributeValue(null, "owner"));
        }
        tile = (Tile) getGame().getFreeColGameObject(in.getAttributeValue(null, "tile"));
        if (tile == null) {
            tile = new Tile(getGame(), in.getAttributeValue(null, "owner"));
        }
        owner.addSettlement(this);
        hammers = getAttribute(in, "hammers", 0);
        bells = getAttribute(in, "bells", 0);
        sonsOfLiberty = getAttribute(in, "sonsOfLiberty", 0);
        oldSonsOfLiberty = getAttribute(in, "oldSonsOfLiberty", 0);
        tories = getAttribute(in, "tories", 0);
        oldTories = getAttribute(in, "oldTories", 0);
        productionBonus = getAttribute(in, "productionBonus", 0);
        currentlyBuilding = getAttribute(in, "currentlyBuilding", -1);
        landLocked = getAttribute(in, "landLocked", true);
        unitCount = getAttribute(in, "unitCount", -1);
        final String exportString = in.getAttributeValue(null, "exports");
        if (exportString != null) {
            for (int i = 0; i < exportString.length(); i++) {
                exports[i] = ((exportString.charAt(i) == '1') ? true : false);
            }
        }
        // Read child elements:
        while (in.nextTag() != XMLStreamConstants.END_ELEMENT) {
            if (in.getLocalName().equals(ColonyTile.getXMLElementTagName())) {
                ColonyTile ct = (ColonyTile) getGame().getFreeColGameObject(in.getAttributeValue(null, "ID"));
                if (ct != null) {
                    ct.readFromXML(in);
                } else {
                    addWorkLocation(new ColonyTile(getGame(), in));
                }
            } else if (in.getLocalName().equals(Building.getXMLElementTagName())) {
                Building b = (Building) getGame().getFreeColGameObject(in.getAttributeValue(null, "ID"));
                if (b != null) {
                    b.readFromXML(in);
                } else {
                    addWorkLocation(new Building(getGame(), in));
                }
            } else if (in.getLocalName().equals(GoodsContainer.getXMLElementTagName())) {
                GoodsContainer gc = (GoodsContainer) getGame().getFreeColGameObject(in.getAttributeValue(null, "ID"));
                if (gc != null) {
                    goodsContainer.readFromXML(in);
                } else {
                    goodsContainer = new GoodsContainer(getGame(), this, in);
                }
            } else if (in.getLocalName().equals("lowLevel")) {
                lowLevel = readFromArrayElement("lowLevel", in, new int[0]);
            } else if (in.getLocalName().equals("highLevel")) {
                highLevel = readFromArrayElement("highLevel", in, new int[0]);
            } else if (in.getLocalName().equals("exportLevel")) {
                exportLevel = readFromArrayElement("exportLevel", in, new int[0]);
            } else {
                logger.warning("Unknown tag: " + in.getLocalName() + " loading colony " + name);
            }
        }
    }

    /**
     * Gets the tag name of the root element representing this object.
     * 
     * @return "colony".
     */
    public static String getXMLElementTagName() {
        return "colony";
    }

    /**
     * Returns just this Colony itself.
     * 
     * @return this colony.
     */
    public Colony getColony() {
        return this;
    }
    
    /**
     * Returns the power for bombarding
     * 
     * @return the power for bombarding
     */
    public float getBombardingPower() {
        float attackPower = 0;
        Iterator<Unit> unitIterator = getTile().getUnitIterator();
        while (unitIterator.hasNext()) {
            Unit unit = unitIterator.next();
            switch (unit.getType()) {
            case Unit.ARTILLERY:
            case Unit.DAMAGED_ARTILLERY:
                attackPower += unit.getOffensePower(unit);
                break;
            default:
            }
        }
        if (attackPower > 48) {
            attackPower = 48;
        }
        return attackPower;
    }
    
    /**
     * Returns a unit to bombard
     *
     * @return a unit to bombard
     */
    public Unit getBombardingAttacker() {
        Unit attacker = null;
        Iterator<Unit> unitIterator = getTile().getUnitIterator();
        loop: while (unitIterator.hasNext()) {
            Unit unit = unitIterator.next();
            logger.finest("Unit is " + unit.getName());
            switch (unit.getType()) {
            case Unit.ARTILLERY:
                attacker = unit;
                break loop;
            case Unit.DAMAGED_ARTILLERY:
                if (attacker == null) {
                    attacker = unit;
                }
                break;
            default:
            }
        }
        return attacker;
    }
    
    /**
     * Returns true when colony has a stockade
     *
     * @return whether the colony has a stockade
     */
    boolean hasStockade() {
        return getBuilding(Building.STOCKADE).getLevel() >= Building.HOUSE;
    }
}
