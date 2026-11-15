package shop.blinksmp.network.paper;

/** Auxiliary worlds use explicit edit permission rather than survival-region ownership. */
final class WorldEditPolicy {
    private WorldEditPolicy() {}
    static boolean allowed(boolean fenced, boolean auxiliary, boolean controllerReady,
                           boolean editPermission, boolean ownedTerritory) {
        if (fenced) return false;
        return auxiliary ? controllerReady && editPermission : ownedTerritory;
    }
}
