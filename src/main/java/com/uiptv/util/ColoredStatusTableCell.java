package com.uiptv.util;

import com.uiptv.ui.ChannelListUI;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableRow;

public class ColoredStatusTableCell extends TableCell<TableRow, ChannelListUI.ChannelItem> {

    @Override
    protected void updateItem(ChannelListUI.ChannelItem item, boolean empty) {
        super.updateItem(item, empty);

        setText(empty ? "" : getItem().getChannelName());
        setGraphic(null);

        TableRow currentRow = getTableRow();

        if (!isEmpty()) {

            if (getItem().getChannelName().startsWith("**"))
                currentRow.setStyle("-fx-color:lightgreen");
        }
    }
}