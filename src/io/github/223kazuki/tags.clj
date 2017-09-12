(ns io.github.223kazuki.tags
  (:require [io.github.223kazuki.common :refer [layout]]))

(defn render [{global-meta :meta posts :entries entry :entry :as config}]
  (layout config
    [:h1 (:title entry)]
     [:ul.items.columns.small-12
      (for [post posts]
        [:li (:title post)])]))