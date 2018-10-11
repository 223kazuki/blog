(ns io.github.223kazuki.post
  (:require [io.github.223kazuki.common :refer [layout formatter]]
            [clj-time.format :as f]
            [clj-time.coerce :as c]))

(defn render [{:keys [entry] :as config}]
  (let [{:keys [:title :image :description :author-twitter
                :tags :date-published :content]} entry]
    (layout (assoc config :post
                   {:title title
                    :image image
                    :description description
                    :author-twitter author-twitter
                    :tags tags})
            [:div.post
             [:h1 title]
             [:div.fit.clearfix
              [:div.mb1.col.sm-col-12.md-col-3
               [:time.left
                (f/unparse formatter (c/from-date date-published))]]
              [:div.col.sm-col-12.md-col-9
               (for [tag (reverse tags)]
                 [:a.ml1.mb1.tag.right {:href (str "/tags/" tag ".html")} tag])]]
             [:hr.mb2]
             [:article.markdown-body.mb2
              content]])))
