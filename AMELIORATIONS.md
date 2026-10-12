# RHFlow Maroc — Audit fonctionnel & backlog d'améliorations

> Audit réalisé le 2026-10-12 par lecture du code (backend Spring Boot 3.4 / Java 21 + frontend React 19 / Vite).
> **Aucune modification de code** n'a été faite : ce document est uniquement un état des lieux et un backlog.
> Priorités : **P0** = sécurité/conformité légale/risque de données · **P1** = forte valeur métier · **P2** = confort / différenciation.
> Effort : **S** (< 2 j) · **M** (≈ 1 sem.) · **L** (> 1 sem.).

---

## 1. Fonctionnalités existantes

### 1.1 Plateforme & multi-tenant
- Multi-société (tenant) avec filtre Hibernate automatique (`tenant/`), abonnements `ESSENTIEL` (25 emp./10 users), `PRO` (100/40, assistant IA), `ENTREPRISE` (500/150).
- Espace **SUPER_ADMIN** (`/platform`) : création de sociétés, paiements plateforme, plans par défaut, quotas (`QuotaService`).
- Page vitrine, inscription société, mot de passe oublié / reset (e-mail), page **carrières publique** (`/careers/{slug}`, candidature sans compte).

### 1.2 Authentification & droits
- JWT access (15 min) + refresh (7 j / 30 j « se souvenir de moi », 5 jetons max par utilisateur), logout, `/me`.
- 4 rôles (`SUPER_ADMIN`, `ADMIN`, `MANAGER`, `EMPLOYEE`) + ~30 permissions fines (`PermissionCodes`) éditables par utilisateur ; périmètre manager (`ScopeService` : un manager ne voit que son équipe).
- Gestion des utilisateurs (création, rôle, permissions, suppression), journal d'audit (`AuditLog`).

### 1.3 Dossier employé & organisation
- Employés (CRUD, photo, onboarding avec upload, fiche complète `EmployeeFilePage`), départements, postes, organigramme, contrats (PDF), changements de profil soumis à validation, formations (cours + inscriptions), parc matériel (`assets`), sanctions disciplinaires, établissements (géolocalisés).
- Documents RH : upload (MinIO / disque), génération (attestations, etc.), PDF, **signature interne** (`/documents/{id}/sign`), coffre privé (`DOCUMENTS_PRIVATE`).
- Demandes salariés (attestation de travail/salaire, etc.) avec circuit d'approbation à étapes (`approval_step`).

### 1.4 Temps & présences
- Pointage (web + géoloc/rayon établissement, `method`, `hours`), horaires de travail, jours fériés, shifts + affectations, absences, export CSV/XLSX/PDF.
- Calcul d'heures (`AttendanceHoursService`), lien absences → paie (`PayrollAbsenceLinker`).

### 1.5 Congés
- Types de congés, soldes (acquis légaux 1,5 j/mois + bonus ancienneté), demande / approbation / rejet / annulation, PDF, calendrier d'équipe, notifications (in-app + e-mail + WhatsApp).

### 1.6 Paie (cœur différenciant « Maroc »)
- Moteur `PayrollEngine` : CNSS (plafond), AMO, frais professionnels (35 % / 25 %), barème IR, charges familiales, ancienneté, heures sup (jour/nuit/repos/sans repos), primes imposables/non imposables, avances, saisies, CIMR, mutuelle, alerte SMIG, coût employeur (AF, PSCT, PSLT, AMO, TFP).
- Règles versionnées (`PayrollRuleSet`, `ma2026`), périodes (calcul → approbation → payé → verrou), bulletin PDF, éléments variables, prêts, simulateur brut/net, régularisation IR, solde de tout compte.
- Exports réglementaires : **Damancom EDI**, **état 9421**, **virement SIMT**, **CIMR**, journal de paie, registre.

### 1.7 Frais, talents, performance
- Notes de frais, missions/déplacements, onboarding/offboarding (checklists), entretiens d'évaluation, objectifs, offres d'emploi, candidats (CV texte), publication d'offres.

### 1.8 Pilotage & transverse
- Dashboard + graphiques, recherche globale, 25+ rapports (CSV/XLSX/PDF), annonces, notifications (cloche), paramètres société (logo, WhatsApp, règles), FR/AR avec RTL, PWA basique, portail employé (`/portal`) séparé du back-office.
- **Assistant IA** (Ollama local) : intentions (créer employé, poser congé, solde, effectif…) avec confirmation d'action (`AssistantPendingAction`).

---

## 2. Constats techniques (dette & risques)

| # | Constat | Où | Gravité |
|---|---|---|---|
| T1 | `CatalogService` = **2 461 lignes**, un gros `switch(resource)` pour ~25 ressources (list/create/update/delete). Difficile à tester, à relire et à sécuriser. | `service/CatalogService.java` | Dette majeure |
| T2 | Aucun **rate-limit / verrouillage de compte** sur `/auth/login`, `/forgot-password`, `/careers/*/apply` (brute-force, spam candidatures). | `AuthService`, `SecurityConfig` | **P0** |
| T3 | Secret JWT **par défaut en dur** dans `application.properties` (fallback si `JWT_SECRET` absent) → un déploiement oublié est forgeable. | `application.properties` | **P0** |
| T4 | `/ops/status` et `/h2-console/**` en `permitAll` (fuite d'infos infra, console DB si activée). | `SecurityConfig` | **P0** |
| T5 | `ddl-auto=update` **et** Flyway activés simultanément ; Hibernate peut dériver du schéma Flyway. | `application.properties` | P1 |
| T6 | Service Worker met en cache **toutes** les réponses GET (y compris API authentifiées : paie, bulletins) → données sensibles persistées dans le cache navigateur, partagées après logout. | `RhFrontend/public/sw.js` | **P0** |
| T7 | Jetons JWT en `localStorage` (exposés à toute XSS). | `RhFrontend/src/lib/auth.tsx` | P1 |
| T8 | `HrRecord` = table fourre-tout (`kind` + `payload` String 4000) pour prêts, shifts, sanctions, matériel, signatures, WhatsApp logs… Pas de contraintes, pas d'index par colonne métier, JSON dans un VARCHAR. | `domain/entity/HrRecord.java`, `V4__…sql` | P1 |
| T9 | **Aucune tâche planifiée** malgré `@EnableScheduling` (pas d'alertes d'échéances, pas d'acquisition mensuelle de congés, pas de purge). | `RhFlowApplication.java` | P1 |
| T10 | Couverture de tests faible : 4 classes (`PayrollEngineTest`, `ExtraModulesTest`, `RecetteTests`, `V2InfraTest`), **aucun test frontend**, pas de CI. | `src/test`, `package.json` | P1 |
| T11 | Corps de requêtes en `Map<String,Object>` partout : pas de DTO, pas de Bean Validation, pas de contrat OpenAPI. | contrôleurs | P1 |
| T12 | Pas de pagination ni de limites sur certains exports (chargement mémoire complet) ; fichiers max 10 Mo sans antivirus ni whitelist MIME stricte. | `ReportService`, `FileStorageService` | P1 |
| T13 | Actuator limité à `health,info`, pas de métriques/tracing ; logs non structurés. | `application.properties` | P2 |
| T14 | Assistant : parser d'intentions à base de mots-clés FR uniquement (pas d'arabe/darija), dépend d'Ollama local. | `AssistantIntentParser` | P2 |

---

## 3. Écarts de conformité légale marocaine à vérifier / corriger (priorité paie)

| # | Sujet | Constat | Action |
|---|---|---|---|
| L1 | **Prime d'ancienneté** | `PayrollEngine.seniorityRate` s'arrête à 20 ans (20 %). Le Code du travail / pratique prévoit **25 % à partir de 25 ans**. | Ajouter le palier 25 ans ; rendre les paliers paramétrables dans `PayrollRuleSet`. **P0 / S** |
| L2 | Ancienneté sur salaire de base | Taux appliqué sur `baseSalary` : OK, mais pas de prise en compte des **conventions collectives / accords d'entreprise** (taux différents). | Paliers par tenant/contrat. P1 / M |
| L3 | **Congés** | Acquisition 1,5 j/mois + bonus : OK. Manquent : **congés exceptionnels légaux** (mariage 4 j, naissance 3 j, décès 3 j, circoncision 2 j…), congé maternité 14 sem. / paternité 3 j, congé sans solde, **report/expiration** des soldes. | Catalogue légal pré-chargé + règles de report. P1 / M |
| L4 | **Jours fériés** | Saisie manuelle (`holidays`) ; fêtes religieuses dépendent de la lune. | Import annuel (calendrier officiel) + ajustement 1 clic. P1 / S |
| L5 | **CNSS / Damancom** | Export EDI présent. Manque contrôle de validation (matricule CNSS, CIN, format) avant export, historique des exports, gestion des **déclarations complémentaires/rectificatives**. | Pré-validation + journal. P1 / M |
| L6 | **IR** | Barème 2026 codé (`ma2026`). Manque : **abattements/réductions spécifiques** (prêt logement, intérêts, déductions pour retraite CIMR/assurance), calcul **annuel de régularisation** automatisé en décembre. | Compléter + test de non-régression par barème. P1 / M |
| L7 | **Heures sup & durée légale** | Majorations 25/50/100 % codées ; pas de contrôle du **plafond légal (durée hebdo, repos hebdo, travail de nuit)**. | Alertes de conformité temps de travail. P1 / M |
| L8 | **Rupture de contrat** | Solde de tout compte (`settlement`) existe ; indemnité de licenciement : à valider vs. barèmes légaux (96 h/an les 5 premières années, etc.), préavis, **indemnité de congé non pris**. | Test unitaire par cas légal + édition du reçu pour solde de tout compte. P1 / M |
| L9 | **Registre & documents obligatoires** | Registre présent. Manquent : **registre unique du personnel**, **règlement intérieur** versionné avec accusé de lecture, **déclaration d'accident du travail**, **visite médicale**. | Modèles documentaires + suivi. P2 / M |
| L10 | **Protection des données (loi 09-08 / CNDP)** | Pas de gestion du consentement, durée de conservation, droit d'accès/suppression, **registre des traitements**. Données sensibles (salaire, santé) sans chiffrement applicatif. | Politique de rétention + export/anonymisation par employé. **P0 / M** |

---

## 4. Backlog de fonctionnalités à ajouter (par domaine)

### 4.1 Sécurité & comptes — *(P0)*
1. **Rate-limiting + verrouillage progressif** (login, reset, candidature publique) — S.
2. **2FA** (TOTP, SMS/WhatsApp OTP) obligatoire pour ADMIN/PAIE — M.
3. **Politique de mots de passe** (complexité, expiration, historique) + invalidation de toutes les sessions au changement de mot de passe — S.
4. **Gestion des sessions** : liste des appareils, révocation des refresh tokens — S.
5. **Cookies `HttpOnly; SameSite`** pour le refresh token à la place du `localStorage` — M.
6. **Audit enrichi** : IP, user-agent, diff avant/après, export, alerte sur actions sensibles (modification salaire, export paie) — M.
7. **SSO** (Google/Microsoft/OIDC) pour grandes entreprises — L.
8. **Rôles personnalisés** (profil « Paie », « RH junior », « DG ») en plus des 4 rôles figés — M.

### 4.2 Paie & conformité — *(cœur de valeur)*
1. **Paliers d'ancienneté paramétrables** + palier 25 ans (L1) — S.
2. **Bulletin de paie multi-modèles** (logo, rubriques, mentions légales), envoi **groupé par e-mail/WhatsApp** avec PDF protégé par mot de passe — M.
3. **Paie par lot** : clôture mensuelle guidée (checklist : absences importées, variables validées, écarts vs M-1) — M.
4. **Analyse d'écarts N vs N-1** par salarié/département + détection d'anomalies (net négatif, SMIG, doublons) — M.
5. **Rubriques de paie personnalisables** (indemnités transport, panier, logement…, avec règles d'imposabilité CNSS/IR) — M.
6. **Avances & prêts** : échéancier auto, retenue sur bulletin, solde restant, justificatifs — S (base existante via `loans`).
7. **Pré-validation Damancom / 9421 / CIMR** avant génération + historique des dépôts — M.
8. **Provisions** (congés payés, 13ᵉ mois, indemnités de départ) et coût masse salariale prévisionnel — M.
9. **Multi-devises / paie expatriés / saisonniers / journaliers** (contrats horaires, CDD agricole) — L.
10. **Intégration comptable** : export écritures (Sage, Odoo, CSV paramétrable) — M.
11. **Virement bancaire** : formats par banque (CIH, Attijariwafa, BP…) au-delà de SIMT — M.

### 4.3 Temps, présences & plannings
1. **Application mobile / PWA complète** avec pointage hors-ligne, QR code par établissement, photo/selfie anti-fraude — L.
2. **Planning visuel** (drag & drop) des shifts avec détection de conflits et de non-respect du repos légal — M.
3. **Règles de retard / sortie anticipée / pauses** paramétrables, génération automatique d'anomalies à corriger — M.
4. **Compteurs d'heures sup** et validation manager avant intégration à la paie — M.
5. **Intégration pointeuses biométriques** (API/CSV import) — M.
6. **Télétravail** (jours autorisés, quotas) — S.

### 4.4 Congés & absences
1. **Catalogue légal** de congés exceptionnels, maternité/paternité, maladie (certificat obligatoire) (L3) — M.
2. **Acquisition mensuelle automatique** par tâche planifiée + reports/expirations annuelles — M.
3. **Délégation d'approbation** (manager absent), circuit multi-niveaux paramétrable — M.
4. **Justificatifs obligatoires** (certificat médical) avec délai de dépôt et relance — S.
5. **Calendrier iCal/Google** exportable + vue « qui est absent cette semaine » dans le dashboard — S.
6. **Règles d'équipe** (nb max d'absents simultanés, périodes de blocage) — M.

### 4.5 Recrutement & intégration
1. **Pipeline Kanban** (candidature → entretien → offre → embauche) avec étapes personnalisables — M.
2. **Parsing de CV** (PDF/Word) + scoring de pertinence (IA) — L.
3. **Planification d'entretiens** (créneaux, e-mails, agenda), scorecards d'évaluation — M.
4. **Génération de lettre d'offre / contrat** depuis un modèle puis **conversion candidat → employé** en 1 clic — M.
5. **Diffusion multi-canaux** (Rekrute, LinkedIn, Emploi.ma) — L.
6. **Onboarding** : modèles par poste/département, tâches assignées (IT, RH, manager) avec échéances et relances auto — M.

### 4.6 Performance, formation & carrière
1. **Campagnes d'évaluation** (périodes, 360°, auto-évaluation) avec grilles de compétences — M.
2. **OKR** liés aux entretiens, suivi d'avancement et rappels — M.
3. **Plan de formation annuel**, budget, **FPT/OFPPT** (remboursement TFP), certifications avec dates d'expiration et alertes — M.
4. **Matrice de compétences** et plans de succession / carrière (9-box) — L.
5. **Enquêtes de climat / eNPS** anonymes — M.

### 4.7 Documents & signature
1. **Signature électronique** à valeur probante (prestataire certifié, horodatage, hash du document) à la place de la signature interne — L.
2. **Modèles de documents éditables** (variables `{{nom}}`, `{{salaire}}`) + versionnement + multilingue FR/AR — M.
3. **Coffre-fort** avec chiffrement, droits par document, **dates d'expiration** (CIN, permis, visa, contrat) et alertes — M.
4. **Accusé de lecture** (règlement intérieur, notes de service) — S.
5. **OCR** des pièces justificatives (notes de frais, CIN) — M.

### 4.8 Frais & déplacements
1. **Barèmes de frais** (kilométrique, per-diem par pays/ville), plafonds par catégorie/poste — M.
2. **Workflow multi-niveaux** + budgets par projet/centre de coût — M.
3. **Refacturation / remboursement groupé** inclus dans la paie ou virement dédié — S.
4. **Scan de reçus mobile** + OCR + détection de doublons — M.

### 4.9 Reporting & pilotage
1. **Tableaux de bord RH avancés** : turnover, absentéisme (Bradford), ancienneté, pyramide des âges, parité, masse salariale par service — M.
2. **Rapports planifiés** (e-mail hebdo/mensuel) et **constructeur de rapports** personnalisables — M.
3. **Export des données** complet (portabilité) par société et par employé — S.
4. **Alertes proactives** (fin de période d'essai, fin de CDD, anniversaires, échéance de documents, SMIG) — S (nécessite T9).
5. **Indicateurs de conformité** (score : contrats signés, déclarations déposées, documents manquants) — M.

### 4.10 Communication & portail employé
1. **Centre de notifications unifié** (page dédiée — aujourd'hui `/app/notifications` redirige vers l'accueil) avec préférences par canal (in-app / e-mail / WhatsApp) — S.
2. **Messagerie / fil d'actualité** avec accusés de lecture, ciblage (service, établissement), pièces jointes — M.
3. **Notifications push** (PWA/mobile) — M.
4. **Annuaire & organigramme interactif** (recherche compétences, export PNG/PDF) — S.
5. **Portail manager** : tuile « mes validations en attente » (congés, frais, demandes, évaluations) en un seul écran — S.
6. **Libre-service avancé** : changement de RIB, de situation familiale (impact IR) avec pièces justificatives et validation — M (base `profile-changes` existante).

### 4.11 Assistant IA
1. **Support de l'arabe et de la darija**, reconnaissance d'intentions par LLM (function-calling) plutôt que mots-clés — M.
2. **Questions RH légales** (RAG sur Code du travail marocain, convention collective, politique interne) avec citations — L.
3. **Explication de bulletin** pas-à-pas (déjà amorcé : `EXPLAIN_PAYSLIP`) — S.
4. **Actions guidées** supplémentaires : valider congé, relancer un document, générer une attestation — M.
5. **Garde-fous** : journalisation des prompts, pas de données sensibles hors périmètre de l'utilisateur, mode hors-ligne si Ollama indisponible — S.

### 4.12 Plateforme SaaS
1. **Facturation** : plans, échéances, factures PDF, paiement en ligne (CMI/Payzone/Stripe), relances d'impayés — L.
2. **Onboarding société guidé** (import CSV/Excel des employés, paramétrage légal pré-rempli) — M.
3. **Import / export en masse** (employés, soldes de congés, historique de paie) avec rapport d'erreurs ligne par ligne — M.
4. **Marque blanche** (domaine, logo, couleurs par tenant) — M.
5. **API publique + webhooks** (clés API, quotas) et intégrations (Zapier/n8n) — L.
6. **Portail cabinet comptable / expert-comptable** multi-sociétés avec accès délégué — L.

---

## 5. Améliorations techniques (non fonctionnelles)

### 5.1 Architecture & qualité
- **Découper `CatalogService`** : un service/contrôleur par agrégat (Employees, Contracts, Attendance, Expenses, Recruitment…), remplacer le `switch(resource)` par des handlers enregistrés (stratégie) — L.
- **DTO + Bean Validation** (`@Valid`) et mappers (MapStruct) pour remplacer les `Map<String,Object>` ; messages d'erreur localisés FR/AR — L.
- **OpenAPI/Swagger** (springdoc) et génération de types TypeScript côté front — M.
- **Modéliser les entités de `HrRecord`** en vraies tables (`loans`, `assets`, `shifts`, `disciplinary_actions`, `signatures`) avec FK et contraintes — L.
- **Flyway seul** : passer `ddl-auto` à `validate`, versionner toutes les évolutions de schéma, tester les migrations — S.
- **Index & performances** : index sur `(tenant_id, employee_id, date)`, éviter les `findAll` + filtre en mémoire (ex. recherche employé dans `LeaveService.list`), pagination/stream pour exports — M.
- **Cache Redis** réellement utilisé (profils, règles de paie, permissions) avec invalidation — M.
- **Soft delete** + `@Version` (verrouillage optimiste) sur paie, contrats, soldes — M.

### 5.2 Sécurité technique
- Secrets **obligatoires** en prod (échec au démarrage si `JWT_SECRET` par défaut) ; profil `prod` distinct ; retirer H2 console du jeu de règles `permitAll` — S.
- **Headers de sécurité** (CSP, HSTS, X-Content-Type-Options, Referrer-Policy) — S.
- **Chiffrement au repos** des champs sensibles (RIB, CIN, CNSS) ; rotation de clés — M.
- **Scan antivirus** et vérification MIME réelle des uploads ; URLs signées à durée courte (existe : `/files/signed`) avec journalisation — M.
- Revoir le **Service Worker** : ne jamais mettre en cache `/api`/données authentifiées, vider le cache au logout, stratégie « network-first » limitée aux assets (T6) — S.
- Analyse de dépendances (Dependabot/OWASP) et SAST en CI — S.

### 5.3 Tests, CI/CD, observabilité
- **Tests de paie à haute couverture** : table de cas (SMIG, plafond CNSS, tranches IR, charges familiales, ancienneté 25 ans, heures sup, solde de tout compte) — M.
- Tests d'intégration (Testcontainers PostgreSQL), tests d'**isolation multi-tenant** (un tenant ne voit jamais les données d'un autre) et de **périmètre manager** — M.
- Frontend : Vitest + Testing Library, Playwright (parcours login → congé → approbation → bulletin) — M.
- **CI** (GitHub Actions) : build, tests, lint, audit dépendances ; **Docker Compose** (API + Postgres + Redis + MinIO + Ollama + front) — M.
- **Observabilité** : logs JSON avec `tenantId`/`traceId`, métriques Micrometer/Prometheus, alerting sur échecs d'envoi mail/WhatsApp et de calcul de paie — M.
- Sauvegardes Postgres automatisées + procédure de restauration testée — S.

### 5.4 Frontend / UX
- Découper les gros fichiers de pages (5 435 lignes réparties sur 12 fichiers) en modules par fonctionnalité ; lazy-loading des routes — M.
- **Accessibilité** (navigation clavier, ARIA, contrastes) et finalisation du **RTL arabe** sur tous les écrans (graphiques, tableaux, PDF) — M.
- États de chargement/erreur uniformes (skeletons, retry), saisie en masse dans les tableaux, filtres enregistrés, colonnes configurables — M.
- Mode **hors-ligne** réel pour le pointage mobile (file d'attente + synchro) — M.
- Page **notifications** dédiée ; tour guidé d'accueil par rôle ; aide contextuelle — S.
- Internationalisation : ajouter l'**anglais** et externaliser les chaînes restantes (`phrases.ts`) — M.

---

## 6. Feuille de route suggérée

| Phase | Objectif | Contenu principal |
|---|---|---|
| **0 — Durcissement (1–2 sem.)** | Éliminer les risques | T2, T3, T4, T6 · L1 (ancienneté 25 ans) · headers sécurité · Flyway seul · tests de paie étendus |
| **1 — Fiabilité (3–4 sem.)** | Base saine | Découpage `CatalogService` · DTO/validation · CI + Docker Compose · tâches planifiées (acquisition congés, alertes d'échéances) · tests d'isolation tenant |
| **2 — Conformité & valeur Maroc (4–6 sem.)** | Différenciation | Catalogue légal des congés · pré-validation Damancom/9421/CIMR · paliers & rubriques paramétrables · loi 09-08 (rétention/export) · clôture de paie guidée |
| **3 — Expérience (4–6 sem.)** | Adoption | PWA mobile + pointage hors-ligne · notifications push/centre de notifications · portail manager · recrutement Kanban · import en masse |
| **4 — Croissance (continu)** | Scale & monétisation | Facturation SaaS · e-signature probante · API/webhooks · IA juridique (RAG) · intégrations compta & banques |

---

## 7. Quick wins (≤ 2 jours chacun)
1. Palier d'ancienneté 25 ans (+ test) — L1.
2. Ne plus mettre en cache l'API dans `sw.js` — T6.
3. Retirer `/ops/status` et `/h2-console` du `permitAll` en profil prod — T4.
4. Faire échouer le démarrage si `JWT_SECRET` n'est pas défini hors profil `dev` — T3.
5. Rate-limit sur `/auth/*` et `/careers/*/apply` (Bucket4j) — T2.
6. `ddl-auto=validate` + Flyway comme seule source de vérité — T5.
7. Page « Notifications » réelle au lieu de la redirection.
8. Alertes automatiques fin de période d'essai / fin de CDD / documents expirants (une `@Scheduled` + `NotificationService` existant).
9. Import annuel des jours fériés officiels.
10. Export « mes données » par employé (portabilité 09-08).
