package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;

import java.io.IOException;

public interface AccountPlayerService {
    PlayerResponse get(Account account, Channel channel, String series, String parentSeriesId, String categoryId) throws IOException;
}
