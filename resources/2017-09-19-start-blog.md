---
title: ブログ始めます
description: ブログ始めます
author: Kazuki Tsutsumi
author-email: rixi223.kazuki@gmail.com
author-url: https://twitter.com/goronao
author-github: https://github.com/223kazuki
author-twitter: goronao
author-avatar: /images/anton-avatar.png
location: San Jose, CA, USA
date-created: 2017-09-19
date-modified: 2017-09-19
date-published: 2017-09-19
headline:
in-language: ja
keywords: clojure, boot-clj, perun
uuid: a349c300-af64-443a-8ef6-d8bcbd8dbe29
tags:
 - clojure
 - boot-clj
 - perun
---

223 です。  
而立を迎えてしまったのでとりあえず記録用にブログを開設することにしました。

## 自己紹介

軽く自己紹介をすると、

* [@goronao](https://twitter.com/goronao)
* SIer 勤務
* カリフォルニア・ベイエリア駐在中
* Web 寄りエンジニア
* よく使う言語は Clojure/Script
* 大學相撲部監督

といった感じなので、これらに関連したことを綴っていこうと思います。  
上司に「弊社の名前を出していいか」と聞いたら「ダメ」とのことだったので、個人ブログとして気楽に組織と関係のないことを書き綴っていきます。

## ブログ開設方法検討

差し当たってはブログを作らなければいけなかったのですが、以下の理由で Qiita などではなく自分で作ろうと思いました。

* 出来る限りシンプルにしたい
* 記事は emacs で編集し git で管理したい
* ブログに関する知識を低いレベルで理解したい
* たまに英語記事も書きたい（予定）ので日本で中心に使われるサービスは避けたい
* 心が弱いので特定のサービス上のマナー違反に怯えたくない
* 多少技術に関係ないことも書きたい（追い出されそう）

一番手っ取り早くこの条件を満たせそうな、静的サイトジェネレータ + GitHub Pages で作ることにしました。  
そして、数ある静的サイトジェネレータから何を選ぶかについては知見が特になかったので、 clojure 製のもの縛りで探すことに。"clojure static site generator" で検索すると下記がヒットしました。

* [cryogen](https://github.com/cryogen-project/cryogen)
* [stasis](https://github.com/magnars/stasis)
* [perun](https://github.com/hashobject/perun)

この内、 stasis はもう保守されてなさそうなので除外しました。  
cryogen と perun を比較すると、cryogen はテンプレートエンジンとして Selmer、perun は hiccup を使っているなどの細かい違いに加え、  

* cryogen は [leiningen](https://leiningen.org/) でビルドし、ブログ設定も宣言的
* perun は [boot](http://boot-clj.com/) でビルドし、タスクごとのパラメータでブログを組み立てていく

といったように、まさに leiningen と boot の対比が当てはまりそうな感じです。  
私自身普段は比較的 leininge を多く使っていますが、今回はブログに関する知識を低いレベルで理解したいという目的もあるので、そのあたりを宣言的にやってくれそうな cryogen ではなく perun を選ぶことにしました。

## perun + Github Pages でブログ開設

さて perun ですが、この記事の時点で最新の ```0.3.0``` を使いました。  
template が無い代わりに examples/blog を参考にすればほぼそのまま作れます。
それを参考に、当ブログ用に修正した build.boot の内容が下記です。

```clojure:build.boot
(set-env!
  :source-paths #{"src"}
  :resource-paths #{"resources"}
  :target-path #{"../223kazuki.github.io"}
  :dependencies '[[perun "0.4.2-SNAPSHOT"]
                  [hiccup "1.0.5"]
                  [pandeiro/boot-http "0.6.3-SNAPSHOT"]
                  [clj-time "0.14.0"]])

(require '[clojure.string :as str]
         '[io.perun :refer :all]
         '[pandeiro.boot-http :refer [serve]])

(task-options!
  markdown   {:out-dir ""}
  render     {:out-dir ""}
  collection {:out-dir ""}
  static     {:out-dir ""}
  tags       {:out-dir "tags"}
  paginate   {:out-dir "pages"}
  assortment {:out-dir "assorts"}
  serve      {:resource-root ""})

(deftask build
  []
  (comp (global-metadata)
        (markdown :md-exts {:tables true})
        (draft)
        (print-meta)
        (slug)
        (ttr)
        (word-count)
        (build-date)
        (gravatar :source-key :author-email :target-key :author-gravatar)
        (render :renderer 'io.github.223kazuki.post/render)
        (collection :renderer 'io.github.223kazuki.index/render :page "index.html")
        (collection :renderer 'io.github.223kazuki.posts/render :page "posts.html")
        (tags :renderer 'io.github.223kazuki.tags/render)
        (paginate :renderer 'io.github.223kazuki.paginate/render)
        (assortment :renderer 'io.github.223kazuki.assortment/render
                    :grouper (fn [entries]
                               (->> entries
                                    (mapcat (fn [entry]
                                              (if-let [kws (:keywords entry)]
                                                (map #(-> [% entry]) (str/split kws #"\s*,\s*"))
                                                [])))
                                    (reduce (fn [result [kw entry]]
                                              (let [path (str kw ".html")]
                                                (-> result
                                                    (update-in [path :entries] conj entry)
                                                    (assoc-in [path :entry :keyword] kw))))
                                            {}))))
        (static :renderer 'io.github.223kazuki.about/render :page "about.html")
        (inject-scripts :scripts #{"start.js"})
        (sitemap :out-dir "./")
        (rss :description "223 log" :out-dir "./")
        (atom-feed :filterer :original :out-dir "./")
        (print-meta)
        (target :dir #{"../223kazuki.github.io"} :no-clean true)
        (notify)))

(deftask dev
  []
  (comp (watch)
        (build)
        (serve)))
```

少しオプションなどが散らかっていますが、 ```build``` タスクで静的なリソースを生成し、```dev``` タスクでソースを監視しつつ ```build``` を呼んで開発サーバでホスティングします。  
```src``` 以下にはリソース作成用のコードしか存在せず、サーバ側の実装が一切現れないので非常にシンプルでいい感じです。

```../223kazuki.github.io``` に GitHub Pages repository を clone し、開発時もそこにリソースを出力してホスティングするようにしたので、開発時も本番と同じ構成で確認できます。
また、開発時は ```watch``` による監視でコードの変更がすぐに反映されます。

他のサイトジェネレータは試してませんが perun はかなりシンプルかつ強力な部類なのではないでしょうか。

## 今後

* ブログを続ける
* もうブログを少し作り込む
* AMP 対応したい

ついでに告知ではないですが、10/12 - 10/14 に Baltimore で開催される Clojure/conj 2017 に参加することにしました。  
参加する方々宜しくお願い致します。
