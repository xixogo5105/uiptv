package com.uiptv.ui.util;
import com.uiptv.ui.util.*;
import com.uiptv.ui.util.*;

import com.uiptv.ui.ChannelListUI;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;

public class ColoredStatusTableCell extends TableCell<ChannelListUI.ChannelItem, ChannelListUI.ChannelItem> {

    @Override
    protected void updateItem(ChannelListUI.ChannelItem item, boolean empty) {
        super.updateItem(item, empty);

        setText(empty ? "" : getItem().getChannelName());
        setGraphic(null);

        TableRow<ChannelListUI.ChannelItem> currentRow = getTableRow();

        if (!isEmpty() && getItem().getChannelName().startsWith("**")) {
            currentRow.setStyle("-fx-color:lightgreen");
        }
    }
}
