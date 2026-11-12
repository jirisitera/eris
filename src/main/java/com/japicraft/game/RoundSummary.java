package com.japicraft.environment;

public record RoundSummary(String reason, int mistakes, int secondsRemaining, int solved, int total) {
    public String text() {
        return reason + "\n\nMistakes: " + mistakes
            + "\nTime remaining: " + String.format("%d:%02d", secondsRemaining / 60, secondsRemaining % 60)
            + "\nModules solved: " + solved + "/" + total
            + "\n\nChoose roles and ready up in the lobby to start a fresh round.";
    }
}
