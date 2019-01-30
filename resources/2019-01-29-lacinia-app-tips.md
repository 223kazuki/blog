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
keywords: Clojure, ClojureScript, Lacinia, GraphQL, re-frame, integrant
uuid: 123b5035-6286-4b20-b544-758765c380a6
tags:
 - Clojure
 - ClojureScript
 - Lacinia
 - GraphQL
 - re-frame
 - integrant
---

Recently, I tried Lacinia because many clojurians and companies begin to use it. It's one of the most exciting clojure library today. I'm very fascinated by it. But while developing Lacinia application, I also met some difficulties. In this post, I introduce how I resolved those difficulties as Tips for lacinia app development. As I don't intend to introduce Lacinia itself, I don't explain whant Lacinia is. Please read [Lacinia document](https://lacinia.readthedocs.io/en/latest/) and try tutorial first.

## Example repositories
What I developed are these.

* https://github.com/223kazuki/clj-graphql-server ... GraphQL API server
* https://github.com/223kazuki/clj-graphql-client ... GraphQL client

The theme is data API of 大相撲: [Grand Sumo Tornament](http://www.sumo.or.jp/En/). And technology stack are followings.

* GraphQL API: Lacinia + Pedestal
* Persistence layer: Datomic
* API Authencatation: OAuth2
* Framework: duct
* Dmain modeling: hodur
* Client: re-frame + integrant

Plese refer to README of each repositories in order to know the version of dependencies and how to setup them.
clj-graphql-server also hosts [GraphiQL](https://github.com/graphql/graphiql) in dev profile. So you can try queries.
![graphiql.gif](https://qiita-image-store.s3.amazonaws.com/0/109888/7d78104c-ff68-77ae-2975-06c9f9412772.gif)

In following sections, I will introduce the Tips for Lacinia app development by using these repositories.

## ductify
I use [pedestal](https://github.com/pedestal/pedestal) to host Lacinia because Lacinia provides [lacinia-pedestal](https://github.com/walmartlabs/lacinia-pedestal). As both Lacinia and Pedestal have a lot of parameters to initialize, I use [duct](https://github.com/duct-framework/duct) framework to manage them declaretively. [module.pedestal](https://github.com/lagenorhynque/duct.module.pedestal) provides pedestal server module to duct. And lacinita-pedestal provides GraphQL API service map for pedestal. So I wrap it in integrant key(`:graphql-server.lacinia/service`) and set ref to pedestal key(`:duct.server/pedestal`). And I define some other integrant keys to manage lacinia components like resolver, streamer, or schema because they have their own dependencies.
The abstract of configration map is as follow.

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
   :schema #ig/ref :graphql-server.lacinia/schema ;; Lacinia スキーマ定義
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

## Implement authentication for GraphQL API
そもそも GraphQL の認証ってどうやるんだっけという点からよくわかってませんでした。GraphQL 自体が何らかの仕様を定めてくれているのかと思いましたが、GraphQL はあくまでデータアクセスの仕様に過ぎず、そういったものは存在しないようです。lacinia-pedestal も認証周りの機能は特に持たないため自分で実装する必要があります。また、GraphQL は接続方法として HTTP と WebSocket 両方サポートするため、それぞれで認証方法を考える必要があります。

GitHub GraphQL API の認証を参考にすると HTTP 通信時の認証はトークンベースだったので、今回は OAuth2 によるトークンベースの認証を採用しようと思います。
https://developer.github.com/v4/guides/forming-calls/#authenticating-with-graphql

OAuth2 実装部は[以前作成したもの](https://github.com/223kazuki/clj-oauth-server)の使いまわしのため詳述しません。[src/graphql_server/handler/auth.clj](https://github.com/223kazuki/clj-graphql-server/blob/master/src/graphql_server/handler/auth.clj) で OAuth2 のフローに従ってアクセストークンを発行し、[auth コンポーネント](https://github.com/223kazuki/clj-graphql-server/blob/master/src/graphql_server/auth.clj)でそれを管理します。認証関連ハンドラは `:graphql-server.lacinia/service` に `:optional-routes` という形で挿入しています。

### Authencication check for HTTP access
HTTP アクセスの場合、認証チェックは interceptor で行います。下記が認証を行う interceptor です。

```clojure:src/graphql_server/interceptors.clj
(defmethod ig/init-key ::auth [_ {:keys [:auth]}]
  {:name  ::auth
   :enter (fn [{{:keys [:headers :uri :request-method] :as request} :request :as context}]
            (let [forbidden-response {:status 403
                                      :headers {"Content-Type" "application/json"
                                                "Access-Control-Allow-Origin" (get headers "origin")}
                                      :body (json/write-str {:errors [{:message "Forbidden"}]})}]
              (if-not (and (= uri "/graphql")
                           (= request-method :post)) ;; 認証する対象は GraphQL API への POST のみ
                context
                (if-let [access-token (some-> headers ;; リクエストヘッダからトークンを取得
                                              (get "authorization")
                                              (str/split #"Bearer ")
                                              last
                                              str/trim)]
                  (if-let [auth-info (auth/get-auth auth access-token)] ;; auth コンポーネントから認証情報取得
                    (assoc-in context [:request :auth-info] auth-info) ;; 認証情報が見つかればコンテキストにセット
                    (assoc context :response forbidden-response)) ;; 認証情報がなければ Forbidden を返す
                  (assoc context :response forbidden-response)))))}) ;; トークンがなければ Forbidden を返す
```

この interceptor は `:graphql-server.lacinia/service` コンポーネントで `:optional-interceptors` という形で設定出来るようにしています。認証チェックに成功した場合、auth コンポーネントで管理する認証情報を、コンテキストにセットして後続の処理（resolver）から利用できるようにします。

```clojure:src/graphql_server/handler/resolver.clj
(defmethod ig/init-key ::get-viewer [_ {:keys [auth db]}]
  (fn [{request :request :as ctx} args value]
    (let [{:keys [id email-address]} (get-in request [:auth-info :client :user])] ;; 認証情報を resolver から利用
      (->Lacinia {:id id :email-address email-address}))))
```

### Authencication check for WebSocket access
WebSocket により接続する場合は、ハンドシェイク時にトークンを渡して認証チェックする方法が一般的です。トークンは同様にヘッダー経由で渡せますが、クライアントライブラリの実装によってはヘッダを設定できない可能性があるため、今回は URL パラメータとして渡すことにします。

`GET http://localhost:8080/graphql-ws?token=xxxxxxxxxxx`

lacinia-pedestal は WebSocket のハンドシェイク時に呼び出されるコンテキスト処理化関数を設定出来るようになっているため、その関数内で認証チェックを行います。

```clojure:src/graphql_server/handler/auth.clj
(defmethod ig/init-key ::ws-init-context [_ {:keys [auth]}]
  (fn [ctx ^UpgradeRequest req ^UpgradeResponse res]
    (if-let [access-token (some->> (.get (.getParameterMap req) "token") ;; URL パラメータからアクセストークン取り出し
                                   first)]
      (if-let [auth-info (auth/get-auth auth access-token)]
        (assoc-in ctx [:request :lacinia-app-context :request :auth-info] auth-info) ;; resolver に情報を渡すには [:request :lacinia-app-context] 以下にセットする必要がある
        (do
          (.sendForbidden res "Forbidden") ;; org.eclipse.jetty.websocket.api.UpgradeResponse に接続拒否を設定
          ctx))
      (do
        (.sendForbidden res "Forbidden")
        ctx))))
```

これを `com.walmartlabs.lacinia.pedestal/service-map` のオプションに `:init-context` キーで渡せば、認証チェックを実現できます。
後続の処理（resolver, streamer）で認証情報を利用するためには同様にコンテキストにセットすればよいですが、lacinia-pedestal の実装のせいでコンテキスト以下 `[:request :lacinia-app-context]` に設定した情報しか渡らないようになっており、かつ default-subscription-interceptors に含まれる `:com.walmartlabs.lacinia.pedestal.subscriptions/inject-app-context` という interceptor により `:lacinia-app-context` が初期化されてしまいます。そのため、lacinia-pedestal のサービス初期化時にこの interceptor を除外する必要があります。

```clojure:src/graphql_server/lacinia.clj
        subscription-interceptors (->> [exception-handler-interceptor
                                        send-operation-response-interceptor
                                        (query-parser-interceptor compiled-schema)
                                        execute-operation-interceptor] ;; default-subscription-interceptors から inject-app-context を除外
                                       (concat optional-subscription-interceptors)
                                       (map interceptor/map->Interceptor)
                                       (into []))
```

やや面倒ですが、これにより HTTP 通信時と同様に認証チェックし、後続の resolver, streamer でユーザ情報を使えるようになります。

```clojure:src/graphql_server/handler/streamer.clj
(defmethod ig/init-key ::stream-torikumis [_ {:keys [db channel]}]
  (fn [{request :request :as ctx} {:keys [num]} source-stream]
    (println "Start subscription.")
    (let [{:keys [id]} (get-in request [:auth-info :client :user]) ;; 認証情報取り出し
          torikumis (db/find-torikumis db id num)]
      ;; ...
  )))
```

## Connect to Datomic
Lacinia に直接関係ないですが、データ永続化層にはせっかくなので Datomic を使ってみることにしました。module.sql を参考に module.datomic を実装します。

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

このモジュールによりコネクション情報を保持した `duct.database/datomic` コンポーネントが生成されるので、config.edn 上で resolver, streamer から参照させれば Datomic アクセスを実現できます。

```clojure:resources/geraphql_server/config.edn
  :graphql-server.handler.resolver/get-viewer {:auth #ig/ref :graphql-server/auth}
  :graphql-server.handler.resolver/user-favorite-rikishis {:db #ig/ref :duct.database/datomic}
  :graphql-server.handler.resolver/get-favorite-rikishis {:db #ig/ref :duct.database/datomic}
  ;;...
  :graphql-server.handler.streamer/stream-torikumis {:db #ig/ref :duct.database/datomic
                                                     :channel #ig/ref :graphql-server/channel}
```

Datomic アクセスは boundary として抽象化しますが、Datomic のトランザクションは Clojure のマップとして発行できるので、Lacinia との相性は抜群です。また、Datomic は GraphQL の表現力の高さを十分に活かせる柔軟性があり、例えば下記のクエリでは「あるユーザのお気に入り力士のいずれかが東西のどちらかに含まれている全ての取り組み」を取得するクエリです。多対多の結合をこれほどシンプルに書けるのは Datalog クエリならではですね。

```clojure:user.clj
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
Datomic は Lacinia と同様にスキーマ定義が必要です。そうなると必然としてスキーマ定義の共通化と自動生成が欲しくなってきます。

[以前の記事](https://qiita.com/223kazuki/items/ba4ba84e2da1daea3b52)では [umlaut](https://github.com/workco/umlaut) というライブラリを使ってスキーマ定義の共通化を行いましたが、昨年の clojure/conj では新たにドメイン駆動開発のツールとして各種スキーマの自動生成が行える [hodur](https://github.com/luchiniatwork/hodur-engine) が紹介されました。
[![Declarative Domain Modeling for Datomic Ion/Cloud](http://img.youtube.com/vi/EDojA_fahvM/0.jpg)](https://www.youtube.com/watch?v=EDojA_fahvM)

実現できることに大きな違いはありませんが、敢えて違いを上げるのであれば umlaut では共通スキーマを GraphQL ライクな形式のファイルで定義するのに対し、hodur では edn として clojure のデータ構造で定義出来ることでしょうか。hodur は内部的には [datascript](https://github.com/tonsky/datascript) により中間データベース（meta-db）を生成し、datalog クエリで各スキーマ生成に必要な情報を取り出しているようです。下記が hodur の共通スキーマ定義ファイルです。

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

Datomic や Lacinia などそれぞれのタグとして使うかはメタデータ内で `:[plugin]/tag` キーにより制御します。その他、それぞれの対象でしか存在しない定義（Lacinia の resolver 指定など）も `:[plugin]/*` キーで指定します。共通スキーマが edn であることで duct 的には config ファイルから直接 include 出来るのが少しうれしいです。

```clojure:resources/graphql_server/config.edn
  :graphql-server/hodur {:schema #duct/include "graphql_server/schema"} ;; schema.edn 定義ファイルを直接 include
  :graphql-server.datomic/schema {:meta-db #ig/ref :graphql-server/hodur} ;; Datomic schema 生成
  :graphql-server.lacinia/schema {:meta-db #ig/ref :graphql-server/hodur} ;; Lacinia schema 生成
```

```clojure:src/graphql_server/hodur.clj
(ns graphql-server.hodur
  (:require [integrant.core :as ig]
            [hodur-engine.core :as hodur]))

(defmethod ig/init-key :graphql-server/hodur [_ {:keys [schema]}]
  #_ (binding [*print-meta* true]
       (pr schema))
  (hodur/init-schema schema)) ;; meta-db 生成
```

Lacinia と Datomic のスキーマ定義のエンティティは共に参照型を許容するため、殆どそのまま互いに使うことが可能です。ただし、一点、GraphQL スキーマは循環参照を許容するのに対し、Datomic は許容しません。そのため、上記の例で言うと、力士（rikishi）は所属相撲部屋（sumobeya）参照を持ち、相撲部屋（sumobeya）は所属力士（rikishis）参照を持ちますが、Datomic では sumobeya/rikishis は `:datomic/tag false` により除外しています。

### Generate Lacinia schema
Lacinia プラグインを使うことで hodur の meta-db から Lacinia スキーマを生成できます。
https://github.com/luchiniatwork/hodur-lacinia-schema

```clojure:src/graphql_server/lacinia.clj
(defmethod ig/init-key ::schema [_ {:keys [meta-db]}]
  (-> meta-db
      hodur-lacinia/schema))
```

生成したスキーマは `:graphql-server.lacinia/service` コンポーネントから参照してコンパイルして利用します。

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

### Migrate Datomic schema
Datomic プラグインを使うことで、hodur の meta-db から Datomic スキーマを生成します。
https://github.com/luchiniatwork/hodur-datomic-schema

```clojure:src/graphql_server/datomic.clj
(defmethod ig/init-key ::schema [_ {:keys [meta-db]}]
  (hodur-datomic/schema meta-db)) ;; meta-db から Datomic スキーマを生成
```

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

migrator.datomic を実装し、生成したスキーマをトランザクションに渡すことで Datomic に登録します。

```clojure:src/duct/migrator/datomic.clj
(defmethod ig/init-key :duct.migrator/datomic [_ {:keys [database schema migrations]
                                                  :as options}]
  (d/transact (:connection database) schema) ;; 上記で生成したスキーマをトランザクションとして発行
  (->> migrations
       (map :up)
       (map #(d/transact (:connection database) %))
       doall)
  (println "Migrated"))
```

### Visualize schema
hodur は動的なスキーマビューワを持ちます。
https://github.com/luchiniatwork/hodur-visualizer-schema

ただし、実装を見ると分かりますが、このプラグインは cljs + figwheel で実装されており、スキーマ定義を cljs コードから読み込む必要があり、通常 edn ファイルからスキーマを読み込むことは出来ません。今回は上記の通り edn ファイルにスキーマを定義したいので、一工夫加えます。visualizer.cljs が上記プラグインを使ってスキーマビューワを SPA として描画しているコードです。

```clojure:dev/src/graphql_server/visualizer.cljs
(ns graphql-server.visualizer
  (:require [hodur-engine.core :as engine]
            [hodur-visualizer-schema.core :as visualizer])
  (:require-macros [graphql-server.macro :refer [read-schema]]))

(-> (read-schema "graphql_server/schema.edn") ;; edn ファイルをマクロにより読み込んで展開
    engine/init-schema
    visualizer/schema
    visualizer/apply-diagram!)
```

ここで、`read-schema` は clojure マクロで、コンパイル時に edn ファイルを読み込んで cljs コードに展開しています。

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

こうすることで、edn ファイルのスキーマ定義を cljs から直接読み込んで可視化することが可能となります。当アプリでビューワは開発プロファイルでは http://localhost:9500 に起動します。

![Screen Shot 2019-01-28 at 10.04.46 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/ce339a6a-15d4-16bd-5763-a6a1b554b9d1.png)

スキーマ定義ファイルを編集した時は、`(reset)` により上記ビューワにも変更が反映されます。スキーマ定義ファイルの変更検知、再コンパイルは figwheel では出来ないため、`dev/src/graphql_server/visualizer_schema.clj` で直接変更検知したりしてなんとかしました。

### Generate clojure.spec definitions
hodur は clojure.spec 定義も生成することが出来ます。
https://github.com/luchiniatwork/hodur-spec-schema

今回 spec は開発プロファイルのみで利用することとし、関数の入出力精査に利用します。meta-db から生成した spec は未評価のフォームとなるので、eval することで spec を登録します。

```clojure:dev/src/graphql_server/spec.clj
(defmethod ig/init-key :graphql-server/spec [_ {:keys [meta-db] :as options}]
  (let [spec (hodur-spec/schema meta-db {:prefix :graphql-server.spec})]
    (eval spec) ;; スペック読み込み
    (fdef) ;; 関数スペックの読み込み
    (stest/instrument) ;; 関数の入出力精査の有効化
    (assoc options :spec spec)))
```

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

生成されるのは属性とエンティティに対するスペックだけなので、関数のスペックを定義するためには、上記の値のスペックを使って別途定義する必要があります。（上記では fdef という関数内で定義してる）関数スペック定義後、`(stest/instrument)` によって実行時の関数スペック精査をオンにしています。ここで clojure.spec.test.alpha の instrument だと入力に対する精査しか行われないため、[orchestra](https://github.com/jeaye/orchestra) というライブラリを使って入出力ともに実行時精査できるようにしています。

確認中ですが hodur のスペックプラグインは若干バグが有り、参照を含むと生成がうまくいかないケースが存在します。`:spec/tag false` などを利用して参照のスペック生成を抑制する必要がありそうです。

## Use Subscription
Subscription は GraphQL でも比較的新しい仕様で、WebSocket を通じてリソースの更新をリアルタイムにストリームとして受け取れる機能です。
https://facebook.github.io/graphql/June2018/#sec-Subscription

Lacinia はこの仕様をサポートしており、resolver の代わりに streamer という関数を実装することで実現できます。
https://lacinia.readthedocs.io/en/latest/subscriptions/

下記が Lacinia ドキュメントにおける streamer 参考実装です。

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

第三引数の `source-stream` が、クライアント側に更新を通知するための関数です。`create-log-subscription` は何らかのイベントソースのサブスクリプションを生成する関数で、イベント生成に応じて `source-stream` を実行するようにします。streamer 関数（`log-message-streamer`）自体は、サブスクリプションを終了するためのコールバックを返すように実装します。サブスクリプションは実際には core.async や Kafka を使って実現することになります。

今回の API では、取組情報を取得する部分を streamer として実装してみました。まずは、システム内で一意に使える core.async チャンネルとその配信を作成します。 

```clojure:src/graphql_server/channel.clj
(ns graphql-server.channel
  (:require [clojure.core.async :refer [chan close! pub unsub-all]]
            [integrant.core :as ig]))

(defmethod ig/init-key :graphql-server/channel [_ _]
  (let [channel (chan)]
    {:channel channel :publication (pub channel :msg-type)})) ;; core.async チャンネルと配信生成

(defmethod ig/halt-key! :graphql-server/channel [_ {:keys [channel publication]}]
  (unsub-all publication)
  (close! channel))
```

streamer はこの配信を初期化時に受け取り、購読するようにします。

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
      (let [{:keys [publication]} channel ;; 配信の受け取り
            subscription (chan)] ;; 購読作成
        (sub publication :torikumi/updated subscription) ;; 配信の購読開始
        (go-loop []
          (when-let [{:keys [data]} (<! subscription)] ;; イベント待ち受け
            (let [torikumis (db/find-torikumis db id num)]
              (println "Subscription received data" data)
              (source-stream torikumis) ;; クライアントへの通知
              (recur))))
        #(do
           (println "Stop subscription.")
           (unsub publication :torikumi/updated subscription)
           (close! subscription)))))) ;; 購読の停止
```

core.async チャンネルを通じて下記の通りメッセージを発行すれば、streamer は source-stream を実行し、クライアント側で更新を受け取れます。

```clojure
(require '[clojure.core.async :refer [>!!]])

(>!! (:channel (:graphql-server/channel integrant.repl.state/system))
     {:msg-type :torikumi/updated :data {:msg "Updated!"}})
```

今回は duct.scheduler.simple を利用し、10s ごとにランダムで取り組み情報を更新し、更新メッセージを発行する関数を用意しました。

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
      (>!! (:channel channel) ;; イベント発行
           {:msg-type :torikumi/updated
            :data {:msg "Updated!" :torikumi torikumi}}))))
```

GraphiQL から subscription を試せばリアルタイムに結果が更新されていくのがわかります。（力士をお気に入りに登録する必要あり）
![Screen Shot 2019-01-28 at 3.48.09 PM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/4b2a58b2-827c-3418-3452-7b5f994285ba.png)

## Pagenation
力士情報を一度に全部取得するのは負荷が大きいため、ページネーションを実装します。GraphQL は公式なページネーションの方法を定義してはいませんが、GraphQL のクライアントである Relay は Relay-Style Cursor Pagination というページネーション仕様を持ちます。
https://facebook.github.io/relay/graphql/connections.htm

リソースのリストをコネクションという情報でラップし、各データにカーソルを持たせ、ページ単位に開始位置と終了位置を持たせることで、次の（前の）ページを取得できるようにする方式で、クライアント側でリソースの無限スクロールを実現するのに向いています。Relay-Style Cursor Pagination に準拠したクエリの引数には下記を指定できます。通常の用途では after と first を使うことになります。

* after ... 指定したカーソルより後ろから
* first ... 最初の first 個のデータを取得
* before ... 指定したカーソルより前の
* last ... 後ろから last 個を取得

結果であるコネクションは下記の属性をもちます。

* pageInfo ... ページング情報
    * hasPreviousPage ... 前のページを持つか
    * hasNextPage ... 次のページを持つか
    * startCursor ... 開始カーソル
    * endCursor ... 最後のカーソル
* edges ... 結果のリスト
    * cursor ... データのカーソル。データを一意に特定できる文字列なら何でも良いが、通常 ID の base64 エンコーディングとかにする
    * node ... データの実体

次のページを取得するためには、現在の結果の pageInfo.endCursor を引数 after に指定してクエリを投げれば次のページが取得できます。下記が上記の仕様を実装した箇所です。first, last が Clojure ではシンプルに実装できていいですね。

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
最後に GraphQL クライアントの実装方法です。
https://github.com/223kazuki/clj-graphql-client

<!-- クライアントは、力士一覧ページで力士をお気に入りに登録し、取組ページからお気に入り力士の取組結果をリアルタイムに閲覧できるというアプリになっています。（取組結果はランダム）
![sumoql.png](https://qiita-image-store.s3.amazonaws.com/0/109888/e690aca3-05d0-567f-1c5b-556af1852109.png)
 -->

ここまで実装してきた API サーバは GraphQL の仕様に準拠しているので、クライアントは [Apollo](https://www.apollographql.com/) や [Relay](https://facebook.github.io/relay/) により開発することが可能ですが、ここまで来たらやはり ClojureScript で実装したいです。[re-graph](https://github.com/oliyh/re-graph) を使うと re-frame で Apollo のように GraphQL API にアクセスすることが出来ます。re-graph を使うと GraphQL アクセス用の re-frame イベントや副作用が登録され、利用できるようになります。

### Initialize re-graph
re-graph を使うにはまず `:re-graph.core/init` イベントを dispatch します。dispatch には下記のように GraphQL のアクセス情報を渡します。

```cljs
{:http-url                "http://localhost:8080/graphql" ;; GraphQL エンドポイント
 :ws-url                  "ws://localhost:8080/graphql-ws?token=xxxxxxxxxxxxxxxx" ;; WebSocket 接続用のアクセストークン付き URL
 :ws-reconnect-timeout    2000
 :resume-subscriptions?   true
 :connection-init-payload {}
 :http-parameters         {:with-credentials? false
                           :headers {"Authorization" "Bearer xxxxxxxxxxxxxxxx"}}} ;; HTTP 接続用のアクセストークン
```

アクセストークンは OAuth2 フローを通じて取得したものを設定します。このオプションを使って re-graph を初期化します。

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

※ クライアントは re-frame を integrant と組み合わせて開発しています。このアーキテクチャについては [re-frame+integrant による ClojureScript SPA 開発](https://qiita.com/223kazuki/items/ce1680dc54ff8fe4770c) を参照して下さい。

### Perform GraphQL query
re-graph を初期化すると、`:re-graph.core/query` イベントを dispatch することで GraphQL にクエリが投げられます。イベントにクエリ（文字列）、引数、コールバックを渡して dispatch します。成功するとコールバックが呼び出されるので、app-db に結果を書き込みます。

```cljs:src/graphql_client/client/module/graphql.cljs
(defmethod reg-sub ::sub-query [k] ;; クエリ結果の subscription
  (re-frame/reg-sub-raw
   k (fn [app-db [_ query args path]]
       (re-frame/dispatch [::re-graph/query
                           (graphql-query query) args [::on-query-success path]]) ;; クエリ dispatch
       (reagent.ratom/make-reaction
        #(get-in @app-db path)
        :on-dispose #(re-frame/dispatch [::clean-db path])))))

;; ...

(defmethod reg-event ::on-query-success [k]
  (re-frame/reg-event-fx
   k [re-frame/trim-v]
   (fn-traced
    [{:keys [db]} [path {:keys [data errors] :as payload}]] ;; クエリ結果
    (if errors
      (case (get-in (first errors) [:extensions :status])
        403 {:redirect "/login"}
        {})
      {:db (update-in db path merge data)})))) ;; クエリ結果の app-db 書き込み
```

ビュー側からクエリを発行する際に GraphQL の柔軟性を損ないたくないため、ビュー側で直接 GraphQL クエリを指定できるようにします。また、クエリ自体も柔軟に操作出来るように、文字列ではなく GraphQL クエリライブラリの graphql-query により clojure データとして扱えるようにします。
https://github.com/district0x/graphql-query

```cljs:src/graphql_client/client/views.cljs
(defn _home-panel []
  (let [query {:operation {:operation/type :query ;; クエリ
                           :operation/name :rikishisQuery}
               :variables [{:variable/name :$after
                            :variable/type :String}]
               :queries [{:query/data [:favoriteRikishis [:id]]}
                         {:query/data [:rikishis {:first 20 :after :$after}
                                       [[:pageInfo [:hasNextPage :endCursor]]
                                        [:edges [[:node [:id :shikona :banduke
                                                         [:sumobeya [:name]]]]]]]]}]}
        path [::rikishis]
        rikishis (re-frame/subscribe [::graphql/sub-query query {} path])] ;; re-frame subscription を通じてクエリ発行
    (fn []
      (when-let [rikishis @rikishis]
        ;; ...
      ))))
```

上記で実装した Relay-Style Cursor Pagination API を利用すれば無限スクロールも実現できます。当サンプルでは [soda-ash](https://github.com/gadfly361/soda-ash) というライブラリにより [Semantic UI React](https://react.semantic-ui.com/) を使っており、[Visibility](https://react.semantic-ui.com/behaviors/visibility/) を利用することで力士一覧を無限スクロールさせています。

```cljs:src/graphql_client/client/views.cljs
[sa/Visibility {:as "tbody"
                           :on-update (fn [_ ctx]
                                        (let [{:keys [percentagePassed offScreen bottomPassed onScreen width topPassed fits
                                                      pixelsPassed passing topVisible direction height bottomVisible] :as calc}
                                              (js->clj (aget ctx "calculations")
                                                       :keywordize-keys true)]
                                          (when (and bottomVisible hasNextPage)
                                            (re-frame/dispatch [::graphql/fetch-more ;; 追加リソースの fetch
                                                                query path :rikishis])
                                            (js/console.log "fetch more!"))))}
            (for [{{:keys [id shikona banduke sumobeya]} :node} edges]
              [sa/TableRow {:key id}
                ;; ...
                ])]
```

### Start Subscription
re-graph は Subscription にも対応しています。Subscription を有効化するには re-graph 初期化時に `:ws-url` を指定している必要があります。Subscription を開始するには、`:re-graph.core/subscribe` イベントにサブスクリプション ID、クエリ、引数、コールバックを指定して dispatch します。サブスクリプション ID は複数の Subscription を発行する際に、それぞれの接続を特定するために利用されます。

```cljs/graphql_client/client/module/graphql.cljs
(defmethod reg-sub ::sub-subscription [k]
  (re-frame/reg-sub-raw
   k (fn [app-db [_ query args path]]
       (let [subscription-id (keyword (str path))]
         (re-frame/dispatch [::re-graph/subscribe ;; Subscription 開始
                             subscription-id (graphql-query query) args
                             [::on-thing path]])
         (reagent.ratom/make-reaction
          #(get-in @app-db path)
          :on-dispose #(re-frame/dispatch [::re-graph/unsubscribe subscription-id])))))) ;; Subscription 終了
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

これにより、取組情報ページでは取組情報の更新をリアルタイムで受け取れます。

## What I didn't do
Lacinia は他にも多数の機能を持ち、試せなかったことはたくさんありますが、敢えてふれなかった機能を上げます。

### Avoid N+1 problem
@lagenorhynque さんがまとめてくれていたので、そちらを参照して下さい。Datomic のクエリ表現力と組み合わせると非常に強力な機能だと思います。
https://qiita.com/lagenorhynque/items/eebb9a36859789520dbf#9-n1%E5%95%8F%E9%A1%8C%E3%81%AE%E5%9B%9E%E9%81%BF

### Validate and test data by clojure.spec
~~clojure.spec を利用して書きたかったのですが、現状 hodur の spec プラグインの表現力が乏しく、型とキー以外の指定ができなかったためやめておきました。umlaut は spec に関して外部スペックの指定などが出来たので、その辺は umlaut の方が優れているかも知れません。~~
https://github.com/workco/umlaut#spec-generator

見落としてましたが　hodur-spec-schema でも spec 定義の拡張が出来ました。
https://github.com/223kazuki/hodur-spec-schema/tree/v0.1.0#overriding-and-extending

しかし、現状参照型周りでよくわからない挙動があるため、開発を追ってみようと思います。

## Summary
Lacinia でアプリを開発する上での Tips をまとめました。
Tips といいつつも「私はとりあえずこうした」という内容なので、実際の企業でどのように使われているかは私も気になるところで、コメント等あれば嬉しいです。

## References
* [Lacinia document](https://lacinia.readthedocs.io/en/latest/)
* [GraphQL Specification](https://facebook.github.io/graphql/June2018/)
* [Clojureサービス開発ライブラリPedestal入門](https://qiita.com/lagenorhynque/items/fbd66ebaa0352ec4253d)
* [ClojureのLaciniaでGraphQL API開発してみた](https://qiita.com/lagenorhynque/items/eebb9a36859789520dbf)
* [Relay Cursor Connections Specification](https://facebook.github.io/relay/graphql/connections.htm)
* [re-frame+integrant による ClojureScript SPA 開発](https://qiita.com/223kazuki/items/ce1680dc54ff8fe4770c)
* [ClojureでGraphQLサーバを立てる](https://qiita.com/223kazuki/items/ba4ba84e2da1daea3b52)
* [The OAuth 2.0 Authorization Framework](https://tools.ietf.org/html/rfc6749)
* [Datomic On-Prem Documentation](https://docs.datomic.com/on-prem/index.html)
* [hodur-engine](https://github.com/luchiniatwork/hodur-engine)
