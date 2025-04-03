package extremity;

import arc.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.defense.turrets.*;
import mindustry.world.blocks.distribution.*;
import mindustry.world.blocks.units.*;
import mindustry.world.consumers.*;

import java.util.Arrays;

import static mindustry.Vars.*;

public class Manager{
    static boolean host = false, allowPvp = false;
    static int difficulty = 3; // highest by default

    final static OrderedMap<UnitType, Seq<UnitType>> spawns = new OrderedMap<>();

    private static Seq<UnitType> units = new Seq<>();
    private final static Seq<Tile> areas = new Seq<>();
    private final static Seq<Player> players = new Seq<>();
    private final static ObjectFloatMap<StatusEffect> effects = new ObjectFloatMap<>();

    static boolean[] weathers = new boolean[3];
    static Item type;
    static int ammo;
    static float heat, req, time, damage;

    static void setup(){
        Timer.schedule(Manager::updateWeathers, 0, 1f/12); // 5 tps updating of the weather state
        Events.run(EventType.Trigger.update, Manager::update);

        // startup task that creates a map of unit spawns, *should* be compatible with most mods
        Events.on(EventType.ContentInitEvent.class, e -> {
            reload();
            Log.infoTag("Extremity","Pain has been fully loaded...");
        });

        Core.app.post(() -> {
            netServer.addPacketHandler("extremity-resync", (player, content) -> sync(player, content.equals("true")));
            netServer.addPacketHandler("extremity-confirm", (player, version) ->{
                players.addUnique(player);

                if(!version.equals(version()))
                    Call.clientPacketReliable(player.con, "extremity-error", version());

                int ver = Strings.parseInt(version().replaceAll("\\.", ""), -1), comp = Strings.parseInt(version.replaceAll("\\.", ""), -1);
                if(ver < comp){
                    if(headless)
                        Log.warn(Strings.format("A player with newer version of Extremity has joined, consider updating to avoid issues! (Local: v@, Theirs: v@)", version(), version));
                    else ui.chatfrag.addMessage(Strings.format(Core.bundle.get("extremity-outdated"), version(), version));
                }
            });

            if(headless) return;

            netClient.addPacketHandler("extremity-error", ver ->
                ui.showInfoFade(Strings.format(Core.bundle.get("extremity-error"), version(), ver), 10f)
            );
            netClient.addPacketHandler("extremity-config", cfg -> {
                String[] vars = cfg.split(":");

                difficulty = Strings.parseInt(vars[1], -1);
                allowPvp = vars[2].equals("true");

                if(difficulty <= -1){
                    ui.showInfoFade(Core.bundle.get("extremity-fail"), 10f);
                    Call.serverPacketReliable("extremity-resync", vars[0]);
                }else if(vars[0].equals("true")){
                    ui.showInfoFade(Strings.format(Core.bundle.get("extremity-success"), toName()), 10f);
                    Call.serverPacketReliable("extremity-confirm", version());
                }
            });
        });


        Events.on(EventType.BlockDestroyEvent.class, e -> {
            if(difficulty < 2) return;

            if(validTurret(e.tile.build)){
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
            }
        });

        Events.on(EventType.WaveEvent.class, e -> {
            if(difficulty < 2) return;

            Seq<Building> builds = new Seq<>();
            spawner.getSpawns().each(t -> {
                t.circle((int) ((state.rules.dropZoneRadius + 16f) * 1.05f) / tilesize, tmp -> {
                    if(tmp.build != null)
                        builds.addUnique(tmp.build);
                });
            });
            builds.each(b -> b.damage(b.maxHealth / (4f / (difficulty - 1))));
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            units.clear();
            effects.clear();

            Arrays.fill(weathers, false);

            host = net.server() || !net.active();
            if(host){
                if(headless)
                    allowPvp = state.rules.pvp;
                else{
                    allowPvp = (Core.settings.getBool("extremity-pvp", false) && state.rules.pvp);
                    difficulty = Core.settings.getInt("extremity-difficulty", -1);
                    ui.chatfrag.addMessage(Strings.format(Core.bundle.get("extremity-welcome"), toName()));
                }

                players.each(p -> sync(p, false));
            }
        });

        Events.on(EventType.PlayerJoin.class, e -> {
            if(host){
                sync(e.player, true);
                Timer.schedule(() -> {
                    if(!players.contains(e.player)) // this isn't in a bundle cause of dedicated servers
                        e.player.sendMessage(
                            Strings.format(
                                "[accent]Welcome!\n\nThe host's running [scarlet]Extremity[], a difficulty enhancing mod by [green]W[orange]M[brown]F[]!\nConsider installing the mod greatly reduce certain desyncs!\n\nCurrent difficulty is @!\nGood luck.",
                                Extremity.getName(difficulty)
                            )
                        );
                }, 3f);
            }
        });
        Events.on(EventType.PlayerLeave.class, e -> players.remove(e.player));

        Events.on(EventType.UnitDestroyEvent.class, e -> {
            if(!host || difficulty < 1 || hasPlayers(e.unit.team)) return;

            var tile = e.unit.tileOn();
            if(tile == null) return;

            units = spawns.get(e.unit.type, Seq.with());
            if(units.isEmpty()) return;

            effects.clear(); // this isn't particularly efficient, but it works!
            for(StatusEffect eff : content.statusEffects())
                if(e.unit.hasEffect(eff))
                    effects.put(eff, e.unit.getDuration(eff));

            units.each(type -> {
                areas.clear();
                tile.circle((int)((e.unit.hitSize + 16f) / tilesize), var -> {
                    if(var != null && (!var.solid() || type.flying) && (var.floor().isLiquid || !type.naval))
                        areas.add(var);
                });

                if(areas.isEmpty()) // no spawn locations, bail!
                    return;

                int rand = Mathf.random(difficulty);
                for(int i = 0; i <= (difficulty + rand); i++){
                    Tile tmp = areas.random();

                    Unit u = type.spawn(e.unit.team, tmp.getX() + Mathf.random(-0.2f, 0.2f), tmp.getY() + Mathf.random(-0.2f, 0.2f));
                    u.rotation(e.unit.rotation);
                    effects.each(entry -> u.apply(entry.key, entry.value));
                }
            });
        });
    }

    private static UnitType getUnit(String input){
        return Strings.canParseInt(input) ? content.unit(Strings.parseInt(input)) : content.unit(input);
    }

    private static void update(){
        if(difficulty < 1 || !state.isPlaying()) return;

        if(host){ // only the host has to apply effects, they're synced
            Groups.unit.each(u -> u.type.playerControllable, u -> {
                if(u == null || !u.isValid()) return;

                if(difficulty >= 2){
                    if(!hasPlayers(u.team))
                        u.apply(StatusEffects.fast, 300f);
                    else{
                        if(difficulty >= 3)
                            u.apply(StatusEffects.slow, 300f);

                        if(u.hasEffect(StatusEffects.wet))
                            u.apply(StatusEffects.corroded, 180f);
                    }
                }
            });
        }

        if(difficulty >= 3) // bullets deal less damage if they're new
            Groups.bullet.each(b -> hasPlayers(b.team), b -> b.damage = b.type.damage / (b.lifetime / b.time));

        if(allowPvp) return;

        world.tiles.eachTile(t -> {
            if(t.build == null || !hasPlayers(t.team())) return;

            if(weathers[1]) // the weather slowdowns have to be simulated on both ends - they aren't synced!
                t.build.applySlowdown(0.35f, 2f);
            if(weathers[2] && t.build.block instanceof Conveyor)
                t.build.applySlowdown(0.5f, 2f);

            if(!host) return;

            damage = 0;
            if(weathers[0] && Mathf.chance(0.64d))
                damage += 0.0083f * t.build.block.size * difficulty;
            if(validTurret(t.build) && (t.build.liquids.current() == null || t.build.liquids.currentAmount() <= req) && heat >= 0.2f)
                damage += heat * t.build.block.size * difficulty;

            t.build.damageContinuous(damage);
        });
    }

    private static void updateWeathers(){
        if(difficulty < 1 || !state.isPlaying()) return;

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

    private static boolean hasPlayers(Team team){
        return !allowPvp && ((!headless && team == player.team()) || team.data().players.size > 0);
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

        time += Time.elapsed();

        // Read settings to let mods add units without having this as a dependency
        // To add a unit, do Core.settings.put("extremity-unitdex-" + your mod's internal name, unitdex string)
        // where the unitdex string is [parent unit name/id]=[child unit name/id],
        // or [parent unit name/id]=[child unit name/id],[child unit name/id] for multiple spawns, stacks indefinitely
        // To set multiple different indexes, separate unitdex entries with : in this way [parent]=[child]:[parent]=[child]
        // Only one unitdex string will be processed per mod, so make sure to include all the units in it

        // Example unitdex strings:
        // dagger=crawler:crawler=nova
        // dagger=crawler,spiroct:spiroct=reign:reign=flare */

        Log.infoTag("Extremity", "Fetching entries from other mods...");
        Time.mark();
        Core.app.post(() -> {
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
                        else
                            Log.infoTag("Extremity", Strings.format("Found no units matching the unitdex entry data (@)", main[1]));
                    }
                }else Log.infoTag("Extremity", Strings.format("Mod @ does not have any unitdex entries", m.meta.displayName));
            });
        });

        Log.infoTag("Extremity", Strings.format("Created @ entries in @ms!", spawns.size, Math.round(time + Time.elapsed())));
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
                        results.addUnique(spawn);
                    else Log.infoTag("Extremity", Strings.format("Couldn't find any unit for entry @", var));
                }

                if(parent != null && !results.isEmpty())
                    spawns.put(parent, results.copy());
                else Log.infoTag("Extremity", Strings.format("Found no units matching the unitdex entry data (@)", main[1]));
            }
        }else Log.infoTag("Extremity", Strings.format("Save @ does not have any unitdex entries", name));
    }

    private static String toName(){
        return Core.bundle.get("extremity-diff" + difficulty);
    }

    private static String version(){
        return mods.getMod("extremity").meta.version;
    }

    private static void sync(Player player, boolean initial){
        Call.clientPacketReliable(player.con, "extremity-config", Strings.format("@:@:@", initial, difficulty, allowPvp));
    }
}
