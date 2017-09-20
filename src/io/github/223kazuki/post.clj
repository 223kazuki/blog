(ns io.github.223kazuki.post
  (:require [io.github.223kazuki.common :refer [layout]]
            [clj-time.format :as f]
            [clj-time.coerce :as c]))

(def formatter (f/formatter "MM/dd/yyyy"))

(defn render [{:keys [entry] :as config}]
  (layout config
          (let [{:keys [title tags date-published content]} entry]
            [:div.post
             [:h1 title]
             [:div.fit.clearfix
              [:time.left (f/unparse formatter (c/from-date date-published))]
              (for [tag (reverse tags)]
                [:a.ml1.tag.right {:href (str "/tags/" tag ".html")} tag])]
             [:hr.mb2]
             [:article.markdown-body.mb2
              content]])))
