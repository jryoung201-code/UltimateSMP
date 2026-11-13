package shop.blinksmp.network.paper;

import java.io.IOException;
import java.util.*;
import org.bukkit.*;
import org.bukkit.advancement.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;

/** Main-thread only. Every snapshot contains exact versioned data, not Java object serialization. */
final class VanillaProfiles {
    private VanillaProfiles(){}

    static String export(Player p,String category) {
        YamlConfiguration y=new YamlConfiguration();y.set("version",1);
        if(category.equals("advancements")) {
            y.createSection("advancements");
            Iterator<Advancement> it=Bukkit.advancementIterator();
            while(it.hasNext()) {
                Advancement a=it.next();
                var awarded=p.getAdvancementProgress(a).getAwardedCriteria();
                if(!awarded.isEmpty())y.set("advancements."+a.getKey(),List.copyOf(awarded));
            }
        } else if(category.equals("statistics")) {
            y.createSection("statistics");
            for(Statistic stat:Statistic.values()) {
                String base="statistics."+stat.name()+".";
                switch(stat.getType()) {
                    case UNTYPED -> { int value=p.getStatistic(stat);if(value!=0)y.set(base+"value",value); }
                    case BLOCK,ITEM -> {
                        for(Material material:Material.values()){
                            if(stat.isBlock()&&!material.isBlock())continue;
                            if(stat.getType()==Statistic.Type.ITEM&&!material.isItem())continue;
                            try {int value=p.getStatistic(stat,material);if(value!=0)y.set(base+material.name(),value);}
                            catch(IllegalArgumentException ignored) {}
                        }
                    }
                    case ENTITY -> {
                        for(EntityType type:EntityType.values()){
                            try {int value=p.getStatistic(stat,type);if(value!=0)y.set(base+type.name(),value);}
                            catch(IllegalArgumentException ignored) {}
                        }
                    }
                }
            }
        } else throw new IllegalArgumentException("Unknown vanilla category");
        return y.saveToString();
    }

    static void apply(Player p,String category,String raw) throws IOException {
        if(raw.length()>2_000_000)throw new IOException("Player profile too large");
        YamlConfiguration y=new YamlConfiguration();
        try{y.loadFromString(raw);}
        catch(org.bukkit.configuration.InvalidConfigurationException ex){throw new IOException("Invalid vanilla profile",ex);}
        if(y.getInt("version")!=1)throw new IOException("Unsupported vanilla profile version");
        if(category.equals("advancements")){
            // Optional/experimental: awarding criteria invokes vanilla advancement completion logic.
            // The network must restore the authoritative PlayerSnapshot afterward.
            ConfigurationSection section=y.getConfigurationSection("advancements");
            if(section==null)throw new IOException("Invalid advancement profile");
            Iterator<Advancement> all=Bukkit.advancementIterator();
            while(all.hasNext()){
                Advancement advancement=all.next();
                var progress=p.getAdvancementProgress(advancement);
                List<String> target=section.getStringList(advancement.getKey().toString());
                Set<String> expected=new HashSet<>(target);
                if(!advancement.getCriteria().containsAll(expected))throw new IOException("Unknown advancement criterion");
                for(String criterion:List.copyOf(progress.getAwardedCriteria()))
                    if(!expected.contains(criterion))progress.revokeCriteria(criterion);
                for(String criterion:target)if(!progress.getAwardedCriteria().contains(criterion))progress.awardCriteria(criterion);
            }
        } else if(category.equals("statistics")){
            ConfigurationSection statistics=y.getConfigurationSection("statistics");
            if(statistics==null)throw new IOException("Invalid statistics profile");
            // Absent counters mean zero. Clear counters from an older local server copy
            // instead of silently carrying them into this authoritative network profile.
            YamlConfiguration old=new YamlConfiguration();
            try {old.loadFromString(export(p,"statistics")); }
            catch(org.bukkit.configuration.InvalidConfigurationException ex){throw new IOException("Cannot inspect old statistics",ex);}
            ConfigurationSection previous=old.getConfigurationSection("statistics");
            if(previous!=null)for(String name:previous.getKeys(false)){
                ConfigurationSection entries=previous.getConfigurationSection(name);
                if(entries==null)continue;
                Statistic stat=Statistic.valueOf(name);
                for(String qualifier:entries.getKeys(false)){
                    if(statistics.isSet(name+"."+qualifier))continue;
                    switch(stat.getType()){
                        case UNTYPED -> p.setStatistic(stat,0);
                        case BLOCK,ITEM -> p.setStatistic(stat,Material.valueOf(qualifier),0);
                        case ENTITY -> p.setStatistic(stat,EntityType.valueOf(qualifier),0);
                    }
                }
            }
            for(String name:statistics.getKeys(false)){
                Statistic stat=Statistic.valueOf(name);
                ConfigurationSection entries=statistics.getConfigurationSection(name);
                if(entries==null)throw new IOException("Invalid statistic entries");
                for(String qualifier:entries.getKeys(false)){
                    if(!entries.isInt(qualifier))throw new IOException("Invalid statistic");
                    int value=entries.getInt(qualifier);
                    if(value<0)throw new IOException("Negative statistic");
                    switch(stat.getType()){
                        case UNTYPED -> {if(!qualifier.equals("value"))throw new IOException("Invalid qualifier");p.setStatistic(stat,value);}
                        case BLOCK,ITEM -> {
                            Material material=Material.valueOf(qualifier);
                            p.setStatistic(stat,material,value);
                        }
                        case ENTITY -> p.setStatistic(stat,EntityType.valueOf(qualifier),value);
                    }
                }
            }
        } else throw new IOException("Unsupported vanilla category");
    }
}
