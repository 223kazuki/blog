---
title: Tips for lacinia app development
description: ""
image: https://223kazuki.github.io/images/me.jpg
author: Kazuki Tsutsumi
author-email: rixi223.kazuki@gmail.com
author-url: https://twitter.com/goronao
author-github: https://github.com/223kazuki
author-twitter: goronao
author-avatar: https://pbs.twimg.com/profile_images/1218227972/P1060544_400x400.jpg
location: San Jose, CA, USA
date-created: 2019-01-29
date-modified: 2019-01-29
date-published: 2019-01-29
headline:
in-language: en
keywords: Clojure, ClojureScript, Lacinia, GraphQL, re-frame, integrant, hodur
uuid: 123b5035-6286-4b20-b544-758765c380a6
tags:
 - Clojure
 - ClojureScript
 - Lacinia
 - GraphQL
 - re-frame
 - integrant
 - hodur
---

Recently, I tried Lacinia because many clojurians and companies begin to use it. It's one of the most exciting clojure library today. I'm very fascinated by it. But while developing Lacinia application, I also met some difficulties. In this post, I introduce how I resolved those difficulties as Tips for lacinia app development. As I don't intend to introduce Lacinia itself, I don't explain what Lacinia is. Please read [Lacinia document](https://lacinia.readthedocs.io/en/latest/) and try tutorial first.

## Example repositories
What I developed are these.

* https://github.com/223kazuki/clj-graphql-server ... GraphQL API server
* https://github.com/223kazuki/clj-graphql-client ... GraphQL client

The theme is data API of 大相撲: [Grand Sumo Tournament](http://www.sumo.or.jp/En/). And technology stack are followings.

* GraphQL API: Lacinia + Pedestal
* Persistence layer: Datomic
* API Authentication: OAuth2
* Framework: duct
* Domain modeling: hodur
* Client: re-frame + integrant

Please refer to README of each repositories in order to know the version of dependencies and how to setup them.
clj-graphql-server also hosts [GraphiQL](https://github.com/graphql/graphiql) in dev profile. So you can try queries.
![graphiql.gif](https://qiita-image-store.s3.amazonaws.com/0/109888/7d78104c-ff68-77ae-2975-06c9f9412772.gif)

In following sections, I will introduce the Tips for Lacinia app development by using these repositories.

## ductify
I use [pedestal](https://github.com/pedestal/pedestal) to host Lacinia because Lacinia provides [lacinia-pedestal](https://github.com/walmartlabs/lacinia-pedestal). As both Lacinia and Pedestal have a lot of parameters to initialize, I use [duct](https://github.com/duct-framework/duct) framework to manage them declaratively. [module.pedestal](https://github.com/lagenorhynque/duct.module.pedestal) provides pedestal server module to duct. And lacinita-pedestal provides GraphQL API service map for pedestal. So I wrap it in integrant key(`:graphql-server.lacinia/service`) and set ref to pedestal key(`:duct.server/pedestal`). And I define some other integrant keys to manage lacinia components like resolver, streamer, or schema because they have their own dependencies.
The abstract of configuration map is as follow.

```clojure:resources/graphql_server/config.edn
{:duct.profile/base
 {:duct.core/project-ns graphql-server

  :duct.server/pedestal {:service #ig/ref :graphql-server.lacinia/service} ;; pedestal server refer to lacinia service.

  :graphql-server.lacinia/schema {:meta-db #ig/ref :graphql-server/hodur} ;; Lacinia schema

  :graphql-server.lacinia/service ;; lacinia-pedestal service refer to schema, resolvers and etc.
  {:port #duct/env ["PORT" Int :or 8080]
   :subscriptions true
   :keep-alive-ms 10000
   :init-context #ig/ref :graphql-server.handler.auth/ws-init-context
   :optional-interceptors [#ig/ref :graphql-server.interceptor/auth]
   :optional-routes
   [["/login"      :get  [#ig/ref :graphql-server.handler.auth/login-page] :route-name :login-page]
    ["/login"      :post [#ig/ref :graphql-server.interceptor/body-params
                          #ig/ref :graphql-server.handler.auth/login] :route-name :login]
    ["/token"      :post [#ig/ref :graphql-server.handler.auth/token] :route-name :token]
    ["/introspect" :get  [#ig/ref :graphql-server.handler.auth/introspect] :route-name :introspect]
    ["/graphql"    :options [#ig/ref :graphql-server.handler.cors/preflight] :route-name :preflight]]
   :schema #ig/ref :graphql-server.lacinia/schema ;; Lacinia schema
   :resolvers {:get-viewer #ig/ref :graphql-server.handler.resolver/get-viewer ;; lacinia resolvers
               :user-favorite-rikishis #ig/ref :graphql-server.handler.resolver/user-favorite-rikishis
               :get-favorite-rikishis #ig/ref :graphql-server.handler.resolver/get-favorite-rikishis
               :get-rikishi #ig/ref :graphql-server.handler.resolver/get-rikishi
               :rikishi-sumobeya #ig/ref :graphql-server.handler.resolver/rikishi-sumobeya
               :sumobeya-rikishis #ig/ref :graphql-server.handler.resolver/sumobeya-rikishis
               :get-sumobeya #ig/ref :graphql-server.handler.resolver/get-sumobeya
               :get-rikishi-by-shikona  #ig/ref :graphql-server.handler.resolver/get-rikishi-by-shikona
               :create-rikishi #ig/ref :graphql-server.handler.resolver/create-rikishi
               :fav-rikishi  #ig/ref :graphql-server.handler.resolver/fav-rikishi
               :unfav-rikishi  #ig/ref :graphql-server.handler.resolver/unfav-rikishi
               :get-rikishis #ig/ref :graphql-server.handler.resolver/get-rikishis
               :torikumi-higashi-rikishi #ig/ref :graphql-server.handler.resolver/torikumi-higashi-rikishi
               :torikumi-nishi-rikishi #ig/ref :graphql-server.handler.resolver/torikumi-nishi-rikishi
               :torikumi-shiroboshi-rikishi #ig/ref :graphql-server.handler.resolver/torikumi-shiroboshi-rikishi}
   :streamers {:stream-torikumis #ig/ref :graphql-server.handler.streamer/stream-torikumis}}

  ;; Lacinia resolver, streamer
  :graphql-server.handler.resolver/get-viewer {:auth #ig/ref :graphql-server/auth}
  :graphql-server.handler.resolver/user-favorite-rikishis {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/get-favorite-rikishis {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/get-rikishi {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/rikishi-sumobeya {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/sumobeya-rikishis {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/get-sumobeya {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/get-rikishi-by-shikona {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/create-rikishi {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/fav-rikishi {:auth #ig/ref :graphql-server/auth :db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/unfav-rikishi {:auth #ig/ref :graphql-server/auth :db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/get-rikishis {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/torikumi-higashi-rikishi {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/torikumi-nishi-rikishi {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/torikumi-shiroboshi-rikishi {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.streamer/stream-torikumis {:db #ig/ref :duct.database/datomic
                                                     :channel #ig/ref :graphql-server/channel}}
 :duct.module/pedestal {}}
```

The implementation of `:graphql-server.lacinia/service` method is as follows.

```clojure:src/graphql_server/lacinia.clj
(defmethod ig/init-key ::service
  [_ {:keys [schema resolvers streamers
             optional-routes optional-interceptors optional-subscription-interceptors]
      :as options}]
  (let [compiled-schema (-> schema
                            (attach-resolvers resolvers)
                            (attach-streamers streamers)
                            schema/compile)
        interceptors (->> (lacinia/default-interceptors compiled-schema options)
                          (concat optional-interceptors)
                          (map interceptor/map->Interceptor)
                          (into []))
        subscription-interceptors (->> [exception-handler-interceptor
                                        send-operation-response-interceptor
                                        (query-parser-interceptor compiled-schema)
                                        execute-operation-interceptor]
                                       (concat optional-subscription-interceptors)
                                       (map interceptor/map->Interceptor)
                                       (into []))
        routes (->> (lacinia/graphql-routes compiled-schema
                                            (assoc options :interceptors interceptors))
                    (concat optional-routes)
                    (into #{}))
        service-map (lacinia/service-map compiled-schema
                                         (assoc options
                                                :routes routes
                                                :subscription-interceptors subscription-interceptors))]
    service-map))
```

It compose service map by `com.walmartlabs.lacinia.pedestal/service-map` from parameters and pass it to `:duct.server/pedestal`. Then `:duct.server/pedestal` starts server.

## Implement authentication for GraphQL API access
As GraphQL is just a specification of data access, there's no specification about authentication. Lacinia also doesn't have any function of authentication. So we have to implement it by ourselves. Then we have to implement it in two access methods, HTTP and WebSocket, because GraphQL supports both of them.

According to GitHub GraphQL API, it provides token based authentication.
https://developer.github.com/v4/guides/forming-calls/#authenticating-with-graphql

So I decided to apply OAuth 2.0 authentication for API access.

I've already implement [OAuth 2.0 authentication in duct](https://github.com/223kazuki/clj-oauth-server) before. So I use it and don't explain itself in this post. But to put it briefly, I implemented OAuth 2.0 handlers in [clj-graphql-server/src/graphql_server/handler/auth.clj](https://github.com/223kazuki/clj-graphql-server/blob/master/src/graphql_server/handler/auth.clj) and manage access token in [clj-graphql-server/src/graphql_server/auth.clj](https://github.com/223kazuki/clj-graphql-server/blob/master/src/graphql_server/auth.clj). Auth handlers are injected to `:graphql-server.lacinia/service` by `:optional-routes` key.

### Authencication check for HTTP access
Then where should we implement authentication check? When an authenticated client posts HTTP request to GraphQL API, we should implement it in interceptor. I implemented it as follows.

```clojure:src/graphql_server/interceptors.clj
(defmethod ig/init-key ::auth [_ {:keys [:auth]}]
  {:name  ::auth
   :enter (fn [{{:keys [:headers :uri :request-method] :as request} :request :as context}]
            (let [forbidden-response {:status 403
                                      :headers {"Content-Type" "application/json"
                                                "Access-Control-Allow-Origin" (get headers "origin")}
                                      :body (json/write-str {:errors [{:message "Forbidden"}]})}]
              (if-not (and (= uri "/graphql")
                           (= request-method :post)) ;; Authenticate only GraphQL endpoint.
                context
                (if-let [access-token (some-> headers
                                              (get "authorization")
                                              (str/split #"Bearer ")
                                              last
                                              str/trim)] ;; Get access token from request headers.
                  (if-let [auth-info (auth/get-auth auth access-token)] ;; Get auth info by access token.
                    (assoc-in context [:request :auth-info] auth-info) ;; Set it to context if auth info that includes login user info.
                    (assoc context :response forbidden-response)) ;; Return forbidden response if no auth info.
                  (assoc context :response forbidden-response)))))}) ;; Return forbidden response if no token.
```

This interceptor is injected to `:graphql-server.lacinia/service` by `:optional-interceptors` key. It gets access token from request headers, gets auth info by token, sets auth info to context and returns context. So following procedures like resolver can use auth info via context.

```clojure:src/graphql_server/handler/resolver.clj
(defmethod ig/init-key ::get-viewer [_ {:keys [auth db]}]
  (fn [{request :request :as ctx} args value]
    (let [{:keys [id email-address]} (get-in request [:auth-info :client :user])] ;; Use auth info via context.
      (->Lacinia {:id id :email-address email-address}))))
```

### Authentication check for WebSocket access
When an authenticated client tried to access GraphQL API by WebSocket, it's common to check token in Handshake. Although WebSocket Handshake request also be able to pass access token in request headers, some client libraries don't support it. So I chose the way passing access token as a URL parameter.

`GET http://localhost:8080/graphql-ws?token=xxxxxxxxxxx`

And lacinia-pedestal has `:init-context` parameter to set a function to initialize context in WebSocket HandShake. So we can implement token check there.

```clojure:src/graphql_server/handler/auth.clj
(defmethod ig/init-key ::ws-init-context [_ {:keys [auth]}]
  (fn [ctx ^UpgradeRequest req ^UpgradeResponse res]
    (if-let [access-token (some->> (.get (.getParameterMap req) "token") ;; Get access token from URL parameter.
                                   first)]
      (if-let [auth-info (auth/get-auth auth access-token)]
        (assoc-in ctx [:request :lacinia-app-context :request :auth-info] auth-info) ;; We have to set auth info in [:request :lacinia-app-context] in this case.
        (do
          (.sendForbidden res "Forbidden") ;; Set forbidden to org.eclipse.jetty.websocket.api.UpgradeResponse.
          ctx))
      (do
        (.sendForbidden res "Forbidden")
        ctx))))
```

This function is injected to `:graphql-server.lacinia/service` by `:init-context` key. 
In order to use auth info in resolver, we have to set it in context. But according to lacinia-pedestal implementation, only `(get-in context [:request :lacinia-app-context])` will be passed to resolvers. And `lacinia-app-context` will be overwritten by `:com.walmartlabs.lacinia.pedestal.subscriptions/inject-app-context`. 
So we have to remove this interceptor, and set auth info under `[:request :lacinia-app-context]` keys in context.

```clojure:src/graphql_server/lacinia.clj
        subscription-interceptors (->> [exception-handler-interceptor
                                        send-operation-response-interceptor
                                        (query-parser-interceptor compiled-schema)
                                        execute-operation-interceptor] ;; Remove inject-app-context from default-subscription-interceptors.
                                       (concat optional-subscription-interceptors)
                                       (map interceptor/map->Interceptor)
                                       (into []))
```

In this way, we can check authentication in WebSocket Handshake and use auth info in resolvers and streamers.

```clojure:src/graphql_server/handler/streamer.clj
(defmethod ig/init-key ::stream-torikumis [_ {:keys [db channel]}]
  (fn [{request :request :as ctx} {:keys [num]} source-stream]
    (println "Start subscription.")
    (let [{:keys [id]} (get-in request [:auth-info :client :user]) ;; Get auth info from context.
          torikumis (db/find-torikumis db id num)]
      ;; ...
  )))
```

## Connect to Datomic
I use Datomic as data persistence layer. In order to use it in duct, I implemented module.datomic by referring to module.sql.

```clojure:src/duct/module/datomic.clj
(ns duct.module.datomic
  (:require [duct.core :as core]
            [duct.core.env :as env]
            [duct.core.merge :as merge]
            [integrant.core :as ig]))

(def ^:private default-datomic-url
  (env/env "DATOMIC_URL"))

(def ^:private env-strategy
  {:production  :raise-error
   :development :rebase})

(defn- database-config [connection-uri]
  {:duct.database/datomic
   ^:demote {:connection-uri connection-uri
             :logger   (ig/ref :duct/logger)}})

(defn- migrator-config [environment]
  {:duct.migrator/datomic
   ^:demote {:database (ig/ref :duct.database/datomic)
             :logger (ig/ref :duct/logger)
             :transactions []}})

(defn- get-environment [config options]
  (:environment options (:duct.core/environment config :production)))

(defmethod ig/init-key :duct.module/datomic [_ options]
  (fn [config]
    (core/merge-configs
     config
     (database-config (:connection-uri options default-datomic-url))
     (migrator-config (get-environment config options)))))
```

This module inject `:duct.database/datomic` key that has connection to Datomic. So we can use it to access Datomic in resolvers or streamers.

```clojure:resources/geraphql_server/config.edn
  :graphql-server.handler.resolver/user-favorite-rikishis {:db #ig/ref :duct.database/datomic} ;; Refer to datomic key.
```

Though I abstract Datomic access as a boundary, Datomic transaction and Lacinia parameters are both just a clojure data. So they are very compatible. And Datomic query has enough flexibility to accept expressive power of GraphQL query. Following query means "Get all torikumis which has rikishi which an user added to his favorite". ("Torikumi" means a sumo match between rikishi in "higashi" and rikishi in "nishi".) It's surprising that Datalog can easily express such a complicated query among many-to-many entities.

```clojure
(d/q '[:find ?e
       :in $ ?user
       :where (or (and [?e :torikumi/higashi ?higashi]
                       [?e :torikumi/nishi ?nishi]
                       [?user :user/favorite-rikishis ?higashi])
                  (and [?e :torikumi/higashi ?higashi]
                       [?e :torikumi/nishi ?nishi]
                       [?user :user/favorite-rikishis ?nishi]))]
     db [:user/id user-id])
```

## Commonize schema definition
Datomic needs schema as with Lacinia. Consequently we want to commonize them and generate automatically. I tried [umlaut](https://github.com/workco/umlaut) before to that. But I found another tool, [hodur](https://github.com/luchiniatwork/hodur-engine) in last clojure/conj.
[![Declarative Domain Modeling for Datomic Ion/Cloud](http://img.youtube.com/vi/EDojA_fahvM/0.jpg)](https://www.youtube.com/watch?v=EDojA_fahvM)

It's a domain modeling tool which can generate various schema definitions.
Although there are not so many differences among them, hodur can define model definition as edn in contrast to umlaut's GraphQL like definition.
Hodur uses [datascript](https://github.com/tonsky/datascript) internally to generate meta-database. And each plugins queries it to generate their schemas.
The following is the model definition of hodur.

```clojure:resources/graphql_server/schema.edn
[^{:lacinia/tag true
   :datomic/tag true
   :spec/tag true}
 default

 rikishi
 [^{:type Integer
    :datomic/unique :db.unique/identity} id
  ^String shikona
  ^{:type sumobeya
    :lacinia/resolve :rikishi-sumobeya
    :spec/tag false} sumobeya
  ^String banduke
  ^String syusshinchi]

 sumobeya
 [^{:type Integer
    :datomic/unique :db.unique/identity} id
  ^String name
  ^{:type rikishi
    :spec/tag false
    :cardinality [0 n]
    :datomic/tag false
    :lacinia/resolve :sumobeya-rikishis} rikishis]
 ;; ...

 ^:lacinia/query
 QueryRoot
 [^{:type user
    :lacinia/resolve :get-viewer} viewer []
  ;; ...
 ]
 ;; ...
]
```

Which model each plugins use is controlled by `:[plugin]/tag` key in their meta data. And other meta data for each plugins are defined `:[plugin]/*` keys. For example, lacinia resolver name is defined `:lacinia/resolver` key.
As the model definition is edn, we can include it directly from config.edn

```clojure:resources/graphql_server/config.edn
  :graphql-server/hodur {:schema #duct/include "graphql_server/schema"} ;; Include schema.edn directly
  :graphql-server.datomic/schema {:meta-db #ig/ref :graphql-server/hodur} ;; Generate Datomic schema
  :graphql-server.lacinia/schema {:meta-db #ig/ref :graphql-server/hodur} ;; Generate Lacinia schema
```

```clojure:src/graphql_server/hodur.clj
(ns graphql-server.hodur
  (:require [integrant.core :as ig]
            [hodur-engine.core :as hodur]))

(defmethod ig/init-key :graphql-server/hodur [_ {:keys [schema]}]
  #_ (binding [*print-meta* true]
       (pr schema))
  (hodur/init-schema schema)) ;; Generate meta-db
```

Because both Lacinia and Datomic allow reference type in their schema definition, we can almost same entity models. But GraphQL accepts circular reference while Datomic doesn't. So we have to set `:datomic/tag false` in one side of reference attribute.

### Generate Lacinia schema
By using Lacinia plugin, we can generate Lacinia schema from hodur meta-db.
https://github.com/luchiniatwork/hodur-lacinia-schema

```clojure:src/graphql_server/lacinia.clj
(defmethod ig/init-key ::schema [_ {:keys [meta-db]}]
  (-> meta-db
      hodur-lacinia/schema))
```

Generated schema is as follows.

```clojure
{:objects
 {:Torikumi
  {:fields
   {:higashi
    {:type (non-null :Rikishi), :resolve :torikumi-higashi-rikishi},
    :id {:type (non-null Int)},
    :kimarite {:type (non-null :Kimarite)},
    :nishi
    {:type (non-null :Rikishi), :resolve :torikumi-nishi-rikishi},
    :shiroboshi
    {:type (non-null :Rikishi),
     :resolve :torikumi-shiroboshi-rikishi}}},
  :RikishiConnection
  {:fields
   {:edges {:type (non-null (list (non-null :RikishiEdge)))},
    :pageInfo {:type (non-null :PageInfo)},
    :totalCount {:type (non-null Int)}}},
  :Rikishi
  {:fields
   {:banduke {:type (non-null String)},
    :id {:type (non-null Int)},
    :shikona {:type (non-null String)},
    :sumobeya
    {:type (non-null :Sumobeya), :resolve :rikishi-sumobeya},
    :syusshinchi {:type (non-null String)}}},
  :Sumobeya
  {:fields
   {:id {:type (non-null Int)},
    :name {:type (non-null String)},
    :rikishis
    {:type (non-null (list (non-null :Rikishi))),
     :resolve :sumobeya-rikishis}}},
 ;; ...
 :enums
 {:Kimarite
  {:values
   [{:enum-value :ABISETAOSHI}
    {:enum-value :AMIUCHI}
    {:enum-value :ASHITORI}
    ;; ...
    ]}},
 :queries
 {:favoriteRikishis
  {:type (non-null (list (non-null :Rikishi))),
   :resolve :get-favorite-rikishis},
  :rikishi
 ;; ...
 },
 :mutations
 {:createRikishi
  {:type (non-null :Rikishi),
   :resolve :create-rikishi,
   :args
   {:banduke {:type (non-null String)},
    :shikona {:type (non-null String)},
    :sumobeyaId {:type (non-null Int)},
    :syusshinchi {:type (non-null String)}}},
 ;; ...
 },
 :subscriptions
 {:torikumis
  {:type (non-null (list (non-null :Torikumi))),
   :stream :stream-torikumis,
   :args {:num {:type (non-null Int)}}}}}
```

The method implemented by `:graphql-server.lacinia/service` key refers this schema and compiles it.

### Migrate Datomic schema
By using Datomic plugin, we can generate Datomic schema from hodur meta-db.
https://github.com/luchiniatwork/hodur-datomic-schema

```clojure:src/graphql_server/datomic.clj
(defmethod ig/init-key ::schema [_ {:keys [meta-db]}]
  (hodur-datomic/schema meta-db)) ;; Generate Datomic schema
```

Generated schema is as follows.

```clojure
[#:db{:ident :mutation-root/create-rikishi,
      :valueType :db.type/ref,
      :cardinality :db.cardinality/one}
 #:db{:ident :mutation-root/fav-rikishi,
      :valueType :db.type/ref,
      :cardinality :db.cardinality/many}
 #:db{:ident :mutation-root/unfav-rikishi,
      :valueType :db.type/ref,
      :cardinality :db.cardinality/many}
 #:db{:ident :query-root/favorite-rikishis,
      :valueType :db.type/ref,
      :cardinality :db.cardinality/many}
 #:db{:ident :query-root/rikishi,
      :valueType :db.type/ref,
      :cardinality :db.cardinality/one}
 #:db{:ident :query-root/rikishi-by-shikona,
      :valueType :db.type/ref,
      :cardinality :db.cardinality/one}
 #:db{:ident :query-root/rikishis,
      :valueType :db.type/ref,
      :cardinality :db.cardinality/one}
 #:db{:ident :query-root/sumobeya,
      :valueType :db.type/ref,
      :cardinality :db.cardinality/one}
 #:db{:ident :query-root/viewer,
      :valueType :db.type/ref,
      :cardinality :db.cardinality/one}
 #:db{:ident :subscription-root/torikumis,
      :valueType :db.type/ref,
      :cardinality :db.cardinality/many}
 #:db{:ident :client/application-name,
      :valueType :db.type/string,
      :cardinality :db.cardinality/one}
 #:db{:ident :client/application-type,
      :valueType :db.type/string,
      :cardinality :db.cardinality/one}
 #:db{:ident :client/client-id,
      :valueType :db.type/string,
      :cardinality :db.cardinality/one,
      :unique :db.unique/identity}
 #:db{:ident :client/client-secret,
      :valueType :db.type/string,
      :cardinality :db.cardinality/one}
 #:db{:ident :client/client-type,
      :valueType :db.type/string,
      :cardinality :db.cardinality/one}
 #:db{:ident :client/redirect-uris,
      :valueType :db.type/string,
      :cardinality :db.cardinality/one}
 #:db{:ident :kimarite/abisetaoshi}
 #:db{:ident :kimarite/amiuchi}
 #:db{:ident :kimarite/ashitori}
 ...]
```

In order to migrate it to Datomic, I implemented migrator.datomic.

```clojure:src/duct/migrator/datomic.clj
(defmethod ig/init-key :duct.migrator/datomic [_ {:keys [database schema migrations]
                                                  :as options}]
  (d/transact (:connection database) schema) ;; Migrate schema as transaction.
  (->> migrations
       (map :up)
       (map #(d/transact (:connection database) %))
       doall)
  (println "Migrated"))
```

### Visualize schema
Hodur provides dynamic schema visualizer.
https://github.com/luchiniatwork/hodur-visualizer-schema

We defined hodur models in edn file. As this plugin is implemented by cljs + figwheel, we can't read edn file in a normal way.
But by using clojure macro, we cant do that.
The following is the cljs code which visualize hodur models as SPA.

```clojure:dev/src/graphql_server/visualizer.cljs
(ns graphql-server.visualizer
  (:require [hodur-engine.core :as engine]
            [hodur-visualizer-schema.core :as visualizer])
  (:require-macros [graphql-server.macro :refer [read-schema]]))

(-> (read-schema "graphql_server/schema.edn") ;; This code reads schema.edn file and expand it to cljs code.
    engine/init-schema
    visualizer/schema
    visualizer/apply-diagram!)
```

`read-schema` is a clojure macro implemented as follows.

```clojure:dev/src/graphql_server/macro.cljc
(ns graphql-server.macro
  (:require #?(:clj [clojure.java.io :as io])))

(defmacro read-schema [path]
  #?(:clj (->> path
               io/resource
               slurp
               (str "'")
               read-string)))
```

By this code, we can load edn schema file and visualize it. clj-graphql-server runs this viewer in http://localhost:9500 in dev profile.
![Screen Shot 2019-01-28 at 10.04.46 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/ce339a6a-15d4-16bd-5763-a6a1b554b9d1.png)

When you edit schema file, `(reset)` updates the view. Detect updates and recompile are implemented in `dev/src/graphql_server/visualizer_schema.clj`.

### Generate clojure.spec definitions
Hodur can also generate clojure.spec definitions.
https://github.com/luchiniatwork/hodur-spec-schema

In dev profile, I use clojure.spec to validate input and output of boundary functions. Spec plugin generates unevaluated spec forms from meta-db. So we need to evaluate them to register.

```clojure:dev/src/graphql_server/spec.clj
(defmethod ig/init-key :graphql-server/spec [_ {:keys [meta-db] :as options}]
  (let [spec (hodur-spec/schema meta-db {:prefix :graphql-server.spec})]
    (eval spec) ;; Register specs.
    (fdef) ;; Load function specs.
    (stest/instrument) ;; Activate instrumentation of clojure.spec for functions.
    (assoc options :spec spec)))
```

Generated spec forms are as follows.

```clojure
(clojure.spec.alpha/def
  :graphql-server.spec.client/application-name
  clojure.core/string?)
 (clojure.spec.alpha/def
  :graphql-server.spec.kimarite/tasukizori
  (fn*
   [p1__42308__42309__auto__]
   (clojure.core/= "TASUKIZORI" p1__42308__42309__auto__)))
 (clojure.spec.alpha/def
  :graphql-server.spec.kimarite/tottari
  (fn*
   [p1__42308__42309__auto__]
   (clojure.core/= "TOTTARI" p1__42308__42309__auto__)))
 (clojure.spec.alpha/def
  :graphql-server.spec.kimarite/izori
  (fn*
   [p1__42308__42309__auto__]
   (clojure.core/= "IZORI" p1__42308__42309__auto__)))
 ...]
```

As the plugin generates only specs for entities and attributes, we need to define and register function specs by ourselves. I defined `fdef` function to register function specs in my code.
Then we need to execute `(stest/instrument)` to activate instrumentation for function specs. By default, clojure.spec will only instrument :arg. So I use [orchestra](https://github.com/jeaye/orchestra) to validate both :arg and :ret during its execution.

## Use Subscription
Subscription is a relatively new specification of GraphQL. It enables client to get real time data streaming via WebSocket.
https://facebook.github.io/graphql/June2018/#sec-Subscription

Lacinia supports this functionality. In order to use it, we need to implement not only resolver but also streamer function.
https://lacinia.readthedocs.io/en/latest/subscriptions/

The following is the reference implementation of streamer in Lacinia document.

```clojure
(defn log-message-streamer
  [context args source-stream]
  ;; Create an object for the subscription.
  (let [subscription (create-log-subscription)]
    (on-publish subscription
      (fn [log-event]
        (-> log-event :payload source-stream)))
    ;; Return a function to cleanup the subscription
    #(stop-log-subscription subscription)))
```

The third argument, `source-stream` is the function which notify resource update to the client. `create-log-subscription` is a function which create subscription for some resources. On subscription it executes `source-stream`. In a real system, we may implement streamer by using core.async, Kafka or something like that. Streamer function itself returns the callback which ends the subscription.
In this time I implemented API to get torikumi information as a streamer. At first, I implemented a integrant method which generates core.async channel and its publication as follows.

```clojure:src/graphql_server/channel.clj
(ns graphql-server.channel
  (:require [clojure.core.async :refer [chan close! pub unsub-all]]
            [integrant.core :as ig]))

(defmethod ig/init-key :graphql-server/channel [_ _]
  (let [channel (chan)]
    {:channel channel :publication (pub channel :msg-type)})) ;; Generates core.async channel and its publication.

(defmethod ig/halt-key! :graphql-server/channel [_ {:keys [channel publication]}]
  (unsub-all publication)
  (close! channel))
```

streamer takes this publication when initialization and subscribes it.

```clojure:src/graphql_server/handler/streamer.clj
(ns graphql-server.handler.streamer
  (:require [integrant.core :as ig]
            [clojure.core.async :refer [pub sub chan go-loop go >! <!
                                        timeout close! >!! <!! unsub]]
            [graphql-server.boundary.db :as db]))

(defmethod ig/init-key ::stream-torikumis [_ {:keys [db channel]}]
  (fn [{request :request :as ctx} {:keys [num]} source-stream]
    (println "Start subscription.")
    (let [{:keys [id]} (get-in request [:auth-info :client :user])
          torikumis (db/find-torikumis db id num)]
      (source-stream torikumis)
      (let [{:keys [publication]} channel ;; Take the publication.
            subscription (chan)] ;; Create subscription.
        (sub publication :torikumi/updated subscription) ;; Start to subscribe the publication.
        (go-loop []
          (when-let [{:keys [data]} (<! subscription)] ;; Wait for event.
            (let [torikumis (db/find-torikumis db id num)]
              (println "Subscription received data" data)
              (source-stream torikumis) ;; Notify update to client.
              (recur))))
        #(do
           (println "Stop subscription.")
           (unsub publication :torikumi/updated subscription)
           (close! subscription)))))) ;; Stop subscription.
```

You can publish event via core.async channel as follows. 

```clojure
(require '[clojure.core.async :refer [>!!]])

(>!! (:channel (:graphql-server/channel integrant.repl.state/system))
     {:msg-type :torikumi/updated :data {:msg "Updated!"}})
```

In this time, I use duct.scheduler.simple to create torikumi information randomly and to publish event in every 10s. The following is the function to do that.

```clojure:src/graphql_server/dohyo.clj
(ns graphql-server.dohyo
  (:require [clojure.core.async :refer [>!!]]
            [integrant.core :as ig]
            [graphql-server.boundary.db :as db]))

(defmethod ig/init-key :graphql-server/dohyo [_ {:keys [db channel]}]
  (fn []
    (let [rikishis (db/find-rikishis db nil nil nil nil)
          higashi (:node (rand-nth (:edges rikishis)))
          nishi (loop [rikishi (:node (rand-nth (:edges rikishis)))]
                  (if (not= (:sumobeya rikishi) (:sumobeya higashi))
                    rikishi
                    (recur (:node (rand-nth (:edges rikishis))))))
          torikumi (db/create-torikumi db
                                       {:higashi higashi
                                        :nishi nishi
                                        :shiroboshi (if (rand-nth [true false])
                                                      higashi nishi)
                                        :kimarite (rand-nth ["TSUKIDASHI" "TSUKITAOSHI" "OSHIDASHI" ;; ...
                                                             "FUMIDASHI"])})]
      (>!! (:channel channel) ;; Publish event.
           {:msg-type :torikumi/updated
            :data {:msg "Updated!" :torikumi torikumi}}))))
```

You can try this subscription in GraphiQL. You will see the result will be updated in real time.
![Screen Shot 2019-01-28 at 3.48.09 PM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/4b2a58b2-827c-3418-3452-7b5f994285ba.png)

## Pagination
Since it's heavy load to fetch all rikishis information at once, I will implement pagination. Although GraphQL itself doesn't define how to implement pagination, Relay, a GraphQL client, has a pagination specification called Relay-Style Cursor Pagination.
https://facebook.github.io/relay/graphql/connections.htm

It wraps resource list in a entity named connection to give cursor to each data. And as it also has start cursor and end cursor, it can get next page. It is good for implementing infinite scroll. In query for Relay-Style Cursor Pagination, you can specify following arguments. In normal use cases, you should use after and first.

* after ... Get resources after the cursor.
* first ... Get first n resources.
* before ... Get resources before the cursor.
* last ... Get last n resources.

Then the result connection has following attributes.

* pageInfo ... paging information.
    * hasPreviousPage
    * hasNextPage
    * startCursor
    * endCursor
* edges ... The result list
    * cursor ... The cursor for resource that is normally base64 encoding of resource id.
    * node ... The resource itself.

In order to get the next page, you specify pageInfo.endCursor after argument of next query. The following is the implementation of that.

```clojure:src/graphql_server/boundary/db.clj
  (find-rikishis [{:keys [connection]} before after first-n last-n]
    (let [before (when before (biginteger (decode-str before)))
          after (when after (biginteger (decode-str after)))
          db (d/db connection)
          ids (->> db
                   (d/q '[:find ?e
                          :where [?e :rikishi/id]])
                   (sort-by first)
                   (map first))
          edges (cond->> ids
                  after (filter #(> % after))
                  before (filter #(< % before)))
          edges' (cond->> edges
                   first-n (take first-n)
                   last-n (take-last last-n)
                   true (d/pull-many db '[*])
                   true (map #(hash-map :cursor (encode-str (str (:db/id %)))
                                        :node (->entity %))))
          page-info (cond-> {:has-next-page false
                             :has-previous-page false}
                      (and last-n (< last-n (count edges))) (assoc :has-previous-page true)
                      (and after (not-empty (filter #(> % after) ids))) (assoc :has-previous-page true)
                      (and first-n (< first-n (count edges))) (assoc :has-next-page true)
                      (and before (not-empty (filter #(< % before) ids))) (assoc :has-next-page true)
                      true (assoc :start-cursor (:cursor (first edges')))
                      true (assoc :end-cursor (:cursor (last edges'))))]
      {:total-count (count ids) :page-info page-info :edges edges'}
```

## Implement client with re-frame
Last but not least, I introduce how to implement GraphQL client.
https://github.com/223kazuki/clj-graphql-client
![sumoql.png](https://qiita-image-store.s3.amazonaws.com/0/109888/e690aca3-05d0-567f-1c5b-556af1852109.png)
 -->

Since the API server I've implemented so far conforms to GraphQL specification, you can use [Apollo](https://www.apollographql.com/) or [Relay](https://facebook.github.io/relay/) to develop client. But I want to use cljs to develop client as with the server. By using [re-graph](https://github.com/oliyh/re-graph), you can implement GraphQL client with re-frame. re-graph register re-frame events and effects to access GraphQL API.

### Initialize re-graph
To use re-graph, you need to dispatch `:re-graph.core/init` event first with following parameters.

```cljs
{:http-url                "http://localhost:8080/graphql" ;; GraphQL endpoint URL.
 :ws-url                  "ws://localhost:8080/graphql-ws?token=xxxxxxxxxxxxxxxx" ;; WebSocket endpoint URL with access token.
 :ws-reconnect-timeout    2000
 :resume-subscriptions?   true
 :connection-init-payload {}
 :http-parameters         {:with-credentials? false
                           :headers {"Authorization" "Bearer xxxxxxxxxxxxxxxx"}}} ;; Access token for HTTP access.
```

You need to set access token which you get through OAuth 2.0 flow.
And the following is the code which dispatches initialize event.

```cljs:src/graphql_client/client/module/graphql.cljs
(defmethod reg-event ::init [k]
  (re-frame/reg-event-fx
   k [re-frame/trim-v]
   (fn-traced
    [{:keys [:db]} [options token]]
    (let [options (cond-> options
                    (:ws-url options) (update-in [:ws-url] str "?token=" token)
                    true (assoc
                          :http-parameters {:with-credentials? false
                                            :headers {"Authorization" (str "Bearer " token)}}))]
      {:db (merge db initial-db)
       :dispatch [::re-graph/init options]}))))
```

※ I use re-frame with integrant. I explained this architecture in the [previous post](https://223kazuki.github.io/re-integrant-app.html). So please refer to it.

### Perform GraphQL query
After initialization, you can issue GraphQL query by `:re-graph.core/query` event. This event receives query as string, query argument and callback event. If the query succeeds, the callback event will be dispatched to update app-db.

```cljs:src/graphql_client/client/module/graphql.cljs
(defmethod reg-sub ::sub-query [k] ;; Subscription for query result.
  (re-frame/reg-sub-raw
   k (fn [app-db [_ query args path]]
       (re-frame/dispatch [::re-graph/query
                           (graphql-query query) args [::on-query-success path]]) ;; Dispatch query.
       (reagent.ratom/make-reaction
        #(get-in @app-db path)
        :on-dispose #(re-frame/dispatch [::clean-db path])))))

;; ...

(defmethod reg-event ::on-query-success [k]
  (re-frame/reg-event-fx
   k [re-frame/trim-v]
   (fn-traced
    [{:keys [db]} [path {:keys [data errors] :as payload}]] ;; Query result.
    (if errors
      (case (get-in (first errors) [:extensions :status])
        403 {:redirect "/login"}
        {})
      {:db (update-in db path merge data)})))) ;; Write query result to app-db.
```

Because I want to keep flexibility of GraphQL query when dispatching from view, I enable to specify query directly in view.
And I'd like to deal with query as data. So I use graphql-query.
https://github.com/district0x/graphql-query

```cljs:src/graphql_client/client/views.cljs
(defn _home-panel []
  (let [query {:operation {:operation/type :query ;; Query.
                           :operation/name :rikishisQuery}
               :variables [{:variable/name :$after
                            :variable/type :String}]
               :queries [{:query/data [:favoriteRikishis [:id]]}
                         {:query/data [:rikishis {:first 20 :after :$after}
                                       [[:pageInfo [:hasNextPage :endCursor]]
                                        [:edges [[:node [:id :shikona :banduke
                                                         [:sumobeya [:name]]]]]]]]}]}
        path [::rikishis]
        rikishis (re-frame/subscribe [::graphql/sub-query query {} path])] ;; Issue query via re-frame subscription.
    (fn []
      (when-let [rikishis @rikishis]
        ;; ...
      ))))
```

You can implement infinite scroll with using Relay-Style Cursor Pagination API which I've already implemented.
In this example I use [soda-ash](https://github.com/gadfly361/soda-ash) to use [Visibility component](https://react.semantic-ui.com/behaviors/visibility/) of [Semantic UI React](https://react.semantic-ui.com/) to do that.

```cljs:src/graphql_client/client/views.cljs
[sa/Visibility {:as "tbody"
                           :on-update (fn [_ ctx]
                                        (let [{:keys [percentagePassed offScreen bottomPassed onScreen width topPassed fits
                                                      pixelsPassed passing topVisible direction height bottomVisible] :as calc}
                                              (js->clj (aget ctx "calculations")
                                                       :keywordize-keys true)]
                                          (when (and bottomVisible hasNextPage)
                                            (re-frame/dispatch [::graphql/fetch-more ;; Fetch additional resources
                                                                query path :rikishis])
                                            (js/console.log "fetch more!"))))}
            (for [{{:keys [id shikona banduke sumobeya]} :node} edges]
              [sa/TableRow {:key id}
                ;; ...
                ])]
```

### Start Subscription
re-graph also supports Subscription. To use Subscription, you need to specify `:ws-url` in initialization. Then dispatch `:re-graph.core/subscribe` event with subscription id, query as string, query arguments and callback.

```cljs/graphql_client/client/module/graphql.cljs
(defmethod reg-sub ::sub-subscription [k]
  (re-frame/reg-sub-raw
   k (fn [app-db [_ query args path]]
       (let [subscription-id (keyword (str path))]
         (re-frame/dispatch [::re-graph/subscribe ;; Start subscription.
                             subscription-id (graphql-query query) args
                             [::on-thing path]])
         (reagent.ratom/make-reaction
          #(get-in @app-db path)
          :on-dispose #(re-frame/dispatch [::re-graph/unsubscribe subscription-id])))))) ;; Stop subscription.
;; ...
(defmethod reg-event ::on-thing [k]
  (re-frame/reg-event-fx
   k [re-frame/trim-v]
   (fn-traced
    [{:keys [db]} [path {:keys [data errors] :as payload}]]
    (if errors
      (case (get-in (first errors) [:extensions :status])
        403 {:redirect "/login"}
        {})
      {:db (assoc-in db path data)}))))
```

Then you can get resource update in real time.

## Summary
In this post, I introduce Tips for Lacinia app development. Although there are some hard point in GraphQL, it's one of the most exciting use case of Clojure.
If you have some comments or question, please send me message in [goronao@Twitter](https://twitter.com/goronao).

## References
* [Lacinia document](https://lacinia.readthedocs.io/en/latest/)
* [GraphQL Specification](https://facebook.github.io/graphql/June2018/)
* [Relay Cursor Connections Specification](https://facebook.github.io/relay/graphql/connections.htm)
* [The OAuth 2.0 Authorization Framework](https://tools.ietf.org/html/rfc6749)
* [Datomic On-Prem Documentation](https://docs.datomic.com/on-prem/index.html)
* [hodur-engine](https://github.com/luchiniatwork/hodur-engine)
* [Clojureサービス開発ライブラリPedestal入門(Japanese)](https://qiita.com/lagenorhynque/items/fbd66ebaa0352ec4253d)
* [ClojureのLaciniaでGraphQL API開発してみた(Japanese)](https://qiita.com/lagenorhynque/items/eebb9a36859789520dbf)
