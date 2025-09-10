package extremity;

import arc.*;
import arc.graphics.Color;
import arc.scene.event.*;
import arc.scene.style.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static mindustry.Vars.*;

public class UnitdexEditor{
    public static Seq<String> customdexes = new Seq<>();
    public static BaseDialog unitdex = new BaseDialog("@extremity-unitdex"), dexedit = new BaseDialog("extremity-dexedit");
    public static Table buttonsRight = new Table(), buttonsLeft = new Table(), buttonsBottom = new Table(), editor = new Table();
    public static String save = "newdex";
    public static int size = 60;

    public static Cell<ScrollPane> canvas;

    public static void init(){
        customdexes.addAll(Core.settings.getJson("extremity-customdexes", Seq.class, String.class, Seq::new));

        unitdex.addCloseButton();
        unitdex.fill(t ->{
            t.name = "extremity-unitdex-elements";
            t.top().right().marginTop(30f).add(buttonsRight);
            t.fill(sub -> sub.top().left().marginTop(30f).add(buttonsLeft));
            t.fill(sub -> sub.bottom().left().add(buttonsBottom));
            t.fill(sub ->
                sub.center().top().marginTop(40f).marginBottom(90f).table(entry ->
                    canvas = entry.pane(editor).size(entry.getWidth(), entry.getWidth()).scrollX(false).growX()
                )
            );
        });
    }

    public static void show(){
        rebuild();
        unitdex.show();
    }

    public static void rebuild(){
        buttonsRight.reset();
        buttonsLeft.reset();
        buttonsBottom.reset();

        editor.reset();
        editor.setWidth(Core.graphics.getWidth());

        if(Manager.spawns.size > 0){
            int count = 0;
            for(var data : Manager.spawns){
                UnitType unit = data.key;
                Seq<UnitType> vars = data.value;

                editor.table(Tex.button, s -> {
                    s.table(Tex.underline, e-> {
                        Image img = new Image(Icon.trash);
                        img.clicked(() ->{
                            Manager.spawns.remove(unit);
                            rebuild();
                        });

                        e.add(img).size(size / 3f).touchable(Touchable.enabled).scaling(Scaling.bounded).padLeft(2f);
                    }).growX().row();
                    s.table( u -> {
                        Label txt = new Label(Strings.format("@ @", Iconc.pencil, unit.localizedName));
                        txt.setWrap(true);
                        txt.fillParent = true;
                        txt.clicked(() -> editEntry(unit));

                        Image img = new Image(unit.uiIcon);
                        img.clicked(() -> editEntry(unit));

                        u.add(img).scaling(Scaling.bounded).size(size * 0.85f).touchable(Touchable.enabled);
                        u.add(txt).padLeft(0f).touchable(Touchable.enabled).grow();
                    }).growX().row();
                    s.table(Tex.underline, e -> {
                        int ct = 0;
                        for(UnitType child : vars){
                            Image img = new Image(child.uiIcon);
                            img.clicked(() -> editValues(unit, child));

                            e.add(img).scaling(Scaling.bounded).size(size * 0.85f).touchable(Touchable.enabled).padRight(0f).left();
                            if(++ct >= 5){
                                e.row();
                                ct = 0;
                            }
                        }

                        Image add = new Image(Icon.add);
                        add.clicked(() -> editValues(unit, null));
                        e.add(add).scaling(Scaling.bounded).size(size * 0.85f).touchable(Touchable.enabled).padRight(0f).left();
                    }).growX().row();
                    s.row();
                }).pad(1.5f).growX().width(canvas.maxWidth());

                if(++count >= Core.settings.getInt("extremity-rows", 1)){
                    editor.row();
                    count = 0;
                }
            }
        }else editor.table(tb -> tb.label(() -> "@empty").growX().center().style(Styles.outlineLabel).pad(20f)).width(canvas.maxWidth()).row();

        if(hasSpace()){
            editor.table(Tex.button, s -> {
                s.table(u -> {
                    Label txt = new Label(Strings.format("@ @", Iconc.add, Core.bundle.get("extremity-new-entry")));
                    txt.setWrap(true);
                    txt.fillParent = true;
                    txt.clicked(UnitdexEditor::addEntry);

                    Image img = new Image(Icon.units);
                    img.clicked(UnitdexEditor::addEntry);

                    u.add(txt).padLeft(0f).touchable(Touchable.enabled).grow();
                    u.add(img).scaling(Scaling.bounded).size(size * 0.85f).touchable(Touchable.enabled).padRight(0f).left();
                }).growX().row();
            }).pad(1.5f).growX().width(canvas.maxWidth());
        }

        buttonsRight.button("@extremity-reset", () -> {
            Manager.reload();
            rebuild();
        }).width(120f).row();
        buttonsRight.button("@extremity-clear", () ->
            ui.showConfirm("@confirm", "@extremity-confirm-clear", () -> {
                Manager.spawns.clear();
                rebuild();
            })
        ).width(120f).row();

        buttonsLeft.button("@extremity-save", () -> {
            Table table = new Table();
            save = "newdex";

            table.add(Strings.format("@: @", Core.bundle.get("extremity-current-name"), save));

            dexedit.reset();
            dexedit.fill(t ->
                t.center().bottom().marginBottom(55f).button("@extremity-save", () -> {
                    customdexes.addUnique(save);
                    Core.settings.putJson("extremity-customdexes", String.class, customdexes);
                    Core.settings.put("extremity-customdex-" + save, Manager.packDex());

                    ui.showInfoFade(Strings.format("@ @", Core.bundle.get("extremity-saved"), save));
                    dexedit.hide();
                }).width(180f)
            );

            dexedit.row();
            dexedit.fill(t -> {
                t.center().add("@extremity-save-name").row();
                t.field("", in -> {
                    String name = in.replaceAll("[^a-zA-Z0-9]", "");
                    save = name.isEmpty() ? "newdex" : name;

                    table.reset();
                    table.add(Strings.format("@: @", Core.bundle.get("extremity-current-name"), save)).row();

                    if(customdexes.contains(save))
                        table.add("@extremity-overwrite").color(Color.scarlet).row();
                }).width(380f).row();
                t.add(table).row();
            });

            dexedit.fill(t -> t.center().bottom().button("@back", Icon.left, () -> dexedit.hide()).width(180f));
            dexedit.show();
        }).width(180f).row();
        buttonsLeft.button("@extremity-load", () -> {
            dexedit.reset();

            dexedit.fill(t -> {
                if(customdexes.isEmpty()){
                    t.center().add("@extremity-no-saves").width(220f);
                }else{
                    for(var string : customdexes){
                        if(string.isEmpty()) continue;

                        t.button(string, () -> {
                            Manager.loadDex(string);
                            rebuild();

                            ui.showInfoFade(Strings.format("@ @", Core.bundle.get("extremity-loaded"), string));
                            dexedit.hide();
                        }).width(50f + (10f * string.length()));
                    }
                }
            });

            dexedit.fill(t -> t.center().top().marginTop(40f).add("@extremity-select").width(220f));
            dexedit.fill(t -> t.center().bottom().button("@back", Icon.left, () -> dexedit.hide()).width(180f));

            dexedit.show();
        }).width(180f).row();
        buttonsLeft.button("@extremity-remove", () -> {
            dexedit.reset();

            dexedit.fill(t -> {
                if(customdexes.isEmpty())
                    t.center().add("@extremity-no-saves").width(220f);
                else{
                    for(var string : customdexes){
                        if(string.isEmpty()) continue;

                        t.button(string, () -> {
                            customdexes.remove(string);
                            Core.settings.putJson("extremity-customdexes", String.class, customdexes);

                            Core.settings.remove("extremity-customdex-" + string);

                            ui.showInfoFade("@extremity-removed");
                            dexedit.hide();
                        }).width(50f + (10f * string.length()));
                    }
                }
            });

            dexedit.fill(t -> t.center().top().marginTop(40f).add("@extremity-select").width(220f));
            dexedit.fill(t -> t.center().bottom().button("@back", Icon.left, () -> dexedit.hide()).width(180f));

            dexedit.show();
        }).width(180f).row();

        buttonsBottom.button("@extremity-export", () -> {
            Core.app.setClipboardText(Manager.packDex());

            ui.showInfoFade("@extremity-exported");
        }).width(180f).row();
        buttonsBottom.button("@extremity-import", () -> {
            Manager.loadRaw(Core.app.getClipboardText(), "clipboard");
            rebuild();

            ui.showInfoFade("@extremity-imported");
        }).width(180f).row();
    }

    static boolean hasSpace(){
        for(UnitType u : content.units())
            if(!Manager.spawns.containsKey(u))
                return true;
        return false;
    }

    static void addEntry(){
        BaseDialog dialog = new BaseDialog("");

        dialog.cont.pane(t -> {
            t.defaults().size(280f, 64f).pad(2f).scrollX(false);
            int c = 0;
            for(UnitType item : content.units()){
                if(Manager.spawns.containsKey(item)) continue;

                StringBuilder tip = new StringBuilder(item.localizedName);
                if(item.isModded()) tip.append("\n(").append(item.minfo.mod.meta.displayName).append(")");

                t.button(new TextureRegionDrawable(item.uiIcon), Styles.flati, iconLarge, () -> {
                    Manager.spawns.put(item, new Seq<>());

                    rebuild();
                    dialog.hide();
                }).size(iconXLarge).scaling(Scaling.bounded).tooltip(tip.toString());

                if(++c % 6 == 0) t.row();
            }
        });

        dialog.addCloseButton();
        dialog.show();
    }

    static void editEntry(UnitType entry){
        BaseDialog dialog = new BaseDialog("");
        boolean advanced = Core.input.keyDown(Binding.boost);

        dialog.cont.pane(t -> {
            t.defaults().size(280f, 64f).pad(2f).scrollX(false);
            int c = 0;
            for(UnitType item : content.units()){
                if(!advanced && (!item.unlockedNow() || item.isHidden() || !item.logicControllable)) continue;

                StringBuilder tip = new StringBuilder(item.localizedName);
                if(item.isModded()) tip.append("\n(").append(item.minfo.mod.meta.displayName).append(")");

                t.button(new TextureRegionDrawable(item.uiIcon), Styles.flati, iconLarge, () -> {
                    Seq<UnitType> res = Manager.spawns.get(entry);

                    Manager.spawns.remove(entry);
                    Manager.spawns.put(item, res);

                    rebuild();
                    dialog.hide();
                }).size(iconXLarge).scaling(Scaling.bounded).tooltip(tip.toString() );

                if(++c % 6 == 0) t.row();
            }
        });

        dialog.addCloseButton();
        dialog.show();
    }


    static void editValues(UnitType entry, UnitType value){
        BaseDialog dialog = new BaseDialog("");
        boolean advanced = Core.input.keyDown(Binding.boost);

        dialog.cont.pane(t -> {
            t.defaults().size(280f, 64f).pad(2f).scrollX(false);
            int c = 0;
            for(UnitType item : content.units()){
                if(!advanced && (!item.unlockedNow() || item.isHidden() || !item.logicControllable)) continue;

                StringBuilder tip = new StringBuilder(item.localizedName);
                if(item.isModded()) tip.append("\n(").append(item.minfo.mod.meta.displayName).append(")");

                t.button(new TextureRegionDrawable(item.uiIcon), Styles.flati, iconLarge, () -> {
                    Seq<UnitType> res = Manager.spawns.get(entry);
                    res.add(item);
                    res.remove(value);

                    Manager.spawns.put(entry, res);

                    rebuild();
                    dialog.hide();
                }).size(iconXLarge).scaling(Scaling.bounded).tooltip(tip.toString());

                if(++c % 6 == 0) t.row();
            }

            t.button(Icon.cancel, Styles.flati, iconLarge, () -> {
                Manager.spawns.get(entry).remove(value);

                rebuild();
                dialog.hide();
            }).size(iconXLarge).scaling(Scaling.bounded);
        });

        dialog.addCloseButton();
        dialog.show();
    }
}
