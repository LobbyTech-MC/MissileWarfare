package me.kaiyan.missilewarfare.blocks;

import java.util.Objects;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dispenser;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.slimefunguguproject.misslewarfare.Utils;

import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockDispenseHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.libraries.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.ItemUtils;
import me.kaiyan.missilewarfare.MissileWarfare;
import me.kaiyan.missilewarfare.items.MissileClass;
import me.kaiyan.missilewarfare.missiles.MissileController;
import me.kaiyan.missilewarfare.util.Translations;
import me.kaiyan.missilewarfare.util.VariantsAPI;

public class GroundMissileLauncher extends SlimefunItem{
    public GroundMissileLauncher(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public void preRegister() {
        //cancel thing on place
        BlockPlaceHandler blockPlaceHandler = new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(BlockPlaceEvent event) {
                World world = event.getBlock().getWorld();
                Block below = world.getBlockAt(event.getBlock().getLocation().subtract(new Vector(0, 1, 0)));
                BlockData data = event.getBlockPlaced().getBlockData();
                ((Directional)data).setFacing(BlockFace.UP);
                event.getBlockPlaced().setBlockData(data);
                //Block bottom = world.getBlockAt(event.getBlock().getLocation().subtract(new Vector(0, 2, 0)));
                if (below.getType() == Material.GREEN_CONCRETE){
                    Utils.send(event.getPlayer(), Translations.get("messages.launchers.ground.create.success"));
                    TileState state = (TileState)event.getBlockPlaced().getState();
                    PersistentDataContainer cont = state.getPersistentDataContainer();

                    cont.set(new NamespacedKey(MissileWarfare.getInstance(), "canfire"), PersistentDataType.INTEGER, 1);
                    state.update();
                    /*if (bottom.getType() == Material.GREEN_CONCRETE){
                        event.getPlayer().sendMessage("Created Small Launcher!");
                    }else{
                        event.getPlayer().sendMessage("Bottom Block is type: " + bottom.getType() + " It needs Type GREEN_CONCRETE");
                        event.setCancelled(true);
                    }*/
                } else {
                    Utils.send(event.getPlayer(), Translations.get("messages.launchers.ground.create.failure").replace("{type}", below.getType().name()));
                }
            }
        };
        addItemHandler(blockPlaceHandler);

        BlockUseHandler blockUseHandler = this::onBlockRightClick;
        addItemHandler(blockUseHandler);

        BlockDispenseHandler blockDispenseHandler = this::blockDispense;
        addItemHandler(blockDispenseHandler);
    }

    private void blockDispense(BlockDispenseEvent blockDispenseEvent, Dispenser dispenser, Block block, SlimefunItem slimefunItem) {
        blockDispenseEvent.setCancelled(true);

        TileState state = (TileState)dispenser.getBlock().getState();
        PersistentDataContainer cont = state.getPersistentDataContainer();

        if (cont.has(new NamespacedKey(MissileWarfare.getInstance(), "canfire"), PersistentDataType.INTEGER) && cont.get(new NamespacedKey(MissileWarfare.getInstance(), "canfire"), PersistentDataType.INTEGER) != 1){
            MissileWarfare.getInstance().getServer().broadcastMessage("Missile at : "+dispenser.getBlock().getLocation().toVector() +" Is unable to fire: Missing GREEN_CONCRETE Below");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (dispenser.getBlock().getRelative(BlockFace.DOWN).getType() == Material.GREEN_CONCRETE) {
                    fireMissile(dispenser);
                }
            }
        }.runTaskLater(MissileWarfare.getInstance(), 1);
    }

    private void onBlockRightClick(PlayerRightClickEvent event) {
        // Stick/Blaze Rod Method
        if (event.getItem().getType() == Material.STICK){
            event.cancel();
            TileState state = (TileState) Objects.requireNonNull(event.getInteractEvent().getClickedBlock()).getState();
            PersistentDataContainer cont = state.getPersistentDataContainer();
            try {
                if (event.getPlayer().isSneaking()) {
                    int[] coords = cont.get(new NamespacedKey(MissileWarfare.getInstance(), "coords"), PersistentDataType.INTEGER_ARRAY);
                    float dist = (float) new Vector(coords[0], 0, coords[1]).distance(new Vector(event.getInteractEvent().getClickedBlock().getX(), 0, event.getInteractEvent().getClickedBlock().getY()));
                    Utils.send(event.getPlayer(), Translations.get("messages.launchers.ground.coords").replace("{xcoord}", String.valueOf(coords[0])).replace("{ycoord}", String.valueOf(coords[1])).replace("{dist}", String.valueOf(dist)));
                    return;
                }
            } catch (NullPointerException e){
                cont.set(new NamespacedKey(MissileWarfare.getInstance(), "coords"), PersistentDataType.INTEGER_ARRAY, new int[]{0, 0});
                state.update();
                event.getPlayer().sendMessage(ChatColor.GREEN+"Setup launcher, right click again to set coordinates!");
            }
            try {
                    Prompt askCoordY = new StringPrompt() {
                        @Override
                        public String getPromptText(ConversationContext conversationContext) {
                            return ChatColors.color("&e输入Z坐标，输入&bexit&e以取消设置");
                        }

                        @Override
                        public Prompt acceptInput(ConversationContext conversationContext, String s) {
                            try {
                                cont.set(new NamespacedKey(MissileWarfare.getInstance(), "coords"), PersistentDataType.INTEGER_ARRAY, new int[]{cont.get(new NamespacedKey(MissileWarfare.getInstance(), "coords"), PersistentDataType.INTEGER_ARRAY)[0], Integer.parseInt(s)});
                            } catch (NumberFormatException e) {
                                conversationContext.getForWhom().sendRawMessage(Translations.get("messages.launchers.ground.setting.notanumber"));
                                state.update();
                                return END_OF_CONVERSATION;
                            }
                            conversationContext.getForWhom().sendRawMessage(ChatColors.color("&a已设置目标位置 Z: &b" + Integer.parseInt(s)));
                            state.update();
                            return END_OF_CONVERSATION;
                        }
                    };
                    Prompt askCoordX = new StringPrompt() {
                        @Override
                        public String getPromptText(ConversationContext conversationContext) {
                            return ChatColors.color("&e输入X坐标，输入&bexit&e以取消设置");
                        }

                        @Override
                        public Prompt acceptInput(ConversationContext conversationContext, String s) {
                            try {
                                cont.set(new NamespacedKey(MissileWarfare.getInstance(), "coords"), PersistentDataType.INTEGER_ARRAY, new int[]{Integer.parseInt(s), 0});
                            } catch (NumberFormatException e) {
                                conversationContext.getForWhom().sendRawMessage(ChatColors.color(Translations.get("messages.launchers.ground.setting.notacoord")));
                                state.update();
                                return END_OF_CONVERSATION;
                            }
                            conversationContext.getForWhom().sendRawMessage(ChatColors.color("&a已设置目标位置 X: &b" + Integer.parseInt(s)));
                            return askCoordY;
                        }
                    };

                    ConversationFactory cf = new ConversationFactory(MissileWarfare.getInstance());
                    Conversation conversation = cf.withFirstPrompt(askCoordX)
                        .withLocalEcho(false)
                        .withEscapeSequence("exit")
                        .withTimeout(20)
                        .buildConversation(event.getPlayer());
                    conversation.begin();
            } catch (NullPointerException e){
                state.update();
            }
        } else if (event.getItem().getType() == Material.BLAZE_ROD){
            event.cancel();
            TileState state = (TileState) Objects.requireNonNull(event.getInteractEvent().getClickedBlock()).getState();
            PersistentDataContainer cont = state.getPersistentDataContainer();

            try{
                Prompt askCruiseAlt = new StringPrompt() {
                    @Override
                    public String getPromptText(ConversationContext conversationContext) {
                        return ChatColors.color("&e输入巡航高度(Y)，输入&bexit&e以取消设置");
                    }

                    @Override
                    public Prompt acceptInput(ConversationContext conversationContext, String s) {
                        try {
                            cont.set(new NamespacedKey(MissileWarfare.getInstance(), "alt"), PersistentDataType.INTEGER, Integer.valueOf(s));
                        } catch (NumberFormatException e) {
                            conversationContext.getForWhom().sendRawMessage(ChatColors.color(Translations.get("messages.launchers.ground.setting.notanumber")));
                            state.update();
                            return END_OF_CONVERSATION;
                        }
                        conversationContext.getForWhom().sendRawMessage(ChatColors.color(Translations.get("messages.launchers.ground.setting.cruisealt") + Integer.parseInt(s)));
                        state.update();
                        return END_OF_CONVERSATION;
                    }
                };
                ConversationFactory cf = new ConversationFactory(MissileWarfare.getInstance());
                Conversation conversation = cf.withFirstPrompt(askCruiseAlt)
                        .withLocalEcho(false)
                        .withEscapeSequence("exit")
                        .withTimeout(20)
                        .buildConversation(event.getPlayer());
                conversation.begin();
            } catch (NullPointerException e){
                state.update();
            }
        } else if (SlimefunItem.getByItem(event.getItem()) == SlimefunItem.getById("PLAYERLIST")){
            event.cancel();
            TileState state = (TileState) event.getClickedBlock().get().getBlockData();
            state.update();
        }
    }

    /*@Deprecated
    public void fireMissile(PlayerRightClickEvent event){
        Dispenser disp = (Dispenser) Objects.requireNonNull(event.getInteractEvent().getClickedBlock()).getState();
        int type = hasAmmo(disp.getInventory(), (SmallGtGMissile) itemMissile);
        if (type !=0){
            TileState state = (TileState) Objects.requireNonNull(event.getInteractEvent().getClickedBlock()).getState();
            PersistentDataContainer cont = state.getPersistentDataContainer();
            int[] coords = cont.get(new NamespacedKey(AdvancedWarfare.getInstance(), "coords"), PersistentDataType.INTEGER_ARRAY);
            event.getPlayer().sendMessage(Arrays.toString(coords));
            if (coords == null) {
                event.getPlayer().sendMessage("You need to input coordinates!");
                return;
            }
            MissileController missile = new MissileController(true, event.getInteractEvent().getClickedBlock().getLocation().add(new Vector(0.5, 1, 0.5)).toVector(), new Vector(coords[0], 0, coords[1]), 1, event.getPlayer().getWorld(), 3, 30);
            missile.FireMissile();
        }
    }
     */
    public void fireMissile(Dispenser disp){
        ItemStack missileitem = VariantsAPI.getFirstMissile(disp.getInventory());
        int type = VariantsAPI.getIntTypeFromSlimefunitem(SlimefunItem.getByItem(missileitem));

        MissileClass missile = VariantsAPI.missileStatsFromType(type);
        boolean fired = fireMissile(disp, missile);
        if (fired) {
            ItemUtils.consumeItem(missileitem, false);
        }

        /*// -- SmallGtGMissile --
        if (type == 1){
            fireMissile(disp,3, 1, 100, 1);
        } else if (type == 2){
            fireMissile(disp,2, 1.5, 130, 2);
        } else if (type == 3){
            fireMissile(disp, 3, 1, 100, 3);
        } else if (type == 4){
            fireMissile(disp, 3, 1, 30, 4);
        }*/
    }

    public boolean fireMissile(Dispenser disp, MissileClass missile){
        TileState state = (TileState) disp.getBlock().getState();
        PersistentDataContainer cont = state.getPersistentDataContainer();
        int[] coords = cont.get(new NamespacedKey(MissileWarfare.getInstance(), "coords"), PersistentDataType.INTEGER_ARRAY);
        Integer alt = cont.get(new NamespacedKey(MissileWarfare.getInstance(), "alt"), PersistentDataType.INTEGER);
        if (coords == null) {
            MissileWarfare.getInstance().getServer().broadcastMessage("导弹无法发射，位于: "+new Vector(disp.getBlock().getLocation().getX(), disp.getBlock().getLocation().getY(), disp.getBlock().getLocation().getZ()) + " 无效坐标!");
            return false;
        } else if (VariantsAPI.isInRange((int) disp.getLocation().distanceSquared(new Vector(coords[0], 0, coords[1]).toLocation(disp.getWorld())), missile.type)){
            MissileWarfare.getInstance().getServer().broadcastMessage("导弹无法发射，位于: "+disp.getBlock().getLocation() + " 目标位置超出射程!");
            return false;
        }
        if (alt == null){
            alt = 120;
        }
        if (MissileWarfare.plugin.getConfig().getBoolean("logging.logMissileShots")) {
            try {
                Player result = null;
                double lastDistance = Double.MAX_VALUE;
                for (Player p : disp.getWorld().getPlayers()) {
                    double distance = disp.getLocation().distanceSquared(p.getLocation());
                    if (distance < lastDistance) {
                        lastDistance = distance;
                        result = p;
                    }
                }
                MissileWarfare.getInstance().getLogger().info("导弹发射 || 位于: " + disp.getBlock().getLocation() + " 目标: " + new Vector(coords[0], 0, coords[1]) + " 附近玩家: " + result.getName());
                if (MissileWarfare.getInstance().getConfig().getBoolean("logging.broadcastMissileShots")) {
                    final String playername = result.getName();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            MissileWarfare.getInstance().getServer().broadcastMessage("导弹发射! 发射井坐标: " + disp.getBlock().getLocation().toVector() + " 附近玩家: " + playername);
                        }
                    }.runTaskLater(MissileWarfare.getInstance(), 20L * MissileWarfare.getInstance().getConfig().getLong("logging.waitTimeBeforeBroadcast"));
                }
            } catch (NullPointerException e){
                MissileWarfare.getInstance().getLogger().warning("玩家不在线，无法记录导弹发射");
            }
        }
        MissileController _missile = new MissileController(true, disp.getBlock().getLocation().add(new Vector(0.5, 1.35, 0.5)).toVector(), new Vector(coords[0], 0, coords[1]), (float) missile.speed, disp.getBlock().getWorld(), missile.power, missile.accuracy, missile.type, alt);
        _missile.FireMissile();
        return true;
    }
}