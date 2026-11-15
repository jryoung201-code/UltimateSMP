package shop.blinksmp.lodestones;

import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class UltimateLodestones extends JavaPlugin implements Listener {
    private CompassService service;private long repaired;private int tick;
    @Override public void onEnable(){
        saveDefaultConfig();Map<String,World> worlds=new HashMap<>();
        for(String dimension:List.of("Overworld","Nether","End")){
            String fallback=switch(dimension){case "Nether"->"world_nether";case "End"->"world_the_end";default->"world";};
            World world=Bukkit.getWorld(getConfig().getString("worlds."+dimension,fallback));
            World.Environment expected=switch(dimension){case "Nether"->World.Environment.NETHER;case "End"->World.Environment.THE_END;default->World.Environment.NORMAL;};
            if(world==null||world.getEnvironment()!=expected){getLogger().warning(dimension+" world is unavailable or has the wrong environment; its compasses will be preserved without remapping.");continue;}
            worlds.put(dimension,world);
        }
        service=new CompassService(worlds,getConfig().getBoolean("adopt-vanilla-compasses",true));
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskTimer(this,()->{
            tick++;for(Player player:Bukkit.getOnlinePlayers())scan(player,tick%20==0);
        },1L,1L);
        getLogger().info("UltimateLodestones ready. Portable coordinate anchors; install on every world server. No proxy plugin or database required.");
    }
    private void scan(Player player,boolean ender){
        if(!player.isOnline())return;
        scanInventory(player.getInventory());
        ItemStack cursor=player.getItemOnCursor();if(service.repair(cursor)){player.setItemOnCursor(cursor);repaired++;}
        if(ender)scanInventory(player.getEnderChest());
    }
    private void scanInventory(org.bukkit.inventory.Inventory inventory){
        ItemStack[] items=inventory.getContents();for(int slot=0;slot<items.length;slot++)if(service.repair(items[slot])){inventory.setItem(slot,items[slot]);repaired++;}
    }
    private void later(Player player){Bukkit.getScheduler().runTask(this,()->scan(player,true));}
    @EventHandler(priority=EventPriority.MONITOR) public void join(PlayerJoinEvent event){scan(event.getPlayer(),true);later(event.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pickup(PlayerAttemptPickupItemEvent event){ItemStack item=event.getItem().getItemStack();if(service.repair(item)){event.getItem().setItemStack(item);repaired++;}}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void interact(PlayerInteractEvent event){
        if(event.getAction()==Action.RIGHT_CLICK_BLOCK&&event.getClickedBlock()!=null&&event.getClickedBlock().getType()==Material.LODESTONE)later(event.getPlayer());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void inventory(InventoryClickEvent event){if(event.getWhoClicked() instanceof Player player)later(player);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void drag(InventoryDragEvent event){if(event.getWhoClicked() instanceof Player player)later(player);}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(!sender.hasPermission("ultimatelodestones.use")){sender.sendMessage("You do not have permission.");return true;}
        if(!(sender instanceof Player player)){sender.sendMessage("UltimateLodestones 1.0.0; item repairs: "+repaired);return true;}
        if(args.length>1||args.length==1&&!Set.of("status","forget").contains(args[0].toLowerCase(Locale.ROOT))){sender.sendMessage("Usage: /"+label+" [status|forget]");return true;}
        ItemStack item=player.getInventory().getItemInMainHand();
        if(args.length==1&&args[0].equalsIgnoreCase("forget")){sender.sendMessage(service.forget(item)?"Compass target cleared.":"Hold a compass in your main hand.");return true;}
        if(item.getType()!=Material.COMPASS||!(item.getItemMeta() instanceof CompassMeta meta)){sender.sendMessage("Hold a lodestone compass in your main hand.");return true;}
        Target target=service.target(meta);sender.sendMessage(target==null?"This compass has no saved target.":"Target: "+target.dimension()+" "+target.x()+", "+target.y()+", "+target.z()+". Portable across servers; use it in the target dimension.");return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        return sender.hasPermission("ultimatelodestones.use")&&args.length==1?List.of("status","forget").stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of();
    }
}
