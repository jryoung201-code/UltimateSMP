package shop.blinksmp.gui;

import java.util.UUID;

public final class GuiRulesTest {
    private static int checks;
    private static void check(boolean c,String m) { checks++; if(!c) throw new AssertionError(m); }
    public static void main(String[] args) {
        UUID owner=UUID.randomUUID(),other=UUID.randomUUID();
        check(GuiRules.OWNER_UUID.toString().equals("bd33980d-5b8d-4f1c-becc-0520f92c4a0d"),"Owner UUID must match the supplied account");
        check(!GuiRules.allowed(GuiRules.OWNER_UUID,other),"Other users cannot access the prebound panel");
        check(!GuiRules.allowed(null,owner),"Unbound panel must deny access");
        check(!GuiRules.allowed(owner,null),"No caller must be denied");
        check(!GuiRules.allowed(owner,other),"Different UUID must be denied even with OP/name/role");
        check(GuiRules.allowed(owner,owner),"Only bound owner UUID allowed");
        for(String command:new String[]{"ultimategui","/ugui","ULTIMATEGUI","ultimategui:ugui","/ultimategui:ultimategui"})
            check(GuiRules.command(command),"Known GUI route rejected");
        for(String command:new String[]{"gamemode creative","ugui-other","minecraft:ugui","op me","", "say ultimategui", "x".repeat(257)})
            check(!GuiRules.command(command),"Unrelated command intercepted");
        check(GuiRules.hiddenSuggestion("RealJR_25736",GuiRules.OWNER_NAME),"Hidden completion not filtered");
        check(GuiRules.hiddenSuggestion("@p[name=realjr_25736]",GuiRules.OWNER_NAME),"Selector completion not filtered");
        check(!GuiRules.hiddenSuggestion("OtherPlayer",GuiRules.OWNER_NAME),"Unrelated completion removed");
        check(GuiRules.visibleCount(1,true)==0,"Only vanished player should yield zero");
        check(GuiRules.visibleCount(0,true)==0,"Negative count must be clamped");
        check(GuiRules.visibleCount(5,true)==4,"Hidden count adjustment");
        check(GuiRules.visibleCount(5,false)==5,"Visible count unchanged");
        check(GuiRules.action("/ugui")==GuiRules.Action.OPEN,"Bare GUI route");
        for(String cmd:new String[]{"/guivanish","/UGV","/ugui vanish","/ultimategui:guivanish"}) check(GuiRules.action(cmd)==GuiRules.Action.TOGGLE,"Vanish toggle route");
        check(GuiRules.action("/guivanish on")==GuiRules.Action.ON,"Explicit on");
        check(GuiRules.action("/ugui vanish off")==GuiRules.Action.OFF,"Explicit off");
        check(GuiRules.action("/ugv status")==GuiRules.Action.STATUS,"Status route");
        for(String cmd:new String[]{"/guivanish op me","/ugui op me","/ugui vanish off extra","/vanish","/minecraft:guivanish"}) check(GuiRules.action(cmd)==GuiRules.Action.INVALID,"Unknown/other command must not execute");
        System.out.println("PASS: "+checks+" UUID authorization, command-scope and privacy checks");
    }
}

