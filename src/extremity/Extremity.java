package extremity;

import arc.*;
import arc.math.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import extremity.utils.*;

import static mindustry.Vars.*;

public class Extremity extends Mod{
    public final int highestScale = 10;
    public Session vote;

    public Extremity(){
        Manager.setup();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            fetchVersion();

            UnitdexEditor.init();
            if(!Core.settings.getBool("extremity-init", false)){
                ui.showStartupInfo("@extremity-info");
                Core.settings.put("extremity-init", true);
            }

            ui.settings.addCategory(Core.bundle.get("extremity-category"), Icon.book, t -> {
                DynamicSettings.DynamicTable table = new DynamicSettings.DynamicTable();

                var slider = table.sliderPref("extremity-difficulty", 0, 0, highestScale, s -> s + "", null);
                slider.changed = () -> {
                    SettingCache.needsSync = true;
                    Core.app.post(table::rebuild);
                };
                table.checkPref("extremity-pvp", false, s -> SettingCache.needsSync = true, () -> Core.settings.getInt("extremity-difficulty") > 0);
                table.checkPref("extremity-turrets", false, s -> SettingCache.needsSync = true, () -> Core.settings.getInt("extremity-difficulty") > 0);
                table.checkPref("extremity-explosions", false, s -> SettingCache.needsSync = true, null);
                table.checkPref("extremity-guardian", false, s -> SettingCache.needsSync = true, null);
                table.checkPref("extremity-enemies", false, s -> SettingCache.needsSync = true, null);
                table.checkPref("extremity-allies", false, s -> SettingCache.needsSync = true, null);
                table.confirmPref("extremity-campaign", false, c -> {
                    if(Core.settings.getBool("extremity-campaign", false)){
                        table.rebuild("extremity-campaign", false);
                        SettingCache.needsSync = true;
                        return;
                    }

                    ui.showConfirm("@confirm", "@setting.extremity-campaign.confirm", () -> {
                        table.rebuild("extremity-campaign", true);
                        SettingCache.needsSync = true;
                    });
                }, null);
                table.checkPref("extremity-cores", false, s -> SettingCache.needsSync = true, null);
                table.confirmPref("extremity-weather", false, s -> {
                    SettingCache.needsSync = true;
                    table.rebuild("extremity-weather", s);
                }, null);
                table.confirmPref("extremity-buildings", false, s -> {
                    SettingCache.needsSync = true;
                    table.rebuild("extremity-buildings", s);
                }, () -> Core.settings.getBool("extremity-weather", false));
                table.checkPref("extremity-damage", false, s -> SettingCache.needsSync = true, () -> Core.settings.getBool("extremity-buildings", false));
                table.checkPref("extremity-bullets", false, s -> SettingCache.needsSync = true, () -> Core.settings.getInt("extremity-difficulty") > 0);
                table.checkPref("extremity-invasions", false, s -> SettingCache.needsSync = true, () -> Core.settings.getInt("extremity-difficulty") > 0);
                table.checkPref("extremity-zones", false, s -> SettingCache.needsSync = true, () -> Core.settings.getInt("extremity-difficulty") > 0);
                table.sliderPref("extremity-rows", 1, 1, 3, s ->  s + "", null);

                t.add(table);
            });
            ui.menufrag.addButton("@extremity-unitdex-button", Icon.pencil, UnitdexEditor::show);
        });

        Events.on(EventType.ServerLoadEvent.class, e -> {
            fetchVersion();

            StringBuilder dex = new StringBuilder();
            netServer.clientCommands.<Player>register("difficulty", "[difficulty]", "Changes the Extremity difficulty scale (0-10), or prints the current difficulty to chat", (args, player) -> {
                if(args.length < 1){
                    player.sendMessage(Strings.format("[accent][Extremity] Current difficulty scale is @", SettingCache.difficulty));
                    return;
                }

                if(vote != null && !vote.completed){
                    player.sendMessage("[accent][Extremity] [scarlet]A vote is already in progress, please wait before it finishes!");
                    return;
                }

                vote = new Session(player, Mathf.clamp(Strings.parseInt(args[0]), 0, highestScale));
            });
            netServer.clientCommands.<Player>register("diffvote", "<yes/no>", "Submits a vote for the difficulty changing session", (args, player) -> {
                if(vote == null || vote.completed){
                    player.sendMessage("[accent][Extremity] [scarlet]No vote session ongoing, please start one before voting!");
                    return;
                }

                int res = switch(args[0].toLowerCase()){
                    case "c", "cancel" -> -1;
                    case "n", "no" -> 0;
                    case "y", "yes" -> 1;
                    case "f", "force" -> 2;
                    default -> -2;
                };

                if(res <= -2){
                    player.sendMessage("[accent][Extremity] [scarlet]Invalid parameter, please type yes (y) or no (n) in order to vote!");
                    return;
                }

                if(res == -1){
                    if(vote.player == player || player.admin){
                        if(player.admin)
                            Call.sendMessage("[accent][Extremity] Difficulty vote was cancelled by " + player.coloredName());
                        else Call.sendMessage("[accent][Extremity] Initiator has cancelled the difficulty vote.");

                        vote.stop();
                    }else Call.sendMessage("[accent][Extremity] [scarlet]Insufficient permissions.");

                    return;
                }

                if(res == 2){
                    if(player.admin){
                        Call.sendMessage("[accent][Extremity] Difficulty vote force-passed by " + player.coloredName());

                        SettingCache.difficulty = vote.difficulty;
                        vote.stop();
                    }else Call.sendMessage("[accent][Extremity] [scarlet]Insufficient permissions.");

                    return;
                }

                vote.vote(player, res == 1);
            });
            netServer.clientCommands.<Player>register("unitdex", "[unitdex]", "Allows admins to edit the unitdex ingame", (args, player) -> {
                if(!player.admin){
                    player.sendMessage("[accent][Extremity] [scarlet]Missing necessary privileges!");
                    return;
                }

                if(args.length <= 0){
                    player.sendMessage(Manager.packDex());
                    return;
                }

                int res = switch(args[0].toLowerCase()){
                    case "r", "reset" -> 0;
                    case "s", "submit" -> 1;
                    default -> -1;
                };

                if(res == 0){
                    dex.setLength(0);
                    Manager.reload();

                    player.sendMessage("[accent][Extremity] Reset unitdex to default values!");
                    return;
                }

                if(res == 1){
                    Manager.loadRaw(dex.toString(), "dex");
                    dex.setLength(0);

                    player.sendMessage("[accent][Extremity] Submitted new unitdex!");
                    return;
                }

                dex.append(args[0]);
                player.sendMessage("[accent][Extremity] Saved unitdex data in cache\nType [accent]/unitdex submit[] to apply when ready!");
            });
        });
    }

    public void fetchVersion(){
        var mod = mods.list().find(m -> m.main.equals(this));
        if(mod != null && mod.meta != null)
            Manager.modVersion = mod.meta.version;

        if(Manager.modVersion == null){
            Log.infoTag("Extremity","Failed to fetch the version.");
            Manager.modVersion = "err";
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("difficulty","[int]",  "Sets the difficulty scale that Extremity will use", i -> {
            if(i.length >= 1){
                SettingCache.difficulty = Mathf.clamp(Strings.parseInt(i[0], 3), 0, highestScale);
                Log.info(Strings.format("Set difficulty scale to @", SettingCache.difficulty));
            }else Log.info(Strings.format("Current difficulty scale is @", SettingCache.difficulty));
        });
        handler.register("unitdex", "[dex]", "Sets or prints the current unitdex", i -> {
            if(i.length >= 1){
                Manager.loadRaw(i[0], "dex");
                Log.info("Applied new unitdex");
            }else Log.info(Manager.packDex());
        });
        handler.register("resetdex", "Re-runs the indexing task to restore default configs", i -> Manager.reload());
    }
}
