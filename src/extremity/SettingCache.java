package extremity;

import arc.*;
import arc.func.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;

import java.nio.*;

import static extremity.Manager.*;
import static mindustry.Vars.*;

public class SettingCache{
    public static int difficulty;
    public static boolean resetCampaign, allowPvp, fastEnemies, slowAllies, damageTurrets, turretExplosions, killCores, affectBuildings, damageBuildings, weatherEffects, manageBullets, extraInvasions, extendedZones, guardianShielding, noDamageTaken, noUnitDamage;

    public static boolean needsSync, active;

    static ByteBuffer writeBuffer, readBuffer;

    /** Extra bytes for non-boolean settings */
    static final int extraBytes = 1;

    public static void fetch(){
        Log.info("Setting cache updated");

        difficulty = Core.settings.getInt("extremity-difficulty", 0);
        pack(
            allowPvp = Core.settings.getBool("extremity-pvp", false),
            damageTurrets = Core.settings.getBool("extremity-turrets", false),
            fastEnemies = Core.settings.getBool("extremity-enemies", false),
            slowAllies = Core.settings.getBool("extremity-allies", false),
            resetCampaign = Core.settings.getBool("extremity-campaign", false),
            turretExplosions =  Core.settings.getBool("extremity-explosions", false),
            killCores = Core.settings.getBool("extremity-cores", false),
            affectBuildings = Core.settings.getBool("extremity-buildings", false),
            damageBuildings = Core.settings.getBool("extremity-damage", false),
            weatherEffects = Core.settings.getBool("extremity-weather", false),
            manageBullets = Core.settings.getBool("extremity-bullets", false),
            extraInvasions = Core.settings.getBool("extremity-invasions", false),
            extendedZones = Core.settings.getBool("extremity-zones", false),
            guardianShielding = Core.settings.getBool("extremity-guardian", false),
            noDamageTaken = Core.settings.getBool("extremity-taken", false),
            noUnitDamage = Core.settings.getBool("extremity-units", false)
        );
        writeBuffer.put((byte) difficulty);

        active = active || difficulty > 0;

        Manager.syncAll();
        needsSync = false;
    }

    // this only scales to 64 boolean settings, but that's more than i'll ever add so that's alright
    public static void pack(boolean... args){
        long packed = 0;

        int bytes = 1 + (args.length / 8);
        if(bytes % 2 != 0)
            bytes = bytes > 4 ? 8 : 4;

        byte packSize = (byte) bytes++;
        for(int i = 0; i < args.length; i++)
            if(args[i])
                packed |= 1L << i;

        active = packed > 0;
        writeBuffer = ByteBuffer.allocate(extraBytes + bytes);

        writeBuffer.put(packSize);
        switch(packSize){
            case 1 -> writeBuffer.put((byte) packed);
            case 2 -> writeBuffer.putShort((short) packed);
            case 3, 4 -> writeBuffer.putInt((int) packed);
            default -> writeBuffer.putLong(packed);
        }
    }

    public static void unpack(Boolc... args){
        long val = switch(readBuffer.get()){
            case 1 -> readBuffer.get();
            case 2 -> readBuffer.getShort();
            case 3, 4 -> readBuffer.getInt();
            default -> readBuffer.getLong();
        };

        for(int i = 0; i < args.length; i++)
            args[i].get((val & (1L << i)) != 0);
    }

    public static void apply(byte[] data){
        readBuffer = ByteBuffer.wrap(data);

        unpack(
            b -> allowPvp = b,
            b -> damageTurrets = b,
            b -> fastEnemies = b,
            b -> slowAllies = b,
            b -> resetCampaign = b,
            b -> turretExplosions = b,
            b -> killCores = b,
            b -> affectBuildings = b,
            b -> damageBuildings = b,
            b -> weatherEffects = b,
            b -> manageBullets = b,
            b -> extraInvasions = b,
            b -> extendedZones = b,
            b -> guardianShielding = b,
            b -> noDamageTaken = b,
            b -> noUnitDamage = b
        );
        difficulty = readBuffer.get();

        ui.showInfoFade(Core.bundle.get("extremity-success"), 3f);
        Call.serverPacketReliable("extremity-confirm", modVersion());
    }

    public enum List{
        damageTurrets("setting.extremity-pvp", () -> SettingCache.damageTurrets),
        fastEnemies("setting.extremity-enemies", () -> SettingCache.fastEnemies),
        slowAllies("setting.extremity-allies", () -> SettingCache.slowAllies),
        turrerExplosions("setting.extremity-explosions", () -> SettingCache.turretExplosions),
        killCores("setting.extremity-cores", () -> SettingCache.killCores),
        weatherEffects("setting.extremity-weather", () -> SettingCache.weatherEffects),
        affectBuildings("setting.extremity-buildings", () -> SettingCache.affectBuildings),
        manageBullets("setting.extremity-bullets", () -> SettingCache.manageBullets),
        extendedZones("setting.extremity-zones", () -> SettingCache.extendedZones),
        guardianShielding("setting.extremity-guardian", () -> SettingCache.guardianShielding),
        noDamage("setting.extremity-taken", () -> SettingCache.noDamageTaken),
        noUnitDamage("setting.extremity-units", () -> SettingCache.noUnitDamage);

        public final String local;
        public final Boolp setting;

        List(String loc, Boolp set){
            this.local = loc;
            this.setting = set;
        }

        public static final Seq<List> all = Seq.with(values());
        public static final int pages = (all.size / 5) + (all.size % 5 == 0 ? 0 : 1);
    }
}
