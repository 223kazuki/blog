---
title: Playing with IPFS and ClojureScript
description: Playing with IPFS and ClojureScript
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

Ethereum DApp を開発していたときに IPFS に触れる機会があったので遊んでみました。

リポジトリ
https://github.com/223kazuki/ipfs-chain

## IPFS
InterPlanetary File System（惑星間ファイルシステム！）の略で P2P ハイパーメディアプロトコルの一種です。

https://ipfs.io/

IPFS はブロックチェーン同様、ネットワーク上に分散されたノード群から構成されます。
ノードを自前で立てる場合は go 実装の go-ipfs を使うのが一般的です。

https://ipfs-book.decentralized-web.jp/install_ipfs/

使う分にはそれほど複雑な仕組みではありません。

1. ファイルをシステムにアップロードするとハッシュが生成される。
2. ファイルをダウンロードするときは、生成されたハッシュで問い合わせをかける。
3. 問い合わせたノードのリポジトリに該当ファイルが存在しない場合はネットワーク上で P2P 問い合わせを実行し、ファイルを転送してもらう。

ハッシュはファイルに対して一意なため、ネットワーク上で重複することがありません。つまり、ハッシュさえ知っていればファイルをダウンロードすることが可能となります。ハッシュは Qmbqap913AY77BNX2aXGUpU7Q3Vmguu85KZQ3q6KTzGkXd のような感じです。

Ethereum ノードを提供している [infura](https://infura.io/) が IPFS ノードを提供していたりするので、アップロードしたファイルにはブラウザからアクセスすることも簡単です。

https://ipfs.infura.io/ipfs/[Hash]

この様な特徴から次世代の Web を構成する技術の一つと見られています。

## ClojureScript

お馴染み、Clojure のサブセットとして開発される Alt-JS です。
今回使うべき理由は特にありませんでしたが、[前回紹介した re-integrant という構成](https://qiita.com/223kazuki/items/ce1680dc54ff8fe4770c)を使って開発してみました。

## 作ったもの

https://ipfs.infura.io/ipfs/Qmbqap913AY77BNX2aXGUpU7Q3Vmguu85KZQ3q6KTzGkXd

<img width="876" alt="スクリーンショット 2018-10-04 22.50.57.png" src="https://qiita-image-store.s3.amazonaws.com/0/109888/110a0ff2-be4e-b7d9-9ceb-1928f539be5c.png">

ドメインから分かるように SPA 自体が IPFS 上にホストされています。

使い方は簡単で、"Generate New Block" ボタンを押すだけ（しか出来ません）。ボタンを押してしばらくすると、同じ様な画面に遷移します。Previous Block のリンクから、前のページに戻ることが出来ます。

特に面白みもありませんが、何をやっているかというと、実はボタンを押すたびに html ファイルを生成して IPFS にアップロードしています。そして、アップロード後にその html にリダイレクトしていくことで、IPFS 上を辿っていけるようになっているのです。毎回 URL のハッシュが少しずつ違うことに気づくと思います。
つまり、SPA から SPA（正確には meta タグを書き換えた html）を生成しているのです。

もちろん infura のノードに間借りしているわけではありますが、自サバ無しで Web サイトをホストできる IPFS の仕組みを利用してみました。

## ClojureScript から ipfs-js-api を使う

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

### 初期化

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

### イベントハンドラ

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

### 副作用ハンドラ

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

## まとめ

IPFS を使って何か手軽に面白い事はできないかと考え、実用性度外視ですが、「自分自身を生成する Web サイト」を作ってみました。
ClojureScript で作ったのは自分が使いやすい以外の理由はありませんでしたが、re-integrant 構成を使えば副作用やシステム一意なインスタンスを適切に管理できたのでいい感じでした。
Clojure/Script ではこれまで「関数をどこに書くべきか」で悩むことが多かったのですが、re-integrant の様な構成を取ればそれが明確になってきて、より気持ちよく開発することが出来るようになった気がします。

## 参考

* [IPFS](https://ipfs.io/)
* [IPFS入門](https://ipfs-book.decentralized-web.jp/install_ipfs/)
