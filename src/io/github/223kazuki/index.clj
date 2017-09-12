(ns io.github.223kazuki.index
  (:require [io.github.223kazuki.common :refer [layout]]))

(defn render [{posts :entries :as config}]
  (layout config
    [:ul.list-reset
     (for [post posts]
       [:li
        [:a {:href (:permalink post)} (:title post)]])]))
