package br.com.manager.utils;

public final class NegotiationRules {

    private NegotiationRules() {}

    public static String decision(int interest, double roll) {
        if ((interest >= 75 && roll < .70) || (interest >= 55 && roll < .25)) return "ACCEPTED";
        if (interest >= 45 && roll < .85) return "NEGOTIABLE";
        return "REJECTED";
    }
}
