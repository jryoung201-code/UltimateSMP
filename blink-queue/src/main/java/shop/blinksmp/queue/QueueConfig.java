package shop.blinksmp.queue;

import java.net.URI;
import java.util.Properties;

record QueueConfig(boolean enabled,String queue,String defaultServer,int waitSeconds,int pollSeconds,int pingSeconds,int capacity,URI startUri,int startRetry) {
    static QueueConfig read(Properties p,String envUrl){
        String queue=p.getProperty("queue-server","BlinkSMP-Queue-01").trim();
        String destination=p.getProperty("default-server","BlinkSMP-Overworld-01").trim();
        if(!queue.matches("[A-Za-z0-9_-]{1,80}")||!destination.matches("[A-Za-z0-9_-]{1,80}")||queue.equals(destination))throw new IllegalArgumentException("Distinct registered server IDs required");
        String url=envUrl==null?p.getProperty("queue-start-url","").trim():envUrl.trim();
        URI uri=url.isEmpty()?null:URI.create(url);
        if(uri!=null&&(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null))throw new IllegalArgumentException("Start endpoint must use HTTPS without embedded credentials");
        return new QueueConfig(Boolean.parseBoolean(p.getProperty("enabled","true")),queue,destination,
            number(p,"max-wait-seconds",180,15,600),number(p,"poll-seconds",3,1,30),number(p,"ping-timeout-seconds",3,1,10),number(p,"max-waiting-players",100,1,1000),uri,number(p,"start-retry-seconds",60,30,600));
    }
    private static int number(Properties p,String key,int fallback,int min,int max){int n=Integer.parseInt(p.getProperty(key,""+fallback).trim());if(n<min||n>max)throw new IllegalArgumentException("Invalid "+key);return n;}
}
