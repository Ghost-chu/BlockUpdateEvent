package moe.kira.event.block.update;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockPhysicsEvent;

/**
 * Thrown when a block update check is called
 */
public class BlockUpdateEvent extends BlockPhysicsEvent implements Cancellable {
    private final Block changedBlock;
    
    @SuppressWarnings("deprecation")
    public BlockUpdateEvent(Block source, Block updated, int changed) {
        super(updated, changed);
        changedBlock = source;
    }
    
    /**
     * Gets the type of block that changed, causing this event
     *
     * @return Changed block
     */
    public Block getChangedBlock() {
        return changedBlock;
    }
    
}
