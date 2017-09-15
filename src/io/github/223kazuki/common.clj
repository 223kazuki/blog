(ns io.github.223kazuki.common
  (:require [hiccup.page :refer [html5]]))

(defmacro layout [config & content]
  `(html5 {:lang "en" :itemtype "http://schema.org/Blog"}
    [:head
     [:title (-> ~config :meta :site-title)]
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0, user-scalable=no"}]
     [:link {:href "/css/normalize.css" :rel "stylesheet"}]
     [:link {:href "//unpkg.com/basscss@8.0.2/css/basscss.min.css" :rel "stylesheet"}]
     [:link {:href "//cdnjs.cloudflare.com/ajax/libs/github-markdown-css/2.8.0/github-markdown.min.css" :rel "stylesheet"}]
     [:link {:href "//cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/styles/default.min.css" :rel "stylesheet"}]
     [:link {:href "/css/app.css" :rel "stylesheet"}]]
    [:body
     [:header.fixed.top-0.fit.col-12
      [:p.center [:a {:href "/"} "223 Log"]]]
     [:div.content.mx-auto.mt3.pt2
      [:div.clearfix
       ~@content]]
     [:footer.col-12
      [:ul.center.list-reset
       [:li.inline-block.mr1 [:a {:href "/about.html"} "About"]]
       [:li.inline-block.mr1 "/"]
       [:li.inline-block.mr1 [:a {:href "/feed.rss"} "RSS"]]
       [:li.inline-block.mr1 "/"]
       [:li.inline-block.mr1 [:a {:href "/posts.html"} "Posts"]]]]
     [:script {:src "//cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/highlight.min.js"}]
     [:script {:src "//cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/languages/clojure-repl.min.js"}]
     [:script {:src "//cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/languages/clojure.min.js"}]
     [:script {:src "//cdnjs.cloudflare.com/ajax/libs/highlight.js/9.12.0/languages/clojure-repl.min.js"}]
     [:script "hljs.initHighlightingOnLoad();"]]))
