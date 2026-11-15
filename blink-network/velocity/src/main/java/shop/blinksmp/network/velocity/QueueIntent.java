package shop.blinksmp.network.velocity;

/** Queue origin is an explicit intent, never inferred from an offline backend. */
record QueueIntent(String origin,Mode mode,String rtpTarget) {
 enum Mode { JOIN,RESTART }
 static QueueIntent join(String origin){return new QueueIntent(origin,Mode.JOIN,null);}
 static QueueIntent restart(String origin){return new QueueIntent(origin,Mode.RESTART,null);}
 boolean canLeave(){return mode==Mode.RESTART;}
 boolean needsRtp(){return mode==Mode.JOIN&&rtpTarget==null;}
 QueueIntent resolved(String value){return new QueueIntent(value,mode,rtpTarget);}
 QueueIntent prepared(String value){return new QueueIntent(origin,mode,value);}
 QueueIntent leave(){if(!canLeave())throw new IllegalStateException("Leave is only available during a restart wait");return join(origin);}
 String key(){return mode+":"+origin+":"+rtpTarget;}
}
