(ns io.github.223kazuki.about
  (:require [io.github.223kazuki.common :refer [layout]]))

(defn render [{global-meta :meta posts :entries :as config}]
  (layout config
    [:p "This is a demonstration of a static page, for content that won't change"]))