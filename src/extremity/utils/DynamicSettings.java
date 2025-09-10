package extremity.utils;

import arc.*;
import arc.func.*;
import arc.scene.*;
import arc.scene.event.*;
import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ui.*;
import mindustry.ui.dialogs.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class DynamicSettings extends SettingsMenuDialog{
    public static class DynamicTable extends Table{
        public Boolp addDefaults = () -> true;
        protected Seq<Setting> list = new Seq<>();

        public DynamicTable(){
            left();
        }

        public SliderSetting sliderPref(String name, int def, int min, int max, StringProcessor s, Boolp visibility){
            return sliderPref(name, def, min, max, 1, s, visibility);
        }

        public SliderSetting sliderPref(String name, int def, int min, int max, int step, StringProcessor s, Boolp visibility){
            SliderSetting res;
            list.add(res = new SliderSetting(name, def, min, max, step, s, visibility));
            settings.defaults(name, def);
            rebuild();
            return res;
        }

        /** checkPref without the rule changing part, rules are to be changed by invoking rebuild() at the Boolc with the name of the setting and Boolc state */
        public void confirmPref(String name, boolean def, Boolc changed, Boolp visibility){
            list.add(new ConfirmSetting(name, def, changed, visibility));
            settings.defaults(name, def);
            rebuild();
        }

        public void checkPref(String name, boolean def, Boolc changed, Boolp visibility){
            list.add(new CheckSetting(name, def, changed, visibility));
            settings.defaults(name, def);
            rebuild();
        }

        public void textPref(String name, String def, Cons<String> changed, Boolp visibility){
            list.add(new TextSetting(name, def, changed, visibility));
            settings.defaults(name, def);
            rebuild();
        }

        public void areaTextPref(String name, String def, Cons<String> changed, Boolp visibility){
            list.add(new AreaTextSetting(name, def, changed, visibility));
            settings.defaults(name, def);
            rebuild();
        }

        /** Changes a setting to the desired state, then rebuilds the dynamic table */
        public void rebuild(String setting, Object state){
            settings.put(setting, state);
            rebuild();
        }

        /** Rebuilds the dynamic table */
        public void rebuild(){
            clearChildren();

            for(Setting setting : list){
                if(setting.visibility.get())
                    setting.add(this);
            }

            if(addDefaults.get())
                button(bundle.get("settings.reset", "Reset to Defaults"), this::setDefaults).margin(14).width(240f).pad(6);
        }

        /** Resets all settings to default values */
        public void setDefaults(){
            setDefaults(null);
        }

        /** Resets all settings to default values, additionally runs the given task before rebuilding the table */
        public void setDefaults(Runnable task){
            for(Setting setting : list){
                if(setting.name == null || setting.title == null) continue;

                settings.remove(setting.name);
            }

            if(task != null)
                task.run();

            rebuild();
        }

        public abstract static class Setting{
            public String name;
            public String title;
            public @Nullable String description;
            public Boolp visibility;

            public Setting(String name, Boolp visibility){
                this.name = name;
                String winkey = "setting." + name + ".name.windows";
                title = OS.isWindows && bundle.has(winkey) ? bundle.get(winkey) : bundle.get("setting." + name + ".name", name);
                description = bundle.getOrNull("setting." + name + ".description");
                this.visibility = visibility == null ? () -> true : visibility;
            }

            public abstract void add(DynamicTable table);

            public void addDesc(Element elem){
                ui.addDescTooltip(elem, description);
            }
        }

        /** CheckSetting without the rule changing part */
        public static class ConfirmSetting extends Setting{
            boolean def;
            Boolc changed;

            public ConfirmSetting(String name, boolean def, Boolc changed, Boolp visibility){
                super(name, visibility);
                this.def = def;
                this.changed = changed;
            }

            @Override
            public void add(DynamicTable table){
                CheckBox box = new CheckBox(title);

                box.update(() -> box.setChecked(settings.getBool(name)));

                box.changed(() -> {
                    if(changed != null)
                        changed.get(box.isChecked());
                });

                box.left();
                addDesc(table.add(box).left().padTop(3f).get());
                table.row();
            }
        }

        public static class CheckSetting extends Setting{
            boolean def;
            Boolc changed;

            public CheckSetting(String name, boolean def, Boolc changed, Boolp visibility){
                super(name, visibility);
                this.def = def;
                this.changed = changed;
            }

            @Override
            public void add(DynamicTable table){
                CheckBox box = new CheckBox(title);

                box.update(() -> box.setChecked(settings.getBool(name)));

                box.changed(() -> {
                    settings.put(name, box.isChecked());
                    if(changed != null){
                        changed.get(box.isChecked());
                    }
                });

                box.left();
                addDesc(table.add(box).left().padTop(3f).get());
                table.row();
            }
        }

        public static class SliderSetting extends Setting{
            int def, min, max, step;
            StringProcessor sp;
            Slider slider;
            public Runnable changed;

            public SliderSetting(String name, int def, int min, int max, int step, StringProcessor s, Boolp visibility){
                super(name, visibility);
                this.def = def;
                this.min = min;
                this.max = max;
                this.step = step;
                this.sp = s;

                this.slider = new Slider(min, max, step, false);
            }

            @Override
            public void add(DynamicTable table){
                slider.setValue(settings.getInt(name));

                Label value = new Label("", Styles.outlineLabel);
                Table content = new Table();
                content.add(title, Styles.outlineLabel).left().growX().wrap();
                content.add(value).padLeft(10f).right();
                content.margin(3f, 33f, 3f, 33f);
                content.touchable = Touchable.disabled;

                slider.changed(() -> {
                    if(changed != null && slider.getValue() != settings.getInt(name))
                        changed.run();

                    settings.put(name, (int)slider.getValue());
                    value.setText(sp.get((int)slider.getValue()));
                });

                slider.change();

                addDesc(table.stack(slider, content).width(Math.min(Core.graphics.getWidth() / 1.2f, 460f)).left().padTop(4f).get());
                table.row();
            }
        }

        public static class TextSetting extends Setting{
            String def;
            Cons<String> changed;

            public TextSetting(String name, String def, Cons<String> changed, Boolp visibility){
                super(name, visibility);
                this.def = def;
                this.changed = changed;
            }

            @Override
            public void add(DynamicTable table){
                TextField field = new TextField();

                field.update(() -> field.setText(settings.getString(name)));

                field.changed(() -> {
                    settings.put(name, field.getText());
                    if(changed != null){
                        changed.get(field.getText());
                    }
                });

                Table prefTable = table.table().left().padTop(3f).get();
                prefTable.add(field);
                prefTable.label(() -> title);
                addDesc(prefTable);
                table.row();
            }
        }

        public static class AreaTextSetting extends TextSetting{
            public AreaTextSetting(String name, String def, Cons<String> changed, Boolp visibility){
                super(name, def, changed, visibility);
            }

            @Override
            public void add(DynamicTable table){
                TextArea area = new TextArea("");
                area.setPrefRows(5);

                area.update(() -> {
                    area.setText(settings.getString(name));
                    area.setWidth(table.getWidth());
                });

                area.changed(() -> {
                    settings.put(name, area.getText());
                    if(changed != null){
                        changed.get(area.getText());
                    }
                });

                addDesc(table.label(() -> title).left().padTop(3f).get());
                table.row().add(area).left();
                table.row();
            }
        }
    }
}
