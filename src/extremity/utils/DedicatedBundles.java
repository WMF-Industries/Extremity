package extremity.utils;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.mod.*;

public class DedicatedBundles{
    public static ObjectMap<String, Fi> bundles = new ObjectMap<>();
    public static ObjectSet<String> locales = new ObjectSet<>();

    public static void init(Mods.LoadedMod self){
        if(self == null){
            Log.warn("Failed to load DedicatedBundles util");
            return;
        }

        Fi folder = self.root.child("bundles");
        if(folder.exists()){
            for(Fi file : folder.list()){
                if(file.name().startsWith("bundle") && file.extension().equals("properties")){
                    String name = file.nameWithoutExtension();
                    addLocale(name);
                    bundles.put(name, file);
                }
            }
        }
    }

    public static void addLocale(String name){
        locales.add(
            name.replace("bundle", "").replaceFirst("_", "")
        );
    }

    public static I18NBundle dynamicLocale(Player player){
        if(player == null){
            Log.warn("Invalid player object");
            return Core.bundle;
        }

        String loc = locales.contains(player.locale) ? "_" + player.locale : "";
        Fi bundle = bundles.get("bundle" + loc);

        // this shouldn't be possible, but i'll keep the code here just in case
        if(bundle == null){
            Log.warn("Failed to find bundle (" + player.locale + ")");
            return Core.bundle;
        }

        I18NBundle dynamic = I18NBundle.createEmptyBundle();
        Reflect.set(dynamic, "parent", Core.bundle);

        try{
            PropertiesUtils.load(dynamic.getProperties(), bundle.reader());
        }catch(Throwable e){
            Log.err("Error loading bundle: " + bundle + "/" + player.locale, e);
            dynamic = Core.bundle;
        }

        return dynamic;
    }
}
