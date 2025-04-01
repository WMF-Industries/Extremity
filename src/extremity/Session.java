package extremity;

import arc.math.Mathf;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;

public class Session{
    ObjectMap<String, Boolean> votes = new ObjectMap<>();
    Timer.Task manager;

    int difficulty, time = 30;
    boolean completed = false;

    public Session(Player player, int difficulty){
        this.difficulty = difficulty;
        this.vote(player, true);

        manager = Timer.schedule(() -> {
            if(completed){
                if(manager != null)
                    manager.cancel();
                return;
            }

            if(time <= 0){
                if(manager != null)
                    manager.cancel();

                fail();
            }else{
                if((--time % 15) == 0)
                    Call.sendMessage(Strings.format("[accent][Extremity][] @ seconds remaining to vote!", time));

                refreshState();
            }
        }, 0, 1);
    }

    public boolean refreshState(){
        if(votes() >= votesRequired()){
            success();
            return false;
        }

        if(votes() <= -votesRequired() || (votes() < votesRequired() && votes.size >= Groups.player.size())){
            fail();
            return false;
        }

        return true;
    }

    public void vote(Player player, boolean res){
        if(votes.containsKey(player.uuid())){
            player.sendMessage("[accent][Extremity] [scarlet]You have already voted!");
            return;
        }

        votes.put(player.uuid(), res);
        Call.sendMessage(Strings.format("@ [white] voted @ changing the difficulty to @ (@/@)", player.coloredName(), res ? "in favor of" : "against", Extremity.getName(difficulty), votes(), votesRequired()));

        if(refreshState()){
            time += 10;
            Call.sendMessage("[accent][Extremity][] Added 10 seconds to the voting session.");
        }
    }

    public void success(){
        Call.sendMessage(Strings.format("[accent][Extremity][] Difficutly change vote passed, setting difficulty to @!", Extremity.getName(difficulty)));
        Manager.difficulty = difficulty;
        stop();
    }

    public void fail(){
        Call.sendMessage("[accent][Extremity][] Difficulty change vote failed.");
        stop();
    }

    public void stop(){
        if(manager != null)
            manager.cancel();

        completed = true;
    }

    public int votes(){
        int ratio = 0;
        for(var entry : votes)
            ratio += entry.value ? 1 : -1;

        return ratio;
    }

    public int votesRequired(){
        return Math.max(Math.min(2, Groups.player.size()), Mathf.ceil(Groups.player.size() * 0.67f));
    }
}
