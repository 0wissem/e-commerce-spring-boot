# Révision — Spring Boot & Microservices
## Format : Question → Ta réponse → Correction → Note

---

## BLOC 1 — Architecture Hexagonale

**Q1.** Cite les 4 couches de l'architecture hexagonale utilisée dans ce projet et donne le rôle de chacune en une phrase.

> **Ta réponse :** couche 1 domaine: ici on defini la couche metier, les dto. couche 2 application: ici on defini la logique metier. couche 3 infrastructure: ici on utilise les dependences de l'infrastructure. couche 4 api: ici on defini les contrats api.

> **Correction :** Bonne base mais les DTOs ne sont pas dans le domaine — ils sont dans `application`. Le domaine contient : entités, enums, interfaces de repository, value objects. Zéro import Spring/JPA. La règle de dépendance manquait aussi : `api → application → domain ← infrastructure`. Domain ne connaît personne.

**Note : 13/20**

---

**Q2.** Dans ce projet, `IOrderRepository` est défini dans le domaine, mais `OrderRepositoryAdapter` est dans l'infrastructure. Pourquoi cette séparation ? Qu'est-ce que ça change concrètement ?

> **Ta réponse :** C'est la dependency inversion, la couche domaine decide l'interface a utiliser dans l'infra, IOrderRepository c'est le port, pour que ça soit utiliser dans notre adaptor.

> **Correction :** Exactement. `IOrderRepository` = port (domaine). `OrderRepositoryAdapter` = adaptateur (infrastructure). Si demain tu changes PostgreSQL pour MongoDB, tu crées un nouveau adapter — `OrderService` ne le sait pas. Il manquait la conséquence concrète (remplacement transparent, testabilité).

**Note : 18/20**

---

**Q3.** Quelle est la règle de dépendance entre les couches ?

> **Ta réponse :** domain ne dependre d'aucun couche. application peut dependre de domaine. infra peut dependre de domaine et application. api peut dependre de domaine et application.

> **Correction :** Correct. Nuance : `api` en pratique dépend uniquement de `application`, pas directement du domaine. La règle absolue : `domain` ne connaît personne — pas Spring, pas JPA, pas Jackson.

**Note : 17/20**

---

**Q4.** Dans le code du monolithe, `OrderService` dépend de `IOrderRepository` et non de `OrderRepositoryAdapter`. Pourquoi ?

> **Ta réponse :** Par ce que OrderService est dans la couche application ne doit pas dependre de la couche infra, pour respecter la clean archi.

> **Correction :** Correct. Il manquait la conséquence pratique : `OrderService` déclare `private final IOrderRepository orderRepository` — Spring injecte `OrderRepositoryAdapter` automatiquement mais `OrderService` ne le sait pas. Permet les tests unitaires sans DB.

**Note : 16/20**

---

**Q5.** Donne l'analogie frontend pour chacune des 4 couches.

> **Ta réponse :** domaine, application, infra, presentation. *(noms des couches, pas les analogies)*

> **Correction :** Mauvaise compréhension de la question. La réponse attendue : domain = hook utilitaire pur (calcul sans fetch), application = hook qui orchestre les appels, infrastructure = appels HTTP directs, api = composant qui déclenche au clic.

**Note : 0/20**

---

## BLOC 2 — DTO & Mapper Pattern

**Q6.** Qu'est-ce qu'un DTO ? Pourquoi est-ce qu'on ne retourne pas directement l'entité JPA ?

> **Ta réponse :** Le dto c'est l'objet qu'on retourne dans une reponse api. On retourne pas directement l'entité jpa pour ne pas retourner toutes les données car c'est dangereux coté securité, et meme pour reduire le payload.

> **Correction :** DTO = Data Transfer Object — pas uniquement en réponse, aussi en entrée (OrderRequest). Les vraies raisons manquantes : découplage (changer le schéma DB sans casser le frontend) et sérialisation circulaire (@ManyToOne/@OneToMany → boucle infinie JSON).

**Note : 14/20**

---

**Q7.** Pourquoi `OrderRequest` et `OrderResponse` sont deux classes séparées ?

> **Ta réponse :** OrderRequest c'est l'interface de la requete reçu de l'exterieur, et OrderResponse c'est l'interface de la reponse.

> **Correction :** Tu as dit ce que chacune fait, pas pourquoi elles sont séparées. La vraie raison : les champs sont différents. `id`, `customerName`, `status`, `totalPrice` n'existent pas dans la requête — ils sont générés/calculés par le serveur. Une seule classe forcerait des champs nullables et permettrait au client d'envoyer des champs qu'il ne devrait pas contrôler.

**Note : 10/20**

---

**Q8.** Quel est le rôle exact de `OrderMapper` ? Pourquoi ce n'est pas le service qui fait la conversion ?

> **Ta réponse :** OrderMapper c'est lui qui va transformer les données, ce n'est pas concretement un service qui fait de la business logic, mais il fait des petits calculs logiques purs.

> **Correction :** Bon instinct sur la séparation. Mais le mapper ne calcule rien — `getSubtotal()` est dans `OrderItem`, pas dans le mapper. Le mapper fait uniquement du mapping mécanique. Raison de la séparation : Single Responsibility Principle — le mapper change quand le DTO change, le service change quand la logique métier change.

**Note : 13/20**

---

**Q9.** `customerName` dans le monolithe vs order-service — d'où vient-il dans chaque cas ?

> **Ta réponse :** *No response*

> **Correction :** Monolithe : vient d'un JOIN JPA via `@ManyToOne Customer` → `order.getCustomer().getName()`. Order-service : vient directement de la colonne `customer_name` en DB — snapshot stocké à la création via `CustomerServiceClient`. La source est différente car order-service n'a pas de table customers.

**Note : —**

---

## BLOC 3 — Flyway & Migrations

**Q10.** Qu'est-ce que Flyway ? Quand est-ce qu'il s'exécute ?

> **Ta réponse :** Flyway c'est un outil qui nous permet la migration de la bd. Il s'execute avant tourner le projet automatiquement.

> **Correction :** Correct mais incomplet. Il manquait le mécanisme clé : Flyway maintient une table `flyway_schema_history` avec un checksum de chaque fichier. Il compare au démarrage et n'exécute que les nouvelles versions. Garantit que tous les environnements ont le même schéma.

**Note : 12/20**

---

**Q11.** Que se passe-t-il si tu modifies `V4__create_orders_tables.sql` déjà appliqué ?

> **Ta réponse :** Rien, car y en a un tableau, dés que flyway comprend que V22 est faite il va chercher V23. *(décrit le fonctionnement normal, pas le cas de modification)*

> **Correction :** Flyway stocke un checksum de chaque fichier. Si tu modifies un fichier déjà appliqué → checksum mismatch → exception au démarrage → application ne démarre pas. Règle absolue : une migration appliquée est immuable. Toujours créer un nouveau fichier.

**Note : 8/20**

---

**Q12.** Pourquoi order-service recommence à `V1` alors que le monolithe est à `V22` ?

> **Ta réponse :** Parce que order-service a son propre base de données, chaque micro-service a son environnement, et ils sont independants.

> **Correction :** Parfait. Il manquait juste que les numéros de version sont locaux à chaque service — `V4` du monolithe et `V4` de product-service sont deux fichiers complètement différents sur deux DBs différentes.

**Note : 18/20**

---

**Q13.** Qu'est-ce que `spring.jpa.hibernate.ddl-auto=validate` ?

> **Ta réponse :** Validate et pas update ou create-drop pour ne pas ecraser la base de donnés, une verification est demandé, si c'est ok ça marche, sinon un msg d'erreur renvoyé et l'application ne demarre pas, sans modifier les tables.

> **Correction :** Parfait. Il manquait la complémentarité Flyway/Hibernate : Flyway crée le schéma, Hibernate vérifie que les entités Java correspondent. Les deux se complètent.

**Note : 18/20**

---

## BLOC 4 — Repository Pattern

**Q14.** Différence entre `OrderJpaRepository` et `OrderRepositoryAdapter` — lequel est le port, lequel est l'adaptateur ?

> **Ta réponse :** OrderRepositoryAdapter situe dans domaine, OrderJpaRepository situe dans infra. OrderJpaRepository est le port, OrderRepositoryAdapter est l'adaptateur. *(tout inversé)*

> **Correction :** Tout inversé. `IOrderRepository` (domaine) = PORT. `OrderRepositoryAdapter` (infrastructure) = ADAPTATEUR. `OrderJpaRepository` (infrastructure) = outil JPA utilisé par l'adaptateur. `OrderService` parle au port, l'adaptateur fait le lien entre port et JPA.

**Note : 4/20**

---

**Q15.** Pourquoi `OrderJpaRepository` est `package-private` ?

> **Ta réponse :** Sécurité.

> **Correction :** Pas de sécurité — c'est de l'encapsulation. `package-private` = visible uniquement dans le package `infrastructure`. Empêche qu'un développeur l'injecte directement dans `OrderService`, ce qui casserait l'architecture hexagonale. Le compilateur devient gardien d'architecture.

**Note : 0/20**

---

**Q16.** Qu'est-ce que `@EntityGraph(attributePaths = {"items"})` ?

> **Ta réponse :** *(pas de réponse)*

> **Correction :** Résout le problème N+1. Sans : 10 commandes = 1 requête orders + 10 requêtes order_items = 11 requêtes. Avec `@EntityGraph` : 1 seule requête SQL avec JOIN. Hibernate charge les commandes et leurs items en une seule fois.

**Note : —**

---

## BLOC 5 — JSONB Snapshot Pattern

**Q17.** Pourquoi stocker un snapshot du produit plutôt que juste le `productId` ?

> **Ta réponse :** Par ce qu'on veut prendre les données a l'instant T ou l'ordre est fait, dans l'autre cas, on peut perdre les valeurs s'ils se changent.

> **Correction :** Exactement. Il manquait les trois cas protégés : prix modifié, nom modifié, produit supprimé (soft delete) — dans tous les cas le snapshot garde la vérité du moment de l'achat.

**Note : 17/20**

---

**Q18.** Rôle de `convertToDatabaseColumn` et `convertToEntityAttribute` ?

> **Ta réponse :** convertToDatabaseColumn retourne un jsonb pour stocker dans la bd. convertToEntityAttribute retourne l'objet OrderProductSnapshot converti de la base de données.

> **Correction :** Correct sur les deux sens. Petite imprécision : on stocke en `TEXT` pas `JSONB` dans ce projet (`@Column(columnDefinition = "text")`). JSONB permettrait des requêtes sur les champs JSON, TEXT stocke juste la chaîne brute.

**Note : 17/20**

---

**Q19.** Lien conceptuel entre `customerName` et `productSnapshot` ?

> **Ta réponse :** Stockage du nom de la customer, meme s'il change son nom.

> **Correction :** Exactement le même pattern. Il manquait la conséquence clé : grâce à ce snapshot, `GET /api/orders` ne fait aucun appel HTTP vers le monolithe. Tout est en DB locale — order-service est vraiment indépendant à la lecture.

**Note : 16/20**

---

**Q20.** Un produit passe de 100€ à 150€. Que voit le client dans son historique ?

> **Ta réponse :** Il va voir 100 euros, car on renvoie un snapshot du produit au moment acheté, du coup pas de http requete pour rendre le nouveau prix.

> **Correction :** Parfait. 100€ dans l'historique, snapshot immuable, aucun appel HTTP à la lecture.

**Note : 20/20**

---

## BLOC 6 — Outbox Pattern

**Q21.** Qu'est-ce que le problème du dual-write ?

> **Ta réponse :** Si une requete en cours d'execution et l'un des services s'arrete, on aura une requete finie et l'autre non, du coup une difference dans la bd.

> **Correction :** Tu décris une conséquence mais pas la définition exacte. Dual-write = écrire dans deux systèmes distincts (DB + Kafka) sans transaction distribuée. Le problème existe même sans crash — si Kafka est indisponible 2 secondes, l'event est perdu mais la DB est committée.

**Note : 11/20**

---

**Q22.** Comment l'Outbox Pattern résout le dual-write en 3 étapes ?

> **Ta réponse :** *No response*

> **Correction :** Étape 1 : écriture atomique en DB (orders + outbox_events dans la même transaction). Étape 2 : OutboxPublisher (Kafka Producer) poll toutes les 5s, envoie à Kafka, marque SENT. Étape 3 : ProductEventConsumer (@KafkaListener dans product-service) consomme et met à jour sa DB. Garantie : at-least-once delivery.

**Note : —**

---

**Q23.** Pourquoi `.get()` pour bloquer l'envoi Kafka ?

> **Ta réponse :** *No response*

> **Correction :** Sans `.get()` : `kafkaTemplate.send()` retourne immédiatement sans attendre confirmation → on marque SENT avant que Kafka confirme → si Kafka échoue, l'event est perdu définitivement. Avec `.get()` : bloque jusqu'à confirmation de Kafka. Si Kafka échoue → exception → event reste PENDING → job réessaie au prochain cycle.

**Note : —**

---

**Q24.** Pourquoi a-t-on décommissionné l'Outbox + Kafka en Phase 4 ?

> **Ta réponse :** Parce que le service est migré avec la base de données, on n'a plus besoin d'utiliser le monolothic pour manipuler les produits.

> **Correction :** Exactement. L'Outbox + Kafka existaient pour synchroniser les deux DBs pendant la phase de double ownership (routing pondéré). À 100% product-service, le monolithe ne possède plus les produits → plus de sync nécessaire → complexité inutile supprimée.

**Note : 17/20**

---

## BLOC 7 — API Gateway & Weighted Routing

**Q25.** Rôle du Gateway. Pourquoi le frontend ne parle pas directement aux services ?

> **Ta réponse :** Pour rederiger la requete vers le service correspondant, enfin on va rendre le monolothic un seul service.

> **Correction :** Correct sur le rôle de redirection. Manquait les raisons de ne pas parler directement : 1. Un seul point d'entrée — le frontend n'a qu'une URL à connaître. 2. Le frontend ne voit pas les migrations internes. 3. CORS et sécurité — les services internes ne sont pas exposés à internet.

**Note : 12/20**

---

**Q26.** Quand `PRODUCT_SERVICE_WEIGHT=1`, que se passait-il ?

> **Ta réponse :** 1% des requetes vont passer par product-service et 99% vont passer par le monolothic.

> **Correction :** Parfait. Il manquait le "pourquoi" : progressive rollout pour tester en conditions réelles avec un impact limité, et possibilité de rollback immédiat à 0% si problème.

**Note : 17/20**

---

**Q27.** Pourquoi un `WeightedRoutingFilter` maison ?

> **Ta réponse :** *No response*

> **Correction :** Le Weight predicate natif de Spring Cloud Gateway 2024.0.1 avait un bug qui retournait 500 en runtime. On l'a remplacé par un `GlobalFilter` custom qui fait exactement la même chose sans le bug.

**Note : —**

---

**Q28.** Pourquoi `/api/orders/**` doit être déclaré avant `/api/**` ?

> **Ta réponse :** Parce que dans le cas contraire, si on passe une requete /api/orders/ elle va etre redirigée vers /api/**, car ça correspond a la pattern.

> **Correction :** Parfait. Spring Cloud Gateway évalue les routes dans l'ordre de déclaration — la première qui match gagne. Règle générale : toujours du plus spécifique au plus général.

**Note : 18/20**

---

## BLOC 8 — Kafka

**Q29.** Kafka est utilisé pour deux choses dans ce projet. Cite-les.

> **Ta réponse :** Kafka est utilisé pour envoyer des mails aux clients, et pour l'outbox dans la migration du monolothic vers product-service.

> **Correction :** Les deux bonnes utilisations. Précision manquante : pour les mails, le Producer ET le Consumer sont dans le même service (monolithe). Pour la sync produits, Producer dans le monolithe et Consumer dans product-service.

**Note : 18/20**

---

**Q30.** Pourquoi `@EnableKafka` obligatoire en Spring Boot 4.x ?

> **Ta réponse :** *No response*

> **Correction :** Dans les versions précédentes, Kafka était activé automatiquement par l'auto-configuration. Spring Boot 4.x a supprimé cette auto-config — `@EnableKafka` doit être déclaré explicitement sinon les `@KafkaListener` ne se déclenchent pas. Comportement silencieux = bug difficile à debugger.

**Note : —**

---

**Q31.** Pourquoi `productId` comme Kafka message key ?

> **Ta réponse :** Ça garantie l'identification du produit.

> **Correction :** La vraie garantie c'est l'ordre des messages. Kafka route les messages vers des partitions via un hash de la clé. Même clé = même partition = ordre garanti. Sans clé : CREATE/UPDATE/DELETE d'un même produit peuvent arriver dans le désordre → incohérence en DB product-service.

**Note : 8/20**

---

## BLOC 9 — HTTP Client Pattern

**Q32.** Qu'est-ce que `RestClient` ? Pourquoi préféré à `RestTemplate` ou `WebClient` ?

> **Ta réponse :** RestClient renvoie les réponses en format json, ça nous aide coté front de lire les infos facilement.

> **Correction :** Mauvaise compréhension. RestClient = client HTTP côté serveur pour appeler un autre service backend. Ce n'est pas pour le frontend. Préféré car : RestTemplate est déprécié, WebClient est réactif/complexe, RestClient est moderne et synchrone avec une API fluide.

**Note : 4/20**

---

**Q33.** À quelle couche hexagonale appartiennent `ProductServiceClient` et `CustomerServiceClient` ?

> **Ta réponse :** *No response*

> **Correction :** Couche infrastructure. Ce sont des détails techniques qui parlent à l'extérieur via HTTP — exactement comme `OrderRepositoryAdapter` parle à PostgreSQL. Règle : tout ce qui parle à l'extérieur (DB, HTTP, Kafka, email) appartient à l'infrastructure.

**Note : —**

---

**Q34.** Que se passe-t-il si product-service est down pendant `POST /api/orders` ?

> **Ta réponse :** Les requetes renvoient une erreur, mais les autres services marchent quand même.

> **Correction :** Correct sur l'isolation. Précisions manquantes : `ProductServiceClient` lance une exception → `GlobalExceptionHandler` retourne 500. Seul `POST /api/orders` est affecté — `GET /api/orders`, `GET /api/customers` continuent de fonctionner car ils ne dépendent pas de product-service.

**Note : 13/20**

---

## BLOC 10 — Strangler Fig & Phase 4

**Q35.** Explique le pattern Strangler Fig en une phrase.

> **Ta réponse :** Strangler fig nous permet de remplacer ou moderniser un vieux systeme de maniere progressive.

> **Correction :** Parfait. Il manquait le "pourquoi progressif" : pour ne jamais couper le service et limiter le risque — à l'opposé du "big bang rewrite".

**Note : 17/20**

---

**Q36.** Pourquoi routing pondéré avant de couper à 100% ?

> **Ta réponse :** Pour assurer que le transfert marche bien, dés qu'on constate qu'avec 1% y'en a pas des erreurs, on augmente le % progressivement jusqu'à 100%. Ça nous permet de tester un transfert sans faute, sinon on peut revenir vers 0% pour fixer.

> **Correction :** Parfait. Les trois points clés : tester progressivement, monter graduellement, rollback facile via variable d'environnement.

**Note : 20/20**

---

**Q37.** Pourquoi supprimer le code product du monolithe seulement après 100% ?

> **Ta réponse :** Avant 100% c'est impossible car on a besoin d'executer le code, après le code devient inutile, et c'est notre objectif, c'est de séparer le service.

> **Correction :** Exactement. À 100% le code est "mort fonctionnellement" — présent mais jamais exécuté. On supprime le code mort. Il manquait la formulation explicite "code mort à 100%".

**Note : 18/20**

---

**Q38.** Pourquoi était-il sûr de dropper les tables `products` et `product_categories` en V20 ?

> **Ta réponse :** On est sûr car la migration vers product-service est déjà faite, du coup on utilise la db de product-service, y'en a plus de communication avec les tables products et product_categories du monolothic.

> **Correction :** Parfait. Les deux conditions : 100% du trafic routé vers product-service + Outbox décommissionné = plus aucune lecture ni écriture sur ces tables.

**Note : 20/20**

---

## BLOC 11 — Soft Delete

**Q39.** Qu'est-ce que le soft delete ? Quelle annotation ?

> **Ta réponse :** Soft delete c'est le fait de supprimer un row par exemple un produit, mais en le gardant dans la base, c'est a dire c'est un UPDATE et pas DELETE sur SQL, en utilisant une colonne pour dire c'est supprimé ou supprimé a quelle date. La commande @SQLRestriction("deleted_at IS NULL").

> **Correction :** Réponse quasi-parfaite. Il manquait pourquoi on garde le row : historique, audit, récupération possible.

**Note : 19/20**

---

**Q40.** Effet concret de `@SQLRestriction("deleted_at IS NULL")` ?

> **Ta réponse :** Lorsqu'on a une requete get products, jpa va retourner les produits qui ont dans la colonne deleted_at null, si c'est pas null il va pas la renvoyer.

> **Correction :** Parfait. Et ça s'applique à toutes les requêtes automatiquement — findAll, findById, findByName — Hibernate injecte la condition partout sans que tu l'écrives manuellement.

**Note : 20/20**

---

## BLOC 12 — Spring Profiles & Configuration

**Q41.** Différence dev vs prod pour la base de données ?

> **Ta réponse :** En dev on utilise H2 c'est une base de données dont les requetes sont ecrites en java, en prod y en a une bd PostgreSQL hébergée chez AWS.

> **Correction :** Correct sur la séparation. Imprécision : H2 n'est pas "une DB dont les requêtes sont écrites en Java" — c'est une DB **in-memory** écrite en Java qui tourne dans la mémoire de l'application. Démarre avec l'app, données perdues à l'arrêt. Zéro installation requise.

**Note : 14/20**

---

**Q42.** Que signifie `${PRODUCT_SERVICE_URL:http://localhost:8081}` ?

> **Ta réponse :** Dans les env variables, s'il y a une var nommée PRODUCT_SERVICE_URL donc l'utilise, sinon par defaut la valeur sera http://localhost:8081.

> **Correction :** Parfait.

**Note : 20/20**

---

## BLOC 13 — CI/CD & AWS

**Q43.** Qu'est-ce que le step "self-healing" dans le pipeline ?

> **Ta réponse :** Self-healing c'est a dire, si on trouve pas l'instance AWS Elastic BeansTalk, on en crée une. L'objectif c'est de ne pas créer l'instance manuellement, et automatiser tout.

> **Correction :** Parfait. Il manquait les cas déclencheurs : env crashé, supprimé manuellement, ou premier déploiement (env n'existe pas encore). Le pipeline gère aussi le cas "existe mais broken" → rebuild.

**Note : 18/20**

---

**Q44.** Pourquoi "Wait for environment to be Ready" avant le deploy ?

> **Ta réponse :** Lors de la création de l'image on la déploie sur ECR, puis on deploy sur Elastic Beanstalk. Pour some reason EBTalk peut être pas stable, not ready, par exemple un redémarrage manuel sur AWS est en cours. Pour assurer le déploiement on attend jusqu'à avoir l'instance ready.

> **Correction :** Correct. Il manquait le cas concret vécu dans ce projet : force-push de dev et master au même SHA → deux pipelines simultanés → le deuxième trouvait l'env en état `Updating` → erreur "Must be Ready".

**Note : 18/20**

---

**Q45.** Qu'est-ce que `github.sha` et pourquoi c'est un bon choix ?

> **Ta réponse :** *No response*

> **Correction :** `github.sha` = le hash du commit qui a déclenché le pipeline. Bon choix car : unique (chaque commit a un SHA différent), traçable (on sait exactement quel code est déployé), automatique (pas de gestion manuelle de version). Permet le rollback vers n'importe quel commit précédent.

**Note : —**

---

## BLOC 14 — Global Exception Handler

**Q46.** Que fait `@RestControllerAdvice` ?

> **Ta réponse :** Au lieu de mettre try catch partout, @RestControllerAdvice va detecter le crash, va se comporter comme un try catch sans crasher tout le backend, et envoyer une error response de GlobalExceptionHandler.

> **Correction :** Parfait. Il manquait que ça garantit une réponse cohérente (`ApiResponse`) pour toutes les erreurs peu importe d'où elles viennent.

**Note : 18/20**

---

**Q47.** Bug lié à `handleGeneral(Exception ex)` qui retourne toujours 500 ?

> **Ta réponse :** Quand on fait une requete sur un api avec un payload mal écrit (ne correspond pas a l'interface Request).

> **Correction :** Mauvaise question lue — tu décris la validation (`@Valid`). Le vrai bug : `handleGeneral` attrape `NoHandlerFoundException` (route inexistante). Une route qui n'existe pas retourne 500 au lieu de 404 — impossible de distinguer "route inexistante" de "vrai crash serveur". Découvert avec `/api/auth/login` qui n'existe pas dans le backend.

**Note : 0/20**

---

## BLOC 15 — Order Service (planification)

**Q48.** Dans order-service, `Order` n'a pas de `@ManyToOne Customer`. Qu'est-ce qu'il a à la place ?

> **Ta réponse :** On a mis CustomerName comme un snapshot de la customer (même pattern que productSnapshot) pour garder le nom à l'instant de l'order. Le nom peut être modifié au futur et on aura besoin de garder le nom exact au moment de l'achat.

> **Correction :** Parfait. Il manquait la précision technique : `String customerId` (plain String, pas de FK) + `String customerName` (snapshot). Pas de `@ManyToOne` car order-service n'a pas de table customers dans sa DB.

**Note : 18/20**

---

**Q49.** `CustomerServiceClient` — quand est-il appelé ? Et à la lecture ?

> **Ta réponse :** Pour verifier le bon client au moment de la creation de l'order, est ce qu'on a vraiment ce customer dans notre db ou pas. Si c'est le cas on continue la creation, sinon on retourne une erreur. Pour la lecture non, car on garde le nom du client dans customerName.

> **Correction :** Bonne intuition mais nuance : order-service n'a pas de DB customers — il délègue la vérification au monolithe via `CustomerServiceClient`. L'appel fait deux choses en même temps : vérifier l'existence ET récupérer le nom pour le snapshot.

**Note : 15/20**

---

**Q50.** Comment résoudre l'absence de `customer_name` lors de la migration ?

> **Ta réponse :** *No response*

> **Correction :** La table `orders` du monolithe n'a pas de colonne `customer_name`. Solution : un JOIN dans le script de migration — `SELECT o.*, c.name AS customer_name FROM orders o JOIN customers c ON c.id = o.customer_id`. On reconstitue le snapshot au moment de la migration en joignant les deux tables.

**Note : —**

---

## BONUS — Concepts généraux

**Q51.** Différence monolithe vs microservice ?

> **Ta réponse :** Monolothic, backend dans un seul server, les requetes sql sont plus rapides car on a un seul db. Micro-service, plusieurs servers, chaque service est déployé dans un server avec son db, on a besoin des RestClient pour la communication, ce qui met la latence plus élevée. Mais si un serveur est arrêté, le reste continue, contrairement au monolothic.

> **Correction :** Très bonne réponse. Il manquait la scalabilité indépendante — souvent la vraie raison de passer aux microservices. Avec monolithe : tu dois scaler tout le système même si seule la recherche est sous charge. Avec microservices : tu scales uniquement le service concerné.

**Note : 17/20**

---

**Q52.** Pourquoi ne pas utiliser une base de données partagée ?

> **Ta réponse :** *No response*

> **Correction :** DB partagée = couplage fort. Si la DB tombe → les deux services tombent. Schéma partagé = migrations dangereuses (une équipe peut casser l'autre). Scalabilité impossible (goulot d'étranglement commun). Ownership flou (qui est responsable de quelle table ?). Règle : un service = une DB pour être vraiment indépendant.

**Note : —**

---

**Q53.** Comment order-service connaît le prix d'un produit sans accès à la DB product-service ?

> **Ta réponse :** Le prix est dans productSnapshot.

> **Correction :** Parfait. Prix capturé via `ProductServiceClient` à la création, stocké dans `product_snapshot`. À la lecture, le prix vient directement du snapshot — aucun appel HTTP vers product-service.

**Note : 20/20**

---

**Q54.** Rôle de `@Transactional` sur `OrderService` ?

> **Ta réponse :** Dans orderService il y a plus d'une seule requête SQL, donc on utilise Transactional pour assurer que les requêtes fonctionnent toutes ensemble, sinon on fait un rollback sur les requêtes réussies.

> **Correction :** Parfait. Il manquait l'exemple concret : sans `@Transactional`, si l'INSERT du 3ème order_item échoue, la commande existe en DB avec seulement 2 items sur 3 → données corrompues.

**Note : 19/20**

---

## BILAN GLOBAL

| Bloc | Questions | Moy. estimée |
|---|---|---|
| Architecture Hexagonale | Q1–Q5 | 9/20 |
| DTO & Mapper | Q6–Q9 | 13/20 |
| Flyway | Q10–Q13 | 14/20 |
| Repository Pattern | Q14–Q16 | 5/20 |
| JSONB Snapshot | Q17–Q20 | 18/20 |
| Outbox Pattern | Q21–Q24 | 14/20 |
| Gateway & Routing | Q25–Q28 | 16/20 |
| Kafka | Q29–Q31 | 13/20 |
| HTTP Client | Q32–Q34 | 9/20 |
| Strangler Fig | Q35–Q38 | 19/20 |
| Soft Delete | Q39–Q40 | 19/20 |
| Spring Config | Q41–Q42 | 17/20 |
| CI/CD & AWS | Q43–Q46 | 18/20 |
| Exception Handler | Q46–Q47 | 9/20 |
| Order Service Plan | Q48–Q50 | 17/20 |
| Bonus | Q51–Q54 | 18/20 |

**Moyenne globale : ~14/20**

### Points forts
- Strangler Fig, Soft Delete, Snapshots, CI/CD, Spring Config → maîtrisés

### À retravailler
- Architecture hexagonale (Q1, Q14) — DTOs mal placés, port/adaptateur inversés
- Outbox flux complet (Q22, Q23) — le flux Producer → Kafka → Consumer
- RestClient (Q32, Q33) — client HTTP inter-services, pas sérialisation frontend
- Kafka avancé (Q30, Q31) — @EnableKafka, garantie d'ordre par partition
