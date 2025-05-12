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
    public Session vote;

    public Extremity(){
        Manager.setup();

        Events.on(EventType.ClientLoadEvent.class, e -> {
            UnitdexEditor.init();
            addCommands();

            if(!Core.settings.getBool("extremity-init", false)){
                ui.showStartupInfo("@extremity-info");
                Core.settings.put("extremity-init", true);
            }

            ui.settings.addCategory(Core.bundle.get("extremity-category"), Icon.book, t ->{
                DynamicSettings.DynamicTable table = new DynamicSettings.DynamicTable();
                table.addDefaults = () -> !Core.settings.getBool("extremity-beyond", false);

                table.sliderPref("extremity-difficulty", 0, 0, 3, s -> Core.bundle.get("extremity-diff" + s), () -> !Core.settings.getBool("extremity-beyond"));
                table.sliderPref("extremity-difficulty", 4, 4, 7, s -> Core.bundle.get("extremity-diff" + s), () -> Core.settings.getBool("extremity-beyond"));
                table.confirmPref("extremity-beyond", false, c ->
                    ui.showConfirm("@confirm", "@setting.extremity-beyond.confirm", () -> table.rebuild("extremity-beyond", true)
                ), () -> !Core.settings.getBool("extremity-beyond"));
                table.checkPref("extremity-pvp", false, null, null);
                table.sliderPref("extremity-rows", 1, 1, 3, 1, s -> s + "", null);

                t.add(table);

                Events.on(Manager.ExtremityLoseEvent.class, ev -> {
                    // make sure it sets the difficulty to default
                    Core.settings.put("extremity-beyond", false);
                    table.rebuild("extremity-difficulty", 0);
                });
            });
            ui.menufrag.addButton("@extremity-unitdex-button", Icon.pencil, UnitdexEditor::show);

            Events.on(EventType.ClientChatEvent.class, ev -> {
                if(!net.client()) return;

                if(ev.message.equals("/extremity"))
                    Tutorial.start(player);
            });

            Tutorial.setupClient();
        });

        Events.on(EventType.ServerLoadEvent.class, e ->{
            StringBuilder dex = new StringBuilder();
            addCommands();

            netServer.clientCommands.<Player>register("difficulty", "[difficulty]", "Changes the Extremity difficulty level (0-3), or prints the current difficulty to chat", (args, player) -> {
                if(args.length < 1)
                    player.sendMessage(Strings.format("[accent][Extremity] Current difficulty level is @ ([orange]@[])", getName(Manager.difficulty), Manager.difficulty));

                if(vote != null && !vote.completed){
                    player.sendMessage("[accent][Extremity] [scarlet]A vote is already in progress, please wait before it finishes!");
                    return;
                }

                vote = new Session(player, Mathf.clamp(Strings.parseInt(args[0]), 0, player.admin || Core.settings.getBool("extremity-beyond-public", false) ? 7 : 3));
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

                        Manager.difficulty = vote.difficulty;
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

            Tutorial.setupServer();
        });
    }

    // adds the universal local & dedicated server commands
    public static void addCommands(){
        netServer.clientCommands.<Player>register("extremity", "Shows the Extremity's tutorial", (args, player) -> {
            // this will be handled clientside
            if(Manager.players.contains(player)) return;

            Tutorial.start(player);
        });
    }

    public static String getName(int difficulty){
        return switch(difficulty){
            case 0 -> "[lime]Vanilla[]";
            case 1 -> "[yellow]Mediocrity[]";
            case 2 -> "[orange]Fatality[]";
            case 3 -> "[scarlet]True Extremity[]";
            case 4 -> "[#c40004]High Mediocrity[] [scarlet](Beyond Mode)[]";
            case 5 -> "[#cf0078]Prime Fatality[] [scarlet](Beyond Mode)[]";
            case 6 -> "[#a000ba]Peak Extremity[] [scarlet](Beyond Mode)[]";
            case 7 -> "[#45015f]Real Insanity[] [scarlet](Beyond Mode)[]";
            default -> "[stat]Error[]";
        };
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("difficulty","[int]",  "0 Vanilla, 1 Mediocrity, 2 Fatality, 3 True Extremity", i -> {
            if(i.length >= 1){
                Manager.difficulty = Mathf.clamp(Strings.parseInt(i[0], 3), 0, 7);
                Log.info(Strings.format("Set difficulty to @", Strings.stripColors(getName(Manager.difficulty))));
            }else Log.info(Strings.format("Current difficulty is @", Strings.stripColors(getName(Manager.difficulty))));
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
