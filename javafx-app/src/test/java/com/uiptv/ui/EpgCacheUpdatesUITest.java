package com.uiptv.ui;

import com.uiptv.testsupport.*;
import com.uiptv.widget.ResponsiveCardGrid;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.*;

class EpgCacheUpdatesUITest extends DbBackedUiTest {
    @BeforeAll static void initialize() throws Exception { FxTestSupport.initJavaFx(); }
    @Test void cacheUpdatesPreserveCardsAndSelectionInsteadOfRebuildingTheList() throws Exception {
        runOnFxThread(() -> {
            AtomicInteger builds=new AtomicInteger(),dataRefreshes=new AtomicInteger();
            var grid=new ResponsiveCardGrid<String>(item -> { builds.incrementAndGet(); return new VBox(new Label(item)); });
            grid.setItems(FXCollections.observableArrayList("One","Two"));
            EpgModeUI.observe(grid,grid::refresh);
            EpgModeUI.observeCache(grid,dataRefreshes::incrementAndGet);
            var root=new StackPane(grid); new Scene(root,700,500); root.resize(700,500); root.applyCss(); root.layout();
            grid.selectItems(java.util.List.of("Two"));
            int before=builds.get(),dataBefore=dataRefreshes.get();
            var revision=EpgModeUI.class.getDeclaredField("cacheRevision"); revision.setAccessible(true);
            var property=(javafx.beans.property.LongProperty)revision.get(null);
            for(int i=0;i<5;i++) property.set(property.get()+1);
            root.layout();
            assertEquals(before,builds.get(),"Background guide updates must not recreate channel/bookmark cards");
            assertEquals(java.util.List.of("Two"),grid.getSelectedItems());
            assertEquals(dataBefore+5,dataRefreshes.get(),"Only the existing data panels should update");
            root.getChildren().clear();
            return null;
        });
    }
}
