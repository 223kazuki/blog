(ns io.github.223kazuki.posts
  (:require [io.github.223kazuki.common :refer [layout formatter]]
            [clj-time.format :as f]
            [clj-time.coerce :as c]))

(defn render [{posts :entries :as config}]
  (layout config
          [:p.h1 "Posts"]
          [:ul.list-reset.col-12.posts
           (for [{:keys [title permalink tags date-published] :as post}
                 (filter :title posts)]
             [:li.fit.clearfix
              [:a {:href permalink} title]
              (for [tag tags]
                [:a.ml1.tag {:href (str "/tags/" tag ".html")} tag])
              [:time.right (f/unparse formatter (c/from-date date-published))]])]))
