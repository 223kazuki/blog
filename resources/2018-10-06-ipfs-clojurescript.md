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

Recentry I had a chance to use IPFS when I developed Ethereun DApp. So I played with it.

Repository
https://github.com/223kazuki/ipfs-chain

## IPFS
It stands for InterPlanetary File System. It is a kind of P2P hypermedia protocols.

https://ipfs.io/

IPFS consists of nodes that are distibuted among network as like Blockchain. If you want to run a node for yourself, it's common to use [go-ipfs](https://github.com/ipfs/go-ipfs).

It's not so complex system from the viewpoint of an user.

1. IPFS generates hash when you upload file to it.
2. You can ask the system to download the file with generated hash.
3. If the node you ask does not have the target file, the node asks other nodes to transport it.

As the hash is unique to the file in the network, you can download it with the hash. The has is like Qmbqap913AY77BNX2aXGUpU7Q3Vmguu85KZQ3q6KTzGkXd.
[infura](https://infura.io/) that provides Ethereum node as a service also provides IPFS node. So you can access the uploaded file from browser via internet easily.

https://ipfs.infura.io/ipfs/[Hash]

Because of these characteristics, it's usually regarded as a technology which configures the next generation web.

## ClojureScript

As you know, it's an alt-js language which is developed as sub set of clojure.
Although there was no reason to adopt it for this case, I developed it with [re-integrant pattern]().

## What I developed

https://ipfs.infura.io/ipfs/Qmbqap913AY77BNX2aXGUpU7Q3Vmguu85KZQ3q6KTzGkXd

<img width="876" alt="screenshot 2018-10-04 22.50.57.png" src="https://qiita-image-store.s3.amazonaws.com/0/109888/110a0ff2-be4e-b7d9-9ceb-1928f539be5c.png">

As you can see its domain of url, this SPA itself is hosted on IPFS.

The usage is so simple. All you can do is to click the button naned "Generate New Block". When you click it, it will move to another page that is almost same as the previous one. And you can move back to the prevous page by clicking the "Previous Block" link.

What happens when you click the button? It generates html as a string and uploads it to IPFS. Then it redirects to the uploaded html by generated hash. So you can see that the hash on URL changes each as it redirects.

In other words, it is a SPA that generates itself! (Technically it just generates a html string with rewrited meta tag.)

もちろん infura のノードに間借りしているわけではありますが、自サバ無しで Web サイトをホストできる IPFS の仕組みを利用してみました。

## Using ipfs-js-api in ClojureScript

In order to access IPFS from web browser, it's general to use [ipfs-js-api](https://github.com/ipfs/js-ipfs-api).


Web フロントエンドから IPFS を使う場合、ipfs-js-api を使うことが一般的です。
特に良さそうな cljs ラッパーはなさそうでしたが、cljsjs パッケージは存在したため直接使ってみます。

ipfs-api は re-integrant で言うところの module にラップします。

### config.edn

ipfs-api インスタンス初期化用の設定を定義します。

```clojure:config.edn
{:ipfs-chain.module/ipfs
 {:protocol "https"      ;; module/ipfs に渡す config
  :host "ipfs.infura.io" ;; ..
  :port 5001}            ;; ..

 :ipfs-chain.module/app
 {:mount-point-id "app"
  :ipfs #ig/ref :ipfs-chain.module/ipfs}}
```

### Initialization

ipfs-api インスタンスを設定を使って初期化します。

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
        ipfs (js/IpfsApi (clj->js opts))] ;; ipfs-api インスタンスの初期化
    (->> subs (map reg-sub) doall)
    (->> events (map reg-event) doall)
    (->> effects (map #(reg-fx % ipfs)) doall) ;; 副作用ハンドラには ipfs-api インスタンスを渡しておく
    (re-frame/dispatch-sync [::init ipfs])
    {:subs subs :events events :effects effects}))
```

### ファイルアップロード呼び出し

View から `::ipfs/upload` イベントハンドラをディスパッチします。
ここでは文字列として生成した html をアップロードすることにします。

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
                    (re-frame/dispatch [::ipfs/upload-data data  ;; IPFS アップロードの呼び出し
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

文字列をから Buffer オブジェクトを生成します。
IPFS へのアップロードは副作用なので、副作用ハンドラ（`::add`）を呼び出します。

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

実際に ipfs-api を呼び出している箇所です。
ハンドラは `::init` 時にすでに ipfs-api インスタンスを受け取っています。
`(js-invoke ipfs "add" buffer)` が Promise を返すので、then, catch でコールバックを渡します。

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

on-success では生成されたハッシュを元に、infura ノードでホストされる html ファイルにリダイレクトするようにしています。

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

IPFS を使って何か手軽に面白い事はできないかと考え、実用性度外視ですが、「自分自身を生成する Web サイト」を作ってみました。
ClojureScript で作ったのは自分が使いやすい以外の理由はありませんでしたが、re-integrant 構成を使えば副作用やシステム一意なインスタンスを適切に管理できたのでいい感じでした。
Clojure/Script ではこれまで「関数をどこに書くべきか」で悩むことが多かったのですが、re-integrant の様な構成を取ればそれが明確になってきて、より気持ちよく開発することが出来るようになった気がします。

## Refferences

* [IPFS](https://ipfs.io/)
