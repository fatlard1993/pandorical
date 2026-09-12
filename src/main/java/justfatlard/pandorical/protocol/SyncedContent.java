package justfatlard.pandorical.protocol;

import java.util.List;

/**
 * What a client registers from a content sync, read the same way from either payload:
 * {@link SyncContentConfigS2C} in the configuration phase, {@link SyncContentS2C} in the
 * play-phase fallback. The records supply these accessors themselves.
 */
public interface SyncedContent {
    List<SyncContentS2C.BlockEntry> blocks();
    List<SyncContentS2C.ItemEntry> items();
    List<String> entityTypes();
    List<String> blockEntityTypes();
    List<String> villagerProfessions();
    List<String> poiTypes();
    List<String> menuTypes();
    List<String> recipeBookCategories();
    boolean solidRails();
}
