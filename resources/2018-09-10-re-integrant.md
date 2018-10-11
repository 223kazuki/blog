---
title: How to modularize ClojureScript SPA
description: "In this post, I will introduce a pattern to modularize Single Page Application written in ClojureScript.
Sample project is bellow.

https://github.com/223kazuki/cljs-dapp"
image: https://pbs.twimg.com/profile_images/927415477944573952/K7qwI-7f_400x400.jpg
author: Kazuki Tsutsumi
author-email: rixi223.kazuki@gmail.com
author-url: https://twitter.com/goronao
author-github: https://github.com/223kazuki
author-twitter: goronao
author-avatar: https://pbs.twimg.com/profile_images/1218227972/P1060544_400x400.jpg
location: San Jose, CA, USA
date-created: 2018-09-10
date-modified: 2018-09-10
date-published: 2018-09-10
headline:
in-language: en
keywords: ClojureScript, SPA, re-frame, integrant, Ethereum
uuid: 06a77b58-a9dd-4ce8-8777-f1e40ba99b4e
tags:
 - ClojureScript
 - SPA
 - re-frame
 - integrant
 - Ethereum
---

In this post, I will introduce a pattern to modularize Single Page Application written in ClojureScript.
Sample project is bellow.

https://github.com/223kazuki/cljs-dapp

### Background

As I'm interested in Blockchain technology recently, I've developed Ethereum DApp by using ClojureScript + [re-frame](https://github.com/Day8/re-frame).
In the frontend development, I faced a lot of difficulty that comes from the complexity of Ethereum ecosystem.

* There are a lot of libraries(npm modules) to use.
    * [web3.js](https://github.com/ethereum/web3.js/), [ipfs-api](https://github.com/ipfs/js-ipfs-api), [uport-connect](https://github.com/uport-project/uport-connect)...
* Each of them has initialization parameters and needs to change according to environments.
* The states associated with them can change.
* There are also dependencies among them.
    * For example, it is necessary to reinitialize web3 instance when connecting to uPort.

Although the smart contract itself was not so complex, I was about to lose it for sometimes during the frontend development.
In order to solve these problems, I wanted a platform like bellow.

* It can split an application into modules.
* It can manage the states of its modules.
* It can define the lifecycle of each modules.
* It can define initialization parameters and dependencies of modules declaratively.

I think [integrant](https://github.com/weavejester/integrant), Clojure/Script lifecycle framework is exactly what I want.
By using it in ClojureScript development, we can deal with the lifecycle of SPA and make it reloadable.
But in this case, I want to use re-frame at the same time.
So I had to consider about how to reconcile between the integrant modules, the app state managed in re-frame and its handlers.

In the real development, I tried but I couldn't make it.
But after then, I found an article about the pattern to use re-frame as well as [mount](https://github.com/tolitius/mount) by [district0x](https://district0x.io/) who developed [cljs-web3](https://github.com/district0x/cljs-web3).
mount is also a lifecycle management framework in Clojure/Script as with integrant.

This pattern was named [re-mount](https://github.com/district0x/d0x-INFRA/blob/master/re-mount.md).

This is what I wanted!

Although it was enough to use the pattern just as it is, I felt mount is less "declarative" than integrant.
So I tried to import this pattern in integrant.

## Sample project

The bellow is what I developed as a sample project.

https://github.com/223kazuki/cljs-dapp

You also need a development environment for Ethereum smart contract to run it.
Please set up according to README.

### Project Structure

As I made this project mixed with [Truffle](https://truffleframework.com/) project, it is difficult to understand it as a ClojureScript project.
The bellow are the files just related to ClojureScript.

```bash
.
├── project.clj
├── resources
│   ├── config.edn
│   └── public
│       ├── css
│       │   └── site.css
│       └── index.html
└── src
    └── cljs_dapp
        ├── core.cljs
        ├── module
        │   ├── app.cljs
        │   ├── router.cljs
        │   └── web3.cljs
        ├── utils.cljc
        └── views.cljs
```

#### config.edn

I defined initialization parameters for each modules in config.edn.

```clojure:config.edn
{:cljs-dapp.module/router
 ["/" {""       :home
       "about"  :about}]

 :cljs-dapp.module/web3
 {:network-id 1533140371286
  :contract #json "build/contracts/Simplestorage.json"}

 :cljs-dapp.module/app
 {:mount-point-id "app"
  :routes #ig/ref :cljs-dapp.module/router
  :web3 #ig/ref :cljs-dapp.module/web3}}
```

In this case, I split SPA to three modules.

* `:cljs-dapp.module/app` ... React.js(reagent) app module
* `:cljs-dapp.module/router` ... Router module using Html5History
* `:cljs-dapp.module/web3` ... Web3 module

As I want to initialize app module at last, it depends on router and web3 modules.
If there are dependencies, integrant manage the initialization order automatically.

I wonder you notice why it can read EDN file although ClojureScript can't slurp resources.
I will explain how to implement it.

#### Module

Next, the main point of this pattern, the implementation of module.

```clojure:router.cljs
;; In order to make it reloadable, the module registers re-frame handlers when it initializes.
;; reg-subs, reg-event-fxs are the original utility to register multiple handlers.
(defn- load-subs []
  (reg-subs
   {::active-panel
    (fn [db]
      (::active-panel db))}))

(defn- load-events []
  (reg-event-fxs
   {::init ;; A re-frame handler to initialize app-db for this module.
    (fn-traced [{:keys [:db]} _]
               {:db
                (assoc db ::active-panel :none)})

    ::halt ;; A re-frame handler to finalize app-db for this module.
    (fn-traced [{:keys [:db]} _]
               ;; Remove values which related to this module from app-db.
               {:db (clear-re-frame-db db (namespace ::module))})

    ::set-active-panel
    (fn-traced [{:keys [:db]} [panel-name]]
               {:db
                (assoc db ::active-panel panel-name)})}))

;; ...

;; Initialize module.
(defmethod ig/init-key :cljs-dapp.module/router
  [_ routes] ;; Initialization parameter defined in config.edn.
  (js/console.log (str "Initializing " (pr-str ::module)))
  ;; Load handlers of re-frame events and subscriptions.
  (load-subs)
  (load-events)
  ;; Dispatch initialization handler for app-db synchronously.
  (re-frame/dispatch-sync [::init])
  ;; Initialize Html5History. Then return record.
  (app-routes routes))

;; Stop module.
(defmethod ig/halt-key! :cljs-dapp.module/router
  [_ {:keys [history]}]
  (js/console.log (str "Halting " (pr-str ::module)))
  ;; Dispatch halt handler for app-db synchronously.
  (re-frame/dispatch-sync [::halt])
  ;; Remove re-frame handlers related to this module.
  (clear-re-frame-handlers (namespace ::module))
  ;; Stop Html5History.
  (pushy/stop! history))
```

Each of modules has values on re-frame app-db and handlers that watch and update them.
In order to keep it reloadable, the module wrap handler registration in a methond and exucute it during initialization.

After that, initialization method call `::init` event handler synchronously to initial values on re-frame db.
The router module has `::active-panel` as an initial value.

As all of values on re-frame db and handlers were registered with namespaced keywords, they are bound to modules loosely.

* DB
    * `:cljs-dapp.module.router/active-panel` = `:none`
* Subscription handler
    * `:cljs-dapp.module.router/active-panel`
* Event handler
    * `:cljs-dapp.module.router/init`
    * `:cljs-dapp.module.router/halt`
    * `:cljs-dapp.module.router/set-active-panel`

At the end of the initialization, the module initializes Html5History API and return its instance.
I use [pushy](https://github.com/kibu-australia/pushy) to manage the lifecycle of Html5History instance.

When it stops, it remove the value from re-frame app-db by calling `::halt` handler, delete handlers and terminate Html5History.
As the instance (technically it's a cljs record) is passed by integrant, it's easy to deal with it.

##### Specification with that module should comply

As like re-mount mentions, this pattern is not so strict.
So when you implement a new module, you have to check if it complies with specification.
A module should comply with the bellow specifications.

* Initialization
    * Register re-frame handlers only associated with the module itself.
    * Register (assoc) values on re-frame db by calling `::init` handler synchronously if it needs state.
    * Initialize instances and listeners and return them.
    * Values on app-db and handlers should be registered with keywords namespaced with the module's namespace.
* Termination
    * Remove (dissoc) values on re-frame db by calling `::halt` handler synchronously if it has state.
    * Remove re-frame handlers only associated with the module itself.
    * Stop instances and listeners.

#### views.cljs

App module mounts views.cljs when it's initialized.
Because re-frame handlers are registered with keywords namespaced by each modules, we can resolve them by their namespace alias.

```clojure:views.cljs
(require '[soda-ash.core :as sa]
         '[cljs-dapp.module.router :as router]
         '[cljs-dapp.module.web3 :as web3])

;; ::web3/my-address -> :cljs-dapp.module.web3/my-address

(defn home-panel []
  (let [my-address (re-frame/subscribe [::web3/my-address]) ;; subscription handler
        data (re-frame/subscribe [::web3/data])] ;; Resolve with alias.
    (reagent/create-class
     {:component-will-mount
      #(re-frame/dispatch [::web3/get-data]) ;; event handler

      :reagent-render
      (fn []
        [:div
         [sa/Segment
          [sa/Table {:celled true}
           [sa/TableBody
            [sa/TableRow
             [sa/TableCell {:style {:width "200px" :background-color "#F9FAFB"}}
              "Your address"]
             [sa/TableCell @my-address]]
            [sa/TableRow
             [sa/TableCell {:style {:background-color "#F9FAFB"}} "Stored data"]
             [sa/TableCell @data]]]]
          [sa/Divider {:hidden true}]
          (when @data
            [data-form {:configs {:initial-data @data}
                        :handlers {:update-handler
                                   #(re-frame/dispatch [::web3/set-data %])}}])]])})))
```

By the way, I use [Semantic UI React](https://react.semantic-ui.com/) and [soda-ash](https://github.com/gadfly361/soda-ash) that is a cljs wrapper library of it here.

#### core.cljs

Last but not least, core.cljs.
It's very similar to the usage in server side.
The entry point is `init`. And Figwheel's on-jsload calls `reset`.

```clojure:core.cljs
(defonce system (atom nil))

(defn start []
  (reset! system (ig/init (read-config "resources/config.edn"))))

(defn stop []
  (ig/halt! @system)
  (reset! system nil))

(defn reset []
  (stop)
  (start))

(defn ^:export init []
  (dev-setup)
  (start))
```

In `start` method, it reads config.edn.
Why can it `read-config` although cljs can't use `slurp`?

The answer is that this read-config is not the function provided by integrant but a clojure macro.

```clojure:utils.cljc
(defmacro read-config [file]
  #?(:clj (ig/read-string
           {:readers {'json #(-> %
                                 slurp
                                 (json/read-str :key-fn keyword))}}
           (slurp file))))
```

ClojureScript is just an AltJS.
But it can execute Clojure macro on JVM during build.

By using macro, the compiler reads `resources/config.edn` and expands it in start method.
It's like a magic!
This is possible because ClojureScript is a Clojure subset.

Honestly, it's not necessary to use this way.
It's enough to define config in code.
But there is also an integrand reader function `#json` that uses same way to read json file.

It reads a definition file of smart contract, `SimpleStorage.json` and convert it to Clojure data.
As this kind of file varies with the compile, it's loaded via Ajax usually.
But this file is very big.
As it almost has MB size, it's desirable to write them in code.

### Development

Then what is like the development using this pattern?
You can start Figwheel server by typing the following command.

```sh
% lein dev
Figwheel: Cutting some fruit, just a sec ...
Figwheel: Validating the configuration found in project.clj
Figwheel: Configuration Valid ;)
Figwheel: Starting server at http://0.0.0.0:3449
Figwheel: Watching build - dev
Figwheel: Cleaning build - dev
Compiling build :dev to "resources/public/js/compiled/app.js" from ["src"]...
Successfully compiled build :dev to "resources/public/js/compiled/app.js" in 38.744 seconds.
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

When you save the code, Figwheel detect that and build it.
Then the compiled code is pushed to the browser and `reset` function will be executed.

Even if you don't use this pattern, you can reflect the change of view by re-frame.
But you usually fail to refresh listeners or instances and finally reload the browser.

By using this pattern, you can properly reload the modules and develop SPA smoothly.

When you need to change configs, all you have to do is to edit config.edn and let Figwheel build.
Then the change will reflect to the browser automatically.

Furthermore, the module developed by this pattern is totally modulable.
district0x develop some modules as libraries by using `re-mount` pattern.
For example they publish GraphQL client module.

https://github.com/district0x/district-ui-graphql

Even if you don't develop library, you can develop loosely-coupled and reusable code.

## Summary

Taking the pattern I introduced in this article allows you to split SPA into modules each of that has original state and lifecycle.
That solves problems related to DApp development as stated bellow.

* There are a lot of libraries(npm modules) to use.
    * ▶ You can manage them by modularizing.
* Each of them has initialization parameters and needs to change according to environments.
    * ▶ You can easily manage them by defining them in config.edn declaratively.
* The state associated with them can change.
    * ▶ You can relate them to their modules by re-frame.
* There are also dependencies among them.
    * ▶ You can define them as dependencies of modules.

On the other hand, the problems of this pattern are as follows.

* You have to check if each modules complies with specification when you implement it.
* The code which initialize and finalize module is complicated.
* As integrant initializes application synchronously, it blocks rendering page until the end of initialization.

In spite of these problems, this pattern is very effective for development of complicated SPA.
So you may apply it to develop SPA like DApp.
