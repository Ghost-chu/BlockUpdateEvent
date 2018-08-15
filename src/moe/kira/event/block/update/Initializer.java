package moe.kira.event.block.update;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import moe.kira.event.block.update.internal.v1_12_R1.InjectedWorldServer;

import static moe.kira.event.block.update.common.VersionLevel.strip;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Initializer extends JavaPlugin implements Listener {
    private Class<?> classCraftWorld;
    private Class<?> classCraftServer;
    private Class<?> classCraftScoreboardManager;
    
    private Class<?> classWorld;
    private Class<?> classScoreboard;
    private Class<?> classWorldServer;
    private Class<?> classMinecraftServer;
    //private static Class<?> classInjectedWorldServer;
    
    private final static boolean debug = true;
    
    @EventHandler
    public void debug(BlockUpdateEvent evt) {
        if (!debug) return;
        Location loc = evt.getBlock().getLocation();
        Bukkit.getLogger().info("\nBlockUpdateEvent info:\n > updated block type: " + evt.getBlock().getType() + "\n > source block type prev: " + evt.getChangedType() + "\n > source block type now: " + evt.getChangedBlock().getType() + "\n > in " + loc.getWorld().getName() + ", " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "\n");
    }
    
    @Override
    public void onEnable() {
        try {
            classCraftWorld = Class.forName("org.bukkit.craftbukkit." + strip() + ".CraftWorld");
            classCraftServer = Class.forName("org.bukkit.craftbukkit." + strip() + ".CraftServer");
            classCraftScoreboardManager = Class.forName("org.bukkit.craftbukkit." + strip() + ".scoreboard.CraftScoreboardManager");
            
            classWorld = Class.forName("net.minecraft.server." + strip() + ".World");
            Class.forName("net.minecraft.server." + strip() + ".PlayerList");
            classScoreboard = Class.forName("net.minecraft.server." + strip() + ".Scoreboard");
            classWorldServer = Class.forName("net.minecraft.server." + strip() + ".WorldServer");
            classMinecraftServer = Class.forName("net.minecraft.server." + strip() + ".MinecraftServer");
            //classInjectedWorldServer = Class.forName("moe.kira.event.update.nms." + strip() + ".InjectWorldServer");
            
        } catch (Throwable t) {
            t.printStackTrace();
        }
        
        Bukkit.getPluginManager().registerEvents(this, this);
    }
    
    static Field access(Field field) {
        field.setAccessible(true);
        return field;
    }
    
    public Set<String> transformedWorlds = Sets.newHashSet();
    public List<World> injectedWorlds = Lists.newLinkedList();
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void injectWorld(WorldInitEvent evt) throws Throwable {
        if (!transformedWorlds.add(evt.getWorld().getName())) {
            Bukkit.getLogger().warning("Skip injecting " + evt.getWorld().getName());
            return;
        }
        // Stage: Destroy trace
        @SuppressWarnings("unchecked")
        final Map<String, World> craftWorlds = (Map<String, World>) access(classCraftServer.getDeclaredField("worlds")).get(Bukkit.getServer());
        craftWorlds.remove(evt.getWorld().getName().toLowerCase(Locale.ENGLISH));
        
        Bukkit.getLogger().warning("Transform start for " + evt.getWorld().getName());
        // Stage: Construct instance
        World transformed = createWorld(evt.getWorld());
        injectedWorlds.add(transformed);
        // Stage: Capture holder
        craftWorlds.put(evt.getWorld().getName().toLowerCase(Locale.ENGLISH), transformed);
        
        Object instanceMinecraftServer = classMinecraftServer.getDeclaredMethod("getServer").invoke(null);
        @SuppressWarnings("unchecked")
        List<Object> nmsWorlds = (List<Object>) classMinecraftServer.getDeclaredField("worlds").get(instanceMinecraftServer);
        nmsWorlds.clear();
        nmsWorlds.addAll(Lists.transform(injectedWorlds, new Function<World, Object>() {
            @Override
            public Object apply(World world) {
                try {
                    return classCraftWorld.getDeclaredMethod("getHandle").invoke(world);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                throw new AssertionError();
            }
        }));
        
        if (injectedWorlds.size() == 1) {
            classCraftServer.getDeclaredField("scoreboardManager").set(Bukkit.getServer(), classCraftScoreboardManager
                    .getConstructor(classMinecraftServer, classScoreboard)
                    .newInstance(instanceMinecraftServer, classWorld.getMethod("getScoreboard").invoke(classCraftWorld.getDeclaredMethod("getHandle").invoke(transformed))));
        }
        
        if (injectedWorlds.size() == 3) {
            Field fieldWorldServers = classMinecraftServer.getDeclaredField("worldServer");
            Object nmsWorldServers = fieldWorldServers.get(instanceMinecraftServer);
            for (int i = 0; i < 3; i++) Array.set(nmsWorldServers, i, classCraftWorld.getDeclaredMethod("getHandle").invoke(injectedWorlds.get(i)));
            Bukkit.getLogger().warning("Transformed original world array");
        }
        
        Bukkit.getLogger().warning("Transformed " + evt.getWorld().getName() + ", transfromed worlds: " + injectedWorlds.size());
        Bukkit.getLogger().warning("Current nmsWorlds size: " + ((List<?>) classMinecraftServer.getDeclaredField("worlds").get(instanceMinecraftServer)).size());
        Bukkit.getLogger().warning("Current craftWorlds size: " + ((Map<?, ?>) access(classCraftServer.getDeclaredField("worlds")).get(Bukkit.getServer())).size());
    }
    
    private World createWorld(World copy) throws Throwable {
        final Object instanceWorldServer = classCraftWorld.getDeclaredMethod("getHandle").invoke(copy);
        Object injected = classWorldServer.getDeclaredMethod("b").invoke(new InjectedWorldServer(instanceWorldServer, copy.getEnvironment(), copy.getGenerator(), injectedWorlds.isEmpty()));
        
        World world = (World) classWorld.getDeclaredMethod("getWorld").invoke(injected);
        Bukkit.getPluginManager().callEvent(new WorldInitEvent(world));
        Bukkit.getPluginManager().callEvent(new WorldLoadEvent(world));
        
        return world;
    }
    
    /*public static boolean overworld = true;
    public Object overworldServerInstance;
    
    @EventHandler(priority = EventPriority.LOWEST)
    public void injectWorlds(WorldInitEvent evt) throws Throwable {
        if (!transformedWorlds.add(evt.getWorld().getName())) return;
        // STAGE 1: Pre-progress
        final Map craftWorlds = (Map) access(classCraftServer.getDeclaredField("worlds")).get(Bukkit.getServer());
        
        final Object instanceWorldServer = classCraftWorld.getDeclaredMethod("getHandle").invoke(evt.getWorld());
        Object instanceCraftWorld = classWorld.getDeclaredMethod("getWorld").invoke(instanceWorldServer);
        final String worldName = (String) classCraftWorld.getDeclaredMethod("getName").invoke(instanceCraftWorld);
        Bukkit.getLogger().warning("Transform start for " + worldName);
        
        craftWorlds.remove(worldName.toLowerCase(Locale.ENGLISH));
        // STAGE 2: Inject world
        final Object instanceMinecraftServer = classMinecraftServer.getDeclaredMethod("getServer").invoke(null);
        Object instancePropertyManager = classDedicatedServer.getDeclaredMethod("getPropertyManager").invoke(instanceMinecraftServer);
        Method methodAccessString = classPropertyManager.getMethod("getString", String.class, String.class);
        final String levelName = (String) methodAccessString.invoke(instancePropertyManager, "level-name", "world");
        final long seed = evt.getWorld().getSeed();
        
        String levelType = (String) methodAccessString.invoke(instancePropertyManager, "level-type", "DEFAULT");
        final Object worldType = classWorldType.getMethod("getType", String.class).invoke(null, levelType);
        
        final String generatorSettings = (String) methodAccessString.invoke(instancePropertyManager, "generator-settings", "");
        final List nmsWorlds = (List) classMinecraftServer.getDeclaredField("worlds").get(instanceMinecraftServer);
        
        
        
        final Object injectedWorldServer = new InjectedWorldServer(
                instanceMinecraftServer,
                dataManagerField.get(instanceWorldServer),
                classWorld.getDeclaredField("worldData").get(instanceWorldServer),
                (int) classWorldServer.getDeclaredField("dimension").get(instanceWorldServer),
                classWorld.getDeclaredField("methodProfiler").get(instanceWorldServer),
                (Environment) environmentField.get(instanceCraftWorld),
                (ChunkGenerator) classWorld.getDeclaredField("generator").get(instanceWorldServer));
        
        // STAGE 3: Destroy our trace!
        //classMinecraftServer.getDeclaredMethod("a", String.class, String.class, long.class, worldType.getClass(), String.class).invoke(instanceMinecraftServer, levelName, levelName, seed, worldType, generatorSettings);
        if (overworld) {
            Object instancePlayerList = classMinecraftServer.getDeclaredMethod("getPlayerList").invoke(instanceMinecraftServer);
            classPlayerList.getDeclaredMethod("setPlayerFileData", new Class[] {classWorldServer}).invoke(instancePlayerList, new Object[] {instanceWorldServer});
            overworldServerInstance = instanceWorldServer;
        }
        Bukkit.getLogger().warning("Transformed " + worldName + ", current world size: " + nmsWorlds.size());
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                nmsWorlds.remove(instanceWorldServer);
                Bukkit.getLogger().warning("Trace of " + worldName + " destroyed!");
            }
        }, 0);
        if (overworld) {
            overworld = false;
        } else {
            Object instancePlayerList = classMinecraftServer.getDeclaredMethod("getPlayerList").invoke(instanceMinecraftServer);
            classPlayerList.getDeclaredMethod("setPlayerFileData", new Class[] {classWorldServer}).invoke(instancePlayerList, new Object[] {overworldServerInstance});
        }
    }*/
    
    /*@EventHandler(priority = EventPriority.MONITOR)
    public void handle(WorldLoadEvent evt) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, NoSuchFieldException {
        final Object instanceMinecraftServer = classMinecraftServer.getDeclaredMethod("getServer").invoke(null);
        Field craftWorldsField = classCraftServer.getDeclaredField("worlds");
        craftWorldsField.setAccessible(true);
        final Map craftWorlds = (Map) craftWorldsField.get(Bukkit.getServer());
        
        final List nmsWorlds = (List) classMinecraftServer.getDeclaredField("worlds").get(instanceMinecraftServer);
        final Object instanceWorldServer = classCraftWorld.getDeclaredMethod("getHandle").invoke(evt.getWorld());
        Object instanceCraftWorld = classWorld.getDeclaredMethod("getWorld").invoke(instanceWorldServer);
        
        final String name = (String) classCraftWorld.getDeclaredMethod("getName").invoke(instanceCraftWorld);
        Bukkit.getLogger().warning("Transform start for " + name);
        
        Field dataManagerField = classWorld.getDeclaredField("dataManager");
        dataManagerField.setAccessible(true);
        
        Field environmentField = classCraftWorld.getDeclaredField("environment");
        environmentField.setAccessible(true);
        
        final Object injectedWorldServer = new InjectedWorldServer(
                instanceMinecraftServer,
                dataManagerField.get(instanceWorldServer),
                classWorld.getDeclaredField("worldData").get(instanceWorldServer),
                (int) classWorldServer.getDeclaredField("dimension").get(instanceWorldServer),
                classWorld.getDeclaredField("methodProfiler").get(instanceWorldServer),
                (Environment) environmentField.get(instanceCraftWorld),
                (ChunkGenerator) classWorld.getDeclaredField("generator").get(instanceWorldServer));
        
        final Object instanceInitedWorldServer = classWorldServer.getDeclaredMethod("b").invoke(injectedWorldServer);
        final Object instanceInitedCraftWorld = classWorld.getMethod("getWorld").invoke(instanceInitedWorldServer);
        
        Bukkit.getScheduler().runTaskLater(this, new Runnable() {
            @Override
            public void run() {
                nmsWorlds.remove(instanceWorldServer);
                nmsWorlds.add(instanceInitedWorldServer);
                
                craftWorlds.remove(name.toLowerCase(Locale.ENGLISH));
                craftWorlds.put(name, instanceInitedCraftWorld);
            }
        }, 1);
        Bukkit.getLogger().warning("Transformed " + name + ", current world size: " + nmsWorlds.size());
    }*/
    
}
