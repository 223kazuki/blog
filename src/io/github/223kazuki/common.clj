(ns io.github.223kazuki.common
  (:require [hiccup.page :refer [html5]]
            [clj-time.format :as f]))

(def formatter (f/formatter "MM/dd/yyyy"))

(defn cloud-flare [path]
  (str "//cdnjs.cloudflare.com/ajax/libs" path))

(defmacro layout [config & content]
  `(html5 {:lang "en" :itemtype "http://schema.org/Blog"}
          [:head
           [:title (-> ~config :meta :site-title)]
           [:meta {:charset "utf-8"}]
           [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
           [:meta {:name "viewport"
                   :content "width=device-width, initial-scale=1.0, user-scalable=no"}]
           [:meta {:name "twitter:card" :content "summary"}]
           [:meta {:name "twitter:title" :content (-> ~config :post :title)}]
           [:meta {:name "twitter:description" :content (-> ~config :post :description)}]
           [:meta {:name "twitter:image" :content (-> ~config :post :image)}]
           [:link {:href "/css/normalize.css" :rel "stylesheet"}]
           [:link {:href "//unpkg.com/basscss@8.0.2/css/basscss.min.css" :rel "stylesheet"}]
           [:link {:href (cloud-flare "/github-markdown-css/2.8.0/github-markdown.min.css")
                   :rel "stylesheet"}]
           [:link {:href (cloud-flare "/highlight.js/9.12.0/styles/default.min.css")}]
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
             [:li.inline-block.mr1 [:a {:href "/"} "Posts"]]]]
           [:script {:src (cloud-flare "/highlight.js/9.12.0/highlight.min.js")}]
           [:script {:src (cloud-flare "/highlight.js/9.12.0/languages/clojure-repl.min.js")}]
           [:script {:src (cloud-flare "/highlight.js/9.12.0/languages/clojure.min.js")}]
           [:script {:src (cloud-flare "/highlight.js/9.12.0/languages/clojure-repl.min.js")}]
           [:script {:src (cloud-flare "/highlight.js/9.12.0/languages/lisp.min.js")}]
           [:script "hljs.initHighlightingOnLoad();"]]))
