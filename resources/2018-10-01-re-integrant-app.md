---
title: Develop ClojureScript SPA with combination of integrant and re-frame
description: Develop ClojureScript SPA with combination of integrant and re-frame
author: Kazuki Tsutsumi
author-email: rixi223.kazuki@gmail.com
author-url: https://twitter.com/goronao
author-github: https://github.com/223kazuki
author-twitter: goronao
author-avatar: https://pbs.twimg.com/profile_images/1218227972/P1060544_400x400.jpg
location: San Jose, CA, USA
date-created: 2018-10-01
date-modified: 2018-10-01
date-published: 2018-10-01
headline:
in-language: en
keywords: ClojureScript, SPA, re-frame, integrant
uuid: c6cb5fa5-2b5d-4b62-bf97-bed506a51ab1
tags:
 - ClojureScript
 - SPA
 - re-frame
 - integrant
---

In this post, I will introduce how to develop ClojureScript SPA by using the combination of integrant and re-frame.


背景は[前回紹介した記事](https://qiita.com/223kazuki/items/dd1af292a644e95a3085)の通りで、複雑なフロントエンドアプリケーションを re-frame+integrant の組み合わせで開発したいという話です。
前回のサンプルは Ethereum DApp という特殊なケースで分かりづらかったので、改めて pure cljs なサンプルを作ってみました。

https://github.com/223kazuki/re-integrant-app

仮にこの構成は re-integrant と呼んでおきます。

## 全体像

この構成で作った SPA は大きく３つの階層からなります。

1. アプリケーション全体のライフサイクルを制御する integrant 層
2. ユーザの画面操作に応じて更新される単一の app-db を管理する re-frame 層
3. ハンドラ経由で app-db をサブスクライブ・ディスパッチする View の reagent 層

また、アプリケーションは integrant によりモジュールに分割されており、re-frame　のハンドラはそれぞれのモジュールの名前空間に紐づけて初期化時に登録されます。

![re-integrant.png](https://qiita-image-store.s3.amazonaws.com/0/109888/0af426ba-2ab9-5fe5-1fda-22e0f136fcfe.png)

## プロジェクト構成

duct template の様なサーバサイド integrant アプリケーションに近い構成にしています。
開発時と設定を切り分けるために dev ディレクトリを用意しています。

```bash
.
├── project.clj
├── dev
│   ├── resources
│   │   └── dev.edn
│   └── src
│       └── user.cljs
├── resources
│   ├── config.edn
│   └── public
│       ├── css
│       │   └── site.css
│       └── index.html
└── src
    └── re_integrant_app
        ├── core.cljs
        ├── module
        │   ├── app.cljs
        │   ├── moment.cljs
        │   └── router.cljs
        ├── utils.cljc
        └── views.cljs
```

### project.clj

今回使った主要なライブラリのバージョンは下記の通り。

```clojure
[org.clojure/clojure "1.9.0"]
[org.clojure/clojurescript "1.10.339"]
[reagent "0.8.0"]
[re-frame "0.10.5"]
[integrant "0.7.0"]
```

ClojureScript のビルド設定はプロファイルごとに下記の通りで、開発時には Figwheel の jsload 時に `cljs.user/reset` が実行されるようにします。

```clojure
  :cljsbuild
  {:builds
   [{:id "dev"
     :source-paths ["src" "dev/src"]
     :figwheel     {:on-jsload            cljs.user/reset}
     :compiler     {:main                 cljs.user
                    :output-to            "resources/public/js/compiled/app.js"
                    :output-dir           "resources/public/js/compiled/out"
                    :asset-path           "js/compiled/out"
                    :source-map-timestamp true
                    :preloads             [devtools.preload
                                           day8.re-frame-10x.preload]
                    :closure-defines      {"re_frame.trace.trace_enabled_QMARK_" true
                                           "day8.re_frame.tracing.trace_enabled_QMARK_" true}
                    :external-config      {:devtools/config {:features-to-install :all}}}}
    {:id "min"
     :source-paths ["src"]
     :compiler     {:main            re-integrant-app.core
                    :output-to       "resources/public/js/compiled/app.js"
                    :optimizations   :advanced
                    :closure-defines {goog.DEBUG false}
                    :pretty-print    false}}
    {:id "test"
     :source-paths ["src" "test"]
     :compiler     {:main          re-integrant-app.runner
                    :output-to     "resources/public/js/compiled/test.js"
                    :output-dir    "resources/public/js/compiled/test/out"
                    :optimizations :none}}]}
```

### config.edn

毎秒更新される [Moment](https://momentjs.com/) オブジェクトを提供する `:module/moment` を新たに追加しています。

```clojure
{:re-integrant-app.module/moment {}

 :re-integrant-app.module/router
 ["/" {""       :home
       "about"  :about}]

 :re-integrant-app.module/app
 {:mount-point-id "app"
  :routes #ig/ref :re-integrant-app.module/router
  :moment #ig/ref :re-integrant-app.module/moment}}
```

### モジュール

前回記事と役割や仕様は変わりませんが処理を明示的に書くようにしてみました。
マルチメソッド reg-sub と reg-event にハンドラ実装を追加していき、初期化時にまとめてハンドラ登録できるようにしています。
Subscriptions のところは、`::now` をサブスクライブしている間のみ、毎秒 Moment オブジェクトを受け取れるような実装になっています。（参考: [Subscribing to External Data](https://github.com/Day8/re-frame/blob/master/docs/Subscribing-To-External-Data.md)）

```clojure
;; Initial DB
(def initial-db {::now nil})

;; Subscriptions
(defmulti reg-sub identity)
(defmethod reg-sub ::now [k]
  (re-frame/reg-sub-raw
   k (fn [app-db _]
       (let [close (create-loop #(re-frame/dispatch [::fetch-now]) 1000)]
         (reagent.ratom/make-reaction
          #(get-in @app-db [::now])
          :on-dispose close)))))

;; Events
(defmulti reg-event identity)
(defmethod reg-event ::init [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced
    [db _]
    (-> db
        (merge initial-db)
        (assoc ::now (js/moment))))))
(defmethod reg-event ::halt [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced
    [db _]
    (->> db
         (filter #(not= (namespace (key %)) (namespace ::x)))
         (into {})))))
(defmethod reg-event ::fetch-now [k]
  (re-frame/reg-event-db
   k [re-frame/trim-v]
   (fn-traced
    [db _]
    (js/console.log "tick!")
    (assoc db ::now (js/moment)))))

;; Init
(defmethod ig/init-key :re-integrant-app.module/moment
  [k {:keys [:dev]}]
  (js/console.log (str "Initializing " k))
  (when dev (js/console.log "It's dev mode."))
  (let [subs (->> reg-sub methods (map key))      ;; ハンドラキーワードを取得
        events (->> reg-event methods (map key))] ;; 同上
    (->> subs (map reg-sub) doall)                ;; それぞれのキーワードでマルチメソッドを実行しハンドラを登録
    (->> events (map reg-event) doall)            ;; 同上
    (re-frame/dispatch-sync [::init])
    {:subs subs :events events}))

;; Halt
(defmethod ig/halt-key! :re-integrant-app.module/moment
  [k {:keys [:subs :events]}]                      ;; ハンドラキーワードを取得
  (js/console.log (str "Halting " k))
  (re-frame/dispatch-sync [::halt])
  (->> subs (map re-frame/clear-sub) doall)        ;; ハンドラキーワードを消去
  (->> events (map re-frame/clear-event) doall))   ;; 同上
```

### View

特に変更なしです。
`::moment/now` をサブスクライブしている home-panel が開いている間だけ Moment オブジェクトが生成され続けます。

```clojure
(defn home-panel []
  (let [now (re-frame/subscribe [::moment/now])]
    (fn []
      [:div
       [sa/Segment
        [:h2 "Now"]
        (when-let [now @now]
          (str now))]])))

(defn about-panel []
  (fn [] [:div "About"]))

(defn none-panel []
  [:div])

(defmulti  panels identity)
(defmethod panels :home-panel [] #'home-panel)
(defmethod panels :about-panel [] #'about-panel)
(defmethod panels :none [] #'none-panel)

(def transition-group
  (reagent/adapt-react-class js/ReactTransitionGroup.TransitionGroup))
(def css-transition
  (reagent/adapt-react-class js/ReactTransitionGroup.CSSTransition))

(defn app-container []
  (let [title (re-frame/subscribe [:re-integrant-app.module.app/title])
        active-panel (re-frame/subscribe [::router/active-panel])]
    (fn []
      [:div
       [sa/Menu {:fixed "top" :inverted true}
        [sa/Container
         [sa/MenuItem {:as "span" :header true} @title]
         [sa/MenuItem {:as "a" :href "/"} "Home"]
         [sa/MenuItem {:as "a" :href "/about"} "About"]]]
       [sa/Container {:className "mainContainer" :style {:margin-top "7em"}}
        (let [panel @active-panel]
          [transition-group
           [css-transition {:key panel
                            :classNames "pageChange" :timeout 500 :className "transition"}
            [(panels panel)]]])]])))
```

### core.cljs

こちらも特に変更なしです。
各モジュールをここで require しなければならないのはなんとかしたいですが、integrant の load-namespaces は clojure 限定のため難しそうです。
system は開発時に書き換えたいのでアトムとして定義しています。

```clojure
(ns re-integrant-app.core
  (:require [integrant.core :as ig]
            [re-integrant-app.module.app]
            [re-integrant-app.module.router]
            [re-integrant-app.module.moment])
  (:require-macros [re-integrant-app.utils :refer [read-config]]))

(defonce system (atom nil))

(def config (atom (read-config "config.edn")))

(defn start []
  (reset! system (ig/init @config)))

(defn stop []
  (when @system
    (ig/halt! @system)
    (reset! system nil)))

(defn ^:export init []
  (start))
```

### dev.edn

開発時の設定です。
確認用に `:module/moment` に `:dev true` を渡します。

```clojure
{:re-integrant-app.module/moment {:dev true}}
```

### user.cljs

開発時のメイン名前空間です。
dev.edn を読み込み system にマージしています。
figwheel の jsload 時には reset が呼び出されます。

```clojure
(ns cljs.user
  (:require [re-integrant-app.core :refer [system config start stop]]
            [meta-merge.core :refer [meta-merge]])
  (:require-macros [re-integrant-app.utils :refer [read-config]]))

(enable-console-print!)

(println "dev mode")

(swap! config #(meta-merge % (read-config "dev.edn")))

(defn reset []
  (stop)
  (start))
```

## 開発

開発時には下記のコマンドで cljs repl を立ち上げます。
コードを書き換えると Figwheel がビルドをキックして自動でブラウザに反映されます。

```sh
% lein dev                                                                                                                                                                                   (git)-[master]
Figwheel: Cutting some fruit, just a sec ...
Figwheel: Validating the configuration found in project.clj
Figwheel: Configuration Valid ;)
Figwheel: Starting server at http://0.0.0.0:3449
Figwheel: Watching build - dev
Figwheel: Cleaning build - dev
Compiling build :dev to "resources/public/js/compiled/app.js" from ["src" "dev/src"]...
Successfully compiled build :dev to "resources/public/js/compiled/app.js" in 29.414 seconds.
Figwheel: Starting CSS Watcher for paths  ["resources/public/css"]
Launching ClojureScript REPL for build: dev
Figwheel Controls:
          (stop-autobuild)                ;; stops Figwheel autobuilder
          (start-autobuild id ...)        ;; starts autobuilder focused on optional ids
          (switch-to-build id ...)        ;; switches autobuilder to different build
          (reset-autobuild)               ;; stops, cleans, and starts autobuilder
          (reload-config)                 ;; reloads build config and resets autobuild
          (build-once id ...)             ;; builds source one time
          (clean-builds id ..)            ;; deletes compiled cljs target files
          (print-config id ...)           ;; prints out build configurations
          (fig-status)                    ;; displays current state of system
          (figwheel.client/set-autoload false)    ;; will turn autoloading off
          (figwheel.client/set-repl-pprint false) ;; will turn pretty printing off
  Switch REPL build focus:
          :cljs/quit                      ;; allows you to switch REPL to another build
    Docs: (doc function-name-here)
    Exit: :cljs/quit
 Results: Stored in vars *1, *2, *3, *e holds last exception object
Prompt will show when Figwheel connects to your application
[Rebel readline] Type :repl/help for online help info
ClojureScript 1.10.339
dev:cljs.user=>
```

user.cljs を用意しているため、設定を取得したりシステムのリセットを repl から行うことも可能です。

```sh
dev:cljs.user=> @config
#:re-integrant-app.module{:moment {:dev true},
                          :router ["/" {"" :home, "about" :about}],
                          :app
                          {:mount-point-id "app",
                           :routes
                           {:key :re-integrant-app.module/router},
                           :moment
                           {:key :re-integrant-app.module/moment}}}
dev:cljs.user=> (reset)
#:re-integrant-app.module{:moment
                          {:subs (:re-integrant-app.module.moment/now),
                           :events
                           (:re-integrant-app.module.moment/init
                            :re-integrant-app.module.moment/halt
                            :re-integrant-app.module.moment/fetch-now)},
                          :router
                          {:subs
                           (:re-integrant-app.module.router/active-panel
                            :re-integrant-app.module.router/route-params),
                           :events
                           (:re-integrant-app.module.router/init
                            :re-integrant-app.module.router/halt
                            :re-integrant-app.module.router/go-to-page
                            :re-integrant-app.module.router/set-active-panel),
                           :router
                           {:history
                            #object[pushy.core.t_pushy$core31221],
                            :routes ["/" {"" :home, "about" :about}]}},
                          :app
                          {:subs (:re-integrant-app.module.app/title),
                           :events
                           (:re-integrant-app.module.app/init
                            :re-integrant-app.module.app/halt
                            :re-integrant-app.module.app/set-title),
                           :container
                           #object[HTMLDivElement [object HTMLDivElement]]}}
```

## まとめ

re-frame+integrant を使って SPA を開発する方法について考えてみました。
少し重厚なスタックではありますが、多くの依存関係と複雑なライフサイクルを持ち、プロファイルに応じて設定を変更する必要があるような SPA であれば採用する価値のある手法だと思います。

## 参考

* [ClojureScript による SPA のモジュール分割](https://qiita.com/223kazuki/items/dd1af292a644e95a3085)
* [integrant](https://github.com/weavejester/integrant)
* [re-frame doc](https://github.com/Day8/re-frame/blob/master/docs/README.md)
