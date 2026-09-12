package justfatlard.pandorical.protocol;

import java.util.List;

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
