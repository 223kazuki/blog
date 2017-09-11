(ns io.github.223kazuki.core
  (:require [hiccup.page :as hp]))

(defn render [data]
  (hp/html5
   [:div {:style "max-width: 900px; margin: 0 auto;"}
    [:a {:href "/"} "Home"]
    (-> data :entry :content)]))
