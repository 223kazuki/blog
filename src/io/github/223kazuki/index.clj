(ns io.github.223kazuki.index
  (:require [io.github.223kazuki.common :refer [layout formatter]]
            [clj-time.format :as f]
            [clj-time.coerce :as c]))

(defn render [{posts :entries :as config}]
  (layout config
          [:div.index
           [:p.h1 "Posts"]
           (for [{:keys [title permalink tags date-published content] :as post} posts]
             [:div
              [:p.h2
               [:a {:href (:permalink post)} (:title post)]]
              [:div.fit.clearfix
               [:time.left (f/unparse formatter (c/from-date date-published))]
               (for [tag tags]
                 [:a.ml1.tag.left {:href (str "/tags/" tag ".html")} tag])]])]))
