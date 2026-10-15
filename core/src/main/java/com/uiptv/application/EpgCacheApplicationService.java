package com.uiptv.application;

import com.uiptv.db.EpgCacheDb;
import com.uiptv.model.*;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.AccountType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Read-only presentation boundary. No downloads, parsing, matching or cache writes occur here. */
public class EpgCacheApplicationService {
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(Exception error) { throw new IllegalStateException(error); }
    }
    static String sourceId(String source) { return hash("xml:"+source.strip()); }
    static String nativeSource(Account account, Channel channel) {
        return hash("native:utc-v2:"+account.getDbId()+"|"+account.getType()+"|"+(account.getUrl()==null || account.getUrl().isBlank() ? account.getServerPortalUrl() : account.getUrl())+"|"+account.getUsername()+"|"+account.getPassword()+"|"+account.getMacAddress()+"|"+channel.getChannelId());
    }
    private static org.json.JSONObject overrides(Configuration config,Account account) {
        try {
            var mappings=new org.json.JSONObject(config.getEpgMappingsJson()).optJSONObject(account.getDbId());
            return mappings==null ? new org.json.JSONObject() : mappings;
        } catch(Exception ignored) { return new org.json.JSONObject(); }
    }
    private static EpgCacheDb.Identity identity(Account account,Channel channel,Configuration config,org.json.JSONObject mappings) {
        String override=mappings.optString(channel.getChannelId(),"").strip();
        String signature=hash(nativeSource(account,channel)+"|"+account.getEpg()+"|"+account.getM3u8Path()+"|"+config.getGlobalEpgSource()
                +"|"+override+"|"+channel.getExtraJson()+"|"+channel.getName());
        return new EpgCacheDb.Identity(Objects.toString(account.getDbId(),""),Objects.toString(channel.getChannelId(),""),signature);
    }
    public Map<Channel,List<EpgProgramme>> summaries(Account account, List<Channel> channels) {
        var config=ConfigurationService.getInstance().read();
        Map<Channel,List<EpgProgramme>> result=new IdentityHashMap<>();
        if(!config.isEpgEnabled() || account==null || account.getAction()!=Account.AccountAction.itv) return result;
        var mappings=overrides(config,account);
        var ids=channels.stream().map(c -> identity(account,c,config,mappings)).toList();
        var records=new EpgCacheDb().summaries(ids,System.currentTimeMillis());
        for(int i=0;i<channels.size();i++) result.put(channels.get(i),records.getOrDefault(ids.get(i),List.of()));
        return result;
    }
    public Map<Channel,String> icons(Account account,List<Channel> channels) {
        var config=ConfigurationService.getInstance().read(); Map<Channel,String> result=new IdentityHashMap<>();
        if(!config.isEpgEnabled() || account==null || account.getAction()!=Account.AccountAction.itv || channels.isEmpty()) return result;
        var mappings=overrides(config,account); var ids=channels.stream().map(c -> identity(account,c,config,mappings)).toList();
        var records=new EpgCacheDb().icons(ids);
        for(int i=0;i<channels.size();i++) result.put(channels.get(i),records.getOrDefault(ids.get(i),""));
        return result;
    }
    public List<EpgProgramme> schedule(Account account, Channel channel) {
        var config=ConfigurationService.getInstance().read();
        if(!config.isEpgEnabled() || account==null || channel==null || account.getAction()!=Account.AccountAction.itv) return List.of();
        long now=System.currentTimeMillis();
        return new EpgCacheDb().schedule(identity(account,channel,config,overrides(config,account)),now,now-6*3_600_000L,now+7*86_400_000L);
    }
    static boolean nativeProvider(Account account) { return account.getType()==AccountType.STALKER_PORTAL || account.getType()==AccountType.XTREME_API; }
    static List<EpgCacheDb.Match> matches(Account account,List<Channel> channels,List<String> xmlSources) {
        var config=ConfigurationService.getInstance().read();
        var mappings=overrides(config,account);
        var xmlIds=xmlSources.stream().map(EpgCacheApplicationService::sourceId).toList();
        return channels.stream().map(channel -> {
            List<String> sources=new ArrayList<>();
            if(nativeProvider(account)) sources.add(nativeSource(account,channel));
            sources.addAll(xmlIds);
            String override=mappings.optString(channel.getChannelId(),"").strip();
            return new EpgCacheDb.Match(identity(account,channel,config,mappings),override.isBlank() ? EpgApplicationService.guideId(account,channel) : override,
                    XmltvGuide.normalize(channel.getName()),XmltvGuide.normalizeQuality(channel.getName()),!override.isBlank(),List.copyOf(sources),nativeProvider(account) ? nativeSource(account,channel) : "");
        }).toList();
    }
}
