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
        });

        Events.on(EventType.ServerLoadEvent.class, e ->{
            StringBuilder dex = new StringBuilder();

            netServer.clientCommands.<Player>register("difficulty", "<difficulty>", "Changes the Extremity difficulty level, accepts numbers from 0 to 3, higher being harder", (args, player) -> {
                if(vote != null && !vote.completed){
                    player.sendMessage("[accent][Extremity] [scarlet]A vote is already in progress, please wait before it finishes!");
                    return;
                }

                vote = new Session(player, Mathf.clamp(Strings.parseInt(args[0]), 0, 3));
            });
            netServer.clientCommands.<Player>register("diffvote", "<yes/no>", "Submits a vote for the difficulty changing session", (args, player) -> {
                if(vote == null || vote.completed){
                    player.sendMessage("[accent][Extremity] [scarlet]No vote session ongoing, please start one before voting!");
                    return;
                }

                if(args[0].equals("yes") || args[0].equals("no") || args[0].equals("y") || args[0].equals("n")){
                    vote.vote(player, args[0].equals("yes") || args[0].equals("y"));
                }else{
                    player.sendMessage("[accent][Extremity] [scarlet]Invalid parameter, please type yes (y) or no (n) in order to vote!");
                }
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

                if(args[0].equals("reset")){
                    dex.setLength(0);
                    Manager.reload();

                    player.sendMessage("[accent][Extremity] Reset unitdex to default values!");
                    return;
                }

                if(args[0].equals("submit")){
                    Manager.loadRaw(dex.toString(), "dex");
                    dex.setLength(0);

                    player.sendMessage("[accent][Extremity] Submitted new unitdex!");
                }

                dex.append(args[0]);
                player.sendMessage("[accent][Extremity] Saved unitdex data in cache\nType [accent]/unitdex submit[] to apply when ready!");
            });
        });
    }

    public static String getName(int difficulty){
        return switch(difficulty){
            case 0 -> "[lime]Vanilla[]";
            case 1 -> "[yellow]Mediocrity[]";
            case 2 -> "[orange]Fatality[]";
            case 3 -> "[scarlet]True Extremity[]";
            default -> "[stat]Error[]";
        };
    }

    @Override
    public void registerServerCommands(CommandHandler handler){
        handler.register("difficulty","[int]",  "0 Vanilla, 1 Mediocrity, 2 Fatality, 3 True Extremity", i -> {
            if(i.length >= 1){
                Manager.difficulty = Mathf.clamp(Strings.parseInt(i[0], 3), 0, 3);
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
