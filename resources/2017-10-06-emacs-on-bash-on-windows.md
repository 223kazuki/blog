---
title: Emacs on Bash on Windows と Windows でクリップボード共有
description: Emacs on Bash on Windows と Windows でクリップボード共有
author: Kazuki Tsutsumi
author-email: rixi223.kazuki@gmail.com
author-url: https://twitter.com/goronao
author-github: https://github.com/223kazuki
author-twitter: goronao
author-avatar: https://pbs.twimg.com/profile_images/1218227972/P1060544_400x400.jpg
location: San Jose, CA, USA
date-created: 2017-10-06
date-modified: 2017-10-06
date-published: 2017-10-06
headline:
in-language: ja
keywords: Emacs, Bash on Windows, cmder, clipboard, return code
uuid: 42be4d00-ae52-4d34-967b-6aab3d078de8
tags:
 - Emacs
 - Bash on Windows
---

## 背景ポエム

メインの開発機ではないものの Windows 機（Surface Laptop）を使っています。  
Surface Laptop は素晴らしいし、雑務で Windows 必要なことも多いので、持ち出し用 PC としては気に入ってます。

しかし最近、長期間（数週間）社外にて、その Windows 機で開発する機会があり、結構なつらみを感じました。  
特に一緒に作業している開発者(OSX)と OS の違いでお互いにワンテンポの遅れが出てしまう申し訳なさがつらい。

途中で Arch LINUX の Dual Boot を試そうかと思ったほどでしたが、 Surface に対してはちょっとチャレンジングなので思いとどまりました。  
そこで、これは **Bash on Windows** 利用の機運ではないかと思い至りました。

元々の社用 PC が Windows だったこともあり、BoW は発表された当初から試してはいましたが、
ある時は解決できない不具合にぶつかり、ある時は Windows との相互運用性の低さに挫け、と毎回様々な理由で熱が冷めてしまっていました。

特に Windows との相互運用性という面は、そもそも改行コードが違うし、[Windows側からUbuntu側のファイルをいじると壊れることがある](https://qiita.com/kaitoy/items/e20d426cdd1f507bfddb) 件などに対して一つづつ妥協案を検討して行くと、別 PC 用意すべきという結論になってしまいます。

しかし最近は、

* git client は Magit
* ファイラは dired
* そもそもメインの開発言語が clojure/script

というように、ほとんどの作業を Emacs(NTEmacs) 上に集約出来てきたため、開発に限れば相互運用性気にしなくて済みそうになりました。  
そんなわけで Emacs on BoW への乗り換えを進めています。

ただ、現実的には相互運用性を全く無視するわけにはいかないので、そこは一つづ解決していこうとしています。  
そんな中で、今回は一番気になっていたクリップボード共有について、何とか出来たという話です。

## やりたきこと

BoW 上の Emacs と、Windows でバッファ/クリップボード共有がしたい。  
主な理由はコードスニペットを共有したり試したりするためです。

## 環境

今回の話に直接関係はないですが Console Emulator は [cmder](http://cmder.net/) を使っています。  
BoW のセットアップや Emacs インストールについては省略します。

|             | version |
| ----------- | --- |
| Windows     | 10 Pro Version 1703 |
| cmder       | v1.3.2 |
| Ubuntu(BoW) | 16.04 |
| Emacs       | 24.5.1 |
| lemonade    | 1.1.1 |

## 解決方法

### Lemonade セットアップ
まずは BoW 上から Windows のクリップボードに触れなければいけません。`clip` コマンドは触れません。  
そこで [lemonade](https://github.com/pocke/lemonade) というツールを使います。
lemonade は TCP 経由で Windows クリップボードを操作するツールです。

今回の構成では、 Windows 上に lemonade のサーバが起動し BoW 上で lemonade クライアントを使う構成になります。  
BoW 上で lemonade を使う方法については下記の記事がまとめてくれていたため参照してください。

[Bash on Windowsのクリップボード周り](https://qiita.com/aki017/items/8a8768a85590d21919ea)

ただ、これだけだとまだ Emacs バッファとの共有が実現できていません。

### Emacs/Lemonade 接続
Emacs から Lemonade のクライアントを呼び出す必要があります。
Lemonade のクライアント（BoW）側の基本的な使い方は下記の通り。

```bash
$ echo xxxxxxxxx | lemonade copy
$ lemonade paste
```

これをそれぞれ Emacs の kill/yank で中継させてあげればやりたきことが実現できます。  
注意しなければならないのは、双方向でそれぞれ適切に改行コードを換しなければならないことです。
それを考慮し、下記を Emacs 初期化ファイルに追加し読み込みます。

```lisp:init.el
(defun copy-from-windows ()
 (shell-command-to-string "lemonade paste --line-ending LF"))

(defun paste-to-windows (text &optional push)
 (let ((process-connection-type nil))
     (let ((proc (start-process "lemonade-copy" "*Messages*" "lemonade" "copy" "--line-ending" "CRLF")))
       (process-send-string proc text)
       (process-send-eof proc))))

(setq interprogram-cut-function 'paste-to-windows)
(setq interprogram-paste-function 'copy-from-windows)
```

以上で複数行を扱った際も適切にクリップボード共有が出来るようになっているはずです。

## 所感

まずは一関門超えたという感じです。  
NTEmacs よりはサクサク動きますし、次の問題にぶつかるまではこれで行こうと思います。
