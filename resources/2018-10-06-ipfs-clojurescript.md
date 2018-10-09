---
title: Using IPFS from ClojureScript SPA
description: Using IPFS from ClojureScript SPA
author: Kazuki Tsutsumi
author-email: rixi223.kazuki@gmail.com
author-url: https://twitter.com/goronao
author-github: https://github.com/223kazuki
author-twitter: goronao
author-avatar: https://pbs.twimg.com/profile_images/1218227972/P1060544_400x400.jpg
location: San Jose, CA, USA
date-created: 2018-10-06
date-modified: 2018-10-06
date-published: 2018-10-06
headline:
in-language: en
keywords: IPFS, ClojureScript, re-frame, integrant
uuid: d50eae3f-5a71-4ca1-8fa8-3c2d4a6d9381
tags:
 - IPFS
 - ClojureScript
 - re-frame
 - integrant
---

Recently I had a chance to use IPFS when I developed Ethereun DApp. So I played with it.

Repository
https://github.com/223kazuki/ipfs-chain

## IPFS
It stands for InterPlanetary File System. It is a kind of P2P hypermedia protocols.

https://ipfs.io/

IPFS consists of nodes that are distributed among network as like Blockchain. If you want to run a node for yourself, it's common to use [go-ipfs](https://github.com/ipfs/go-ipfs).

It's not so complex system from the viewpoint of user.

1. IPFS generates hash when you upload file to it.
2. You can ask the system to download the file with generated hash.
3. If the node you ask does not have the target file, the node asks other nodes to transport it.

As the hash is unique to the file in the network, you can download it with the hash. The has is like Qmbqap913AY77BNX2aXGUpU7Q3Vmguu85KZQ3q6KTzGkXd.
[infura](https://infura.io/) that provides Ethereum node as a service also provides IPFS node. So you can access the uploaded file from browser via internet easily.

https://ipfs.infura.io/ipfs/[Hash]

Because of these characteristics, it's usually regarded as a technology which configures the next generation web.

## ClojureScript

As you know, it's an alt-js language which is developed as sub set of clojure.
Although there was no reason to adopt it for this case, I developed it with [re-integrant pattern](https://223kazuki.github.io/re-integrant-app.html).

## What I developed

https://ipfs.infura.io/ipfs/Qmbqap913AY77BNX2aXGUpU7Q3Vmguu85KZQ3q6KTzGkXd

![cljs-ipfs.png](https://qiita-image-store.s3.amazonaws.com/0/109888/110a0ff2-be4e-b7d9-9ceb-1928f539be5c.png)

As you can see its domain of URL, this SPA itself is hosted on IPFS.

The usage is so simple. All you can do is to click the button named "Generate New Block". When you click it, it will move to another page that is almost same as the previous one. And you can move back to the previous page by clicking the "Previous Block" link.

What happens when you click the button? It generates html as a string and uploads it to IPFS. Then it redirects to the uploaded html by generated hash. So you can see that the hash on URL changes each as it redirects.

In other words, it is a SPA that generates itself! (Technically it just generates a html string with rewritten meta tag.)

## Using ipfs-js-api in ClojureScript

In order to access IPFS from web browser, it's general to use [ipfs-js-api](https://github.com/ipfs/js-ipfs-api). Although there's no good wrapper library, there's cljsjs package. So I will try to use it.
I use it as a re-integrant module. (Please read [previous post](https://223kazuki.github.io/re-integrant-app.html).)

### config.edn

It defines configurations for initialization of ipfs-api instance.

```clojure:config.edn
{:ipfs-chain.module/ipfs
 {:protocol "https"      ;; configs for :module/ipfs
  :host "ipfs.infura.io" ;; ..
  :port 5001}            ;; ..

 :ipfs-chain.module/app
 {:mount-point-id "app"
  :ipfs #ig/ref :ipfs-chain.module/ipfs}}
```

### Initialization

Integrant initializes ipfs module.

```clojure:ipfs.cljs
(require '[cljsjs.ipfs]
         '[cljsjs.buffer])

;; ...

;; Init
(defmethod ig/init-key :ipfs-chain.module/ipfs
  [k opts]
  (js/console.log (str "Initializing " k))
  (let [[subs events effects] (->> [reg-sub reg-event reg-fx]
                                   (map methods)
                                   (map #(map key %)))
        ipfs (js/IpfsApi (clj->js opts))] ;; Initialized ipfs-api instance.
    (->> subs (map reg-sub) doall)
    (->> events (map reg-event) doall)
    (->> effects (map #(reg-fx % ipfs)) doall) ;; Passed ipfs-api instance to effect handlers.
    (re-frame/dispatch-sync [::init ipfs])
    {:subs subs :events events :effects effects}))
```

### Call upload to IPFS

It dispatches `::ipfs/upload` from view when you press the button. It generates html as a string and passes it to handler.

```clojure:views.cljs
(defn home-panel []
  (let [previous-hash (get-meta-data "previous-ipfs-hash")
        generated (get-meta-data "generated")]
    [:div
     [sa/Segment
      [:h2 "Current Block"]
      (if (empty? generated)
        "Root Block"
        (str "Generated at " generated))]
     [sa/Button {:on-click
                 #(let [data (generate-html)]
                    (re-frame/dispatch [::ipfs/upload-data data  ;; Dispatches upload handler.
                                        [:ipfs-chain.module.app/chain-on-ipfs]
                                        [:ipfs-chain.module.app/throw-error]]))}
      "Generate New Block"]
     (when-not (empty? previous-hash)
       [sa/Segment
        [:h2 "Previous Block"]
        [:a {:href (str "https://ipfs.infura.io/ipfs/" previous-hash)}
         previous-hash]])]))
```

### Event handler

It's dispatched from view. It generates a Buffer object from passed string. Then it calls effect handler (`::add`) because uploading to IPFS is side effect.

```clojure:ipfs.cljs
(def buffer-from (aget js/buffer "Buffer" "from"))

(defmethod reg-event ::upload-data [k]
  (re-frame/reg-event-fx
   k [re-frame/trim-v]
   (fn-traced
    [{:keys [:db]} [data on-success on-error]]
    {:db db
     ::add {:buffer (buffer-from data)
            :on-success on-success
            :on-error on-error}})))
```

### Effect handler

This is where it actually calls ipfs-api. The handler has already had ipfs-api instance when it was initialized. As `(js-invoke ipfs "add" buffer)` returns a Promise object, it's necessary to pass callbacks to it.

```clojure:ipfs.cljs
(defmethod reg-fx ::add [k ipfs]
  (re-frame/reg-fx
   k (fn [{:keys [:buffer :on-success :on-error] :as params}]
       (.. (js-invoke ipfs "add" buffer) ;; ipfs-api 呼び出し
           (then (fn [res]
                   (let [hash (aget (first res) "hash")]
                     (when-not (empty? on-success)
                       (re-frame/dispatch (vec (conj on-success hash)))))))
           (catch (fn [err]
                    (when-not (empty? on-error)
                      (re-frame/dispatch (vec (conj on-error err))))))))))
```

I've already passed on-success callback when dispatching handler. On-success handler makes infura URL from generated hash and redirects to it.

```clojure:app.cljs
;; Event
(defmethod reg-event ::chain-on-ipfs [k]
  (re-frame/reg-event-fx
   k [re-frame/trim-v]
   (fn-traced
    [{:keys [:db]} [hash]]
    (when hash
      (let [path (str "https://ipfs.infura.io/ipfs/" hash)]
        {:db db
         ::redirect {:path path}})))))

;; Effect
(defmethod reg-fx ::redirect [k]
  (re-frame/reg-fx
   k (fn [{:keys [:path] :as params}]
       (when path
         (set! js/location.href path)))))
```

## Summary

In this post, I developed "Self-generating SPA" by using IPFS and ClojureScript. Though it has no practicality, IPFS has a lot of possibilities.
The reason why I adopted ClojureScript was just for my convenience. But I could handle side effects and system instance with re-integrant pattern. So I can also recommend to use ClojureScript for IPFS development.

## References

* [Develop ClojureScript SPA with combination of integrant and re-frame](https://223kazuki.github.io/re-integrant-app.html)
* [IPFS](https://ipfs.io/)
