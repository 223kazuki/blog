(ns io.github.223kazuki.post
  (:require [io.github.223kazuki.common :refer [layout]]))

(defn render [config]
  (layout config
    [:div (-> config :entry :content)]))
