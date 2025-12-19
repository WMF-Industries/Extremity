package extremity;

import arc.*;
import arc.math.*;
import arc.util.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.mod.*;
import extremity.utils.*;

import static mindustry.Vars.*;
import static extremity.utils.DedicatedBundles.*;

public class Extremity extends Mod{
    public final int highestScale = 10;
    public VoteSession diff, modi;

    public Extremity(){
        Manager.setup();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            finishLoading();
            UnitdexEditor.init();

            if(!Core.settings.getBool("extremity-init", false)){
                ui.showStartupInfo("@extremity-info");
                Core.settings.put("extremity-init", true);
            }

            //TODO: this is an eyesore
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
                table.checkPref("extremity-taken", false, s -> SettingCache.needsSync = true, null);
                table.checkPref("extremity-units", false, s -> SettingCache.needsSync = true, null);
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
                table.checkPref("extremity-zones", false, s -> SettingCache.needsSync = true, () -> Core.settings.getInt("extremity-difficulty") > 0);
                table.sliderPref("extremity-rows", 1, 1, 3, s ->  s + "", null);

                t.add(table);
            });
            ui.menufrag.addButton("@extremity-unitdex-button", Icon.pencil, UnitdexEditor::show);
        });

        Events.on(EventType.ServerLoadEvent.class, e -> {
            finishLoading();

            StringBuilder dex = new StringBuilder();
            netServer.clientCommands.<Player>register("difficulty", "[difficulty/vote]", "Changes the Extremity difficulty scale (0-10), or prints the current difficulty to chat", (args, player) -> {
                if(args.length < 1){
                    player.sendMessage(Strings.format(dynamicLocale(player).get("cmd.extremity-difficulty"), SettingCache.difficulty));
                    return;
                }

                if(Manager.lockSettings){
                    player.sendMessage(dynamicLocale(player).get("cmd.extremity-difficulty.locked"));
                    return;
                }

                if(diff == null || diff.completed){
                    if(!Strings.canParseInt(args[0])){
                        player.sendMessage(dynamicLocale(player).get("vote.extremity-invalid.difficulty"));
                        return;
                    }

                    int votediff = Mathf.clamp(Strings.parseInt(args[0]), 0, highestScale);
                    diff = new VoteSession(player){{
                        onVote = (p, vote) -> Groups.player.each(pl ->
                            pl.sendMessage(
                                Strings.format(
                                    dynamicLocale(pl).get(vote ? "vote.extremity-voted.difficulty" : "vote.extremity-voted-against.difficulty"),
                                    p.coloredName(), votediff, votes(), votesRequired()
                                )
                            )
                        );
                        onTime = () -> Groups.player.each(pl ->
                            pl.sendMessage(
                                Strings.format(
                                    dynamicLocale(pl).get("vote.extremity-time.difficulty"),
                                    time
                                )
                            )
                        );
                        onFailure = () -> Groups.player.each(pl ->
                            pl.sendMessage(
                                dynamicLocale(pl).get("vote.extremity-fail.difficulty")
                            )
                        );
                        onSuccess = () -> {
                            Groups.player.each(pl ->
                                pl.sendMessage(
                                    Strings.format(
                                        dynamicLocale(pl).get("vote.extremity-success.difficulty"),
                                        votediff
                                    )
                                )
                            );

                            Core.settings.put("extremity-difficulty", votediff);
                            SettingCache.fetch();
                        };
                    }};
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
                    player.sendMessage(dynamicLocale(player).get("vote.extremity-invalid.difficulty"));
                    return;
                }

                if(res == -1){
                    if(diff.initiator == player || player.admin){
                        String key = player.admin ? "vote.extremity-cancelled-admin.difficulty" : "vote.extremity-cancelled.difficulty";
                        Groups.player.each(p ->
                            Call.sendMessage(
                                Strings.format(
                                    dynamicLocale(p).get(key),
                                    player.coloredName()
                                )
                            )
                        );

                        diff.quit();
                    }else player.sendMessage(dynamicLocale(player).get("cmd.extremity-insufficient"));

                    return;
                }

                if(res == 2){
                    if(player.admin){
                        Groups.player.each(p ->
                            Call.sendMessage(
                                Strings.format(
                                    dynamicLocale(p).get("vote.extremity-skip.difficulty"),
                                    player.coloredName()
                                )
                            )
                        );

                        diff.success();
                    }else player.sendMessage(dynamicLocale(player).get("cmd.extremity-insufficient"));

                    return;
                }

                diff.vote(player, res == 1);
            });
            netServer.clientCommands.<Player>register("modifiers", "[page]", "Returns a modifier list page.", (args, player) -> {
                int page = 1;
                if(args.length > 0)
                    page = Mathf.clamp(Strings.parseInt(args[0], 1), 1, SettingCache.List.pages);

                int offset = 5 * (page - 1), limit = Math.min(5, SettingCache.List.entries - offset);
                StringBuilder sb = new StringBuilder();

                I18NBundle locale = dynamicLocale(player);
                sb.append(locale.get("list.extremity-modifiers.begin"));
                for(int i = offset; i < offset + limit; i++){
                    SettingCache.List entry = SettingCache.List.all.get(i);

                    sb.append("\n[lightgray](id: ").append(i).append(") >[] ").append(entry.setting.get() ? "[lime]" : "[scarlet]").append(locale.get(entry.local + ".name")).append("[]");

                    String desc = locale.get(entry.local + ".description").replaceAll("\n", "\n  [slate][?]:[] ");
                    sb.append("\n  [slate][?]:[] ").append(desc).append("\n");
                }
                sb.append(Strings.format(locale.get("list.extremity-modifiers.end"), page, SettingCache.List.pages));

                player.sendMessage(sb.toString());
            });
            netServer.clientCommands.<Player>register("modifier", "<id/vote>", "Starts a vote to change the modifier state", (args, player) -> {
                if(Manager.lockSettings){
                    player.sendMessage(dynamicLocale(player).get("cmd.extremity-modifier.locked"));
                    return;
                }

                if(modi == null || modi.completed){
                    if(!Strings.canParsePositiveInt(args[0]) || Strings.parseInt(args[0]) > SettingCache.List.all.size){
                        player.sendMessage(dynamicLocale(player).get("vote.extremity-invalid.modifier"));
                        return;
                    }

                    SettingCache.List entry = SettingCache.List.all.get(Strings.parseInt(args[0]));
                    modi = new VoteSession(player){{
                        onVote = (p, vote) -> {
                            String string = (vote ? "vote.extremity-voted.modifier" : "vote.extremity-voted-against.modifier") + (entry.setting.get() ? "-disable" : "");
                            Groups.player.each(pl ->
                                pl.sendMessage(
                                    Strings.format(
                                        dynamicLocale(pl).get(string),
                                        p.coloredName(), dynamicLocale(pl).get(entry.local + ".name"), votes(), votesRequired()
                                    )
                                )
                            );
                        };
                        onTime = () -> Groups.player.each(pl ->
                            pl.sendMessage(
                                Strings.format(
                                    dynamicLocale(pl).get("vote.extremity-time.modifier"),
                                    time
                                )
                            )
                        );
                        onFailure = () -> Groups.player.each(pl ->
                            pl.sendMessage(
                                dynamicLocale(pl).get("vote.extremity-fail.modifier")
                            )
                        );
                        onSuccess = () -> {
                            Groups.player.each(pl ->
                                pl.sendMessage(
                                    Strings.format(
                                        dynamicLocale(pl).get(entry.setting.get() ? "vote.extremity-success.modifier-disable" : "vote.extremity-success.modifier-enable"),
                                        dynamicLocale(pl).get(entry.local + ".name")
                                    )
                                )
                            );

                            Core.settings.put(entry.local.replaceAll("setting.", ""), !entry.setting.get());
                            SettingCache.fetch();
                        };
                    }};
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
                    player.sendMessage(dynamicLocale(player).get("vote.extremity-invalid.modifier"));
                    return;
                }

                if(res == -1){
                    if(modi.initiator == player || player.admin){
                        String key = player.admin ? "vote.extremity-cancelled-admin.modifier" : "vote.extremity-cancelled.modifier";
                        Groups.player.each(p ->
                            Call.sendMessage(
                                Strings.format(
                                    dynamicLocale(p).get(key),
                                    player.coloredName()
                                )
                            )
                        );

                        modi.quit();
                    }else player.sendMessage(dynamicLocale(player).get("cmd.extremity-insufficient"));

                    return;
                }

                if(res == 2){
                    if(player.admin){
                        Groups.player.each(p ->
                            Call.sendMessage(
                                Strings.format(
                                    dynamicLocale(p).get("vote.extremity-skip.modifier"),
                                    player.coloredName()
                                )
                            )
                        );

                        modi.success();
                    }else player.sendMessage(dynamicLocale(player).get("cmd.extremity-insufficient"));

                    return;
                }

                modi.vote(player, res == 1);
            });
            netServer.clientCommands.<Player>register("unitdex", "[unitdex]", "Allows admins to edit the unitdex ingame", (args, player) -> {
                if(!player.admin){
                    player.sendMessage(dynamicLocale(player).get("cmd.extremity-insufficient"));
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

                    player.sendMessage(dynamicLocale(player).get("cmd.extremity-reset"));
                    return;
                }

                if(res == 1){
                    Manager.loadRaw(dex.toString(), "dex");
                    dex.setLength(0);

                    player.sendMessage(dynamicLocale(player).get("cmd.extremity-submitted"));
                    return;
                }

                dex.append(args[0]);
                player.sendMessage(dynamicLocale(player).get("cmd.extremity-ready"));
            });
        });
    }

    public void finishLoading(){
        var self = mods.getMod(getClass());
        if(self != null && self.meta != null){
            DedicatedBundles.init(self);
            Manager.main = self;
        }

        // if it cannot find its own class, something is seriously wrong...
        // though crashing / quitting would be quite over the top, so just log and ignore
        if(Manager.modVersion().equals("err"))
            Log.infoTag("Extremity","Failed to fetch the version.");
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("difficulty","[int]",  "Sets the difficulty scale that Extremity will use", i -> {
            if(i.length >= 1){
                SettingCache.difficulty = Mathf.clamp(Strings.parseInt(i[0], 3), 0, highestScale);
                Log.info(Strings.format(Core.bundle.get("con.extremity-difficulty-set"), SettingCache.difficulty));
            }else Log.info(Strings.format(Core.bundle.get("con.extremity-difficulty"), SettingCache.difficulty));
        });
        handler.register("unitdex", "[dex]", "Sets or prints the current unitdex", i -> {
            if(i.length >= 1){
                Manager.loadRaw(i[0], "dex");
                Log.info(Core.bundle.get("con.extremity-applied"));
            }else Log.info(Manager.packDex());
        });
        handler.register("resetdex", "Re-runs the indexing task to restore default configs", i -> Manager.reload());
    }
}
