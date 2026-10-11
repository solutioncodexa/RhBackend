package ma.rhflow.domain.enums;

public enum SubscriptionPlan {
    ESSENTIEL(25, 10, false),
    PRO(100, 40, true),
    ENTREPRISE(500, 150, true);

    private final int maxEmployees;
    private final int maxUsers;
    private final boolean ollama;

    SubscriptionPlan(int maxEmployees, int maxUsers, boolean ollama) {
        this.maxEmployees = maxEmployees;
        this.maxUsers = maxUsers;
        this.ollama = ollama;
    }

    public int maxEmployees() {
        return maxEmployees;
    }

    public int maxUsers() {
        return maxUsers;
    }

    public boolean ollama() {
        return ollama;
    }
}
