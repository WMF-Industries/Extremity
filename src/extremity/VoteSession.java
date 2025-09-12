package extremity;

import arc.Core;
import arc.func.Cons2;
import arc.math.Mathf;
import arc.struct.*;
import arc.util.*;
import mindustry.gen.*;

import static extremity.utils.DedicatedBundles.dynamicLocale;
import static mindustry.Vars.state;

public class VoteSession{
    ObjectMap<String, Boolean> votes = new ObjectMap<>();
    StringBuilder builder = new StringBuilder();
    Timer.Task manager;
    Cons2<Player, Boolean> onVote = (p, b) -> {};
    Runnable
        onTime = () -> {},
        onSuccess = () -> {},
        onFailure = () -> {};

    Player initiator;
    int time = 60;

    public boolean completed = false;

    public VoteSession(Player player){
        initiator = player;
        Core.app.post(() -> vote(player, true, true));

        manager = Timer.schedule(() -> {
            // this only makes sure that the manager dies when it's supposed to
            if(completed){
                quit();
                return;
            }

            if(refreshState()){
                if(--time == 30)
                    onTime.run();
            }
        }, 0, 1);
    }

    public boolean refreshState(){
        if(votes() >= votesRequired()){
            success();
            return false;
        }

        if(time <= 0 || votes() < -(Groups.player.size() - votesRequired()) || (votes() < votesRequired() && votes.size >= Groups.player.size())){
            fail();
            return false;
        }

        return true;
    }

    public void vote(Player player, boolean res){
        vote(player, res, false);
    }

    public void vote(Player player, boolean res, boolean initial){
        if(votes.containsKey(player.uuid())){
            player.sendMessage(dynamicLocale(player).get("vote.extremity-denied"));
            return;
        }

        builder.setLength(0);
        votes.put(player.uuid(), res);

        onVote.get(player, res);
        if(refreshState() && !initial)
            time += 30;
    }

    public void success(){
        onSuccess.run();
        quit();
    }

    public void fail(){
        onFailure.run();
        quit();
    }

    public void quit(){
        if(manager != null)
            manager.cancel();

        completed = true;

        if(builder.length() > 0)
            Call.sendMessage(builder.toString());
    }

    public int votes(){
        Seq<String> keys = votes.keys().toSeq();

        int ratio = 0;
        for(int i = 0; i < votes.size; i++){
            for(int p = 0; p < Groups.player.size(); p++){
                if(Groups.player.index(p).uuid().equals(keys.get(i))){
                    ratio += votes.get(keys.get(i)) ? 1 : -1;
                    break;
                }
            }
        }

        return ratio;
    }

    public int votesRequired(){
        return Mathf.ceil(Groups.player.size() * 0.65f);
    }
}
