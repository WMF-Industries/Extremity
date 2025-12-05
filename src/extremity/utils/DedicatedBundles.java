package extremity.utils;

import arc.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.gen.*;
import mindustry.mod.*;

import java.util.*;

// bundles are light enough, preload all of them
public class DedicatedBundles{
    public static final String srcDir = "bundles";
    public static ObjectMap<String, I18NBundle> bundles = new ObjectMap<>();

    public static void init(Mods.LoadedMod self){
        self.root.child(srcDir).walk(fi -> {
            if(!fi.nameWithoutExtension().equals("bundle"))
                generate(fi);
        });

        Log.info("Successfully loaded " + bundles.size + " additional Extremity bundles");
    }

    public static void generate(Fi bundle){
        String locale = bundle.nameWithoutExtension().replaceFirst("bundle_", "");

        Locale loc;
        if(locale.contains("_")){
            String[] split = locale.split("_");
            loc = new Locale(split[0], split[1]);
        }else loc = new Locale(locale);

        I18NBundle gen = I18NBundle.createEmptyBundle();
        Reflect.set(gen, "parent", Core.bundle);
        Reflect.set(gen, "locale", loc);
        Reflect.set(gen, "formatter", new TextFormatter(loc, true));

        try{
            PropertiesUtils.load(gen.getProperties(), bundle.reader());
        }catch(Throwable e){
            Log.err("Error loading bundle: " + bundle + "/" + locale, e);
            return;
        }

        bundles.put(locale, gen);
    }

    public static I18NBundle dynamicLocale(Player player){
        if(player == null)
            return Core.bundle;
        return bundles.get(player.locale(), Core.bundle);
    }
}
