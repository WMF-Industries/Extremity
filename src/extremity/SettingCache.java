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
    public static boolean resetCampaign, allowPvp, fastEnemies, slowAllies, damageTurrets, turretExplosions, killCores, affectBuildings, damageBuildings, weatherEffects, manageBullets, extraInvasions, extendedZones, guardianShielding;

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
            guardianShielding = Core.settings.getBool("extremity-guardian", false)
        );
        writeBuffer.put((byte) difficulty);

        active = active || difficulty > 0;

        Manager.syncAll();
        needsSync = false;
    }

    public static void pack(boolean... args){
        int packed = 0, bytes = 1 + Math.min(3, args.length / 8);
        if(bytes == 3)
            ++bytes;

        byte packSize = (byte) bytes++;
        for(int i = 0; i < args.length; i++)
            if(args[i])
                packed |= 1 << i;

        active = packed > 0;
        writeBuffer = ByteBuffer.allocate(extraBytes + bytes);

        writeBuffer.put(packSize);
        switch(packSize){
            case 1 -> writeBuffer.put((byte) packed);
            case 2 -> writeBuffer.putShort((short) packed);
            default -> writeBuffer.putInt(packed);
        }
    }

    public static void unpack(boolean... args){
        int val = switch(readBuffer.get()){
            case 1 -> readBuffer.get();
            case 2 -> readBuffer.getShort();
            default -> readBuffer.getInt();
        };

        for(int i = 0; i < args.length; i++)
            args[i] = (val & (1 << i)) != 0;
    }

    public static void apply(byte[] data){
        readBuffer = ByteBuffer.wrap(data);

        unpack(
            allowPvp,
            damageTurrets,
            fastEnemies,
            slowAllies,
            resetCampaign,
            turretExplosions,
            killCores,
            affectBuildings,
            damageBuildings,
            weatherEffects,
            manageBullets,
            extraInvasions,
            extendedZones,
            guardianShielding
        );
        difficulty = readBuffer.get();

        ui.showInfoFade(Core.bundle.get("extremity-success"), 3f);
        Call.serverPacketReliable("extremity-confirm", modVersion);
    }

    public enum List{
        damageTurrets("setting.extremity-pvp", () -> SettingCache.damageTurrets),
        fastEnemies("setting.extremity-enemies", () -> SettingCache.fastEnemies),
        slowAllies("setting.extremity-allies", () -> SettingCache.slowAllies),
        turrerExplosions("setting.extremity-explosions", () -> SettingCache.turretExplosions),
        killCores("setting.extremity-cores", () -> SettingCache.killCores),
        affectBuildings("setting.extremity-buildings", () -> SettingCache.affectBuildings),
        weatherEffects("setting.extremity-weather", () -> SettingCache.weatherEffects),
        manageBullets("setting.extremity-bullets", () -> SettingCache.manageBullets),
        extendedZones("setting.extremity-zones", () -> SettingCache.extendedZones),
        guardianShielding("setting.extremity-guardian", () -> SettingCache.guardianShielding);

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
