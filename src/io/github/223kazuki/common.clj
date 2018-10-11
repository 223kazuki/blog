(ns io.github.223kazuki.common
  (:require [hiccup.page :refer [html5]]
            [clj-time.format :as f]))

(def formatter (f/formatter "MM/dd/yyyy"))

(defn cloud-flare [path]
  (str "//cdnjs.cloudflare.com/ajax/libs" path))

(defmacro layout [config & content]
  `(html5
    {:lang "en" :itemtype "http://schema.org/Blog"}
    [:head
     [:script {:async true
               :src "https://www.googletagmanager.com/gtag/js?id=UA-127383373-1"}]
     [:script
      "window.dataLayer = window.dataLayer || [];
       function gtag(){dataLayer.push(arguments);}
       gtag('js', new Date());
       gtag('config', 'UA-127383373-1');"]
     [:title (-> ~config :meta :site-title)]
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "X-UA-Compatible" :content "IE=edge,chrome=1"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1.0, user-scalable=no"}]
     [:link {:href "/css/normalize.css" :rel "stylesheet"}]
     [:link {:href "//unpkg.com/basscss@8.0.2/css/basscss.min.css"
             :rel "stylesheet"}]
     (when-let [post# (:post ~config)]
       (list
        [:meta {:name "twitter:card" :content "summary"}]
        [:meta {:name "twitter:title" :content (str (:title post#) " | "
                                                    (-> ~config :meta :site-title))}]
        [:meta {:name "twitter:creator" :content (:author-twitter post#)}]
        [:meta {:name "twitter:description" :content (:description post#)}]
        [:meta {:name "twitter:image" :content (:image post#)}]
        [:link {:href (cloud-flare "/github-markdown-css/2.8.0/github-markdown.min.css")
                :rel "stylesheet"}]
        [:link {:href (cloud-flare "/highlight.js/9.12.0/styles/default.min.css")
                :rel "stylesheet"}]))
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
     (when-let [post# (:post ~config)]
       (list
        [:script {:src (cloud-flare "/highlight.js/9.12.0/highlight.min.js")}]
        (when-let [tags# (:tags post#)]
          (->> tags#
               (map #(.toLowerCase %))
               (map #(case %
                       "clojure" ["clojure" "clojure-repl"]
                       "clojurescript" ["clojure" "clojure-repl"]
                       "emacs" ["lisp"]
                       []))
               flatten
               distinct
               (map #(vec
                      [:script
                       {:src (cloud-flare
                              (str "/highlight.js/9.12.0/languages/" % ".min.js"))}]))))
        [:script "hljs.initHighlightingOnLoad();"]))]))
