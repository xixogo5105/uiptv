package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.shared.Pagination;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.uiptv.model.Account.AccountAction.series;
import static org.junit.jupiter.api.Assertions.*;

class ChannelAndCategoryParsingTest {

    @Test
    void getChannelOrSeriesParams_forSeries_setsSeriesSpecificFields() {
        Map<String, String> params = ChannelService.getChannelOrSeriesParams("cat-1", 3, series, null, null);

        assertEquals("series", params.get("type"));
        assertEquals("get_ordered_list", params.get("action"));
        assertEquals("cat-1", params.get("genre"));
        assertEquals("cat-1", params.get("category"));
        assertEquals("0", params.get("movie_id"));
        assertEquals("0", params.get("season_id"));
        assertEquals("0", params.get("episode_id"));
        assertEquals("3", params.get("p"));
        assertEquals("999", params.get("per_page"));
        assertNotNull(params.get("JsHttpRequest"));
        assertTrue(params.get("JsHttpRequest").endsWith("-xml"));
    }

    @Test
    void parsePagination_readsFromPaginationObject() {
        String json = """
                {
                  "pagination": {
                    "total_items": 120,
                    "max_page_items": 25
                  }
                }
                """;

        Pagination pagination = ChannelService.getInstance().parsePagination(json, null);

        assertNotNull(pagination);
        assertEquals(120, pagination.getMaxPageItems());
        assertEquals(25, pagination.getPaginationLimit());
        assertEquals(5, pagination.getPageCount());
    }

    @Test
    void parsePagination_fallsBackToJsObject() {
        String json = """
                {
                  "js": {
                    "total_items": 10,
                    "max_page_items": 4
                  }
                }
                """;

        Pagination pagination = ChannelService.getInstance().parsePagination(json, null);

        assertNotNull(pagination);
        assertEquals(10, pagination.getMaxPageItems());
        assertEquals(4, pagination.getPaginationLimit());
        assertEquals(3, pagination.getPageCount());
    }

    @Test
    void parseItvChannels_parsesDataAndCategoryId() {
        String json = """
                {
                  "js": {
                    "data": [
                      {
                        "id": "100",
                        "name": "News HD",
                        "number": "1",
                        "cmd": "ffmpeg http://test/news",
                        "cmd_1": "",
                        "cmd_2": "",
                        "cmd_3": "",
                        "logo": "logo1",
                        "censored": 0,
                        "status": 1,
                        "hd": 1,
                        "tv_genre_id": "55"
                      }
                    ]
                  }
                }
                """;

        List<Channel> channels = ChannelService.getInstance().parseItvChannels(json, false);

        assertEquals(1, channels.size());
        Channel channel = channels.get(0);
        assertEquals("100", channel.getChannelId());
        assertEquals("News HD", channel.getName());
        assertEquals("55", channel.getCategoryId());
        assertEquals("ffmpeg http://test/news", channel.getCmd());
    }

    @Test
    void parseVodChannels_seriesAction_expandsEpisodesAndSortsByEpisode() {
        Account account = new Account("acc", "u", "p", "http://x", null, null, null, null, null, null, AccountType.STALKER_PORTAL, null, null, false);
        account.setAction(series);

        String json = """
                {
                  "js": {
                    "data": [
                      {
                        "id": "7",
                        "name": "",
                        "o_name": "My Show",
                        "cmd": "ffmpeg http://vod/stream",
                        "tv_genre_id": "77",
                        "series": [2, 1],
                        "screenshot_uri": "shot"
                      }
                    ]
                  }
                }
                """;

        List<Channel> channels = ChannelService.getInstance().parseVodChannels(account, json, false);

        assertEquals(2, channels.size());
        assertEquals("My Show - Episode 1", channels.get(0).getName());
        assertEquals("My Show - Episode 2", channels.get(1).getName());
        assertEquals("77", channels.get(0).getCategoryId());
        assertEquals("ffmpeg http://vod/stream", channels.get(0).getCmd());
    }

    @Test
    void parseCategories_readsFieldsIncludingOptionalFlags() {
        String json = """
                {
                  "js": [
                    {
                      "id": "1",
                      "title": "Sports",
                      "alias": "sports",
                      "active_sub": true,
                      "censored": 0
                    },
                    {
                      "id": "2",
                      "title": "Adult",
                      "alias": "adult",
                      "active_sub": false,
                      "censored": 1
                    }
                  ]
                }
                """;

        List<Category> categories = CategoryService.getInstance().parseCategories(json, false);

        assertEquals(2, categories.size());
        assertEquals("1", categories.get(0).getCategoryId());
        assertTrue(categories.get(0).isActiveSub());
        assertEquals(0, categories.get(0).getCensored());
        assertEquals("2", categories.get(1).getCategoryId());
        assertFalse(categories.get(1).isActiveSub());
        assertEquals(1, categories.get(1).getCensored());
    }
}
