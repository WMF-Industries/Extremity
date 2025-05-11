package extremity.utils;

import arc.*;
import arc.struct.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;
import mindustry.ui.*;

import static mindustry.Vars.ui;

public class Tutorial{
    public static Seq<Content> localTutorial = new Seq<>(), tutorial = new Seq<>();
    public static ObjectIntMap<Player> pointer = new ObjectIntMap<>();
    public static final int pageAmount = 7;
    static int id, end;

    static String close = "[scarlet]\uE815 Close", next = "[lime]\uE83A Next", prev = "[lime]\uE825 Previous";

    // autogenerates the entries so i wouldn't have to bother doing it twice
    public static void setupClient(){
        for(int i = 1; i <= pageAmount; i++)
            localTutorial.add(new Content(Core.bundle.get("extremity-tutorial" + i + "-title"), Core.bundle.get("extremity-tutorial" + i)));

        setupServer();
    }

    public static void setupServer(){
        Events.on(EventType.PlayerLeave.class, e -> pointer.remove(e.player));

        tutorial.addAll(
            new Content("Welcome!", "[accent]Welcome to Extremity's Tutorial[]\nWe'll shortly cover all the things added by Extremity on every level of difficulty\n\nIf a difficulty level has no tutorial entries, it doesn't add any new mechanics, only scales them up!"),
            new Content("[yellow]Mediocrity[]", "Being the lowest difficulty level, [yellow]Mediocrity[] introduces quite a few new mechanics\n\nDying enemy units spawn other units according to the provided unitdex\nWeathers affect buildings:\nSandstorms slow down conveyors\nSnow slows down every building\nRain damages buildings over time unless they're under \uF898Force Projectors"),
            new Content("[orange]Fatality[]", "[orange]Fatality[] raises the bar by scaling up the previous mechanics & adding a few new ones\n\nEnemy units now get the \uF678Fast status effect\nAlly units get \uF7A6Corroded if \uF7B2Wet\nTurrets require coolant to operate, without it, they will explode after a few shots\nBuildings near the drop zone get damaged each wave"),
            new Content("[scarlet]True Extremity[]", "As the hardest fair difficulty level, [scarlet]True Extremity[] brings a real challenge\n\nAlly units \uF7B3Slow down\nEnemy \uF7A9Guradians give \uF7AAShielded to all nearby enemies upon death"),
            new Content("[#c40004]High Mediocrity[] [scarlet](Beyond Mode)[]", "Entering [scarlet]Beyond Mode[], [#c40004]High Mediocrity[] brings victory chances quite low\n\nAlly bullets have lifetime damage scaling, making them weak at first\n\uF898Force Projectors no longer protect against rain\n[scarlet]Upon losing a sector, the entire campaign of the current planet gets reset"),
            new Content("[#cf0078]Prime Fatality[] [scarlet](Beyond Mode)[]", "[#cf0078]Prime Fatality[] introduces the final, most cruel mechanic\n\nLosing a single core causes every other core to explode"),
            new Content("The End", "[accent]Congrats![]\nYou've reached the end of the tutorial, well done!\nHope this brief content introduction was clear enough\n\n[lightgray]Remember that the mod allows you to customize certain factors, such as the unitdexes![]\n\nEnjoy the mod!")
        );

        id = Menus.registerMenu((player, option) -> {
            int i = pointer.get(player, 0), size = player.isLocal() ? localTutorial.size : tutorial.size;

            if(i > 0 && option == 0){
                --i;
                pointer.increment(player, -1);
                show(player, i, id);
                return;
            }

            if(option == (i == 0 ? 1 : 2)){
                ++i;
                if(i < size){
                    pointer.increment(player);
                    show(player, i, id);
                }else show(player, i, end);
            }
        });

        end = Menus.registerMenu((player, option) -> {
            if(option == 0){
                pointer.increment(player, -1);
                show(player, -1, id);
            }

            pointer.remove(player);
        });
    }

    public static void start(Player player){
        pointer.put(player, 0);
        show(player, 0, id);
    }

    public static void show(Player player, int context, int id){
        if(context < 0)
            context = player.isLocal() ? localTutorial.size - 1 : tutorial.size - 1;

        if(player.isLocal())
            ui.showMenu(localTutorial.get(context).title, localTutorial.get(context).content, options(context), opt -> Menus.menuChoose(player, id, opt));
        else Call.menu(player.con, id, tutorial.get(context).title, tutorial.get(context).content, options(context));
    }

    public static String[][] options(int context){
        return context == 0 ? new String[][]{{close, next}} : context >= tutorial.size - 1 ? new String[][]{{prev, close}} : new String[][]{{prev, close, next}};
    }

    public static class Content{
        final String title;
        final String content;

        Content(String title, String content){
            this.title = title;
            this.content = content;
        }
    }
}
