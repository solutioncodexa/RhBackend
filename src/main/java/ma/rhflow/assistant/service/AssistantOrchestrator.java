package ma.rhflow.assistant.service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rhflow.common.exception.BusinessException;
import ma.rhflow.domain.entity.AssistantPendingAction;
import ma.rhflow.domain.entity.Employee;
import ma.rhflow.domain.entity.LeaveRequest;
import ma.rhflow.domain.entity.LeaveType;
import ma.rhflow.domain.entity.Payslip;
import ma.rhflow.domain.entity.Tenant;
import ma.rhflow.domain.enums.PermissionCodes;
import ma.rhflow.domain.enums.SubscriptionPlan;
import ma.rhflow.domain.enums.UserRole;
import ma.rhflow.domain.repository.AssistantPendingActionRepository;
import ma.rhflow.domain.repository.AttendanceRepository;
import ma.rhflow.domain.repository.EmployeeRepository;
import ma.rhflow.domain.repository.LeaveBalanceRepository;
import ma.rhflow.domain.repository.LeaveRequestRepository;
import ma.rhflow.domain.repository.LeaveTypeRepository;
import ma.rhflow.domain.repository.PayslipRepository;
import ma.rhflow.domain.repository.TenantRepository;
import ma.rhflow.security.UserPrincipal;
import ma.rhflow.service.CatalogService;
import ma.rhflow.service.LeaveService;
import ma.rhflow.service.PermissionService;
import ma.rhflow.tenant.TenantContext;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantOrchestrator {

    private final AssistantIntentParser parser;
    private final AssistantPendingActionRepository pendingRepository;
    private final TenantRepository tenantRepository;
    private final EmployeeRepository employeeRepository;
    private final PayslipRepository payslipRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final AttendanceRepository attendanceRepository;
    private final PermissionService permissionService;
    private final CatalogService catalogService;
    private final LeaveService leaveService;
    private final ObjectMapper objectMapper;

    @Value("${app.assistant.enabled:true}")
    private boolean enabled;
    @Value("${app.assistant.ollama.base-url:http://127.0.0.1:11434}")
    private String ollamaBase;
    @Value("${app.assistant.ollama.model:llama3.2:latest}")
    private String model;
    @Value("${app.assistant.ollama.timeout-seconds:20}")
    private int timeoutSeconds;

    public Map<String, Object> health(UserPrincipal actor) {
        allowAssistant(actor);
        boolean inPlan = actor.getRole() != UserRole.SUPER_ADMIN && includedInPlan();
        boolean ollama = inPlan && pingOllama();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled);
        m.put("ollamaAvailable", ollama);
        m.put("includedInPlan", inPlan || actor.getRole() == UserRole.SUPER_ADMIN);
        m.put("model", model);
        m.put("role", actor.getRole().name());
        m.put("roleLabel", roleLabel(actor.getRole()));
        m.put("suggestions", suggestions(actor.getRole()));
        String message = guide(actor.getRole());
        if (ollama) {
            message = message + " Les questions hors parcours passent par le modèle local.";
        }
        m.put("message", message);
        return m;
    }

    @Transactional
    public Map<String, Object> chat(Map<String, Object> body, UserPrincipal actor) {
        allowAssistant(actor);
        String message = String.valueOf(body.getOrDefault("message", ""));
        String sessionId = body.get("sessionId") == null ? UUID.randomUUID().toString() : String.valueOf(body.get("sessionId"));
        String locale = String.valueOf(body.getOrDefault("locale", "fr"));
        var parsed = parser.parse(message).orElse(new AssistantIntentParser.ParsedIntent("NONE", Map.of()));
        String type = parsed.type();
        Map<String, Object> draft = new LinkedHashMap<>(parsed.draft());
        String activeFlow = intakeFlow(body);
        boolean answering = false;
        if (activeFlow != null && !interruptsIntake(message, activeFlow)) {
            type = activeFlow;
            draft = intakeDraft(body);
            answering = true;
        } else if ("NONE".equals(type) && awaitingEmployeeName(body)) {
            Map<String, Object> named = parser.personDraft(message);
            if (named.containsKey("firstName") && named.containsKey("lastName")) {
                type = "CREATE_EMPLOYEE";
                draft.putAll(named);
            } else {
                type = "NEED_NAME";
            }
        }
        boolean confirm = false;
        String intakeNext = null;
        String intakeFlowOut = null;
        String actionId = null;
        String reply;
        boolean ollamaUp = actor.getRole() != UserRole.SUPER_ADMIN && includedInPlan() && pingOllama();
        if ("HELP".equals(type)) {
            reply = capabilities(actor.getRole());
        } else if ("SEARCH_EMPLOYEE".equals(type)) {
            if (!permissionService.has(actor, PermissionCodes.EMPLOYEES_VIEW) || actor.getRole() == UserRole.SUPER_ADMIN) {
                reply = searchEmployees(actor, "");
            } else {
                String error = answering ? applySearchAnswer(message, draft) : null;
                if (error != null) {
                    reply = error + " Quel nom ou e-mail dois-je chercher ?";
                    intakeNext = "q";
                    intakeFlowOut = "SEARCH_EMPLOYEE";
                } else if (!filled(draft, "q")) {
                    reply = "Avant de chercher, quel nom ou e-mail ?";
                    intakeNext = "q";
                    intakeFlowOut = "SEARCH_EMPLOYEE";
                } else {
                    reply = searchEmployees(actor, String.valueOf(draft.get("q")));
                }
            }
        } else if ("EXPLAIN_PAYSLIP".equals(type)) {
            if (!permissionService.has(actor, PermissionCodes.PAYROLL_VIEW)) {
                reply = explainPayslip(actor, message);
            } else {
                String error = answering ? applyEmployeePick(message, draft, actor, true) : null;
                if (!answering && !filled(draft, "employeeName")) {
                    Employee named = matchEmployee(message, actor, false);
                    if (named != null) {
                        draft.put("employeeId", named.getId());
                        draft.put("employeeName", fullName(named));
                    }
                }
                if (error != null) {
                    reply = error + " Pour quel employé dois-je expliquer le dernier bulletin ?";
                    intakeNext = "employee";
                    intakeFlowOut = "EXPLAIN_PAYSLIP";
                } else if (!filled(draft, "employeeName")) {
                    reply = "Avant d'expliquer un bulletin, pour quel employé ?";
                    intakeNext = "employee";
                    intakeFlowOut = "EXPLAIN_PAYSLIP";
                } else {
                    reply = explainPayslipFor(actor, longValue(draft.get("employeeId")));
                }
            }
        } else if ("ASK".equals(type)) {
            reply = answerTopic(actor, String.valueOf(draft.getOrDefault("topic", "")), message);
        } else if ("CREATE_EMPLOYEE".equals(type) || "NEED_NAME".equals(type)) {
            if (!permissionService.has(actor, PermissionCodes.EMPLOYEES_CREATE)) {
                reply = "Créer un employé est réservé à l'administrateur. " + capabilities(actor.getRole());
            } else {
                String error = null;
                String currentStep = answering ? intakeStep(body) : null;
                if (answering) {
                    error = applyHireAnswer(currentStep, message, draft);
                }
                String next = nextHireStep(draft);
                if (error != null) {
                    reply = error + " " + hireQuestion(currentStep, draft);
                    intakeNext = currentStep;
                    intakeFlowOut = "CREATE_EMPLOYEE";
                } else if (next != null) {
                    String intro = "name".equals(next)
                            ? "Je vais compléter la fiche avant toute création : identité, contrat, puis salaire. "
                            : "";
                    reply = intro + hireQuestion(next, draft);
                    intakeNext = next;
                    intakeFlowOut = "CREATE_EMPLOYEE";
                } else {
                    confirm = true;
                    actionId = savePending("CREATE_EMPLOYEE", draft, actor);
                    reply = hireRecap(draft);
                }
            }
        } else if ("CREATE_LEAVE".equals(type)) {
            if (!permissionService.has(actor, PermissionCodes.LEAVE_REQUEST)) {
                reply = "Poser un congé n'est pas ouvert pour ce rôle. " + capabilities(actor.getRole());
            } else if (leaveTypeRepository.findAll().isEmpty()) {
                reply = "Aucun type de congé n'est configuré pour cette société.";
            } else {
                if (actor.getRole() == UserRole.EMPLOYEE && actor.getEmployeeId() != null && !filled(draft, "employeeId")) {
                    draft.put("employeeId", actor.getEmployeeId());
                    draft.put("employeeName", "vous");
                }
                String error = null;
                String currentStep = answering ? intakeStep(body) : null;
                if (answering) {
                    error = applyLeaveAnswer(currentStep, message, draft, actor);
                }
                String next = nextLeaveStep(draft, actor);
                if (error != null) {
                    reply = error + " " + leaveQuestion(currentStep, actor);
                    intakeNext = currentStep;
                    intakeFlowOut = "CREATE_LEAVE";
                } else if (next != null) {
                    String intro = ("employee".equals(next) || ("leaveType".equals(next) && actor.getRole() == UserRole.EMPLOYEE))
                            ? "Je vais préparer la demande avant toute création. "
                            : "";
                    reply = intro + leaveQuestion(next, actor);
                    intakeNext = next;
                    intakeFlowOut = "CREATE_LEAVE";
                } else {
                    confirm = true;
                    actionId = savePending("CREATE_LEAVE", draft, actor);
                    reply = leaveRecap(draft);
                }
            }
        } else {
            String ollama = ollamaUp ? tryOllama(message, locale, actor) : null;
            reply = ollama != null ? ollama : ("Je n'ai pas reconnu cette demande. " + capabilities(actor.getRole()));
            type = ollama != null ? "ASK" : "NONE";
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reply", reply);
        out.put("replyAr", reply);
        out.put("actionType", type);
        out.put("requiresConfirmation", confirm);
        out.put("actionId", actionId);
        out.put("draft", draft);
        out.put("sessionId", sessionId);
        out.put("ollamaAvailable", ollamaUp);
        out.put("role", actor.getRole().name());
        if (intakeNext != null && intakeFlowOut != null) {
            Map<String, Object> intake = new LinkedHashMap<>();
            intake.put("flow", intakeFlowOut);
            intake.put("step", intakeNext);
            intake.put("draft", draft);
            out.put("intake", intake);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> confirm(Map<String, Object> body, UserPrincipal actor) {
        permissionService.require(actor, PermissionCodes.ASSISTANT_USE);
        String actionId = String.valueOf(body.get("actionId"));
        AssistantPendingAction pending = pendingRepository.findByActionId(actionId)
                .orElseThrow(() -> BusinessException.notFound("Action introuvable"));
        if (pending.isConsumed() || Instant.now().isAfter(pending.getExpiresAt())) {
            throw BusinessException.badRequest("Action expirée");
        }
        if (!actor.getId().equals(pending.getUserId())) {
            throw BusinessException.forbidden("Action d'un autre utilisateur");
        }
        Map<String, Object> draft;
        try {
            draft = objectMapper.readValue(pending.getDraftJson(), new TypeReference<>() {
            });
        } catch (Exception e) {
            draft = new LinkedHashMap<>();
        }
        Object result;
        if ("CREATE_EMPLOYEE".equals(pending.getActionType())) {
            result = catalogService.hire(hireBody(draft), null, actor);
        } else if ("CREATE_LEAVE".equals(pending.getActionType())) {
            result = leaveService.create(draft, actor);
        } else {
            throw BusinessException.badRequest("Action non exécutable");
        }
        pending.setConsumed(true);
        pendingRepository.save(pending);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("message", "Création effectuée.");
        out.put("result", result);
        return out;
    }

    private String intakeFlow(Map<String, Object> body) {
        Object raw = body.get("intake");
        if (!(raw instanceof Map<?, ?> intake) || intake.get("flow") == null) {
            return null;
        }
        String flow = String.valueOf(intake.get("flow"));
        return switch (flow) {
            case "CREATE_EMPLOYEE", "CREATE_LEAVE", "SEARCH_EMPLOYEE", "EXPLAIN_PAYSLIP" -> flow;
            default -> null;
        };
    }

    private boolean interruptsIntake(String message, String currentFlow) {
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (AssistantIntentParser.looksLikeHelp(lower)) {
            return true;
        }
        boolean hire = (lower.contains("créer") || lower.contains("creer") || lower.contains("ajoute") || lower.contains("ajouter"))
                && (lower.contains("employé") || lower.contains("employe") || lower.contains("salarié") || lower.contains("salarie"));
        boolean leave = lower.contains("pose un congé") || lower.contains("pose un conge")
                || lower.contains("poser un congé") || lower.contains("poser un conge")
                || lower.contains("demande de congé") || lower.contains("demande de conge")
                || lower.contains("nouveau congé") || lower.contains("nouveau conge");
        boolean search = (lower.contains("cherche") || lower.contains("recherche"))
                && (lower.contains("employé") || lower.contains("employe"));
        boolean slip = lower.contains("bulletin") || lower.contains("fiche de paie");
        if (hire && !"CREATE_EMPLOYEE".equals(currentFlow)) {
            return true;
        }
        if (leave && !"CREATE_LEAVE".equals(currentFlow)) {
            return true;
        }
        if (search && !"SEARCH_EMPLOYEE".equals(currentFlow)) {
            return true;
        }
        if (slip && !"EXPLAIN_PAYSLIP".equals(currentFlow)) {
            return true;
        }
        return false;
    }

    private Map<String, Object> intakeDraft(Map<String, Object> body) {
        Object raw = body.get("intake");
        if (!(raw instanceof Map<?, ?> intake)) {
            return new LinkedHashMap<>();
        }
        if (intakeFlow(body) == null) {
            return new LinkedHashMap<>();
        }
        Object source = intake.get("draft");
        Map<String, Object> draft = new LinkedHashMap<>();
        if (source instanceof Map<?, ?> map) {
            map.forEach((key, value) -> draft.put(String.valueOf(key), value));
        }
        return draft;
    }

    private String intakeStep(Map<String, Object> body) {
        Object raw = body.get("intake");
        if (raw instanceof Map<?, ?> intake && intake.get("step") != null) {
            return String.valueOf(intake.get("step"));
        }
        return "name";
    }

    private String nextHireStep(Map<String, Object> draft) {
        if (!filled(draft, "firstName") || !filled(draft, "lastName")) {
            return "name";
        }
        if (!filled(draft, "email")) {
            return "email";
        }
        if (!draft.containsKey("cin")) {
            return "cin";
        }
        if (!draft.containsKey("phone")) {
            return "phone";
        }
        if (!draft.containsKey("city")) {
            return "city";
        }
        if (!filled(draft, "contractType")) {
            return "contractType";
        }
        if (!filled(draft, "startDate")) {
            return "startDate";
        }
        if (needsEnd(draft) && !filled(draft, "endDate")) {
            return "endDate";
        }
        if (!filled(draft, "baseSalary")) {
            return "baseSalary";
        }
        if (!draft.containsKey("weeklyHours")) {
            return "weeklyHours";
        }
        return null;
    }

    private String hireQuestion(String step, Map<String, Object> draft) {
        String who = filled(draft, "firstName") ? draft.get("firstName") + " " + draft.get("lastName") : "cette personne";
        return switch (step) {
            case "name" -> "Quel est le prénom et le nom ? Exemple : Ali Bennani.";
            case "email" -> "Quel est l'e-mail de " + who + " ?";
            case "cin" -> "Quel est le CIN ? Répondez « passer » si vous ne l'avez pas.";
            case "phone" -> "Quel est le téléphone ? « passer » si inconnu.";
            case "city" -> "Dans quelle ville travaille " + who + " ? « passer » si inconnu.";
            case "contractType" -> "Quel type de contrat : CDI, CDD, stage ou intérim ?";
            case "startDate" -> "Quelle est la date de début du contrat ? Format AAAA-MM-JJ, ou « aujourd'hui ».";
            case "endDate" -> "Quelle est la date de fin ? Elle est obligatoire pour un CDD, un stage ou un intérim.";
            case "baseSalary" -> "Quel est le salaire de base mensuel, en MAD ?";
            case "weeklyHours" -> "Combien d'heures par semaine ? « passer » retient 44 h.";
            default -> "Précisez cette information.";
        };
    }

    private String applyHireAnswer(String step, String message, Map<String, Object> draft) {
        String text = message == null ? "" : message.trim();
        boolean skip = isSkip(text);
        return switch (step) {
            case "name" -> {
                Map<String, Object> named = parser.personDraft(text);
                if (!named.containsKey("firstName") || !named.containsKey("lastName")) {
                    yield "Il me faut le prénom et le nom.";
                }
                draft.putAll(named);
                yield null;
            }
            case "email" -> {
                java.util.regex.Matcher email = java.util.regex.Pattern
                        .compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b")
                        .matcher(text);
                if (!email.find()) {
                    yield "Cet e-mail n'est pas valide.";
                }
                draft.put("email", email.group());
                yield null;
            }
            case "cin" -> {
                if (skip) {
                    draft.put("cin", "");
                    yield null;
                }
                String cin = text.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
                if (cin.length() < 4) {
                    yield "Le CIN doit comporter au moins 4 caractères.";
                }
                draft.put("cin", cin);
                yield null;
            }
            case "phone" -> {
                if (skip) {
                    draft.put("phone", "");
                    yield null;
                }
                String digits = text.replaceAll("\\D", "");
                if (digits.length() < 8) {
                    yield "Le téléphone doit contenir au moins 8 chiffres.";
                }
                draft.put("phone", text.trim());
                yield null;
            }
            case "city" -> {
                if (skip) {
                    draft.put("city", "");
                    yield null;
                }
                if (text.length() < 2) {
                    yield "Indiquez une ville.";
                }
                draft.put("city", capitalizeWord(text));
                yield null;
            }
            case "contractType" -> {
                String type = contractType(text);
                if (type == null) {
                    yield "Choisissez CDI, CDD, stage ou intérim.";
                }
                draft.put("contractType", type);
                yield null;
            }
            case "startDate", "endDate" -> {
                String date = parseLooseDate(text);
                if (date == null) {
                    yield "Utilisez une date AAAA-MM-JJ, ou « aujourd'hui ».";
                }
                draft.put(step, date);
                yield null;
            }
            case "baseSalary" -> {
                BigDecimal amount = amount(text);
                if (amount == null || amount.signum() <= 0) {
                    yield "Indiquez un salaire en MAD, par exemple 8000.";
                }
                draft.put("baseSalary", amount.toPlainString());
                yield null;
            }
            case "weeklyHours" -> {
                if (skip) {
                    draft.put("weeklyHours", "44");
                    yield null;
                }
                BigDecimal hours = amount(text);
                if (hours == null || hours.signum() <= 0) {
                    yield "Indiquez un nombre d'heures, ou « passer » pour 44 h.";
                }
                draft.put("weeklyHours", hours.toPlainString());
                yield null;
            }
            default -> null;
        };
    }

    private String hireRecap(Map<String, Object> draft) {
        String end = needsEnd(draft) ? " au " + draft.get("endDate") : "";
        return "Récapitulatif de " + draft.get("firstName") + " " + draft.get("lastName")
                + " — " + emptyAs(draft.get("email"), "sans e-mail")
                + ", CIN " + emptyAs(draft.get("cin"), "non renseigné")
                + ", tél. " + emptyAs(draft.get("phone"), "non renseigné")
                + ", " + emptyAs(draft.get("city"), "ville non renseignée")
                + ". Contrat " + draft.get("contractType") + " du " + draft.get("startDate") + end
                + ", salaire " + draft.get("baseSalary") + " MAD, " + draft.get("weeklyHours") + " h/semaine. "
                + "Rien n'est créé tant que vous n'avez pas confirmé.";
    }

    private Map<String, Object> hireBody(Map<String, Object> draft) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("firstName", draft.get("firstName"));
        body.put("lastName", draft.get("lastName"));
        body.put("email", blankToNull(draft.get("email")));
        body.put("cin", blankToNull(draft.get("cin")));
        body.put("phone", blankToNull(draft.get("phone")));
        body.put("city", blankToNull(draft.get("city")));
        body.put("hireDate", draft.get("startDate"));
        body.put("status", "ACTIVE");
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("type", draft.get("contractType"));
        contract.put("startDate", draft.get("startDate"));
        contract.put("endDate", blankToNull(draft.get("endDate")));
        contract.put("baseSalary", draft.get("baseSalary"));
        contract.put("weeklyHours", draft.getOrDefault("weeklyHours", "44"));
        body.put("contract", contract);
        return body;
    }

    private boolean filled(Map<String, Object> draft, String key) {
        Object value = draft.get(key);
        return value != null && !String.valueOf(value).isBlank();
    }

    private boolean needsEnd(Map<String, Object> draft) {
        String type = String.valueOf(draft.get("contractType"));
        return "CDD".equals(type) || "STAGE".equals(type) || "INTERIM".equals(type);
    }

    private boolean isSkip(String text) {
        String n = text.toLowerCase(Locale.ROOT);
        return n.equals("passer") || n.equals("ignorer") || n.equals("skip") || n.equals("non") || n.equals("aucun") || n.equals("aucune");
    }

    private String contractType(String text) {
        String n = text.toLowerCase(Locale.ROOT);
        if (n.contains("intérim") || n.contains("interim")) {
            return "INTERIM";
        }
        if (n.contains("stage")) {
            return "STAGE";
        }
        if (n.contains("cdd")) {
            return "CDD";
        }
        if (n.contains("cdi")) {
            return "CDI";
        }
        return null;
    }

    private String parseLooseDate(String text) {
        String n = text.toLowerCase(Locale.ROOT);
        if (n.contains("aujourd") || n.equals("today")) {
            return LocalDate.now().toString();
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(\\d{4}-\\d{2}-\\d{2})\\b").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        try {
            return LocalDate.parse(matcher.group(1)).toString();
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal amount(String text) {
        String digits = text.replace(" ", "").replace(",", ".");
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(digits);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String fold(String text) {
        if (text == null) {
            return "";
        }
        return java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    private String capitalizeWord(String text) {
        String cleaned = text.trim();
        if (cleaned.isBlank()) {
            return cleaned;
        }
        return cleaned.substring(0, 1).toUpperCase(Locale.ROOT) + cleaned.substring(1);
    }

    private String emptyAs(Object value, String fallback) {
        return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value);
    }

    private Object blankToNull(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : value;
    }

    private String nextLeaveStep(Map<String, Object> draft, UserPrincipal actor) {
        if (actor.getRole() != UserRole.EMPLOYEE && !filled(draft, "employeeId")) {
            return "employee";
        }
        if (!filled(draft, "leaveTypeId")) {
            return "leaveType";
        }
        if (!filled(draft, "startDate")) {
            return "startDate";
        }
        if (!filled(draft, "endDate")) {
            return "endDate";
        }
        if (!draft.containsKey("comment")) {
            return "comment";
        }
        return null;
    }

    private String leaveQuestion(String step, UserPrincipal actor) {
        return switch (step) {
            case "employee" -> actor.getEmployeeId() == null
                    ? "Pour quel employé ? Indiquez le prénom et le nom."
                    : "Pour qui est ce congé ? Indiquez un nom, ou « moi ».";
            case "leaveType" -> "Quel type de congé : " + leaveTypeChoices() + " ?";
            case "startDate" -> "Quelle est la date de début ? Format AAAA-MM-JJ, ou « aujourd'hui ».";
            case "endDate" -> "Quelle est la date de fin ? Format AAAA-MM-JJ.";
            case "comment" -> "Quel est le motif ? « passer » si vous n'en avez pas.";
            default -> "Précisez cette information.";
        };
    }

    private String applyLeaveAnswer(String step, String message, Map<String, Object> draft, UserPrincipal actor) {
        String text = message == null ? "" : message.trim();
        return switch (step) {
            case "employee" -> applyEmployeePick(text, draft, actor, true);
            case "leaveType" -> {
                LeaveType type = matchLeaveType(text);
                if (type == null) {
                    yield "Choisissez un type parmi : " + leaveTypeChoices() + ".";
                }
                draft.put("leaveTypeId", type.getId());
                draft.put("leaveTypeName", type.getName());
                yield null;
            }
            case "startDate", "endDate" -> {
                String date = parseLooseDate(text);
                if (date == null) {
                    yield "Utilisez une date AAAA-MM-JJ, ou « aujourd'hui ».";
                }
                if ("endDate".equals(step) && filled(draft, "startDate") && date.compareTo(String.valueOf(draft.get("startDate"))) < 0) {
                    yield "La date de fin doit être le même jour ou après le début.";
                }
                draft.put(step, date);
                yield null;
            }
            case "comment" -> {
                draft.put("comment", isSkip(text) ? "" : text);
                yield null;
            }
            default -> null;
        };
    }

    private String leaveRecap(Map<String, Object> draft) {
        String who = emptyAs(draft.get("employeeName"), "cet employé");
        String motif = emptyAs(draft.get("comment"), "sans motif");
        return "Demande de " + emptyAs(draft.get("leaveTypeName"), "congé")
                + " pour " + who + ", du " + draft.get("startDate") + " au " + draft.get("endDate")
                + ", motif : " + motif + ". Rien n'est créé tant que vous n'avez pas confirmé.";
    }

    private String leaveTypeChoices() {
        String names = leaveTypeRepository.findAll().stream()
                .map(LeaveType::getName)
                .filter(n -> n != null && !n.isBlank())
                .collect(Collectors.joining(", "));
        return names.isBlank() ? "annuel" : names;
    }

    private LeaveType matchLeaveType(String text) {
        String n = fold(text);
        if (n.isBlank()) {
            return null;
        }
        List<LeaveType> types = leaveTypeRepository.findAll();
        List<LeaveType> hits = types.stream().filter(t -> {
            String name = fold(t.getName());
            String code = fold(t.getCode());
            return !name.isBlank() && (name.contains(n) || n.contains(name) || code.contains(n) || n.contains(code));
        }).toList();
        if (hits.size() == 1) {
            return hits.get(0);
        }
        if (n.contains("annuel") || n.equals("annual")) {
            return types.stream().filter(t -> "ANNUAL".equalsIgnoreCase(t.getCode())).findFirst().orElse(null);
        }
        return null;
    }

    private String applySearchAnswer(String message, Map<String, Object> draft) {
        String text = message == null ? "" : message.trim();
        if (text.length() < 2 || isSkip(text)) {
            return "Indiquez au moins deux lettres.";
        }
        draft.put("q", text);
        return null;
    }

    private String applyEmployeePick(String message, Map<String, Object> draft, UserPrincipal actor, boolean allowSelf) {
        String text = message == null ? "" : message.trim();
        String n = text.toLowerCase(Locale.ROOT);
        if (allowSelf && actor.getEmployeeId() != null && (n.equals("moi") || n.equals("pour moi") || n.contains("moi-même") || n.contains("moi meme"))) {
            draft.put("employeeId", actor.getEmployeeId());
            draft.put("employeeName", employeeName(actor.getEmployeeId()));
            return null;
        }
        List<Employee> hits = findEmployees(text, actor);
        if (hits.isEmpty()) {
            return "Je ne trouve pas cette personne dans votre périmètre.";
        }
        if (hits.size() > 1) {
            String names = hits.stream().limit(4).map(this::fullName).collect(Collectors.joining(", "));
            return "Plusieurs personnes correspondent (" + names + "). Précisez le nom complet.";
        }
        draft.put("employeeId", hits.get(0).getId());
        draft.put("employeeName", fullName(hits.get(0)));
        return null;
    }

    private Employee matchEmployee(String text, UserPrincipal actor, boolean allowLoose) {
        List<Employee> hits = findEmployees(text, actor);
        if (hits.size() == 1) {
            return hits.get(0);
        }
        if (!allowLoose) {
            String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
            return scope(actor).stream()
                    .filter(e -> fullName(e).length() > 2 && lower.contains(fullName(e).toLowerCase(Locale.ROOT)))
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private List<Employee> findEmployees(String text, UserPrincipal actor) {
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) {
            return List.of();
        }
        return scope(actor).stream().filter(e -> {
            String name = fullName(e).toLowerCase(Locale.ROOT);
            String email = e.getEmail() == null ? "" : e.getEmail().toLowerCase(Locale.ROOT);
            return name.contains(q) || q.contains(name) || (!email.isBlank() && (email.contains(q) || q.contains(email)));
        }).limit(6).toList();
    }

    private String explainPayslipFor(UserPrincipal actor, Long employeeId) {
        if (employeeId == null) {
            return "Indiquez l'employé dont vous voulez le bulletin.";
        }
        List<Payslip> slips = payslipRepository.findAll().stream()
                .filter(p -> employeeId.equals(p.getEmployeeId()))
                .toList();
        Payslip last = latest(slips);
        if (last == null) {
            return "Aucun bulletin calculé pour " + employeeName(employeeId) + ".";
        }
        return "Bulletin de " + employeeName(employeeId) + " — " + last.getMonth() + "/" + last.getYear()
                + " : net " + money(last.getNet()) + " MAD, CNSS " + money(last.getCnss())
                + ", AMO " + money(last.getAmo()) + ", IR " + money(last.getIr()) + ".";
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value).replace(".0", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean awaitingEmployeeName(Map<String, Object> body) {
        Object history = body.get("history");
        if (!(history instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        for (int i = list.size() - 1; i >= 0; i--) {
            if (!(list.get(i) instanceof Map<?, ?> row)) {
                continue;
            }
            String role = String.valueOf(row.get("role"));
            if (!"assistant".equalsIgnoreCase(role)) {
                continue;
            }
            Object contentObj = row.get("content");
            if (contentObj == null) {
                contentObj = row.get("text");
            }
            String content = String.valueOf(contentObj == null ? "" : contentObj).toLowerCase(Locale.ROOT);
            return content.contains("prénom") || content.contains("prenom");
        }
        return false;
    }

    private void allowAssistant(UserPrincipal actor) {
        if (actor.getRole() == UserRole.SUPER_ADMIN) {
            return;
        }
        permissionService.require(actor, PermissionCodes.ASSISTANT_USE);
    }

    private String savePending(String type, Map<String, Object> draft, UserPrincipal actor) {
        String actionId = UUID.randomUUID().toString();
        AssistantPendingAction action = new AssistantPendingAction();
        action.setTenantId(TenantContext.getTenantId());
        action.setActionId(actionId);
        action.setActionType(type);
        action.setUserId(actor.getId());
        action.setExpiresAt(Instant.now().plusSeconds(900));
        try {
            action.setDraftJson(objectMapper.writeValueAsString(draft));
        } catch (Exception e) {
            action.setDraftJson("{}");
        }
        pendingRepository.save(action);
        return actionId;
    }

    private void ensureLeaveType(Map<String, Object> draft) {
        if (draft.get("leaveTypeId") != null) {
            return;
        }
        LeaveType annual = leaveTypeRepository.findAll().stream()
                .filter(t -> "ANNUAL".equalsIgnoreCase(t.getCode()))
                .findFirst()
                .orElseGet(() -> leaveTypeRepository.findAll().stream().findFirst().orElse(null));
        if (annual != null) {
            draft.put("leaveTypeId", annual.getId());
        }
    }

    private String searchEmployees(UserPrincipal actor, String query) {
        if (actor.getRole() == UserRole.SUPER_ADMIN) {
            return "La recherche de salariés se fait dans chaque société, pas depuis le rôle plateforme. " + capabilities(actor.getRole());
        }
        if (!permissionService.has(actor, PermissionCodes.EMPLOYEES_VIEW)) {
            return "Vous consultez uniquement votre propre fiche. Demandez votre solde de congé, votre présence ou votre bulletin.";
        }
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Employee> scope = scope(actor);
        if (actor.getRole() == UserRole.MANAGER && q.isBlank() && actor.getEmployeeId() != null) {
            Long self = actor.getEmployeeId();
            List<Employee> team = scope.stream().filter(e -> !self.equals(e.getId())).toList();
            if (!team.isEmpty()) {
                scope = team;
            }
        }
        List<Employee> found = scope.stream()
                .filter(e -> q.isBlank() || fullName(e).toLowerCase(Locale.ROOT).contains(q)
                        || (e.getEmail() != null && e.getEmail().toLowerCase(Locale.ROOT).contains(q)))
                .limit(8)
                .toList();
        if (found.isEmpty()) {
            return actor.getRole() == UserRole.MANAGER
                    ? "Aucun salarié de votre équipe ne correspond."
                    : "Aucun employé trouvé.";
        }
        String label = actor.getRole() == UserRole.MANAGER ? "Votre équipe" : "Résultats";
        return label + " : " + found.stream().map(this::fullName).collect(Collectors.joining(", ")) + ".";
    }

    private String explainPayslip(UserPrincipal actor, String message) {
        if (actor.getRole() == UserRole.SUPER_ADMIN || actor.getRole() == UserRole.MANAGER
                || (!permissionService.has(actor, PermissionCodes.PAYROLL_VIEW)
                && !permissionService.has(actor, PermissionCodes.PAYSLIPS_VIEW_OWN))) {
            if (actor.getRole() == UserRole.MANAGER) {
                return "La paie n'est pas visible pour le manager. Vous pouvez suivre l'équipe, les absences et les congés à valider.";
            }
            if (actor.getRole() == UserRole.SUPER_ADMIN) {
                return "Les bulletins restent dans chaque société. Ce rôle ne lit pas la paie.";
            }
            return "Vous n'avez pas accès aux bulletins.";
        }
        boolean payroll = permissionService.has(actor, PermissionCodes.PAYROLL_VIEW);
        String lower = message.toLowerCase(Locale.ROOT);
        List<Payslip> slips = payslipRepository.findAll().stream()
                .filter(p -> payroll || (actor.getEmployeeId() != null && actor.getEmployeeId().equals(p.getEmployeeId())))
                .toList();
        if (payroll) {
            Employee named = scope(actor).stream()
                    .filter(e -> fullName(e).length() > 2 && lower.contains(fullName(e).toLowerCase(Locale.ROOT)))
                    .findFirst()
                    .orElse(null);
            if (named != null) {
                slips = slips.stream().filter(p -> named.getId().equals(p.getEmployeeId())).toList();
            }
        }
        Payslip last = latest(slips);
        if (last == null) {
            return "Aucun bulletin calculé pour ce périmètre.";
        }
        String who = payroll ? employeeName(last.getEmployeeId()) : "vous";
        return "Bulletin de " + who + " — " + last.getMonth() + "/" + last.getYear()
                + " : net " + money(last.getNet()) + " MAD, CNSS " + money(last.getCnss())
                + ", AMO " + money(last.getAmo()) + ", IR " + money(last.getIr()) + ".";
    }

    private String answerTopic(UserPrincipal actor, String topic, String message) {
        if (actor.getRole() == UserRole.SUPER_ADMIN) {
            return platformSummary();
        }
        return switch (topic) {
            case "leave-balance" -> leaveBalanceReply(actor, message);
            case "attendance" -> attendanceReply(actor);
            case "pending-leave" -> pendingLeaveReply(actor);
            case "headcount" -> headcountReply(actor);
            case "platform" -> capabilities(actor.getRole());
            default -> capabilities(actor.getRole());
        };
    }

    private String leaveBalanceReply(UserPrincipal actor, String message) {
        if (!permissionService.has(actor, PermissionCodes.LEAVE_VIEW)) {
            return "Les soldes de congé ne sont pas ouverts pour ce rôle.";
        }
        int year = LocalDate.now().getYear();
        List<Employee> people = peopleForQuestion(actor, message);
        if (people.isEmpty()) {
            return actor.getRole() == UserRole.MANAGER
                    ? "Aucun membre d'équipe n'est rattaché à votre fiche."
                    : "Votre compte n'est pas lié à une fiche employé.";
        }
        Set<Long> ids = people.stream().map(Employee::getId).collect(Collectors.toSet());
        List<ma.rhflow.domain.entity.LeaveBalance> balances = leaveBalanceRepository.findAll().stream()
                .filter(b -> ids.contains(b.getEmployeeId()) && b.getYear() != null && b.getYear() == year)
                .toList();
        if (actor.getRole() == UserRole.EMPLOYEE || people.size() == 1) {
            Employee person = people.get(0);
            BigDecimal remaining = balances.stream().map(b -> nvl(b.getRemaining())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal accrued = balances.stream().map(b -> nvl(b.getAccrued())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal used = balances.stream().map(b -> nvl(b.getUsed())).reduce(BigDecimal.ZERO, BigDecimal::add);
            String who = actor.getRole() == UserRole.EMPLOYEE ? "Il vous reste" : ("Il reste à " + fullName(person));
            if (balances.isEmpty()) {
                return "Aucun solde de congé " + year + " pour " + (actor.getRole() == UserRole.EMPLOYEE ? "vous" : fullName(person)) + ".";
            }
            return who + " " + money(remaining) + " jour(s) en " + year + " (acquis " + money(accrued) + ", pris " + money(used) + ").";
        }
        BigDecimal remaining = balances.stream().map(b -> nvl(b.getRemaining())).reduce(BigDecimal.ZERO, BigDecimal::add);
        String lines = people.stream().limit(8).map(e -> {
            BigDecimal left = balances.stream()
                    .filter(b -> e.getId().equals(b.getEmployeeId()))
                    .map(b -> nvl(b.getRemaining()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            return fullName(e) + " " + money(left) + " j";
        }).collect(Collectors.joining(", "));
        String title = actor.getRole() == UserRole.MANAGER ? "Équipe" : "Société";
        return title + " — " + people.size() + " personne(s), " + money(remaining) + " jour(s) restants en " + year + ". " + lines + ".";
    }

    private String attendanceReply(UserPrincipal actor) {
        if (actor.getRole() == UserRole.EMPLOYEE) {
            if (actor.getEmployeeId() == null) {
                return "Votre compte n'est pas lié à une fiche employé.";
            }
            return attendanceRepository.findByEmployeeIdAndDate(actor.getEmployeeId(), LocalDate.now())
                    .map(a -> "Aujourd'hui, votre statut est " + statusLabel(a.getStatus()) + ".")
                    .orElse("Aucune présence enregistrée pour vous aujourd'hui (" + LocalDate.now() + ").");
        }
        if (!permissionService.has(actor, PermissionCodes.ATTENDANCE_VIEW)) {
            return "Les présences de l'équipe ne sont pas ouvertes pour ce rôle.";
        }
        List<Employee> people = scope(actor).stream().filter(e -> !"SELF".equals(e.getStatus())).toList();
        if (actor.getRole() == UserRole.MANAGER) {
            Long self = actor.getEmployeeId();
            people = people.stream().filter(e -> self == null || !self.equals(e.getId())).toList();
        }
        Set<Long> ids = people.stream().map(Employee::getId).collect(Collectors.toSet());
        List<ma.rhflow.domain.entity.Attendance> today = attendanceRepository.findByDateAndStatus(LocalDate.now(), "ABSENT").stream()
                .filter(a -> ids.contains(a.getEmployeeId()))
                .toList();
        if (ids.isEmpty()) {
            return "Aucun membre d'équipe n'est rattaché à votre fiche.";
        }
        if (today.isEmpty()) {
            return "Aucun absent enregistré aujourd'hui (" + LocalDate.now() + ") sur " + ids.size() + " personne(s) de votre périmètre.";
        }
        String names = today.stream().limit(8).map(a -> employeeName(a.getEmployeeId())).collect(Collectors.joining(", "));
        return today.size() + " absent(s) aujourd'hui : " + names + ".";
    }

    private String pendingLeaveReply(UserPrincipal actor) {
        if (!permissionService.has(actor, PermissionCodes.LEAVE_VIEW)) {
            return "Les demandes de congé ne sont pas ouvertes pour ce rôle.";
        }
        Set<Long> ids = scope(actor).stream().map(Employee::getId).collect(Collectors.toSet());
        if (actor.getRole() == UserRole.EMPLOYEE && actor.getEmployeeId() != null) {
            ids = Set.of(actor.getEmployeeId());
        }
        Set<Long> visible = ids;
        List<LeaveRequest> pending = leaveRequestRepository.findAll().stream()
                .filter(r -> "PENDING".equalsIgnoreCase(r.getStatus()) && r.getEmployeeId() != null && visible.contains(r.getEmployeeId()))
                .limit(8)
                .toList();
        if (pending.isEmpty()) {
            return actor.getRole() == UserRole.EMPLOYEE
                    ? "Vous n'avez aucune demande de congé en attente."
                    : "Aucune demande de congé en attente dans votre périmètre.";
        }
        String lines = pending.stream()
                .map(r -> employeeName(r.getEmployeeId()) + " (" + r.getStartDate() + " → " + r.getEndDate() + ")")
                .collect(Collectors.joining(", "));
        return pending.size() + " demande(s) en attente : " + lines + ".";
    }

    private String headcountReply(UserPrincipal actor) {
        if (actor.getRole() == UserRole.EMPLOYEE) {
            return "L'effectif de la société n'est pas visible depuis votre espace. Vous pouvez consulter votre solde, votre présence et votre bulletin.";
        }
        if (!permissionService.has(actor, PermissionCodes.EMPLOYEES_VIEW)) {
            return "La liste des employés n'est pas ouverte pour ce rôle.";
        }
        List<Employee> people = scope(actor);
        long active = people.stream().filter(e -> e.getStatus() == null || "ACTIVE".equalsIgnoreCase(e.getStatus())).count();
        String title = actor.getRole() == UserRole.MANAGER ? "Votre équipe compte" : "La société compte";
        return title + " " + active + " personne(s) active(s).";
    }

    private String platformSummary() {
        List<Tenant> tenants = tenantRepository.findAll();
        Map<String, Long> plans = tenants.stream()
                .collect(Collectors.groupingBy(t -> t.getPlan() == null ? "SANS PLAN" : t.getPlan().name(), Collectors.counting()));
        String detail = plans.entrySet().stream().map(e -> e.getKey() + " " + e.getValue()).collect(Collectors.joining(", "));
        return "Plateforme : " + tenants.size() + " société(s). Plans : " + (detail.isBlank() ? "aucun" : detail)
                + ". Les fiches salariés et les bulletins restent dans chaque société.";
    }

    private List<Employee> peopleForQuestion(UserPrincipal actor, String message) {
        List<Employee> people = scope(actor);
        if (actor.getRole() == UserRole.EMPLOYEE) {
            return people;
        }
        String lower = message == null ? "" : message.toLowerCase(Locale.ROOT);
        Employee named = people.stream()
                .filter(e -> fullName(e).length() > 2 && lower.contains(fullName(e).toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElse(null);
        if (named != null) {
            return List.of(named);
        }
        if (actor.getRole() == UserRole.MANAGER) {
            Long self = actor.getEmployeeId();
            List<Employee> team = people.stream().filter(e -> self == null || !self.equals(e.getId())).toList();
            return team.isEmpty() ? people : team;
        }
        return people;
    }

    private List<Employee> scope(UserPrincipal actor) {
        if (actor.getRole() == UserRole.SUPER_ADMIN) {
            return List.of();
        }
        List<Employee> all = employeeRepository.findAll();
        if (actor.getRole() == UserRole.ADMIN) {
            return all;
        }
        Long self = actor.getEmployeeId();
        if (self == null) {
            return List.of();
        }
        if (actor.getRole() == UserRole.EMPLOYEE) {
            return all.stream().filter(e -> self.equals(e.getId())).toList();
        }
        return all.stream().filter(e -> self.equals(e.getId()) || self.equals(e.getManagerId())).toList();
    }

    private Payslip latest(List<Payslip> slips) {
        return slips.stream()
                .max(Comparator.comparing(Payslip::getYear, Comparator.nullsFirst(Integer::compareTo))
                        .thenComparing(Payslip::getMonth, Comparator.nullsFirst(Integer::compareTo)))
                .orElse(null);
    }

    private String employeeName(Long id) {
        if (id == null) {
            return "salarié";
        }
        return employeeRepository.findById(id).map(this::fullName).orElse("salarié");
    }

    private String fullName(Employee e) {
        return ((e.getFirstName() == null ? "" : e.getFirstName()) + " " + (e.getLastName() == null ? "" : e.getLastName())).trim();
    }

    private String money(BigDecimal value) {
        return nvl(value).stripTrailingZeros().toPlainString();
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String statusLabel(String status) {
        if (status == null) {
            return "non renseigné";
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "PRESENT" -> "présent";
            case "ABSENT" -> "absent";
            case "LATE" -> "en retard";
            case "LEAVE" -> "en congé";
            default -> status.toLowerCase(Locale.ROOT);
        };
    }

    private String roleLabel(UserRole role) {
        return switch (role) {
            case SUPER_ADMIN -> "Super administrateur";
            case ADMIN -> "Administrateur";
            case MANAGER -> "Manager";
            case EMPLOYEE -> "Employé";
        };
    }

    private String guide(UserRole role) {
        return switch (role) {
            case EMPLOYEE -> "Assistant personnel : votre solde, votre présence, votre bulletin et vos congés.";
            case MANAGER -> "Assistant manager : votre équipe, les absences et les congés à valider. La paie reste masquée.";
            case ADMIN -> "Assistant RH : employés, congés, présences et paie de la société.";
            case SUPER_ADMIN -> "Assistant plateforme : sociétés et plans. Pas d'accès aux bulletins d'une société.";
        };
    }

    private String capabilities(UserRole role) {
        return switch (role) {
            case EMPLOYEE -> "Je peux vous dire votre solde de congé, votre présence du jour et expliquer votre dernier bulletin. Pour un congé, je pose d'abord les questions, puis vous confirmez.";
            case MANAGER -> "Je peux lister votre équipe, les absents du jour, les congés en attente et les soldes de l'équipe. Vous pouvez préparer un congé après avoir répondu aux questions. La paie et la création d'employés restent réservées à l'administrateur.";
            case ADMIN -> "Je peux chercher un employé, donner l'effectif, les absents, les congés en attente et expliquer un bulletin. Une fiche ou un congé n'est préparé qu'après les questions, puis votre confirmation.";
            case SUPER_ADMIN -> "Je résume les sociétés et leurs plans. Je ne lis pas les salariés, les congés ni les bulletins d'une société.";
        };
    }

    private List<String> suggestions(UserRole role) {
        return switch (role) {
            case EMPLOYEE -> List.of(
                    "Que peux-tu faire ?",
                    "Combien de jours de congé me restent-ils ?",
                    "Suis-je présent aujourd'hui ?",
                    "Explique mon bulletin",
                    "Pose un congé");
            case MANAGER -> List.of(
                    "Que peux-tu faire ?",
                    "Absents aujourd'hui",
                    "Congés en attente",
                    "Cherche un employé",
                    "Pose un congé");
            case ADMIN -> List.of(
                    "Que peux-tu faire ?",
                    "Cherche un employé",
                    "Absents aujourd'hui",
                    "Explique le dernier bulletin",
                    "Créer un employé",
                    "Pose un congé");
            case SUPER_ADMIN -> List.of(
                    "Que peux-tu faire ?",
                    "Combien de sociétés ?",
                    "Quels plans sont actifs ?");
        };
    }

    private boolean includedInPlan() {
        Long tid = TenantContext.getTenantId();
        if (tid == null) {
            return false;
        }
        Tenant t = tenantRepository.findById(tid).orElse(null);
        if (t == null) {
            return false;
        }
        return t.isFeatureOllama() || t.getPlan() == SubscriptionPlan.PRO || t.getPlan() == SubscriptionPlan.ENTREPRISE;
    }

    private boolean pingOllama() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(ollamaBase + "/api/tags"))
                    .timeout(Duration.ofSeconds(2)).GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() >= 200 && res.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private String tryOllama(String message, String locale, UserPrincipal actor) {
        try {
            String prompt = "Tu es l'assistant RHFlow pour le rôle " + roleLabel(actor.getRole())
                    + ". " + capabilities(actor.getRole())
                    + " Réponds en " + locale + ", en peu de phrases. N'invente aucun chiffre, salarié ou bulletin. "
                    + "Si la question sort de ce rôle, refuse et rappelle ce que ce rôle peut demander. Question: " + message;
            String payload = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "stream", false,
                    "prompt", prompt
            ));
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(ollamaBase + "/api/generate"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                return null;
            }
            Map<String, Object> json = objectMapper.readValue(res.body(), new TypeReference<>() {
            });
            Object r = json.get("response");
            return r == null ? null : String.valueOf(r);
        } catch (Exception e) {
            log.debug("ollama unavailable: {}", e.getMessage());
            return null;
        }
    }
}
