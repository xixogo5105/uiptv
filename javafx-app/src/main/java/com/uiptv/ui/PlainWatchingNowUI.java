package com.uiptv.ui;
import com.uiptv.ui.util.*;
import com.uiptv.ui.util.*;

public class PlainWatchingNowUI extends BaseWatchingNowUI {
    public PlainWatchingNowUI() {
        this(JavaFxServices.current());
    }

    public PlainWatchingNowUI(JavaFxServices services) {
        super(services);
    }

    @Override
    protected boolean thumbnailsEnabled() {
        return false;
    }
}
