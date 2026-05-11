package com.uiptv.ui;
import com.uiptv.ui.util.*;
import com.uiptv.ui.util.*;

public class ThumbnailWatchingNowUI extends BaseWatchingNowUI {
    public ThumbnailWatchingNowUI() {
        this(JavaFxServices.current());
    }

    public ThumbnailWatchingNowUI(JavaFxServices services) {
        super(services);
    }

    @Override
    protected boolean thumbnailsEnabled() {
        return true;
    }
}
