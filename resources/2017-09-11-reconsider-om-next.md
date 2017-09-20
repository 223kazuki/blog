---
title: Om Next 振り返り
description: Om Next 振り返り
author: Kazuki Tsutsumi
author-email: rixi223.kazuki@gmail.com
author-url: https://twitter.com/goronao
author-github: https://github.com/223kazuki
author-twitter: goronao
author-avatar: /images/anton-avatar.png
location: San Jose, CA, USA
date-created: 2016-12-22
date-modified: 2016-12-22
date-published: 2016-12-22
headline:
in-language: ja
keywords: clojure, clojurescript, om
uuid: 2c71b011-6987-45bf-b342-e27a93ecccd9
tags:
 - clojure
 - clojurescript
 - om
---

この記事は [Clojure Advent Calendar 2016](http://qiita.com/advent-calendar/2016/clojure) 22日目の記事です。

## 0. 背景

今年の夏にアメリカ出張があり、その機会を利用してサンフランシスコ Clojure ユーザーグループのミートアップに参加してきました。その回の主題が [Om Next](https://github.com/omcljs/om) で、すでにプロダクトに採用しているという人の講演で Om Next でリモートサーバとの通信まで実現するという内容の話を聞くことが出来ました。
Om Next は昨年末くらいに少し触ったのですが、 Om との違いが大き過ぎることとまだ変更が多そうだと感じたため深入りはしていませんでした。しかしこの講演を聴き、 Om Next がどの様な問題を解決し、どの様なことが出来るようになるのかぼんやりと理解することが出来ました。

そこで、自分が普段 Clojure/Script の Web アプリ開発で使っている [Duct フレームワーク](https://github.com/duct-framework/duct) と組み合わせて開発してみることで、Om Next がどの様な問題を解決し、どの様なことが出来るようになるのかを振り返ってみようと思います。

## 1. Om Next とは

Om Next は React.js の ClojureScript ラッパーである Om の後継です。Om 同様に React.js による SPA (Single Page Application) の開発が行えるのですが、複雑になりがちなアプリケーション状態管理をシンプルにするために Reconciler という仕組みが取り入れられています。

### 1.1. Reconciler

訳すと「調整者」や「平和をもたらす人」という意味らしいです。

<img width="300" alt="Reconciler.png" src="https://qiita-image-store.s3.amazonaws.com/0/109888/c223eac8-1453-20c7-f88b-3e6d268e9866.png">

図のように Om Next の各構成要素 ( React Component, アプリケーション状態、リモートサーバ ) のやり取りを仲介し、状態管理をよきに計らってくれます。Om Next では UI ( React Component ) と状態が明確に分離されており、 UI から状態を参照・更新する場合は Reconciler に定義した処理 (parser) を呼び出すことになります。また、 Reconciler は特定の状態参照・更新をリモートサーバにディスパッチして結果をアプリケーション状態に反映することも行ってくれます。

下記が Reconciler の定義の例（今回作成したアプリのもの）です。

``` clojure
(defonce reconciler
  (om/reconciler
    {:state  app-state
     :normalize true
     :parser (om/parser {:read read :mutate mutate})
     :send   (fn [{query :remote} callback]
               (.send XhrIo "/api/query"
                      (fn [e]
                        (this-as this
                                 (callback (transit/read (transit/reader :json) (.getResponseText this)))))
                      "POST" (transit/write (transit/writer :json) query)
                      #js {"Content-Type" "application/transit+json"}))}))

```

om.next/reconciler に状態管理の定義を指定して生成します。

| key        |      |
| :----------| :--- |
| :state     | アプリケーション状態 |
| :normalize | アプリケーション状態の正規化を行うか指定 |
| :parser    | アプリケーション状態の参照・更新の定義 |
| :send      | サーバとのやり取りの定義 |

Reconciler の例えとしては、アメリカの昔のドラマに登場する「好き勝手遊びまわる金持ちのプレイボーイに陰で細かく口出ししながら問題を解決する秘書」が挙げられていたりしていました。とにかくこの Reconciler が Om Next を理解するためのキーでありこれを起点に考えると理解しやすいです。

Om Next 自体のより詳細な説明は snufkon さんが昨年 [入門 Om Next](http://qiita.com/snufkon/items/b19a76a83adce3dc2887) という記事を書かれているのでそちら参照してください。記事の公開から現在 (2016/12/22) までに Om Next のバージョンが alpha-25 から alpha-47 に上がっていますが最新版でも問題なく当てはまる内容です。

## 2. Duct とは

Component というアプリケーションフレームワーク上に Web アプリケーションを構築するためのフレームワークです。今回はメインテーマではないので詳述しませんが以前作成した下記記事・資料で概要に触れています。

[真のミニマルフレームワーク duct](http://qiita.com/kawasima/items/85f59d18e345c90d0383)
[高速！Clojure Web 開発入門](http://www.slideshare.net/ssuser8b0ea4/clojure-web)

## 3. 開発と振り返り

### 3.1. 作ったもの

今回はショッピングサイトを題材に作ってみました。
仕様は下記の通り。

* 商品一覧、ショッピングカート、購入履歴を見ることが出来る
* 商品一覧、購入履歴はサーバ側から取得
* 商品一覧から商品を選択してカートへ追加できる
* カート中の商品を選択してカート中の数を減らすことが出来る
* "Purchase"ボタンでカート中の商品を購入（購入履歴をサーバに登録）出来る

実際に作ったものは下記に置いてあります。

https://github.com/223kazuki/om-next-remoting-example

### 3.2. 起動方法

jdk 8, leiningen がインストール済みの想定で、下記コマンドを打ってください。

```bash
git clone https://github.com/223kazuki/om-next-remoting-example
cd om-next-remoting-example
lein repl
```

repl 起動後に下記の通り dev, go 関数を実行することでClojureScriptがコンパイルされ、サーバが起動します。またサーバ側ではインメモリで Datomic が起動します。

```clojure-repl
user=> (dev)
:loaded
dev=> (go)
...
Successfully compiled "target/figwheel/public/js/main.js" in 21.564 seconds.
...
:started
```

サーバが起動したらブラウザ(Chrome推奨) http://localhost:3001 にアクセスして下さい。下記画面が表示されれば起動成功です。

<img width="618" alt="sample.png" src="https://qiita-image-store.s3.amazonaws.com/0/109888/74a834a0-5568-61e4-a751-6b7bbafd8796.png">

### 3.3. Om Next が解決したこと

今回のサンプルアプリ開発で Om Next が解決したことを振り返ります。

#### 3.3.1. UI と状態の分離

Om で問題だったのは UI ( React Component ) と状態が分離し切れなかったことです。その結果、状態や channel の持ち回しが発生して管理し切れなくなりがちでした。

Om Next では UI と状態が明確に分離できます。下記が今回のサンプルにおける UI の定義です。

```clojure:src/remoting/example/client/view.cljs
(defui ^:once ListProduct
  static om/Ident
  (ident [this {:keys [product/number]}]
         [:product/by-number number]) ;; 描画データの正規化の定義
  static om/IQuery
  (query [this]
         [:product/number :product/name :product/price]) ;; 描画データとして何を取得するか
  Object
  (render [this]
          (let [{:keys [product/number product/name product/price] :as props} (om/props this)]
            (html
              [:tr
               [:td number]
               [:td name]
               [:td (str "$" price)]
               [:td
                [:i.add-cart.glyphicon.glyphicon-shopping-cart
                 {:onClick (fn [e] (om/transact! this `[(cart/add-product ~props) :products/cart]))}]]])))) ;; parser 呼び出し
```

まず、 query 関数の返り値として UI が表示に必要なデータを取得するクエリが定義されています。
このクエリは Reconciler 経由で parser へ渡されてフェッチされ、 ```(om/props this)``` から表示データを取得できます。受け渡されている ```this``` は単に Component 自身です。状態を更新する処理は ```(om/transact! this `[(cart/add-product ~props) :products/cart])``` として呼び出しており、同じく Reconciler 経由の parser 呼び出しとなります。
このように、 Reconciler が状態へのアクセスを全て仲介することで UI と状態の分離が実現出来ます。

#### 3.3.2. リモートサーバとアプリケーション状態の連携

SPA を構築する上で最も煩雑なのはリモートサーバから取得した値とアプリケーション状態の連携だと私は思います。Om Next ではこの問題も Reconciler が解決してくれます。
商品一覧を取得する処理を見てください。今回、商品一覧取得は ```:products/list``` というクエリに割り当てています。
まずは下記が ClojureScript 側の parser 定義です。

```clojure:src/remoting/example/client/parser.cljs
(defmulti read om/dispatch)
(defmethod read :products/list              ;; key が :products/list の時に呼び出される
  [{:keys [state ast] :as env} key params]  ;; 第一引数として呼び出し時のコンテキストが渡され、 state もここから取得できる
  (let [st @state]
    (if (contains? st key)                  ;; state が既に情報を持っているか判定
      {:value (get-product state key)}      ;; 持っている場合はそれを返却、つまり HTTP Cache
      {:remote ast})))                      ;; 持っていない場合はリモートサーバへ問い合わせる 
```

上記でクライアント側の状態中に欲しいデータがなかった場合、 ```:remote``` というキーを返すことで Reconciler にリモートサーバへ問い合わせるよう通知します。Reconciler はリモート問い合わせ定義( Reconciler 定義時に```:send```キーで指定 )を基に、同じクエリをリモートサーバへ投げます。

リモートサーバ側でも同様のクエリに対して parser を用意しておき、DB (Datomic) からデータを取得してレスポンスを返します。

```clojure:src/remoting/example/component/parser.clj
(defmulti read om/dispatch)
(defmethod read :products/list
  [{:keys [datomic] :as env} k _]
  (let [v (d/query datomic
                   '[:find [(pull ?p [*]) ...]
                     :where [?p :product/number]])]
    {:value v}))
```

レスポンスを受け取った Reconciler は結果をアプリケーション状態に自動でマージします。この結果、クライアント側で再度同じクエリが投げられた場合はアプリケーション状態中に該当のデータが既に存在するのでサーバ問い合わせは発生しません。

状態の更新の場合も同様の実装でアプリケーション状態とリモートサーバへの適切なディスパッチが出来ます。今回のサンプルでは、```products/purchase```というクエリで楽観的更新を実現しています。

以上の様にアプリケーション状態とリモートサーバを Reconciler が仲介してくれることで、これらの連携を簡潔に表現することができます。

### 3.4. Om Next のすごいポイント

#### 3.4.1. Reconciler History

Elm というフロントエンド言語を使う後輩から、[Elm's Time Traveling Debugger](http://debug.elm-lang.org/) というものの存在を教えてもらいました。詳しく理解できていないですが、アプリケーションの状態の変更履歴を管理することで画面を過去の指定時点に戻すことが出来るという機能らしいです。

Om Next でも Reconciler が状態の変更履歴を保持しているため似たことが出来ます。今回作成したアプリをブラウザ (Chrome 推奨) から開いてください。ブラウザの開発者コンソールを開いて js の出力を確認しながらカートに商品を追加すると下記のような出力が見れると思います。

```
[7901.524s] [om.next] [:product/by-number 13] transacted '[(cart/add-product {:db/id 17592186045431, :product/number 13, :product/name "plum", :product/price 860}) :products/cart {:products/cart [:product/number :product/name :product/price :product/in-cart]}], #uuid "bb592271-bf8a-49f6-a2b8-7c4f3cf14a6b"
```

これは Reconciler が出力する状態の更新のログで、各更新時点に対して UUID (行末) が発行されます。この UUID を記録し、その後にも何か操作を加えた後、repl で下記の操作を行ってください。

```clojure-repl
dev=> (cljs-repl) ;; cljs replに入る
To quit, type: :cljs/quit
nil
cljs.user=> (in-ns 'remoting.example.client.core) ;; reconciler が存在する名前空間へ移動
nil
remoting.example.client.core=> (om/from-history reconciler #uuid "bb592271-bf8a-49f6-a2b8-7c4f3cf14a6b") ;; 記録した時点での状態の取得
```

上記で記録時点のアプリケーション状態が repl に出力されます。あとはアプリケーション状態をこの時点のものに設定しなおせば、 UI も過去時点に再描画することができます。

```clojure-repl
remoting.example.client.core=> (reset! app-state (om/from-history reconciler #uuid "bb592271-bf8a-49f6-a2b8-7c4f3cf14a6b"))
```

Reconciler はデフォルトで 100 件の履歴を保持するようです。

#### 3.4.2. Figwheel との相性

Figwheel は ClojureScript のコンパイルとブラウザへのホットリロードを行う Leiningen Plugin です。Websoket でブラウザと接続してコンパイルした js をプッシュすることで変更を反映することができます。Duct はこの Figwheel を Component として提供しており、 repl から操作することが可能になっています。

cljs ファイルに何か変更を加え（保存するだけでもよい）repl から reset 関数を実行してください。

```clojure-repl
dev=> (reset)
:reloading ()
Compiling "target/figwheel/public/js/main.js" from ["src" "dev"]...
Compiling src\remoting\example\client\view.cljs
Compiling C:\work\om-next-remoting-example\src\remoting\example\client\core.cljs
Compiling dev\src\cljs\user.cljs
Copying file:/C:/work/om-next-remoting-example/src/remoting/example/client/view.cljs to target\figwheel\public\js\remoting\example\client\view.cljs
Successfully compiled "target/figwheel/public/js/main.js" in 1.528 seconds.
notifying browser that file changed:  out\remoting\example\client\view.js
:resumed
```

cljs ファイルがビルドされ、ブラウザに通知されています。UI を定義している view.cljs に変更を加えたのであれば、その変更がブラウザに反映されていることが確認できるはずです。

これだけでも十分生産性向上が望めますが、 Om Next と組み合わせた場合、もう一歩先に進むことが出来ます。今回のアプリケーションのエントリーポイントとなる core.cljs を見て下さい。

```clojure:src/remoting/example/client/core.cljs
(defonce app-state (atom {}))

(defonce reconciler
  (om/reconciler
    {:state  app-state
     :normalize true
     :parser (om/parser {:read read :mutate mutate})
     :send   (fn [{query :remote} callback]
               (.send XhrIo "/api/query"
                      (fn [e]
                        (this-as this
                                 (callback (transit/read (transit/reader :json) (.getResponseText this)))))
                      "POST" (transit/write (transit/writer :json) query)
                      #js {"Content-Type" "application/transit+json"}))}))

(defonce mounted (atom false))

(defn init! []
  (if-not @mounted
    (let [target (gdom/getElement "app")]
      (om/add-root! reconciler view/RootView target)
      (reset! mounted true))
    (.forceUpdate (om/class->any reconciler view/RootView))))
```

ここで、app-state, reconciler を defonce で定義しています。こうすることで Figwheel のリロードがかかってもこれらの定義が上書かれなくなります。また、 RootView のマウント状態も同様に管理することで Figwheel ロード時には forceUpdate が呼び出されるようにもなっています。これらの変更を加えることで、アプリケーション状態や更新履歴を保持したままホットリロード出来るようになります。

試しに商品をカートに追加した状態で、 cljs ファイルを変更し、 repl から reset 関数を実行してみてください。カートが維持されたまま変更が反映されることが確認できるはずです。

## 4. その他に Om Next で出来ること

今回のサンプルアプリで触れたこと以外にも Om Next は実現できることは多数存在します。主要なものをいくつか挙げてみたいと思います。

### 4.1. Incremental Rendaring

Om Next では React Component の再レンダリングも Reconciler が管理しているため、生の React.js よりも無駄のない方法で行われます。Incremental Rendaring と呼ばれており、状態に変更があった場合に React Component ツリーのうち変更があった個所から再描画が始まるようです。詳細は下記記事を参照してください。

[Om Next internals: Incremental Rendering](https://anmonteiro.com/2016/09/om-next-internals-incremental-rendering/)

### 4.2. 状態管理方法の変更

サーバ側のデータストレージは当然 Datomic 以外に置き換え可能ですし、それに加えてクライアント側のデータストレージも単純な atom 以外に置き換え可能です。クライアント側データストレージとして [DataScript](https://github.com/tonsky/datascript) を使用する方法が [公式のチュートリアル](https://github.com/omcljs/om/wiki/DataScript-Integration-Tutorial) で紹介されています。
特に DataScript を使うとクライアント側でも Datomic と同様のシンタックスでデータの操作が行えるようになるため、よりクライアント側とサーバ側の親和性が高まります。

### 4.3. Server Side Rendering

Om Next は alpha-45 から単体での SSR をサポートしています。
[Clojure Advent Calendar 2016 10日目の記事](https://designudge.org/ja/stories/1481369485901_fe99b3b7/) で karad さんが紹介して下さっています。

### 4.4. UI Property Based Test

状態は全て parser によって操作・参照されるので、クライアント側のテストを簡単に実装することが出来ます。[test.check](https://github.com/clojure/test.check) を使い、UI に対する Property Based Test を実現する方法が [公式チュートリアル](https://github.com/omcljs/om/wiki/Applying-Property-Based-Testing-to-User-Interfaces) で紹介されています。

## 5. まとめ

駆け足となってしまいましたが実際にサンプルアプリを作って Om Next が解決してくれる問題とすごい点を振り返ってみました。

学習コストが高いことは否めませんが、個人的にはそれに見合うほどの魅力を感じました。元々私が Clojure を始めたきっかけは、「現状理解出来てないけど凄いことが実現できそうだ」と感じたこと ( +上司の影響 ) だったため、現在その条件に当てはまる Om Next の学習はとても楽しいです。サンプルアプリはすぐに試せて Om Next の魅力を最低限味わってもらえるように作ったので、これをきっかけに Om Next に触れてくれる人が増えたら嬉しいです。

なお、私は本来フロントエンドエンジニアではないため特にフロントエンド周りの理解が誤っていたり言葉が間違っている可能性が高いです。何か問題点がありましたらご教授ください。

## 6. 参考資料

下記の資料を中心に参考とさせていただきました。

* [Github Om Wiki](https://github.com/omcljs/om/wiki)
* [入門 Om Next](http://qiita.com/snufkon/items/b19a76a83adce3dc2887)
* [Om/Next: The Reconciler](https://medium.com/@kovasb/om-next-the-reconciler-af26f02a6fb4#.yli2wy6bz)
* [Writing Om Next Reloadable Code](https://anmonteiro.com/2016/01/writing-om-next-reloadable-code-a-checklist/)
