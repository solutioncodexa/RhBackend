package ma.rhflow.assistant.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class AssistantIntentParser {

    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern DATE = Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b");

    public Optional<ParsedIntent> parse(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String raw = message.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        if (looksLikeHelp(lower)) {
            return Optional.of(new ParsedIntent("HELP", Map.of()));
        }
        if (lower.contains("bulletin") || lower.contains("fiche de paie") || lower.contains("payslip") || lower.contains("salaire net")) {
            return Optional.of(new ParsedIntent("EXPLAIN_PAYSLIP", Map.of()));
        }
        if (containsAny(lower, "cherche", "trouver", "recherche", "search") && containsAny(lower, "employé", "employe", "salarié", "salarie")) {
            Map<String, Object> draft = new LinkedHashMap<>();
            draft.put("q", extractAfter(lower, "employé", "employe", "salarié"));
            return Optional.of(new ParsedIntent("SEARCH_EMPLOYEE", draft));
        }
        if ((containsAny(lower, "créer", "creer", "ajoute", "ajouter", "nouvel")
                && containsAny(lower, "employé", "employe", "salarié", "salarie"))
                && !containsAny(lower, "cherche", "trouver", "recherche", "search")) {
            Map<String, Object> draft = new LinkedHashMap<>();
            fillName(raw, draft);
            Matcher em = EMAIL.matcher(raw);
            if (em.find()) {
                draft.put("email", em.group());
            }
            return Optional.of(new ParsedIntent("CREATE_EMPLOYEE", draft));
        }
        if (containsAny(lower, "ajoute", "ajouter", "c'est", "cest") && !containsAny(lower, "congé", "conge", "cherche", "recherche")) {
            Map<String, Object> named = personDraft(raw);
            if (named.containsKey("firstName") && named.containsKey("lastName")) {
                return Optional.of(new ParsedIntent("CREATE_EMPLOYEE", named));
            }
        }
        if (containsAny(lower, "pose un congé", "pose un conge", "poser un congé", "poser un conge", "demande de congé", "demande de conge", "créer un congé", "creer un conge", "nouveau congé", "nouveau conge")) {
            Map<String, Object> draft = new LinkedHashMap<>();
            Matcher d = DATE.matcher(raw);
            if (d.find()) {
                draft.put("startDate", d.group());
            }
            if (d.find()) {
                draft.put("endDate", d.group());
            }
            return Optional.of(new ParsedIntent("CREATE_LEAVE", draft));
        }
        if (containsAny(lower, "combien de soci", "quels plans", "plans sont", "sociétés actives", "societes actives")) {
            return Optional.of(new ParsedIntent("ASK", Map.of("topic", "platform")));
        }
        if (containsAny(lower, "effectif", "combien d'employ", "combien d employ", "combien de salari")) {
            return Optional.of(new ParsedIntent("ASK", Map.of("topic", "headcount")));
        }
        if (containsAny(lower, "en attente", "à approuver", "a approuver", "approuver")) {
            return Optional.of(new ParsedIntent("ASK", Map.of("topic", "pending-leave")));
        }
        if (containsAny(lower, "absent", "présence", "presence", "présent", "present", "aujourd")) {
            return Optional.of(new ParsedIntent("ASK", Map.of("topic", "attendance")));
        }
        if (containsAny(lower, "solde", "combien de jours", "congé restant", "conge restant")) {
            return Optional.of(new ParsedIntent("ASK", Map.of("topic", "leave-balance")));
        }
        return Optional.of(new ParsedIntent("NONE", Map.of()));
    }

    public static boolean looksLikeHelp(String lower) {
        return lower.contains("que peux-tu") || lower.contains("que peux tu")
                || lower.equals("aide") || lower.equals("help")
                || lower.contains("que peut-on");
    }

    public Map<String, Object> personDraft(String raw) {
        Map<String, Object> draft = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return draft;
        }
        fillName(raw, draft);
        Matcher em = EMAIL.matcher(raw);
        if (em.find()) {
            draft.put("email", em.group());
        }
        return draft;
    }

    private void fillName(String raw, Map<String, Object> draft) {
        String cleaned = stripInstruction(raw);
        if (cleaned.isBlank() || isInstructionOnly(cleaned)) {
            return;
        }
        String[] parts = cleaned.split("\\s+");
        if (parts.length >= 2 && looksLikeName(parts[0]) && looksLikeName(parts[1])) {
            draft.put("firstName", capitalize(parts[0]));
            draft.put("lastName", capitalize(parts[1]));
        }
    }

    private String stripInstruction(String raw) {
        return raw
                .replaceAll("(?i)\\b(créer|creer|ajoute[rz]?|ajouter|nouvel(?:le)?)\\b", " ")
                .replaceAll("(?i)\\b(un|une|le|la|l)\\b", " ")
                .replaceAll("(?i)\\b(employé|employe|salarié|salarie|fiche)\\b", " ")
                .replaceAll("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b", " ")
                .replaceAll("[^A-Za-zÀ-ÿ'\\-\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isInstructionOnly(String name) {
        String n = name.toLowerCase(Locale.ROOT).trim();
        return n.isBlank()
                || n.matches("(créer|creer|ajouter|ajoute|nouvel|nouvelle|un|une|employé|employe)+")
                || n.equals("créer un employé")
                || n.equals("creer un employe");
    }

    private boolean looksLikeName(String part) {
        if (part == null || part.length() < 2) {
            return false;
        }
        String n = part.toLowerCase(Locale.ROOT);
        return !Set.of(
                "créer", "creer", "ajoute", "ajouter", "nouvel", "nouvelle",
                "un", "une", "employé", "employe", "salarié", "salarie", "fiche",
                "cherche", "trouver", "recherche"
        ).contains(n) && part.matches("[A-Za-zÀ-ÿ][A-Za-zÀ-ÿ'\\-]+");
    }

    private String capitalize(String part) {
        if (part.isBlank()) {
            return part;
        }
        return part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1);
    }

    private String extractAfter(String lower, String... keys) {
        for (String k : keys) {
            int i = lower.indexOf(k);
            if (i >= 0) {
                return lower.substring(i + k.length()).replaceAll("[^a-zà-ÿ ]", "").trim();
            }
        }
        return "";
    }

    private boolean containsAny(String s, String... keys) {
        for (String k : keys) {
            if (s.contains(k)) {
                return true;
            }
        }
        return false;
    }

    public record ParsedIntent(String type, Map<String, Object> draft) {
    }
}
