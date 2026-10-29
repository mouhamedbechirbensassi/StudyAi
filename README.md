# StudyAI — Plateforme Éducative Intelligente & Assistant RAG

StudyAI est une plateforme web moderne d'apprentissage assistée par IA, conçue pour les étudiants afin d'organiser leurs cours, gérer leurs chapitres et supports PDF, et préparer l'exploitation sémantique des documents via une architecture RAG (Retrieval-Augmented Generation).

---

## 🏗 Architecture Globale

Le projet repose sur une architecture hybride, robuste et découplée :

- **Backend Principal (Java 17 / Spring Boot 3.3.4)** :
  - Authentification stateless JWT (JJWT) & Spring Security.
  - API REST pour la gestion des utilisateurs, des cours et des documents.
  - Persistance Spring Data JPA / Hibernate ORM 6.5.
  - Extraction de texte brut PDF via Apache PDFBox.
  - Client REST dédié (`AiServiceClient`) communiquant avec le microservice IA Python.
  - Numérotation consécutive et déterministe des cours par étudiant.

- **Microservice IA (Python 3.10+ / FastAPI)** :
  - Nettoyage et normalisation sémantique du texte extrait.
  - Segmentation intelligente (*semantic chunking*) par phrases et paragraphes avec chevauchement (*overlap*).
  - Validation de l'exploitabilité textuelle (détection des documents scannés/sans OCR).
  - Documentation interactive OpenAPI / Swagger UI sur `/docs`.

- **Frontend (Angular 19 Standalone)** :
  - Composants autonomes (*standalone components*) et gestion d'état réactive avec Angular Signals.
  - Routing moderne avec Lazy-Loading et Guards fonctionnels (`authGuard`, `guestGuard`).
  - Intercepteur HTTP automatique pour l'injection des tokens JWT.
  - Design moderne, ergonomique et épuré (palette claire, cartes d'élévation, typographie Plus Jakarta Sans, design responsive mobile & desktop).

- **Base de Données (MySQL 8.0)** :
  - Base `studyai_db` avec isolation stricte des données par étudiant.
  - Gestion du schéma déléguée à Hibernate (`spring.jpa.hibernate.ddl-auto=update`), sans migrations SQL tierces.

---

## 🚀 Guide de Démarrage

### 1. Prérequis Système
- **Java** : JDK 17 ou supérieur
- **Maven** : Version 3.9+ (ou wrapper Maven)
- **Node.js** : Version 18 ou supérieure & npm
- **Python** : Version 3.10 ou supérieure
- **MySQL** : Serveur MySQL 8.0 en cours d'exécution sur le port 3306

---

### 2. Configuration des Variables d'Environnement

Copiez le fichier de modèle `.env.example` vers `.env` (ou exportez les variables dans votre environnement) :

```bash
cp .env.example .env
```

Variables requises :
- `DB_URL` : URL de connexion MySQL (ex: `jdbc:mysql://localhost:3306/studyai_db?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true`)
- `DB_USERNAME` : Utilisateur de la base de données (ex: `root`)
- `DB_PASSWORD` : Mot de passe de la base de données
- `JWT_SECRET` : Clé secrète cryptographique (au moins 256 bits / 32 caractères)
- `AI_SERVICE_URL` : URL du service IA Python (par défaut `http://localhost:8000`)

---

### 3. Lancement des Services

#### A. Microservice IA (Python / FastAPI)
```bash
cd ai-service

# Création de l'environnement virtuel (optionnel mais recommandé)
python -m venv venv
.\venv\Scripts\activate   # Sur Windows (ou source venv/bin/activate sur Linux/macOS)

# Installation des dépendances
pip install -r requirements.txt

# Démarrage du serveur Uvicorn sur le port 8000
uvicorn app.main:app --host 127.0.0.1 --port 8000 --reload
```
*Le service est accessible sur `http://localhost:8000` (Documentation Swagger : `http://localhost:8000/docs`).*

#### B. Backend (Spring Boot)
Depuis un autre terminal :
```bash
cd backend
mvn spring-boot:run
```
*Le backend démarre sur `http://localhost:8080`.*

#### C. Frontend (Angular 19)
Depuis un troisième terminal :
```bash
cd frontend
npm install
npm start
```
*L'application web est accessible dans votre navigateur sur `http://localhost:4200`.*

---

## 🧭 Fonctionnalités Clés

### 1. Authentification & Sécurité
- Inscription sécurisée (`POST /api/auth/register`) avec validation et hachage BCrypt.
- Connexion (`POST /api/auth/login`) délivrant un token JWT signé.
- Consultation de profil (`GET /api/auth/me`).
- Isolation stricte : aucun étudiant ne peut accéder, modifier ou supprimer les cours ou documents d'un autre étudiant.

### 2. Organisation des Cours & Numérotation Séquentielle
- Numérotation consécutive et propre à chaque étudiant : les cours sont affichés « Cours 1 », « Cours 2 », ..., « Cours N » selon leur ordre de création.
- Renumérotation dynamique : la suppression d'un cours réajuste automatiquement l'ordre consécutif des cours restants (1..N-1).
- Clés techniques MySQL (`id`) strictement préservées en interne pour la fiabilité des routes API.

### 3. Gestion Multi-Documents & Pipeline RAG
- Possibilité d'associer plusieurs documents PDF au même cours.
- Extraction de texte à la volée via Apache PDFBox lors de l'upload.
- Transmission automatique au microservice Python pour le nettoyage et le découpage en segments (*chunks*).
- Persistance des segments dans la table `document_chunks` pour l'indexation vectorielle future.
- Interface étudiant épurée : masque les métriques techniques de découpage et n'affiche que les statuts fonctionnels (« Traité », « Traitement en cours », « En attente », « Échec du traitement », « Aucun texte »).
- Modale fluide de consultation du texte extrait avec fonction de copie rapide dans le presse-papier.
- Suppression sécurisée avec confirmation et nettoyage simultané du fichier physique sur disque.

---

## 🧪 Tests & Qualité de Code

Le projet comprend une couverture complète de tests automatisés à tous les niveaux de la pile :

### Tests Unitaires & d'Intégration Backend (Spring Boot / JUnit 5 / MockMvc)
```bash
cd backend
mvn test
```
*Exécute les 66 tests automatisés (authentification, sécurité JWT, isolation des cours, documents multiples, découpage RAG, services IA).*

### Compilation de Production Frontend (Angular)
```bash
cd frontend
npm run build
```
*Valide l'intégrité de l'ensemble des composants Standalone, des templates HTML, des types TypeScript et des feuilles de style CSS.*

### Tests Unitaires du Microservice IA (Python / Pytest)
```bash
cd ai-service
pytest
```
*Valide les algorithmes de nettoyage, de découpage et les endpoints FastAPI.*

### Suites de Tests End-to-End Automatisées (Python)
- `python test_course_numbering_and_ui.py` : Valide la numérotation consécutive, la renumérotation après suppression, l'isolation entre utilisateurs et le statut de traitement propre.
- `python test_sprint2_part1.py` : Valide le pipeline RAG complet et la persistance des chunks.
- `python test_sprint1_refactoring.py` : Valide l'architecture multi-documents et la non-régression.
