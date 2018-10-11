(ns io.github.223kazuki.tags
  (:require [io.github.223kazuki.common :refer [layout]]
            [clj-time.format :as f]
            [clj-time.coerce :as c]))

(def formatter (f/formatter "MM/dd/yyyy"))

(defn render [{global-meta :meta posts :entries entry :entry :as config}]
  (layout config
          [:p.h1.mt5 "Tag: " (:tag entry)]
          [:div.index.clearfix.mt3
           (for [{:keys [title permalink tags date-published content] :as post} posts]
             [:div
              [:p.h2
               [:a {:href (:permalink post)} (:title post)]]
              [:div.fit.clearfix
               [:time.left (f/unparse formatter (c/from-date date-published))]
               (for [tag tags]
                 [:a.ml1.tag.left {:href (str "/tags/" tag ".html")} tag])]])]))
