---
title: REPL for pair programming with Party REPL
description: REPL for pair programming with Party REPL
image: https://223kazuki.github.io/images/me.jpg
author: Kazuki Tsutsumi
author-email: rixi223.kazuki@gmail.com
author-url: https://twitter.com/goronao
author-github: https://github.com/223kazuki
author-twitter: goronao
author-avatar: https://pbs.twimg.com/profile_images/1218227972/P1060544_400x400.jpg
location: San Jose, CA, USA
date-created: 2018-12-16
date-modified: 2018-12-16
date-published: 2018-12-16
draft: true
headline:
in-language: en
keywords: Clojure, REPL, ClojureScript, ATOM
uuid: 1efb44bf-4cbe-4741-844e-6db91d89db25
tags:
 - Clojure
 - REPL
 - ClojureScript
 - ATOM
---

In this post, I will introduce how to try pair programming with Clojure REPL.

## What's Party REPL
Although pair programming is not a silver vallet, it is effective in many situation. Recently most of modern editors support the remote editing feature. So it's not necessary to sit together in front of the PC in order to do pair programming.
But how do you feel if you can do pair programming on "running application".?

Yes, you can! ...if you use clojure.
[Party REPL](https://github.com/party-repl) that was introduced in [clojure/conj 2018](http://2018.clojure-conj.org/) enables you to do that.
[![Party REPL — A multi-player REPL built for pair-programming](http://img.youtube.com/vi/AJING0Vigpg/0.jpg)](https://www.youtube.com/watch?v=AJING0Vigpg)
Party REPL is Atom editor plugin that is developed by using cljs and shadow-cljs. If you use it with TeleType that is also Atom plugin for pair programming, it enables to do pair programming on running application.
It is now version 1.0. And it supports Linux/Mac. You can use it with nrepl and unrepl(socket REPL) launched by leiningen.
I will introduce how to install and how to use it in this post.

### TeleType
TeleType is an Atom plugin for pair programming. You can edit file in remote 
TeleType は Atom のペアプログラミング用プラグインで、リアルタイムにファイルの共同編集を行なえます。使用するには Github による認証が必要ですが、編集時は WebRTC により PC 同士が直接接続されます。CRDT: Conflict-free Replicated Data Types という編集方式を取っており、編集内容の衝突も起こらない（らしい）です。

### About Clojure REPL
Clojure は Code as Data という特性を持ち、コードが全て構造化・記述・操作可能なデータです。また、コードを入力として受け取り、プログラムを実行し、その結果をデータとして出力する REPL(Read Eval Print Loop) という機能を持ちます。REPL に入力するコードは文字列で表現されたデータであるため、入出力を TCP 経由にすることも出来ます。

REPL はいくつかの階層に分かれます。(Clojure 1.9 以降の場合)

* STD/IO REPL ... 標準入出力による REPL。
* Socket REPL ... 上記に TCP 入出力をかぶせた REPL。
* nrepl ... 上記の上に構築された REPL サーバ・クライアントプロトコル。ミドルウェア（piggieback など）により拡張可能。

nrepl は現状最も使われている REPL ですが nrepl サーバをミドルウェアで拡張するとクライアント REPL 側にも対応したツールが必要となってしまいます。これはローカル開発では問題ではありませんが、リモートの nrepl サーバに接続する場合は問題が起こる可能性があります。**unrepl** はそのために開発された新しい REPL で、接続時にサーバから必要なツールをクライアント側に読み込ませることでこの問題を解決しようとしています。また、Clojure 1.10 からはよりシンプルな prepl というストリームベースの REPL が標準に追加されるようです。Party REPL では unrepl を使ったほうがより多くの機能を使えるようなので、この記事では unrepl を使います。

## Setup
ペアプログラミングに参加するホスト、ゲスト PC をそれぞれセットアップします。そもそも Atom を使ってなかったので、Clojure 開発環境のセットアップからやります。OS は macOS Mojave 10.14.1、Atom のバージョンは 1.33.0 です。

### Install Atom
公式サイトから Atom をインストールします。
https://atom.io/

### Install plugins
必要に応じて Linter や Formatter などを導入します。私は下記の記事・動画を参考にセットアップしました。REPL 関係は紛らわしいのでインストールしないでおいて下さい。
[Slick Clojure Editor Setup with Atom](https://medium.com/@jacekschae/slick-clojure-editor-setup-with-atom-a3c1b528b722)

### Install Party REPL
下記のプラグインをインストールします。バージョンは執筆時点の最新です。

* teletype v0.13.3
* clojure-party-repl v1.1.0

### Sign in TeleType
TeleType を使うためには Github にサインインする必要があります。TeleType メニューからサインインリンクに飛び、取得したトークンを入力して下さい。
![Screen Shot 2018-12-06 at 7.28.10 PM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/4332428b-dddc-8c1d-a73e-7da4b4733ce8.png)

以上でセットアップは完了です。

## Use it
私には conj デモのように Clojurian な奥さんはいないので一人で PC 並べてパーティします。Dark Theme がホスト、Light Theme がゲストです。

### Create project
開発するプロジェクトは Leiningen プロジェクトであればよいですが、今回は開発中にリセットが可能な [duct](https://github.com/duct-framework/duct) を使うことにします。

```
lein new duct lets-party +site +example +ataraxy +cljs
cd lets-party
lein duct setup
```

unrepl を使うためには Socket REPL サーバを公開する必要があるため、project.clj に下記を追加してください。デフォルトではループバックアドレスで起動してリモートから接続出来ないため、`:address \"0.0.0.0\"` も忘れずにつけて下さい。

```clojure
:jvm-opts ["-Dclojure.server.repl={:address \"0.0.0.0\" :port 9999 :accept clojure.core.server/repl}"]
```

後はお好みですが REPL を落とさずに開発を進めたいので、起動中のアプリに依存を読み込ませることの出来る [alembic](https://github.com/pallet/alembic) を開発プロファイルに追加しときます。

```clojure
[alembic "0.3.2"]
```

以上でプロジェクトの準備は完了です。プロジェクトは Github に push して、参加者全員がチェックアウトして下さい。
https://github.com/223kazuki/lets-party

### Run local REPL
まずはホスト側の準備です。ターミナルから repl を起動して下さい。

```sh
cd lets-party
lein repl
```

Party REPL から REPL を立ち上げることも可能ですが、nrepl クライントが開いて unrepl クライアントが開けなかったため、別途立ち上げた REPL サーバに接続する方法を取ります。

### Connect to local REPL
`Clojure Party Repl: ConnectToRemoteRepl` コマンドを実行すると下記のフォームが開くので入力し接続して下さい。
![Screen Shot 2018-12-07 at 10.57.18 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/f6e8615c-def6-468f-8e95-0b0cfe8c8e3a.png)
下記のようなパネル構成となります。
![Screen Shot 2018-12-07 at 10.58.48 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/c48dbe36-6e9f-0567-0a4c-46cb33e10b6a.png)
右上がこれまでの実行結果が表示される REPL History パネル、右下が REPL にコードを送る REPL Entry パネルです。コマンドを実行するときは REPL Entry パネルに入力し、`Clojure Party Repl: SendToRepl` を実行して下さい。SendToRepl は `Cmd-Enter` にキーバインドされていますが、私は他のホットキーとぶつかるので `Ctrl-Enter` に割り当てました。実行結果は REEPL History パネルに反映されます。
![Screen Shot 2018-12-07 at 11.01.45 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/efcc20f5-5f91-071d-03d3-4c366c622f2e.png)
また、ソースファイル上でも実行可能です。Eval したい範囲を選択、もしくは括弧の後ろにカーソルを置いて `SendToRepl` でコードが評価されます。REPL 上の名前空間を移動するために ns フォームを評価することもお忘れなく。
![Screen Shot 2018-12-07 at 11.03.46 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/95dcc01b-a9ba-9ceb-0255-a5ed092c78c3.png)
更に遅延シーケンスやサイズの大きいシーケンスに関しては折りたたみ表示をサポートしており、`...more` をクリックすることで展開していくことが可能です。
![Screen Shot 2018-12-07 at 11.04.26 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/60edd808-13a5-9c0c-0d7f-b51ff5f07c70.png)

### Share file by TeleType
では、ペアプログラミングに移るために TeleType でソースファイルを共有します。ホストは共有を許可してリンクを取得しゲストに知らせます。TeleType アイコンをクリックし、"Share" でリンクを取得し、Slack などでゲストに通知します。
![Screen Shot 2018-12-07 at 11.50.26 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/cbb49710-0239-06ec-dcea-8bc43285c947.png)
ゲストは TeleType メニューの "Join A Portal" にリンクを入力することでホストの開いているファイルを開けるようになります。
![Screen Shot 2018-12-07 at 11.07.01 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/7ff77483-7500-09a0-c026-a5ebf00d66ef.png)
青と緑のカーソルがそれぞれホスト、ゲストのものです。
![Screen Shot 2018-12-07 at 11.07.57 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/d04de1eb-c08e-7b67-d721-d9ceb496d731.png)
ゲストがファイルを編集するとリアルタイムにホスト側にも反映されます。

### Connect to remote REPL
では、いよいよゲストからホスト REPL に接続します。ホストがローカル REPL に接続したのと同様に `Clojure Party Repl: ConnectToRemoteRepl` でホスト REPL にリモート接続します。Host だけホストのものに変えてください。接続すると Entry, History パネルが開きますが、これは REPL サーバには接続していますが自分のローカルで起動した REPL クライアントです。ホスト側の REPL クライアントを共有して使いたいので、下記の２バッファーを開きます。

* `@223kazuki: Clojure Party REPL Entry lets-party`
* `@223kazuki: Clojure Party REPL History lets-party`

![Screen Shot 2018-12-07 at 11.14.20 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/5e27d02c-9271-5541-935d-ccbe0faa6293.png)
これで REPL クライアントも共有しながら開発が出来るようになりました。ゲストも Entry パネルやソースファイルから　`SendToRepl` でコードを実行することが出来ます。また、ホスト側ではゲストの操作をリアルタイムに見ながら、かつ Entry では実行履歴を補完（`Cmd-up`）することも可能です。

## Let's Paty!
では、ペアプログラミング、いやパーティプログラミングを始めましょう。duct にハンドラを追加します。

### 1. Run application
REPL Entry から `(dev)`, `(go)` を実行し、アプリケーションを起動します。

### 2. Add handler
ファイルの追加はホスト側でしか出来ないので、ホスト側はファイルを追加します。

* src/lets_party/handler/party.clj

実装はゲスト側で担当します。それぞれファイルを編集し、party エンドポイントを追加します。

* src/lets_party/handler/party.clj
* resources/lets_party/config.edn

![Screen Shot 2018-12-07 at 11.25.34 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/2ce84dca-2204-f951-0060-00dd1daa6d8d.png)
unrepl はデフォルトで標準出力の表示をサポートしてないようです。

### 3. Inject dependency by alembic
ここでゲストはエンドポイント開発に必要なライブラリ（hiccup）が足りないことに気付きます。ここで alembic を使います。
project.clj を編集し、

```project.clj:clojure
+ [hiccup "1.0.5"]
```

Entry パネルから alembic でプロジェクトを再読込します。

```clojure
(require 'alembic.still)
(alembic.still/load-project)
```

これで REPL を切らずに必要なライブラリを使えるようになりました。エンドポイントに依存を追加して `SendToRepl` して下さい。

```party.clj
(ns lets-party.handler.party
  (:require [ataraxy.core :as ataraxy]
            [ataraxy.response :as response] 
            [clojure.java.io :as io]
            [integrant.core :as ig]
            [hiccup.page :refer [html5 include-css include-js]])) ;; 追加
```

### 4. Inplement handler
必要に応じてホストが関数を実装したりもできます。関数の動作確認もソースファイルから行えます。エクストリーム・プログラミング的な使い方で役割分担しての開発も行えます。ns の切り替えでぶつからないように注意する必要はありますが。（それぞれ別の REPL クライアントを使うという方法もあります）

```clojure:party.clj
(defn- party [n]
  (case n
    1 "Party1"
    2 "Party2"
    3 "Party3")) ;; こちらはホストが実装。

(comment
  (party 1)
  (party 2)
  (party 3)) ;; 動作確認用。範囲選択して `SendToRepl` で試せる。

(defn- party-page [context]
  (let [body
        (html5
         [:head
          [:meta {:charset "utf-8"}]
          [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
          (include-css "//cdnjs.cloudflare.com/ajax/libs/semantic-ui/2.2.13/semantic.min.css")
          [:title "Let's Party!"]]
         [:body
          [:h1 (party 1)]
          (include-js "/js/main.js")])]
    {:status 200 :headers {"Content-Type" "text/html"} :body body})) ;; ここはゲストが実装。

(defmethod ig/init-key :lets-party.handler/party [_ _]
  (fn [{[_] :ataraxy/result}]
    (party-page nil)))
```
### 5. Reset application
実装後は `(in-ns 'dev)`, `(reset)` を実行することでハンドラをサーバに追加し、ブラウザでチェックします。

http://[host address]:3000/party

注意ですが、あくまでもアプリケーションはホスト側で起動しており、そこから何でも出来てしまうので共有には細心の注意を払って下さい。
![Screen Shot 2018-12-07 at 11.34.21 AM.png](https://qiita-image-store.s3.amazonaws.com/0/109888/cb043a59-4baf-0b7e-4c72-6b599074af29.png)

この手順を繰り返すことで、REPL を停止することなくソースファイルに編集した内容をコードとしてアプリケーションに反映しながら開発することが出来ます。勿論 REPL を切ったら（切らざるを得ない状況に追い込んだら）負けです。

## Summary
Party REPL によるパーティプログラミングを紹介をしました。ペアプログラミングはこれだけ広まっていますが、起動しているアプリケーションの状態を共有しながら開発を行うという発想は Clojure + REPL のパワーを持ってしないとなかなか生まれないものだと思います。Atom というのが emacs ユーザには若干ネックで、cider などに比べるとまだ機能は少ないですが、新しいペアプログラミングの道を開く素晴らしいツールなので是非お試し下さい。

## 参考
* [Party REPL — A multi-player REPL built for pair-programming: Tomomi Livingstone + Hans Livingstone](https://www.youtube.com/watch?v=AJING0Vigpg)
* [Party REPL](https://atom.io/packages/clojure-party-repl)
* [Slick Clojure Editor Setup with Atom](https://medium.com/@jacekschae/slick-clojure-editor-setup-with-atom-a3c1b528b722)
