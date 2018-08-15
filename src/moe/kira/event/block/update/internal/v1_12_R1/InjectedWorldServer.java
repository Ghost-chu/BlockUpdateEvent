package moe.kira.event.block.update.internal.v1_12_R1;

import org.bukkit.World.Environment;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_12_R1.util.CraftMagicNumbers;
import org.bukkit.generator.ChunkGenerator;

import moe.kira.event.block.update.BlockUpdateEvent;
import moe.kira.event.block.update.common.VersionLevel;
import net.minecraft.server.v1_12_R1.WorldServer;
import net.minecraft.server.v1_12_R1.Block;
import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.EnumDifficulty;
import net.minecraft.server.v1_12_R1.PlayerList;
import net.minecraft.server.v1_12_R1.Scoreboard;
import net.minecraft.server.v1_12_R1.ScoreboardTeam;
import net.minecraft.server.v1_12_R1.WorldManager;

public class InjectedWorldServer extends net.minecraft.server.v1_12_R1.WorldServer {

    public InjectedWorldServer(Object nmsWorldServer, Environment env, ChunkGenerator gen, boolean overworld) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException {
        super(((WorldServer) nmsWorldServer).getMinecraftServer(), ((WorldServer) nmsWorldServer).getDataManager(), ((WorldServer) nmsWorldServer).worldData, ((WorldServer) nmsWorldServer).dimension, ((WorldServer) nmsWorldServer).methodProfiler, env, gen);
        
        this.addIWorldAccess(new WorldManager(((WorldServer) nmsWorldServer).getMinecraftServer(), this));
        this.worldData.setGameType(((WorldServer) nmsWorldServer).getMinecraftServer().getGamemode());
        
        if (this.worldData.isHardcore()) {
            this.worldData.setDifficulty(EnumDifficulty.HARD);
            this.setSpawnFlags(true, true);
        } else {
            this.worldData.setDifficulty(((WorldServer) nmsWorldServer).getMinecraftServer().getDifficulty());
            this.setSpawnFlags(((WorldServer) nmsWorldServer).allowMonsters, ((WorldServer) nmsWorldServer).allowAnimals);
        }
        
        if (VersionLevel.isPaper() && overworld) {
            // Paper start - Handle collideRule team for player collision toggle
            final Scoreboard scoreboard = this.getScoreboard();
            for (ScoreboardTeam team : scoreboard.getTeams()) {
                if (!team.getName().startsWith("collideRule_")) continue;
                scoreboard.removeTeam(scoreboard.getTeam(team.getName())); // Clean up after ourselves
            }

            if (!com.destroystokyo.paper.PaperConfig.enablePlayerCollisions) {
                String teamName = org.apache.commons.lang3.StringUtils.left("collideRule_" + this.random.nextInt(), 16);
                PlayerList.class.getDeclaredField("collideRuleTeamName").set(this.getMinecraftServer().getPlayerList(), teamName);
                ScoreboardTeam collideTeam = scoreboard.createTeam(teamName);
                collideTeam.setCanSeeFriendlyInvisibles(false); // Because we want to mimic them not being on a team at all
            }
            // Paper end
        }
    }
    
    @Override
    public void a(BlockPosition updatedBlock, final Block changed, BlockPosition changedBlock) {
        CraftWorld world = ((WorldServer) this).getWorld();
        @SuppressWarnings("deprecation")
        BlockUpdateEvent event = new BlockUpdateEvent(
                world.getBlockAt(changedBlock.getX(), changedBlock.getY(), changedBlock.getZ()),
                world.getBlockAt(updatedBlock.getX(), updatedBlock.getY(), updatedBlock.getZ()),
                CraftMagicNumbers.getId(changed));
        
        this.getServer().getPluginManager().callEvent(event);
        
        if (!event.isCancelled()) super.a(updatedBlock, changed, changedBlock);
    }
    
}
