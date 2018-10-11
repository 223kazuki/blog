---
title: Develop ClojureScript SPA with combination of integrant and re-frame
description: "In this post, I'll introduce how to develop ClojureScript Single Page Application by using the combination of integrant and re-frame.

I also introduced that in [the previous post](https://223kazuki.github.io/re-integrant.html). But as the previous example was special case, Ethereum DApp, it was a little complex to understand. So I develop an example again in pure cljs."
image: https://pbs.twimg.com/profile_images/927415477944573952/K7qwI-7f_400x400.jpg
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

In this post, I'll introduce how to develop ClojureScript Single Page Application by using the combination of integrant and re-frame.

I also introduced that in [the previous post](https://223kazuki.github.io/re-integrant.html). But as the previous example was special case, Ethereum DApp, it was a little complex to understand. So I develop an example again in pure cljs.

https://github.com/223kazuki/re-integrant-app

I tentatively call this pattern "re-integrant".

## Overview

The SPA developed in this application pattern consists of three layers.

1. Integrant layer that manages the whole lifecycle of the application.
2. Re-frame layer that manages app-db that is updated by user interaction.
3. Reagent layer represents view that subscribes and dispatches app-db via re-frame handlers.

And the application is divided into modules by integrant. Re-frame handlers are registered in each modules' namespaces when the modules initialize.

![re-integrant.png](https://qiita-image-store.s3.amazonaws.com/0/109888/0af426ba-2ab9-5fe5-1fda-22e0f136fcfe.png)

### Project Structure

I adopted the similar structure to the server side integrant application like duct template. And I created dev directory to manage development settings.

```bash
.
├── project.clj
├── dev
│   ├── resources
│   │   └── dev.edn
│   └── src
│       └── user.cljs
├── resources
│   ├── config.edn
│   └── public
│       ├── css
│       │   └── site.css
│       └── index.html
└── src
    └── re_integrant_app
        ├── core.cljs
        ├── module
        │   ├── app.cljs
        │   ├── moment.cljs
        │   └── router.cljs
        ├── utils.cljc
        └── views.cljs
```

### project.clj

The versions of primary libraries are bellow.

```clojure
[org.clojure/clojure "1.9.0"]
[org.clojure/clojurescript "1.10.339"]
[reagent "0.8.0"]
[re-frame "0.10.5"]
[integrant "0.7.0"]
```

The build settings of ClojureScript in each profiles are bellow. Figwheel executes `cljs.user/reset` on jsload during development.

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

I added `:module/moment` that provides [Moment](https://momentjs.com/) in each seconds.

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

### Module

It doesn't do anything different from what I introduced in the last post. But I wrote process explicitly for the sake of ease.
I added multimethods, reg-sub and reg-event to that we can add the implementation of handlers. We can register all of handlers by using it when the module initializes.
The implementation of `::now` subscription means that it provides Moment object in each seconds only when it's subscribed. (Please refer to [Subscribing to External Data](https://github.com/Day8/re-frame/blob/master/docs/Subscribing-To-External-Data.md))

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
  (let [subs (->> reg-sub methods (map key))      ;; Get the keywords of handlers.
        events (->> reg-event methods (map key))] ;; Same as above.
    (->> subs (map reg-sub) doall)                ;; Execute multimethod and register handlers.
    (->> events (map reg-event) doall)            ;; Same as above.
    (re-frame/dispatch-sync [::init])
    {:subs subs :events events}))

;; Halt
(defmethod ig/halt-key! :re-integrant-app.module/moment
  [k {:keys [:subs :events]}]                      ;; Get the keywords of handlers.
  (js/console.log (str "Halting " k))
  (re-frame/dispatch-sync [::halt])
  (->> subs (map re-frame/clear-sub) doall)        ;; Clear handlers.
  (->> events (map re-frame/clear-event) doall))   ;; Same as above.
```

### View

It's not different from what was in the previous post.
Only when home-panel which is subscribing `::moment/now` opens, `:module/moment` provides Moment objects.

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

It's also not changed so much.
You need to require all modules because we can't use integrant's load-namespaces in ClojureScript.
And I defined config as an atom because I want to change it during development.

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

It's the development setting. I set `:dev true` in `:module/moment` to check if it's reflected.

```clojure
{:re-integrant-app.module/moment {:dev true}}
```

### user.cljs

It's the main namespace during development. It loads dev.edn and merge it to core/config. Figwheel call `reset` on jsload.

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

## Development

You can start Figwheel server and open cljs repl by following command.
When you save the code, Figwheel detect that, build it and reflect it to browser.

```sh
% lein dev
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

And as I created user.cljs as main namespace, we can get config, rewrite it and reset system in repl.

```sh
dev:cljs.user=> @config
{:re-integrant-app.module/moment {:dev true},
 :re-integrant-app.module/router ["/" {"" :home, "about" :about}],
 :re-integrant-app.module/app
 {:mount-point-id "app",
  :routes {:key :re-integrant-app.module/router},
  :moment {:key :re-integrant-app.module/moment}}}
dev:cljs.user=> (swap! config update-in [:re-integrant-app.module/moment :dev] not)
{:re-integrant-app.module/moment {:dev false},
 :re-integrant-app.module/router ["/" {"" :home, "about" :about}],
 :re-integrant-app.module/app
 {:mount-point-id "app",
  :routes {:key :re-integrant-app.module/router},
  :moment {:key :re-integrant-app.module/moment}}}
dev:cljs.user=> (reset)
{:re-integrant-app.module/moment
 {:subs (:re-integrant-app.module.moment/now),
  :events
  (:re-integrant-app.module.moment/init
   :re-integrant-app.module.moment/halt
   :re-integrant-app.module.moment/fetch-now)},
 :re-integrant-app.module/router
 {:subs
  (:re-integrant-app.module.router/active-panel
   :re-integrant-app.module.router/route-params),
  :events
  (:re-integrant-app.module.router/init
   :re-integrant-app.module.router/halt
   :re-integrant-app.module.router/go-to-page
   :re-integrant-app.module.router/set-active-panel),
  :router
  {:history #object[pushy.core.t_pushy$core31222],
   :routes ["/" {"" :home, "about" :about}]}},
 :re-integrant-app.module/app
 {:subs (:re-integrant-app.module.app/title),
  :events
  (:re-integrant-app.module.app/init
   :re-integrant-app.module.app/halt
   :re-integrant-app.module.app/set-title),
  :container #object[HTMLDivElement [object HTMLDivElement]]}}
```

## Summary

In this post, I introduced how to develop ClojureScript Single Page Application by using the combination of integrant and re-frame.
Although it is a little thick stack, you can adopt it to a complicated SPA that has a lot of depedencies, has complex lifecycle and need to change settings depending on profiles.

## Refferences

* [How to modularize ClojureScript SPA](https://223kazuki.github.io/re-integrant.html)
* [integrant](https://github.com/weavejester/integrant)
* [re-frame doc](https://github.com/Day8/re-frame/blob/master/docs/README.md)
