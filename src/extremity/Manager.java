package extremity;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.ctype.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.storage.*;
import mindustry.world.blocks.units.*;
import mindustry.world.consumers.*;

import java.nio.*;
import java.util.Arrays;

import static mindustry.Vars.*;
import static extremity.SettingCache.*;

public class Manager{
    public static final int fixedRate = 5; // the amount of ticks between each fixedUpdate

    static boolean host = false;
    static String modVersion;

    final static OrderedMap<UnitType, Seq<UnitType>> spawns = new OrderedMap<>();

    private static Seq<UnitType> units = new Seq<>();
    private final static Seq<Tile> areas = new Seq<>();
    private static Seq<StatusEffect> effectCache = new Seq<>();
    private final static ObjectFloatMap<StatusEffect> effects = new ObjectFloatMap<>();

    final static Interval intervals = new Interval(1);
    final static Seq<Player> players = new Seq<>();

    static boolean[] covered;
    static boolean[] weathers = new boolean[3];
    static Item type;
    static int ammo, schedule;
    static float heat, req, time, damage;

    static void setup(){
        Events.run(EventType.Trigger.update, Manager::update);

        // startup task that creates a map of unit spawns, *should* be compatible with most mods
        Events.on(EventType.ContentInitEvent.class, e -> {
            reload();

            effectCache = content.statusEffects();
            Log.infoTag("Extremity","Pain has been fully loaded...");
        });

        Core.app.post(() -> {
            netServer.addBinaryPacketHandler("extremity-syncreq", (player, content) -> sync(player));
            netServer.addPacketHandler("extremity-confirm", (player, version) -> {
                if(players.addUnique(player)){
                    if(!version.equals(modVersion))
                        Call.clientPacketReliable(player.con, "extremity-error", modVersion);

                    int ver = Strings.parseInt(modVersion.replaceAll("\\.", ""), -1), comp = Strings.parseInt(version.replaceAll("\\.", ""), -1);
                    if(ver < comp){
                        if(headless)
                            Log.warn(Strings.format("A player with newer version of Extremity has joined, consider updating to avoid issues! (Local: v@, Theirs: v@)", modVersion, version));
                        else
                            ui.chatfrag.addMessage(Strings.format(Core.bundle.get("extremity-outdated"), modVersion, version));
                    }
                }
            });

            if(headless) return;

            netClient.addPacketHandler("extremity-error", ver ->
                ui.showInfoFade(Strings.format(Core.bundle.get("extremity-error"), modVersion, ver), 10f)
            );
            netClient.addBinaryPacketHandler("extremity-config", SettingCache::apply);
        });

        Events.on(EventType.BlockDestroyEvent.class, e -> {
            if(turretExplosions && validTurret(e.tile.build)){
                var block = e.tile.build.block;
                float rng = 0, exp = type.explosiveness * ammo, flb = type.flammability * ammo, pwr = 350 * type.charge * ammo;

                if(block.hasPower){
                    var power = (ConsumePower) block.findConsumer(c -> c instanceof ConsumePower);
                    if(power != null){
                        pwr += power.usage;
                        rng = block.size + ((power.usage / (block.size * block.size)) / tilesize);
                    }
                }else rng = block.size + (((exp / 3) + (flb / 5)) / 2);

                Damage.dynamicExplosion(e.tile.getX(), e.tile.getY(), exp, flb, pwr, rng, host);
                return;
            }

            if(killCores && e.tile.block() instanceof CoreBlock)
                e.tile.team().cores().copy().each(Building::kill);
        });

        Events.on(EventType.WaveEvent.class, e -> {
            if(!extendedZones || difficulty < 1) return;

            Seq<Building> builds = new Seq<>();
            spawner.getSpawns().each(t -> {
                t.circle((int) ((state.rules.dropZoneRadius + 16f) * 1.05f) / tilesize, tmp -> {
                    if(tmp.build != null)
                        builds.addUnique(tmp.build);
                });
            });
            builds.each(b -> b.damage(b.maxHealth / Math.max(0.1f, (4f / difficulty))));
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            units.clear();
            effects.clear();

            Arrays.fill(weathers, false);
            covered = new boolean[world.width() * world.height()];

            host = net.server() || !net.active();
            if(host){
                fetch();
                if(!headless && active)
                    ui.chatfrag.addMessage(Core.bundle.get("extremity-welcome"));
            }
        });

        Events.on(EventType.PlayerJoin.class, e -> {
            if(host){
                sync(e.player);
                Timer.schedule(() -> {
                    if(!players.contains(e.player)) // this isn't in a bundle cause of dedicated servers
                        e.player.sendMessage("[accent]Welcome!\n\n[lightgray]The host's running [scarlet]Extremity[], a difficulty enhancing mod by [green]W[orange]M[brown]F[][][]!\nConsider installing the mod to reduce certain desyncs!");
                }, 3f);
            }
        });
        Events.on(EventType.PlayerLeave.class, e -> players.remove(e.player));

        Events.on(EventType.UnitDestroyEvent.class, e -> {
            if(!host || hasPlayers(e.unit.team)) return;

            if(guardianShielding && e.unit.hasEffect(StatusEffects.boss))
                Units.nearby(e.unit.team, e.unit.x, e.unit.y, e.unit.range(), u -> u.apply(StatusEffects.shielded, Float.MAX_VALUE));

            if(difficulty < 1) return;

            var tile = e.unit.tileOn();
            if(tile == null) return;

            units = spawns.get(e.unit.type, Seq.with());
            if(units.isEmpty()) return;

            effects.clear();
            for(int i = 0; i < effectCache.size; i++)
                if(e.unit.hasEffect(effectCache.get(i)))
                    effects.put(effectCache.get(i), e.unit.getDuration(effectCache.get(i)));

            units.each(type -> {
                areas.clear();
                tile.circle((int)((e.unit.hitSize + 16f) / tilesize), var -> {
                    if(var != null && (!var.solid() || type.flying) && (var.floor().isLiquid || !type.naval))
                        areas.add(var);
                });

                if(areas.isEmpty()) // no spawn locations, bail!
                    return;

                for(int i = 0; i <= unitRand(); i++){
                    Tile tmp = areas.random();

                    Unit u = type.spawn(e.unit.team, tmp.getX() + Mathf.random(-0.2f, 0.2f), tmp.getY() + Mathf.random(-0.2f, 0.2f));
                    u.rotation(e.unit.rotation);
                    effects.each(entry -> u.apply(entry.key, entry.value));
                }
            });
        });

        Events.on(EventType.SectorLoseEvent.class, e -> {
            if(resetCampaign)
                restartCampaign(e.sector.planet);
        });

        Events.on(EventType.GameOverEvent.class, e -> {
            if(resetCampaign && state.isCampaign())
                restartCampaign(state.getPlanet());
        });
    }

    private static void restartCampaign(Planet planet){
        if(!resetCampaign || planet == null) return;

        Call.infoToast("Campaign Lost...", 5f);

        // finishes current sector
        Sector ref = state.rules.sector;
        if(ref != null && ref.isBeingPlayed())
            state.rules.defaultTeam.cores().each(Building::kill);

        // clears all other sectors
        for(var sec : planet.sectors){
            sec.clearInfo();
            if(sec.save != null){
                sec.save.delete();
                sec.save = null;
            }
        }

        // clears launch cache
        universe.clearLoadoutInfo();

        // clears the tech tree
        for(var node : planet.techTree.children){
            node.reset();
            node.content.clearUnlock();
        }
    }

    private static UnitType getUnit(String input){
        return Strings.canParseInt(input) ? content.unit(Strings.parseInt(input)) : content.unit(input);
    }

    private static void update(){
        if(needsSync && (headless || !ui.settings.isShown()))
            fetch();

        if(state.isEditor() || !state.isPlaying()) return;

        if(host && (fastEnemies || slowAllies || weatherEffects)){
            Groups.unit.each(u -> u.type.playerControllable, u -> {
                if(u == null || !u.isValid()) return;

                if(fastEnemies && !hasPlayers(u.team))
                    u.apply(StatusEffects.fast, 300f);
                else{
                    if(!state.rules.pvp && slowAllies)
                        u.apply(StatusEffects.slow, 300f);

                    if(weatherEffects && validUnit(u))
                        u.apply(StatusEffects.corroded, 180f);
                }
            });
        }

        if(manageBullets) // bullets deal less damage if they're new
            Groups.bullet.each(b -> hasPlayers(b.team), b -> b.damage = b.type.damage / ((b.lifetime * 0.8f) / b.time));

        if(++schedule >= fixedRate)
            fixedUpdate();

        if((!weatherEffects || !affectBuildings) && (!damageTurrets || !state.rules.pvp))
            return;

        Groups.build.each(b -> {
            if(b instanceof ForceProjector.ForceBuild build){
                b.tile.circle((int) (build.realRadius() / tilesize) + 1, (tx, ty) -> {
                    if(Intersector.isInsideHexagon(b.tileX(), b.tileY(), (build.realRadius() * 2) / tilesize, tx, ty))
                        covered[tx + ty * world.width()] = true;
                });
            }
        });

        world.tiles.eachTile(t -> {
            if(t.build == null || (!hasPlayers(t.team()) && !state.rules.pvp)) return;

            if(affectBuildings){
                if(weathers[1])
                    t.build.applySlowdown(0.35f, 2f);
                if(weathers[2] && t.build.block instanceof Conveyor)
                    t.build.applySlowdown(0.5f, 2f);
            }

            if(state.rules.pvp) return;

            damage = 0;
            if(damageBuildings && weathers[0] && Mathf.chance(0.64d) && !covered[t.array()])
                damage += 0.0083f * scaledRand();
            if(damageTurrets && validTurret(t.build) && (t.build.liquids.current() == null || t.build.liquids.currentAmount() <= req) && heat >= 0.2f)
                damage += heat * scaledRand();

            if(damage > 0)
                t.build.damageContinuous(damage);
            covered[t.array()] = false;
        });
    }

    private static void fixedUpdate(){
        schedule = 0;

        if(extraInvasions && difficulty > 0 && state.isCampaign()){
            Planet planet = state.getPlanet();
            if(planet != null){
                Seq<Sector> sectors = state.getPlanet().sectors;
                if(!sectors.isEmpty()){
                    int captured = sectors.count(Sector::isCaptured);
                    if(captured > Math.max(2, 8 / difficulty)){
                        float ratio = (float) captured / sectors.count(Sector::isAttacked), max = difficulty * 0.024f;
                        if(ratio < max && canInvade()){
                            Sector sector = sectors.select(Sector::isCaptured).random();
                            int waveMax = Math.max(sector.info.winWave, sector.isBeingPlayed() ? state.wave : sector.info.wave + sector.info.wavesPassed) + Mathf.random(1, difficulty) * 5;

                            if(sector.isBeingPlayed()){
                                state.rules.winWave = waveMax;
                                state.rules.waves = true;
                                state.rules.attackMode = false;
                                planet.campaignRules.apply(planet, state.rules);

                                if(net.server()) Call.setRules(state.rules);
                            }else{
                                sector.info.winWave = waveMax;
                                sector.info.waves = true;
                                sector.info.attack = false;
                                sector.saveInfo();
                            }

                            Events.fire(new EventType.SectorInvasionEvent(sector));
                        }
                    }
                }
            }
        }

        if(weatherEffects){
            Arrays.fill(weathers, false);
            Groups.weather.each(state -> {
                if(state.weather == Weathers.rain)
                    weathers[0] = true;
                else if(state.weather == Weathers.snow)
                    weathers[1] = true;
                else if(state.weather == Weathers.sandstorm)
                    weathers[2] = true;
            });
        }
    }

    private static boolean canInvade(){
        float chance, period;

        switch(difficulty){
            case 3 -> {
                chance = 0.000043f;
                period = 14400f;
            }
            case 4 -> {
                chance = 0.00005f;
                period = 7200f;
            }
            case 5 -> {
                chance = 0.00013f;
                period = 5400f;
            }
            default -> {
                chance = 0.0002f;
                period = 1800f;
            }
        }

        return Mathf.chance(chance) && intervals.get(period);
    }

    private static boolean hasPlayers(Team team){
        return !state.rules.pvp && ((!headless && team == player.team()) || team.data().players.size > 0);
    }

    private static boolean validTurret(Building b){ //TODO: this is terrible... replace with something better eventually
        if(b == null || !b.block.hasLiquids)
            return false;

        if(!(b instanceof Turret.TurretBuild build))
            return false;

        if(b.block instanceof ContinuousTurret || b.block instanceof LiquidTurret)
            return false;

        type = Items.copper;
        ammo = 0;
        req = (build.block instanceof PowerTurret t && t.shootType instanceof ContinuousLaserBulletType) ? t.liquidCapacity * 0.8f : 1f;

        if(build instanceof ItemTurret.ItemTurretBuild t && !t.ammo.isEmpty()){
            var tmp = t.ammo.sort(e -> e.amount).first();

            type = ((ItemTurret.ItemEntry) tmp).item;
            ammo = tmp.amount;
        }

        heat = build.heat;
        return true;
    }

    private static boolean validUnit(Unit u){
        return u != null && (hasPlayers(u.team) || state.rules.pvp) && u.hasEffect(StatusEffects.wet) && u.tileOn() != null && !(covered[u.tileX() + u.tileY() * world.width()] || u.shield > 0);
    }

    private static int unitRand(){
        return difficulty <= 3 ? difficulty + Mathf.random(difficulty) : scaledRand() * Mathf.random(1, difficulty / 2);
    }

    private static int scaledRand(){
        return difficulty <= 3 ? difficulty : difficulty + Mathf.random(difficulty);
    }

    static void reload(){
        spawns.clear();
        time = 0;

        Log.infoTag("Extremity", "Running unit indexing task...");
        Time.mark();

        spawns.putAll(
            UnitTypes.gamma, Seq.with(UnitTypes.beta),
            UnitTypes.beta, Seq.with(UnitTypes.alpha),
            UnitTypes.emanate, Seq.with(UnitTypes.incite),
            UnitTypes.incite, Seq.with(UnitTypes.evoke)
        );

        // Simple unitdex auto-assembler, should pick up most units added by mods, unless they use a custom production method
        // In such case, use the Core.settings based unitdex loading for said mod, documented below this assembler
        content.blocks().select(b -> b instanceof Reconstructor || b instanceof UnitAssembler).each(b -> {
            if(b instanceof Reconstructor r){
                r.upgrades.each(u -> {
                    if(spawns.containsKey(u[1])) return;

                    spawns.put(u[1], Seq.with(u[0]));
                });
            }

            if(b instanceof UnitAssembler a){
                units.clear();
                a.plans.each(p -> {
                    p.requirements.each(s -> {
                        if(s.item instanceof UnitType u)
                            units.add(u);
                    });

                    if(!units.isEmpty())
                        spawns.put(p.unit, units.copy());
                });
            }
        });

        time = Time.elapsed();

        // To add a custom unitdex, do Core.settings.put("extremity-unitdex-<your mod's internal name>", "<unitdex string>")
        // Generate the unitdex string using the in-game unitdex editor, simply clear out all existing entries
        // then create entries for the missing units, using the <unit> -> <spawns> scheme, and copy to clipboard
        // Only one unitdex string will be processed per mod, so make sure to include all the units in it
        // Additionally, make sure the Core.settings.put() is ran every time the mod's being loaded, best if in the mod's main class

        // Assembler for unitdexes stored as strings in the Core.settings
        Core.app.post(() -> {
            Log.infoTag("Extremity", "Fetching entries from other mods...");
            Time.mark();

            Seq<UnitType> results = new Seq<>();
            mods.eachEnabled(m -> {
                if(m.meta.internalName.equals("extremity")) return;

                String data = Core.settings.getString("extremity-unitdex-" + m.meta.internalName, "");
                if(!data.isEmpty()){
                    String[] base = data.split(":");
                    for(String input : base){
                        String[] main = input.split("=");
                        if(main.length < 2){
                            Log.infoTag("Extremity", Strings.format("Couldn't parse unitdex entry @ from @", input, m.meta.displayName));
                            continue;
                        }

                        UnitType parent = getUnit(main[0]);
                        String[] secondary = main[1].split("&");

                        results.clear();
                        for(String var : secondary){
                            UnitType spawn = getUnit(var);
                            if(spawn != null)
                                results.addUnique(spawn);
                            else Log.infoTag("Extremity", Strings.format("Couldn't find any unit for entry @", var));
                        }

                        if(parent != null && !results.isEmpty())
                            spawns.put(parent, results.copy());
                        else Log.infoTag("Extremity", Strings.format("Found no units matching the unitdex entry data (@)", main[1]));
                    }
                }else Log.infoTag("Extremity", Strings.format("Mod @ does not have any custom unitdex entries", m.meta.displayName));
            });

            Log.infoTag("Extremity", Strings.format("Created @ entries in @ms!", spawns.size, Math.round(time + Time.elapsed())));
        });
    }

    public static String packDex(){
        StringBuilder builder = new StringBuilder();

        for(var entry : spawns){
            builder.append(entry.key.isVanilla() ? entry.key.id : entry.key.name).append("=");
            for(int i = 0; i < entry.value.size; i++){
                builder.append(entry.value.get(i).isVanilla() ? entry.value.get(i).id : entry.value.get(i).name);
                if(entry.value.size > 1 && i < (entry.value.size - 1))
                    builder.append("&");
            }
            builder.append(":");
        }

        builder.setLength(Math.max(0, builder.length() - 1));
        return builder.toString();
    }

    public static void loadDex(String name){
        String data = Core.settings.getString("extremity-customdex-" + name, "");
        loadRaw(data, name);
    }

    public static void loadRaw(String data, String name){
        if(!data.isEmpty()){
            Seq<UnitType> results = new Seq<>();
            spawns.clear();

            String[] base = data.split(":");
            for(String input : base){
                String[] main = input.split("=");
                if(main.length < 2){
                    Log.infoTag("Extremity", Strings.format("Couldn't parse unitdex entry @ from @", input, name));
                    continue;
                }

                UnitType parent = getUnit(main[0]);
                String[] secondary = main[1].split("&");

                results.clear();
                for(String var : secondary){
                    UnitType spawn = getUnit(var);
                    if(spawn != null)
                        results.add(spawn);
                    else Log.infoTag("Extremity", Strings.format("Couldn't find any unit for entry @", var));
                }

                if(parent != null && !results.isEmpty())
                    spawns.put(parent, results.copy());
                else Log.infoTag("Extremity", Strings.format("Found no units matching the unitdex entry data (@)", main[1]));
            }
        }else Log.infoTag("Extremity", Strings.format("Save @ does not have any unitdex entries", name));
    }

    public static void syncAll(){
        if(!state.isPlaying()) return;

        Groups.player.each(Manager::sync);
    }

    private static void sync(Player player){
        if(!state.isPlaying()) return;

        Call.clientBinaryPacketReliable(player.con, "extremity-config", writeBuffer.array());
    }
}
