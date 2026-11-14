package shop.blinksmp.queue;

import java.util.Properties;

record QueueConfig(boolean enabled,int waitSeconds,int pollSeconds,int capacity){
    static QueueConfig read(Properties p){
        return new QueueConfig(Boolean.parseBoolean(p.getProperty("enabled","true")),
            number(p,"max-wait-seconds",600,15,3600),number(p,"poll-seconds",3,1,30),number(p,"max-waiting-players",100,1,1000));
    }
    private static int number(Properties p,String key,int fallback,int min,int max){int n=Integer.parseInt(p.getProperty(key,""+fallback).trim());if(n<min||n>max)throw new IllegalArgumentException("Invalid "+key);return n;}
}
